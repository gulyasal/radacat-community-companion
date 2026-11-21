"""Windows Radacat companion. BLE runs on a dedicated asyncio thread."""
import asyncio
import concurrent.futures
import json
import queue
import secrets
import sys
import threading
import time
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import ttk, filedialog, messagebox

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from bleak import BleakClient, BleakScanner
from protocol import encode, describe, named_message, map_links, coordinate
from app_paths import app_paths
DATA_ROOT,ASSET_ROOT=app_paths()

BATTERY = '264607e0-63b9-431e-95ae-b359a00e0141'
RX = '0039f9f7-e86c-4a2f-b612-650358c82d07'
DIAG = '003d565b-fed5-4e93-80ce-76eebd2b45a2'
TX = 'fd2348e1-d0fd-45a7-aaf3-b5d911aa4555'
INFO = ['00002a29-0000-1000-8000-00805f9b34fb', '00002a24-0000-1000-8000-00805f9b34fb', 'fd84a818-5147-4b41-a1a6-07f123ce2398', '1c7b1922-353e-4b4c-b474-3392ea4aa721', 'f921cf19-1453-4e6c-a380-550684841412']

class Companion:
    def __init__(self, window):
        self.window = window
        self.window.title('RADACAT · Desktop 0.9')
        self.window.geometry('800x760')
        self.window.minsize(680, 600)
        self.events = queue.Queue()
        self.client = None
        self.devices = []
        self.connected = False
        self.busy = False
        self.closing = False
        self.recent = {}
        self.sequence = 0
        self.message_id = 0
        self.records = []
        DATA_ROOT.mkdir(parents=True,exist_ok=True)
        self.config_path = DATA_ROOT / 'settings.json'
        try:
            self.settings = json.loads(self.config_path.read_text())
        except (OSError, ValueError):
            self.settings = {}
        # Recover the last verified device when upgrading versions that only logged it.
        if not self.settings.get('lastDeviceAddress'):
            import re
            for logfile in sorted((DATA_ROOT / 'logs').glob('*.jsonl'), reverse=True):
                matches = re.findall(r'Connected to (.*?) \\u00b7 ([0-9A-Fa-f:]{17})', logfile.read_text(encoding='utf-8'))
                if matches:
                    name, address = matches[-1]
                    self.settings.update(lastDeviceAddress=address, lastDeviceName=name)
                    self.config_path.write_text(json.dumps(self.settings, indent=2))
                    break
        self.want_connection = True
        self.retry_at = 0
        self.retry_count = 0
        self.source = self.settings.get('sender', secrets.randbelow(100000000) + 1000000)
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(target=self.run_loop, daemon=True)
        self.thread.start()
        self.build()
        self.window.protocol('WM_DELETE_WINDOW', self.close)
        self.window.after(100, self.poll)
        self.log('Ready. The last connected C1 will be reconnected automatically when available.')

    def run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def build(self):
        style = ttk.Style()
        style.theme_use('clam')
        bg, panel, teal, ink, mint = '#10262b','#1b393f','#006b62','#ebf6f1','#68dec7'
        style.configure('TFrame',background=bg)
        style.configure('TLabel',background=bg,foreground=ink,font=('Segoe UI',10))
        style.configure('TLabelframe',background=bg,bordercolor=panel)
        style.configure('TLabelframe.Label',background=bg,foreground=mint,font=('Segoe UI',11,'bold'))
        style.configure('TButton',padding=8,background=panel,foreground=ink,borderwidth=0)
        style.map('TButton',background=[('active',teal)],foreground=[('disabled','#819b9d')])
        style.configure('TEntry',fieldbackground=panel,foreground=ink,insertcolor=ink,padding=8)
        style.configure('TCombobox',fieldbackground=panel,background=panel,foreground=ink,arrowcolor=mint)
        style.map('TCombobox',fieldbackground=[('readonly',panel)],foreground=[('readonly',ink)])
        style.configure('TCheckbutton',background=bg,foreground=ink)
        style.map('TCheckbutton',background=[('active',panel)])
        self.window.configure(background=bg)
        self.chat_page=ttk.Frame(self.window)
        self.settings_page=ttk.Frame(self.window,padding=20)
        page=self.settings_page
        header=tk.Frame(self.chat_page,background=teal,padx=18,pady=12)
        header.pack(fill='x')
        try:
            from PIL import Image,ImageTk
            icon=Image.open(ASSET_ROOT/'radacat-icon.png')
            self.app_icon=ImageTk.PhotoImage(icon.resize((128,128),Image.Resampling.LANCZOS))
            self.header_icon=ImageTk.PhotoImage(icon.resize((48,48),Image.Resampling.LANCZOS))
            self.window.iconphoto(True,self.app_icon)
            tk.Label(header,image=self.header_icon,background=teal).pack(side='left',padx=(0,12))
        except (OSError,ImportError):
            pass
        brand=tk.Frame(header,background=teal)
        brand.pack(side='left',fill='x',expand=True)
        tk.Label(brand,text='RADACAT',background=teal,foreground=ink,font=('Segoe UI',20,'bold')).pack(anchor='w')
        self.status = tk.StringVar(value='Disconnected')
        self.battery = tk.StringVar(value='Battery —')
        tk.Label(brand,textvariable=self.status,background=teal,foreground='#cfebe4',font=('Segoe UI',10)).pack(anchor='w')
        gear=tk.Canvas(header,width=44,height=44,background=teal,highlightthickness=0,cursor='hand2',takefocus=True)
        import math
        points=[]
        for tooth in range(8):
            for offset,radius in [(-22.5,12),(-13,12),(-10,18),(10,18),(13,12),(22.5,12)]:
                angle=math.radians(tooth*45+offset)
                points.extend((22+math.cos(angle)*radius,22+math.sin(angle)*radius))
        gear.create_polygon(points,fill=ink,outline=ink)
        gear.create_oval(16,16,28,28,fill=teal,outline=teal)
        gear.pack(side='right')
        gear.bind('<Button-1>',lambda _:self.show_settings(True))
        gear.bind('<Return>',lambda _:self.show_settings(True))
        gear.bind('<space>',lambda _:self.show_settings(True))
        self.window.bind('<Escape>',lambda _:self.show_settings(False))
        ttk.Button(page,text='← Conversation',command=lambda:self.show_settings(False)).pack(anchor='w',pady=(0,12))
        ttk.Label(page,text='Settings',font=('Segoe UI',24,'bold')).pack(anchor='w',pady=(0,12))
        ttk.Label(page, textvariable=self.status, font=('Segoe UI', 12, 'bold')).pack(anchor='w')
        ttk.Label(page, textvariable=self.battery).pack(anchor='w', pady=5)
        row = ttk.Frame(page)
        row.pack(fill='x')
        self.scan_button = ttk.Button(row, text='Find devices', command=lambda: self.submit(self.scan()))
        self.scan_button.pack(side='left')
        self.choice = ttk.Combobox(row, state='readonly', width=43)
        self.choice.pack(side='left', padx=8, fill='x', expand=True)
        self.connect_button = ttk.Button(row, text='Connect', command=self.connect_selected)
        self.connect_button.pack(side='left')
        self.disconnect_button = ttk.Button(row, text='Disconnect', command=self.manual_disconnect)
        self.disconnect_button.pack(side='left', padx=(8, 0))
        settings = ttk.LabelFrame(page, text='Public test configuration', padding=10)
        settings.pack(fill='x', pady=12)
        self.group = tk.StringVar(value=str(self.settings.get('group', 2222)))
        self.channel = tk.StringVar(value=str(self.settings.get('channel', 0)))
        ttk.Label(settings, text='Group ID').grid(row=0, column=0, sticky='w')
        ttk.Entry(settings, textvariable=self.group, width=12).grid(row=0, column=1, padx=8)
        ttk.Label(settings, text='Channel (0–59)').grid(row=0, column=2)
        ttk.Entry(settings, textvariable=self.channel, width=8).grid(row=0, column=3, padx=8)
        ttk.Label(settings, text=f'Local sender ID: {self.source}').grid(row=0, column=4, padx=10)
        ttk.Label(settings, text='Unencrypted test mode. These fields select packet channels; they do not reconfigure the radio.').grid(row=1, column=0, columnspan=5, pady=(10, 0), sticky='w')
        self.name = tk.StringVar(value=self.settings.get('displayName', 'PC'))
        ttk.Label(settings, text='Your name').grid(row=2, column=0, pady=(10,0), sticky='w')
        ttk.Entry(settings, textvariable=self.name, width=18).grid(row=2, column=1, columnspan=2, pady=(10,0), sticky='w', padx=8)
        ttk.Button(settings, text='Save name', command=self.save_name).grid(row=2, column=3, pady=(10,0))
        self.share_location=tk.BooleanVar(value=self.settings.get('shareLocation',False))
        self.latitude=tk.StringVar(value=self.settings.get('latitude',''))
        self.longitude=tk.StringVar(value=self.settings.get('longitude',''))
        ttk.Checkbutton(settings,text='Include fixed location with messages',variable=self.share_location).grid(row=3,column=0,columnspan=4,sticky='w',pady=(12,4))
        ttk.Label(settings,text='Latitude').grid(row=4,column=0,sticky='w')
        ttk.Entry(settings,textvariable=self.latitude,width=13).grid(row=4,column=1,padx=8,sticky='w')
        ttk.Label(settings,text='Longitude').grid(row=4,column=2,sticky='w')
        ttk.Entry(settings,textvariable=self.longitude,width=13).grid(row=4,column=3,padx=8,sticky='w')
        ttk.Button(settings,text='Save location',command=self.save_location).grid(row=4,column=4)
        ttk.Label(settings,text='Decimal degrees · north/east positive, south/west negative. Shared publicly as a separate Maps link.').grid(row=5,column=0,columnspan=5,sticky='w',pady=(6,0))
        composer=tk.Frame(self.chat_page,background=panel,padx=14,pady=12)
        composer.pack(side='bottom',fill='x')
        row=ttk.Frame(composer)
        row.pack(fill='x')
        self.message = ttk.Entry(row, font=('Segoe UI', 12))
        self.message.pack(side='left', fill='x', expand=True)
        self.message.bind('<Return>', lambda _: self.send())
        self.send_button = ttk.Button(row, text='Send', command=self.send)
        self.send_button.pack(side='left', padx=(8, 0))
        ttk.Label(page, text='Messages are public and unencrypted. Maximum 42 UTF-8 bytes including your name.\nOutgoing status confirms Bluetooth acceptance, not delivery.',wraplength=620).pack(anchor='w', pady=10)
        chat_frame=ttk.Frame(self.chat_page,padding=(20,16))
        chat_frame.pack(fill='both',expand=True)
        self.conversation=tk.Text(chat_frame,wrap='word',font=('Segoe UI',13),state='disabled',background=bg,foreground=ink,selectbackground=teal,relief='flat',highlightthickness=0,padx=6,pady=8)
        chat_bar=ttk.Scrollbar(chat_frame,command=self.conversation.yview)
        self.conversation.configure(yscrollcommand=chat_bar.set)
        chat_bar.pack(side='right',fill='y')
        self.conversation.pack(fill='both',expand=True)
        log_frame = ttk.Frame(page)
        log_frame.pack(fill='both', expand=True)
        ttk.Label(log_frame,text='Troubleshooting log',font=('Segoe UI',11,'bold')).pack(anchor='w',pady=(0,6))
        self.output = tk.Text(log_frame, height=6, wrap='word', font=('Consolas', 10), state='disabled', background=panel,foreground=ink,relief='flat')
        bar = ttk.Scrollbar(log_frame, command=self.output.yview)
        self.output.configure(yscrollcommand=bar.set)
        bar.pack(side='right', fill='y')
        self.output.pack(fill='both', expand=True)
        buttons = ttk.Frame(page)
        buttons.pack(fill='x', pady=(10, 0))
        ttk.Button(buttons, text='Save report…', command=self.export).pack(side='left')
        ttk.Button(buttons, text='Copy report', command=self.copy).pack(side='left', padx=8)
        ttk.Label(buttons, text='Desktop 0.9 · Logs saved automatically.').pack(side='right')
        self.controls()
        self.show_settings(not bool(self.settings.get('lastDeviceAddress')))

    def show_settings(self,show):
        self.chat_page.pack_forget()
        self.settings_page.pack_forget()
        (self.settings_page if show else self.chat_page).pack(fill='both',expand=True)
        if not show:
            self.message.focus_set()

    def controls(self):
        self.scan_button.configure(state='disabled' if self.busy or self.connected else 'normal')
        self.connect_button.configure(state='normal' if self.devices and not self.busy and not self.connected else 'disabled')
        self.disconnect_button.configure(state='normal' if (self.connected or self.want_connection) and not self.busy else 'disabled')
        self.send_button.configure(state='normal' if self.connected and not self.busy else 'disabled')

    def submit(self, coroutine):
        if self.busy or self.closing:
            coroutine.close()
            return
        self.busy = True
        self.controls()
        future = asyncio.run_coroutine_threadsafe(coroutine, self.loop)
        def done(f):
            try:
                f.result()
            except Exception as exc:
                self.events.put(('log', f'Operation failed: {exc}'))
            self.events.put(('idle', None))
        future.add_done_callback(done)

    async def scan(self):
        self.events.put(('status', 'Scanning for 12 seconds…'))
        found = await BleakScanner.discover(timeout=12, return_adv=True)
        devices = []
        for device, adv in found.values():
            name = adv.local_name or device.name or ''
            if name.upper().startswith(('LL', 'RADACAT', 'TOMCAT')):
                devices.append((device, f'{name} · {device.address} · {adv.rssi} dBm'))
        self.events.put(('devices', devices))
        self.events.put(('status', f'Found {len(devices)} candidate device(s)'))

    def connect_selected(self):
        index = self.choice.current()
        if index >= 0:
            self.want_connection = True
            self.submit(self.connect(self.devices[index][0]))

    def manual_disconnect(self):
        self.want_connection = False
        self.submit(self.disconnect())

    async def reconnect(self, address):
        self.events.put(('status', 'Looking for saved Radacat ' + address + '…'))
        device = await BleakScanner.find_device_by_address(address, timeout=10)
        if device is None:
            self.events.put(('status', 'Saved Radacat unavailable · retrying automatically'))
            return
        await self.connect(device)

    async def connect(self, device):
        await self.disconnect()
        self.events.put(('status', f'Connecting to {device.name}…'))
        client = BleakClient(device, disconnected_callback=self.disconnected, timeout=25)
        self.client = client
        try:
            await client.connect()
            for uuid in (RX, TX, BATTERY):
                if client.services.get_characteristic(uuid) is None:
                    raise RuntimeError('Expected C1 service missing. This may not be a compatible Radacat.')
            for uuid in INFO:
                if client.services.get_characteristic(uuid):
                    data = await asyncio.wait_for(client.read_gatt_char(uuid), 8)
                    self.events.put(('log', data.decode('utf-8', errors='replace').strip('\0 ')))
            for uuid in (BATTERY, RX, DIAG):
                if client.services.get_characteristic(uuid):
                    await client.start_notify(uuid, self.notification)
            self.events.put(('remember', (device.address, device.name or device.address)))
            self.events.put(('connection', True))
            self.events.put(('status', f'Connected to {device.name} · {device.address}'))
        except Exception:
            await self.disconnect()
            raise

    def disconnected(self, client):
        if client is self.client:
            self.events.put(('connection', False))
            self.events.put(('status', 'Disconnected'))

    async def disconnect(self):
        old, self.client = self.client, None
        if old and old.is_connected:
            await old.disconnect()
        self.events.put(('connection', False))
        self.events.put(('status', 'Disconnected'))

    def notification(self, char, data):
        self.events.put(('notification', (str(char.uuid), bytes(data))))

    def send(self):
        if not self.connected or self.busy:
            return
        try:
            group, channel = int(self.group.get()), int(self.channel.get())
            texts=[named_message(self.name.get(),self.message.get())]
            if self.share_location.get():
                texts.append(named_message(self.name.get(),coordinate(self.latitude.get(),self.longitude.get())))
            packets=[(encode(text,self.source,group,channel,self.sequence+i,self.message_id+i+1),text) for i,text in enumerate(texts)]
            self.save_settings(sender=self.source, group=group, channel=channel, displayName=self.name.get().strip(),shareLocation=self.share_location.get(),latitude=self.latitude.get().strip(),longitude=self.longitude.get().strip())
        except (ValueError, OSError) as exc:
            messagebox.showerror('Message not sent', str(exc))
            return
        self.sequence += len(packets)
        self.message_id += len(packets)
        for _,text in packets:
            self.chat_line('QUEUED',text)
        self.message.delete(0,'end')
        self.submit(self.write_messages(packets))

    def save_location(self):
        try:
            if self.share_location.get():
                coordinate(self.latitude.get(),self.longitude.get())
            self.save_settings(shareLocation=self.share_location.get(),latitude=self.latitude.get().strip(),longitude=self.longitude.get().strip())
            self.log('Fixed location settings saved; sharing '+('enabled' if self.share_location.get() else 'disabled'))
        except (ValueError,OSError) as exc:
            messagebox.showerror('Location not saved',str(exc))

    async def write_messages(self,packets):
        for index,(packet,text) in enumerate(packets):
            try:
                await self.write(packet,text)
                if index+1<len(packets):
                    await asyncio.sleep(0.4)
            except Exception:
                for _,remaining in packets[index+1:]:
                    self.events.put(('chat',('NOT SENT',remaining)))
                raise

    def save_name(self):
        try:
            named_message(self.name.get(), 'x')
            self.save_settings(sender=self.source, displayName=self.name.get().strip(), group=int(self.group.get()), channel=int(self.channel.get()))
            self.log('Display name saved: ' + self.name.get().strip())
        except (ValueError, OSError) as exc:
            messagebox.showerror('Name not saved', str(exc))

    def save_settings(self, **changes):
        values = dict(self.settings, **changes)
        temporary = self.config_path.with_suffix('.tmp')
        temporary.write_text(json.dumps(values, indent=2), encoding='utf-8')
        temporary.replace(self.config_path)
        self.settings = values

    async def write(self, packet, text):
        if self.client is None or not self.client.is_connected:
            raise RuntimeError('Connect to a C1 first.')
        try:
            await self.client.write_gatt_char(TX, packet, response=False)
        except Exception:
            self.events.put(('chat',('FAILED',text)))
            raise
        self.events.put(('chat',('OUT · Bluetooth accepted',text)))
        self.events.put(('log', 'Bluetooth write completed; radio delivery unconfirmed · TX ' + packet.hex()))

    def poll(self):
        while True:
            try:
                kind, data = self.events.get_nowait()
            except queue.Empty:
                break
            if kind == 'log':
                self.log(data)
            elif kind == 'chat':
                self.chat_line(*data)
            elif kind == 'status':
                self.status.set(data)
                self.log(data)
            elif kind == 'idle':
                self.busy = False
                if not self.connected:
                    self.retry_count += 1
                    self.retry_at = time.monotonic() + min(30, 2 ** min(self.retry_count, 5))
            elif kind == 'remember':
                address, name = data
                try:
                    self.save_settings(lastDeviceAddress=address, lastDeviceName=name)
                except OSError as exc:
                    self.log('Could not remember device: ' + str(exc))
                self.choice.set(name + ' · ' + address)
            elif kind == 'connection':
                self.connected = data
                if data:
                    self.recent.clear()
                    self.retry_count = 0
                else:
                    self.retry_at = time.monotonic() + 2
            elif kind == 'devices':
                self.devices = data
                self.choice['values'] = [label for _, label in data]
                if data:
                    self.choice.current(0)
            elif kind == 'notification':
                uuid, packet = data
                if uuid == BATTERY:
                    value = packet.decode('utf-8', errors='replace').strip('\0 ')
                    if self.battery.get() != value:
                        self.battery.set(value)
                        self.log(value)
                elif uuid == RX:
                    now = time.monotonic()
                    previous = self.recent.get(packet, -100)
                    self.recent[packet] = now
                    if len(self.recent) > 128:
                        del self.recent[next(iter(self.recent))]
                    try:
                        group = int(self.group.get())
                    except ValueError:
                        group = 0
                    label = 'Duplicate raw packet' if now - previous < 30 else describe(packet, group)
                    marker='\nPossible public text (unencrypted mode): '
                    if now-previous>=30 and marker in label:
                        self.chat_line('IN',label.split(marker,1)[1])
                    self.log(label + '\nRX ' + packet.hex())
                else:
                    self.log(f'Diagnostic update ({len(packet)} bytes): ' + packet.hex())
        self.controls()
        address = self.settings.get('lastDeviceAddress')
        if address and self.want_connection and not self.connected and not self.busy and not self.closing and time.monotonic() >= self.retry_at:
            self.submit(self.reconnect(address))
        if not self.closing:
            self.window.after(100, self.poll)

    def log(self, message):
        record = {'time': datetime.now().astimezone().isoformat(), 'message': message}
        self.records.append(record)
        self.output.configure(state='normal')
        self.output.insert('end', record['time'][11:19] + '  ' + message + '\n\n')
        if len(self.records) > 2000:
            self.records = self.records[-1500:]
            self.output.delete('1.0', '501.0')
        self.output.see('end')
        self.output.configure(state='disabled')
        try:
            folder = DATA_ROOT / 'logs'
            folder.mkdir(exist_ok=True)
            with (folder / (datetime.now().strftime('%Y-%m-%d') + '.jsonl')).open('a', encoding='utf-8') as f:
                f.write(json.dumps(record) + '\n')
        except OSError:
            pass

    def report(self):
        return '\n'.join(r['time'] + ' ' + r['message'] for r in self.records)

    def chat_line(self,direction,message):
        self.conversation.configure(state='normal')
        self.conversation.insert('end',datetime.now().strftime('%H:%M:%S')+' · '+direction+'\n')
        base=self.conversation.index('end-1c')
        self.conversation.insert('end',message+'\n\n')
        for start,end,url in map_links(message):
            tag='map_'+str(len(self.conversation.tag_names()))
            self.conversation.tag_add(tag,f'{base}+{start}c',f'{base}+{end}c')
            self.conversation.tag_configure(tag,foreground='#68dec7',underline=True)
            self.conversation.tag_bind(tag,'<Button-1>',lambda event,link=url: __import__('webbrowser').open(link))
            self.conversation.tag_bind(tag,'<Enter>',lambda event:self.conversation.configure(cursor='hand2'))
            self.conversation.tag_bind(tag,'<Leave>',lambda event:self.conversation.configure(cursor='xterm'))
        self.conversation.see('end')
        self.conversation.configure(state='disabled')

    def export(self):
        path = filedialog.asksaveasfilename(defaultextension='.txt', initialfile='Radacat-report.txt')
        if path:
            try:
                Path(path).write_text(self.report(), encoding='utf-8')
            except OSError as exc:
                messagebox.showerror('Could not save report', str(exc))

    def copy(self):
        self.window.clipboard_clear()
        self.window.clipboard_append(self.report())

    def close(self):
        if self.closing:
            return
        self.closing = True
        self.want_connection = False
        self.status.set('Closing Bluetooth connection…')
        future = asyncio.run_coroutine_threadsafe(self.disconnect(), self.loop)
        deadline = time.monotonic() + 5
        def finish():
            if future.done() or time.monotonic() > deadline:
                self.loop.call_soon_threadsafe(self.loop.stop)
                self.window.destroy()
            else:
                self.window.after(100, finish)
        finish()

if __name__ == '__main__':
    window = tk.Tk()
    Companion(window)
    window.mainloop()
