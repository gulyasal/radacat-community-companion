package com.mapmates.radacat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local-only C1 companion. No radio commands or firmware writes. */
public class MainActivity extends Activity {
    private static final String BATTERY = "264607e0-63b9-431e-95ae-b359a00e0141";
    private static final String RX = "0039f9f7-e86c-4a2f-b612-650358c82d07";
    private static final String DIAG = "003d565b-fed5-4e93-80ce-76eebd2b45a2";
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int INK = Color.rgb(23,44,48), TEAL = Color.rgb(0,107,98);
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private ConnectionService connectionService;
    private final ServiceConnection serviceBinding=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            connectionService=((ConnectionService.LocalBinder)binder).service();connectionService.attach(gattCallback);
            connectionService.setConversationVisible(resumed && !showingSettings);
            BluetoothGatt existing=connectionService.current();
            if(existing!=null){gatt=existing;disconnectButton.setEnabled(true);log("Restored existing background connection.");existing.discoverServices();}
            else if(permissions() && connectionService.resumeConnection()){disconnectButton.setEnabled(true);setState("Reconnecting…","Restoring your saved Radacat connection.");}
            for(String line:connectionService.history())log("Background: "+line);
        }
        @Override public void onServiceDisconnected(ComponentName name){connectionService=null;}
    };
    private boolean scanning, ready, destroyed;
    private int packets;
    private TextView state, detail, charge, identity, packetCount, events;
    private TextView conversation;
    private FrameLayout screenRoot;
    private LinearLayout chatScreen;
    private ScrollView settingsScreen, conversationScroll;
    private TextView chatStatus;
    private boolean setupMode, showingSettings;
    private boolean resumed;
    private boolean locationPermissionPending;
    private EditText messageBox;
    private EditText groupBox, channelBox, nameBox;
    private int sequence, messageId;
    private final Map<String,Long> recentPackets=new LinkedHashMap<>();
    private String lastBattery="";
    private Button scanButton, refreshButton, disconnectButton;
    private LinearLayout devices;
    private final Map<String,Button> discovered = new LinkedHashMap<>();
    private final StringBuilder journal = new StringBuilder();
    private final ArrayDeque<Op> queue = new ArrayDeque<>();
    private Op pending;
    private final Runnable scanTimeout = () -> stopScan(true);
    private final Runnable operationTimeout = () -> {
        log("Bluetooth operation timed out. Disconnecting so it can be retried safely.");
        closeConnection(); setState("Connection timed out", "Keep the C1 nearby and tap Find devices to reconnect.");
    };
    private final Runnable connectionTimeout = () -> {
        closeConnection(); setState("Could not connect", "Close other apps connected to the C1, then try again.");
    };
    private static class Op {
        final BluetoothGattCharacteristic characteristic;
        final boolean subscribe;
        byte[] value;
        String text;
        Op(BluetoothGattCharacteristic c, boolean s) { characteristic=c; subscribe=s; }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        createScreen();
        bindService(new Intent(this,ConnectionService.class),serviceBinding,Context.BIND_AUTO_CREATE);
        log("RADACAT 0.9 · optional location links enabled.");
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},43);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0,dp(5),0,dp(5)); return t;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18),dp(14),dp(18),dp(14)); c.setBackground(shape(Color.WHITE,18));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(14));
        parent.addView(c,p); return c;
    }
    private Button button(String title, LinearLayout parent, View.OnClickListener action) {
        Button b=new Button(this); b.setText(title); b.setAllCaps(false); b.setTextColor(TEAL);
        b.setMinHeight(dp(48)); b.setOnClickListener(action); parent.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    private void createScreen() {
        int chatBackground=Color.rgb(16,38,43),composerBackground=Color.rgb(27,57,63);
        screenRoot=new FrameLayout(this);screenRoot.setBackgroundColor(chatBackground);setContentView(screenRoot);
        getWindow().setStatusBarColor(chatBackground);getWindow().setNavigationBarColor(chatBackground);getWindow().getDecorView().setSystemUiVisibility(0);
        if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().setStatusBarContrastEnforced(false);}
        chatScreen=new LinearLayout(this);chatScreen.setOrientation(LinearLayout.VERTICAL);chatScreen.setBackgroundColor(chatBackground);screenRoot.addView(chatScreen,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(12),dp(12),dp(10));header.setBackgroundColor(TEAL);chatScreen.addView(header);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView logo=text("RADACAT",23,Color.WHITE);logo.setTypeface(null,Typeface.BOLD);logo.setLetterSpacing(0.14f);brand.addView(logo);
        chatStatus=text("Connecting…",12,Color.rgb(208,234,227));brand.addView(chatStatus);
        ImageButton gear=new ImageButton(this);gear.setImageDrawable(new SettingsIcon(Color.WHITE));gear.setPadding(dp(13),dp(13),dp(13),dp(13));gear.setBackgroundColor(Color.TRANSPARENT);gear.setScaleType(ImageView.ScaleType.FIT_CENTER);gear.setContentDescription("Settings");gear.setOnClickListener(v->showSettings(true));header.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
        conversationScroll=new ScrollView(this);conversationScroll.setFillViewport(true);chatScreen.addView(conversationScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout chatBody=new LinearLayout(this);chatBody.setOrientation(LinearLayout.VERTICAL);chatBody.setPadding(dp(20),dp(16),dp(20),dp(16));conversationScroll.addView(chatBody);
        conversation=text(getPreferences(0).getString("conversation",""),17,Color.rgb(235,246,241));conversation.setTextIsSelectable(true);conversation.setLinkTextColor(Color.rgb(104,222,199));chatBody.addView(conversation);renderConversation();
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(12),dp(8),dp(12),dp(8));composer.setBackgroundColor(composerBackground);chatScreen.addView(composer);
        messageBox=new EditText(this);messageBox.setTextColor(Color.WHITE);messageBox.setHintTextColor(Color.rgb(175,200,198));messageBox.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(110,175,163)));messageBox.setHint("Message…");messageBox.setSingleLine(true);messageBox.setTextSize(16);messageBox.setEnabled(false);messageBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);composer.addView(messageBox,new LinearLayout.LayoutParams(0,dp(52),1));
        Button send=new Button(this);send.setText("Send");send.setAllCaps(false);send.setTextColor(Color.WHITE);send.setBackground(shape(TEAL,18));send.setEnabled(false);send.setOnClickListener(v->sendMessage());messageBox.setTag(send);composer.addView(send,new LinearLayout.LayoutParams(dp(80),dp(48)));
        messageBox.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendMessage();return true;}return false;});
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(238,245,242));
        settingsScreen=scroll;screenRoot.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20),dp(24),dp(20),dp(24));
        scroll.addView(page);
        if (Build.VERSION.SDK_INT>=30) screenRoot.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left,i.top,i.right,i.bottom); return insets;
        });
        setupMode=getSharedPreferences("connection",0).getString("address","").isEmpty();
        button("← Conversation",page,v->showSettings(false));
        TextView title=text(setupMode?"Set up your Radacat":"Settings",29,INK);title.setTypeface(null,Typeface.BOLD);page.addView(title);
        page.addView(text(setupMode?"1. Turn on your C1.\n2. Find devices and choose its LL… ID.\n3. Set your name, then start chatting.":"Device connection, your name and troubleshooting.",16,INK));
        LinearLayout statusCard=card(page);
        state=text("Ready to connect",21,TEAL); state.setTypeface(null,Typeface.BOLD); statusCard.addView(state);
        detail=text("Turn on your Radacat and keep it near your phone.",15,INK); statusCard.addView(detail);
        scanButton=button("Find / change Radacat",statusCard,v->startScan());
        disconnectButton=button("Disconnect",statusCard,v->{ closeConnection(); setState("Disconnected","Tap Find devices to connect again."); });
        disconnectButton.setEnabled(false);
        LinearLayout nearby=card(page); nearby.addView(text("Nearby Radacats",18,INK));
        devices=new LinearLayout(this); devices.setOrientation(LinearLayout.VERTICAL); nearby.addView(devices);
        devices.addView(text("Your C1 should appear as LL… or Radacat…",14,INK));
        LinearLayout live=card(page); live.addView(text("Your device",18,INK));
        charge=text("Battery —",27,TEAL); live.addView(charge);
        identity=text("Connect to read manufacturer, model and firmware.",14,INK); live.addView(identity);
        packetCount=text("Incoming packets: 0",14,INK); live.addView(packetCount);
        live.addView(text("Device / sender name",14,INK));
        nameBox=new EditText(this);nameBox.setSingleLine(true);nameBox.setHint("Defaults to your LL… device ID");nameBox.setText(getPreferences(0).getString("displayName",getSharedPreferences("connection",0).getString("name","")));live.addView(nameBox);
        button("Save name",live,v->{try{C1Protocol.namedText(nameBox.getText().toString(),"x");getPreferences(0).edit().putString("displayName",nameBox.getText().toString().trim()).apply();Toast.makeText(this,"Name saved",Toast.LENGTH_SHORT).show();}catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});
        CheckBox locationToggle=new CheckBox(this);locationToggle.setText("Include a recent phone location when sending");locationToggle.setChecked(getPreferences(0).getBoolean("shareLocation",true));live.addView(locationToggle);locationToggle.setOnCheckedChangeListener((b,checked)->getPreferences(0).edit().putBoolean("shareLocation",checked).apply());
        live.addView(text("If permitted, a location fix from the last 5 minutes is sent as a separate clickable message. Location is shared publicly with group 2222. Without a recent fix, only text is sent.",13,INK));
        refreshButton=button("Refresh device info",live,v->refresh()); refreshButton.setEnabled(false);
        groupBox=new EditText(this);groupBox.setText("2222");channelBox=new EditText(this);channelBox.setText("0");
        live.addView(text("Public group: 2222  ·  Channel: 0\nMessages are unencrypted. The 42-byte limit includes your name. Outgoing status confirms Bluetooth acceptance, not recipient delivery.",13,INK));
        LinearLayout history=card(page); history.addView(text("Connection activity",18,INK));
        events=text("",12,INK); events.setTypeface(Typeface.MONOSPACE); events.setTextIsSelectable(true); history.addView(events);
        button("Copy connection report",history,v->{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Radacat report",identity.getText()+"\n"+charge.getText()+"\n"+journal));
            Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();
        });
        button("Share connection report",history,v->{Intent report=new Intent(Intent.ACTION_SEND);report.setType("text/plain");report.putExtra(Intent.EXTRA_TEXT,identity.getText()+"\n"+charge.getText()+"\n"+journal);startActivity(Intent.createChooser(report,"Share C1 report"));});
        button("Message notification settings",history,v->startActivity(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE",getPackageName())));
        page.addView(text("RADACAT Companion 0.9",12,TEAL));
        showSettings(setupMode);
        FrameLayout splash=new FrameLayout(this);splash.setBackgroundColor(TEAL);TextView splashLogo=text("RADACAT",38,Color.WHITE);splashLogo.setGravity(Gravity.CENTER);splashLogo.setTypeface(null,Typeface.BOLD);splashLogo.setLetterSpacing(0.18f);splash.addView(splashLogo,new FrameLayout.LayoutParams(-1,-1));screenRoot.addView(splash,new FrameLayout.LayoutParams(-1,-1));main.postDelayed(()->screenRoot.removeView(splash),900);
    }
    private void showSettings(boolean show){showingSettings=show;settingsScreen.setVisibility(show?View.VISIBLE:View.GONE);chatScreen.setVisibility(show?View.GONE:View.VISIBLE);if(connectionService!=null)connectionService.setConversationVisible(resumed && !show);((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(messageBox.getWindowToken(),0);}
    @Override protected void onResume(){super.onResume();resumed=true;renderConversation();if(getIntent().getBooleanExtra("openConversation",false))showSettings(false);if(connectionService!=null)connectionService.setConversationVisible(!showingSettings);}
    @Override protected void onPause(){resumed=false;if(connectionService!=null)connectionService.setConversationVisible(false);super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("openConversation",false)){showSettings(false);renderConversation();}}
    @Override public void onBackPressed(){if(showingSettings && !setupMode){showSettings(false);return;}super.onBackPressed();}
    private void setState(String title,String explanation) { state.setText(title); detail.setText(explanation);if(chatStatus!=null)chatStatus.setText(title); }
    private void log(String message) {
        journal.append(new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())).append("  ").append(message).append('\n');
        if(journal.length()>14000) journal.delete(0,journal.indexOf("\n",journal.length()-10000)+1);
        events.setText(journal.toString()); android.util.Log.i("RadacatCompanion",message);
        try(java.io.FileOutputStream f=openFileOutput("connection-report.txt",MODE_PRIVATE)) {f.write(journal.toString().getBytes(StandardCharsets.UTF_8));}catch(java.io.IOException ignored){}
    }
    private boolean permissions() {
        if(Build.VERSION.SDK_INT>=31) return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void startScan() {
        if(adapter==null) {setState("Bluetooth unavailable","This phone does not expose a Bluetooth adapter.");return;}
        if(!permissions()) {
            requestPermissions(Build.VERSION.SDK_INT>=31 ? new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT} : new String[]{Manifest.permission.ACCESS_FINE_LOCATION},41); return;
        }
        if(!adapter.isEnabled()) {startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),42);return;}
        if(Build.VERSION.SDK_INT<31) {
            android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            if(!lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                setState("Enable Location to scan","Android 8–11 requires Location services for Bluetooth discovery.");
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;
            }
        }
        stopScan(false); closeConnection(); discovered.clear(); devices.removeAllViews();
        charge.setText("Battery —"); identity.setText("Looking for nearby C1 devices…");
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null) {setState("Scanner unavailable","Turn Bluetooth off and back on, then retry.");return;}
        try {
            scanning=true; scanButton.setEnabled(false); setState("Finding your Radacat…","Scanning for 15 seconds. Tap your device when it appears.");
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            main.postDelayed(scanTimeout,15000); log("Bluetooth scan started.");
        } catch(RuntimeException e) {stopScan(false);setState("Scan failed",e.getMessage());log("Scan error: "+e);}
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results) {
        super.onRequestPermissionsResult(request,names,results);
        if(request==44 && locationPermissionPending){locationPermissionPending=false;sendMessage();return;}
        if(request==41) {
            if(permissions())startScan();
            else {setState("Permission needed","Allow Nearby devices (or Location on Android 8–11) in the app settings, then retry.");log("Bluetooth permission denied.");}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {super.onActivityResult(request,result,data);if(request==42 && result==RESULT_OK)startScan();}
    private final ScanCallback scanCallback=new ScanCallback() {
        @Override public void onScanResult(int type,ScanResult result) {main.post(()->found(result));}
        @Override public void onScanFailed(int error) {main.post(()->{stopScan(false);setState("Scan failed","Android Bluetooth error "+error+". Wait a moment, then retry.");});}
    };
    private void found(ScanResult result) {
        if(!scanning || destroyed)return;
        BluetoothDevice device=result.getDevice(); String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
        if(name==null)name=device.getName(); if(name==null)return;
        String upper=name.toUpperCase(Locale.ROOT);
        if(!upper.startsWith("LL") && !upper.contains("RADACAT") && !upper.contains("TOMCAT"))return;
        String address=device.getAddress(); Button b=discovered.get(address);
        if(b==null) {b=button("",devices,v->connect(device));discovered.put(address,b);log("Found "+name+" ("+address+")");}
        b.setText(name+"   ·   "+result.getRssi()+" dBm\n"+address);
    }
    private void stopScan(boolean showResult) {
        main.removeCallbacks(scanTimeout);
        if(scanning && scanner!=null)try{scanner.stopScan(scanCallback);}catch(RuntimeException ignored){}
        scanning=false;scanButton.setEnabled(true);
        if(showResult) {
            setState(discovered.isEmpty()?"No Radacat found":"Choose a device",discovered.isEmpty()?"Make sure the C1 is on and disconnected from other phones or computers, then retry.":"Tap your Radacat below to connect.");
            if(discovered.isEmpty())devices.addView(text("No matching device found.",14,INK));
        }
    }
    private void connect(BluetoothDevice device) {
        applyDefaultName(device);
        stopScan(false);closeConnection(); setState("Connecting…",device.getName()+" · "+device.getAddress());
        identity.setText("");charge.setText("Battery —");packets=0;packetCount.setText("Incoming packets: 0");
        disconnectButton.setEnabled(true);log("Connecting to "+device.getAddress());
        try {if(connectionService==null)throw new IllegalStateException("Connection service is starting. Try again.");connectionService.connect(device);main.postDelayed(connectionTimeout,25000);}
        catch(RuntimeException e){closeConnection();setState("Connection failed",e.toString());}
    }
    private final BluetoothGattCallback gattCallback=new BluetoothGattCallback() {
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status) {main.post(()->{
            if(g!=gatt || destroyed)return;
            if(pending!=null && pending.text!=null)chatLine(status==0?"OUT · Bluetooth accepted":"FAILED",pending.text);
            log(status==0?"Bluetooth write completed · radio delivery unconfirmed":"Bluetooth write failed: "+status);
            complete(c.getUuid(),false);
        });}
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int newState) { main.post(()->{
            if(!destroyed && status==0 && newState==BluetoothProfile.STATE_CONNECTED && connectionService!=null && connectionService.current()==g)gatt=g;
            if(g!=gatt || destroyed)return;
            if(status==BluetoothGatt.GATT_SUCCESS && newState==BluetoothProfile.STATE_CONNECTED) {
                setState("Reading your C1…","Connected. Discovering available services.");log("Bluetooth connected.");
                if(!g.discoverServices()){closeConnection();setState("Discovery failed","Reconnect to retry.");}
            } else if(newState==BluetoothProfile.STATE_DISCONNECTED || status!=BluetoothGatt.GATT_SUCCESS) {
                log("Signal lost (Android status "+status+"). Automatic reconnection active.");
                gatt=null;ready=false;pending=null;queue.clear();main.removeCallbacks(operationTimeout);main.removeCallbacks(connectionTimeout);
                messageBox.setEnabled(false);((Button)messageBox.getTag()).setEnabled(false);
                setState("Reconnecting…","Keep the C1 on and nearby. Disconnect stops automatic reconnection.");
            }
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) {main.post(()->{
            if(g!=gatt || destroyed)return;main.removeCallbacks(connectionTimeout);
            if(status!=BluetoothGatt.GATT_SUCCESS || characteristic(BATTERY)==null || characteristic(RX)==null) {
                closeConnection();setState("C1 services unavailable","This device did not expose the expected C1 interface.");return;
            }
            log("C1 services verified. Reading identity and subscribing to updates.");
            read("00002a29-0000-1000-8000-00805f9b34fb");read("00002a24-0000-1000-8000-00805f9b34fb");
            read("fd84a818-5147-4b41-a1a6-07f123ce2398");read("1c7b1922-353e-4b4c-b474-3392ea4aa721");
            read("f921cf19-1453-4e6c-a380-550684841412");
            subscribe(BATTERY);subscribe(RX);subscribe(DIAG);pump();
        });}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status) {
            byte[] data=c.getValue()==null?new byte[0]:c.getValue().clone();
            main.post(()->{if(g!=gatt || destroyed)return;if(status==0)accept(c.getUuid().toString(),data,false);else log("Read failed: "+status);complete(c.getUuid(),false);});
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c) {
            byte[] data=c.getValue()==null?new byte[0]:c.getValue().clone();
            main.post(()->{if(g==gatt && !destroyed)accept(c.getUuid().toString(),data,true);});
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status) {main.post(()->{
            if(g!=gatt || destroyed)return;
            log((status==0?"Subscribed: ":"Subscription failed ("+status+"): ")+channel(d.getCharacteristic().getUuid().toString()));
            complete(d.getCharacteristic().getUuid(),true);
        });}
    };
    private BluetoothGattCharacteristic characteristic(String id) {
        if(gatt==null)return null;UUID uuid=UUID.fromString(id);
        for(BluetoothGattService s:gatt.getServices()) {BluetoothGattCharacteristic c=s.getCharacteristic(uuid);if(c!=null)return c;}return null;
    }
    private void read(String id) {BluetoothGattCharacteristic c=characteristic(id);if(c!=null && (c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)!=0)queue.add(new Op(c,false));}
    private void subscribe(String id) {BluetoothGattCharacteristic c=characteristic(id);if(c!=null)queue.add(new Op(c,true));}
    private void pump() {
        if(gatt==null || pending!=null)return;
        if(queue.isEmpty()) {ready=true;refreshButton.setEnabled(true);applyDefaultName(gatt.getDevice());messageBox.setEnabled(true);((Button)messageBox.getTag()).setEnabled(true);setState("Connected","Your C1 stays connected in the background.");if(setupMode){setupMode=false;showSettings(false);}return;}
        pending=queue.remove();boolean started=false;
        try {
            if(pending.subscribe) {
                BluetoothGattDescriptor d=pending.characteristic.getDescriptor(CCCD);
                if(d!=null && gatt.setCharacteristicNotification(pending.characteristic,true)) {
                    d.setValue((pending.characteristic.getProperties()&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    started=gatt.writeDescriptor(d);
                }
            } else if(pending.value!=null) {
                pending.characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                pending.characteristic.setValue(pending.value);
                started=gatt.writeCharacteristic(pending.characteristic);
            } else started=gatt.readCharacteristic(pending.characteristic);
        } catch(RuntimeException e){log("Bluetooth operation error: "+e.getMessage());}
        if(started)main.postDelayed(operationTimeout,10000);
        else {log("Could not start "+channel(pending.characteristic.getUuid().toString()));pending=null;main.post(this::pump);}
    }
    private void complete(UUID id,boolean subscription) {
        if(pending==null || pending.subscribe!=subscription || !pending.characteristic.getUuid().equals(id))return;
        main.removeCallbacks(operationTimeout);pending=null;pump();
    }
    private String channel(String id) {return id.equals(BATTERY)?"battery":id.equals(RX)?"incoming packets":id.equals(DIAG)?"diagnostics":id;}
    private void accept(String id,byte[] data,boolean notification) {
        if(id.equals(BATTERY)) {String value=new String(data,StandardCharsets.UTF_8).replace("\u0000","").trim();charge.setText(value.replace("battery level:","Battery "));if(!value.equals(lastBattery)){log(value);lastBattery=value;}}
        else if(id.equals(RX)) {
            packets++; packetCount.setText("Incoming packets: "+packets);
            String raw=hex(data); long now=SystemClock.elapsedRealtime();Long last=recentPackets.put(raw,now);
            if(recentPackets.size()>128)recentPackets.remove(recentPackets.keySet().iterator().next());
            if(last!=null && now-last<30000){log("Duplicate raw packet suppressed: "+raw);return;}
            log(C1Protocol.describe(data)+" · raw "+raw);
            renderConversation();conversationScroll.post(()->conversationScroll.fullScroll(View.FOCUS_DOWN));
        }
        else if(id.equals(DIAG)) {log("Diagnostic update: "+data.length+" bytes");}
        else {String value=new String(data,StandardCharsets.UTF_8).replace("\u0000","").trim();identity.append(value+"\n");log(value);}
    }
    private void sendMessage() {
        if(gatt==null || !ready || messageBox==null)return;
        if(getPreferences(0).getBoolean("shareLocation",true) && !hasLocationPermission() && !getPreferences(0).getBoolean("locationAsked",false)){
            getPreferences(0).edit().putBoolean("locationAsked",true).apply();locationPermissionPending=true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},44);return;
        }
        byte[] msg;
        try{msg=C1Protocol.namedText(nameBox.getText().toString(),messageBox.getText().toString()).getBytes(StandardCharsets.UTF_8);getPreferences(0).edit().putString("displayName",nameBox.getText().toString().trim()).apply();}
        catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        if(msg.length==0 || msg.length>42){Toast.makeText(this,"Enter 1–42 UTF-8 bytes",Toast.LENGTH_SHORT).show();return;}
        byte[] packet;
        try {packet=buildBroadcast(msg);}catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        BluetoothGattCharacteristic c=characteristic("fd2348e1-d0fd-45a7-aaf3-b5d911aa4555");
        if(c==null){log("Outbound characteristic unavailable");return;}
        if(pending!=null || !queue.isEmpty()){log("Bluetooth busy. Try sending after the current operation.");return;}
        String outgoing=new String(msg,StandardCharsets.UTF_8);chatLine("QUEUED",outgoing);
        messageBox.setText("");
        Op op=new Op(c,false);op.value=packet;op.text=outgoing;queue.add(op);
        String coordinates=recentCoordinates();
        if(coordinates!=null){
            String locationText=C1Protocol.namedText(nameBox.getText().toString(),coordinates);
            Op locationOp=new Op(c,false);locationOp.value=buildBroadcast(locationText.getBytes(StandardCharsets.UTF_8));locationOp.text=locationText;queue.add(locationOp);chatLine("QUEUED",locationText);
        }
        pump();
        log("Queued public test · delivery unconfirmed · raw "+hex(packet));
    }
    private byte[] buildBroadcast(byte[] msg) {
        int group=Integer.parseInt(groupBox.getText().toString()), ch=Integer.parseInt(channelBox.getText().toString());
        if(group<=0)throw new IllegalArgumentException("Enter a positive public group ID.");
        int source=getPreferences(0).getInt("sender",0);
        if(source==0){source=1000000+new java.security.SecureRandom().nextInt(100000000);getPreferences(0).edit().putInt("sender",source).apply();}
        getPreferences(0).edit().putString("group",Integer.toString(group)).putString("channel",Integer.toString(ch)).apply();
        return C1Protocol.text(new String(msg,StandardCharsets.UTF_8),source,group,ch,sequence++,++messageId);
    }
    private String hex(byte[] data){StringBuilder b=new StringBuilder();for(byte v:data)b.append(String.format(Locale.US,"%02x",v&255));return b.toString();}
    private void chatLine(String direction,String message){
        String history=conversation.getText()+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+" · "+direction+"\n"+message+"\n\n";
        if(history.length()>12000)history=history.substring(history.indexOf('\n',history.length()-10000)+1);
        getPreferences(0).edit().putString("conversation",history).apply();renderConversation();conversationScroll.post(()->conversationScroll.fullScroll(View.FOCUS_DOWN));
    }
    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private String recentCoordinates(){
        if(!getPreferences(0).getBoolean("shareLocation",true)||!hasLocationPermission())return null;
        android.location.LocationManager manager=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
        android.location.Location best=null;
        try{for(String provider:manager.getProviders(true)){
            android.location.Location fix=manager.getLastKnownLocation(provider);
            if(fix==null)continue;
            long age=SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos();
            if(age<0 || age>300000000000L)continue;
            if(best==null || fix.getElapsedRealtimeNanos()>best.getElapsedRealtimeNanos())best=fix;
        }}catch(SecurityException ignored){return null;}
        return best==null?null:GeoLinks.coordinate(best.getLatitude(),best.getLongitude());
    }
    private void renderConversation(){
        String history=getPreferences(0).getString("conversation","");android.text.SpannableString linked=new android.text.SpannableString(history);
        java.util.regex.Matcher m=GeoLinks.PATTERN.matcher(history);
        while(m.find()){
            final String url=GeoLinks.url(m.group(1),m.group(2));if(url==null)continue;
            linked.setSpan(new android.text.style.ClickableSpan(){@Override public void onClick(View v){try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(android.content.ActivityNotFoundException e){Toast.makeText(MainActivity.this,"No maps app or browser available",Toast.LENGTH_SHORT).show();}}},m.start(),m.end(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        conversation.setText(linked);conversation.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }
    private void applyDefaultName(BluetoothDevice device){
        if(!getPreferences(0).contains("displayName")){
            String value=device.getName()==null?device.getAddress():device.getName();nameBox.setText(value);
        }
    }
    private void refresh() {
        if(!ready || gatt==null || pending!=null)return;
        identity.setText("");refreshButton.setEnabled(false);ready=false;
        read("00002a29-0000-1000-8000-00805f9b34fb");read("00002a24-0000-1000-8000-00805f9b34fb");
        read("fd84a818-5147-4b41-a1a6-07f123ce2398");read("1c7b1922-353e-4b4c-b474-3392ea4aa721");read("f921cf19-1453-4e6c-a380-550684841412");read(DIAG);pump();
    }
    private void closeConnection() {
        main.removeCallbacks(connectionTimeout);main.removeCallbacks(operationTimeout);queue.clear();pending=null;ready=false;
        if(messageBox!=null){messageBox.setEnabled(false);if(messageBox.getTag()!=null)((Button)messageBox.getTag()).setEnabled(false);}
        BluetoothGatt old=gatt;gatt=null;
        if(connectionService!=null)connectionService.disconnect();
        if(refreshButton!=null)refreshButton.setEnabled(false);if(disconnectButton!=null)disconnectButton.setEnabled(false);
    }
    @Override protected void onStop(){stopScan(false);super.onStop();}
    @Override protected void onDestroy(){destroyed=true;stopScan(false);if(connectionService!=null)connectionService.detach();unbindService(serviceBinding);main.removeCallbacksAndMessages(null);super.onDestroy();}
}
