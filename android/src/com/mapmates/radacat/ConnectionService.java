package com.mapmates.radacat;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Owns the GATT connection independently of the Activity. */
public class ConnectionService extends Service {
    public class LocalBinder extends Binder { public ConnectionService service(){return ConnectionService.this;} }
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothDevice device;
    private BluetoothGattCallback observer;
    private boolean wanted, connected;
    private boolean conversationVisible;
    private final MessageGate messages=new MessageGate();
    public void setConversationVisible(boolean visible){conversationVisible=visible;}
    private int attempt;
    private final ArrayList<String> received=new ArrayList<>();
    private final ArrayDeque<BluetoothGattCharacteristic> subscriptions=new ArrayDeque<>();
    private void subscribeNext(BluetoothGatt g){
        if(g!=gatt || subscriptions.isEmpty())return;
        BluetoothGattCharacteristic c=subscriptions.remove();
        BluetoothGattDescriptor d=c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if(d!=null && g.setCharacteristicNotification(c,true)){
            d.setValue((c.getProperties()&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0?BluetoothGattDescriptor.ENABLE_INDICATION_VALUE:BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if(g.writeDescriptor(d))return;
        }
        subscribeNext(g);
    }
    private final Runnable retry=()->open();
    private final Runnable timeout=()->lost(gatt);
    @Override public IBinder onBind(Intent i){return new LocalBinder();}
    public BluetoothGatt current(){return connected?gatt:null;}
    public boolean active(){return wanted;}
    public void attach(BluetoothGattCallback cb){observer=cb;}
    public void detach(){observer=null;conversationVisible=false;}
    public ArrayList<String> history(){return new ArrayList<>(received);}
    public boolean resumeConnection(){
        if(wanted)return true;
        android.content.SharedPreferences saved=getSharedPreferences("connection",0);
        String address=saved.getString("address","");
        if(!saved.getBoolean("wanted",false) || address.isEmpty())return false;
        try{
            BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            if(manager==null || manager.getAdapter()==null || !manager.getAdapter().isEnabled())return false;
            connect(manager.getAdapter().getRemoteDevice(address));return true;
        }catch(RuntimeException e){record("Could not restore connection: "+e.getMessage());return false;}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && "disconnect".equals(intent.getAction())){disconnect();return START_NOT_STICKY;}
        notifyState(connected?"Connected to Radacat":"Keeping Radacat connected");
        if(!wanted && !resumeConnection()){stopForeground(true);stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    private void notifyState(String text){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("c1_connection","Radacat connection",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ConnectionService.class).setAction("disconnect"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"c1_connection").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Radacat Companion").setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"Disconnect",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(7,notification,16);else startForeground(7,notification);
    }
    private void incomingMessage(byte[] packet){
        String text=C1Protocol.publicText(packet,2222);
        if(text==null || !messages.accept(packet,SystemClock.elapsedRealtime()))return;
        android.content.SharedPreferences chat=getSharedPreferences("MainActivity",0);
        String timestamp=new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date());
        String history=chat.getString("conversation","")+timestamp+" · IN\n"+text+"\n\n";
        if(history.length()>12000)history=history.substring(history.indexOf('\n',history.length()-10000)+1);
        chat.edit().putString("conversation",history).apply();
        if(conversationVisible)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel("c1_messages","Incoming messages",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("New Radacat public-channel text messages");channel.enableVibration(true);nm.createNotificationChannel(channel);
        if(!nm.areNotificationsEnabled() || (Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)){
            record("Message saved; notifications disabled by Android");return;
        }
        Intent intent=new Intent(this,MainActivity.class).putExtra("openConversation",true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(this,2,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification generic=new Notification.Builder(this,"c1_messages").setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("RADACAT").setContentText("New message").build();
        Notification notification=new Notification.Builder(this,"c1_messages").setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("RADACAT · New message").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(generic).build();
        nm.notify(100,notification);record("Incoming message notification posted");
    }
    public void connect(BluetoothDevice target){
        release();device=target;wanted=true;attempt=0;
        getSharedPreferences("connection",0).edit().putString("address",target.getAddress()).putString("name",target.getName()==null?target.getAddress():target.getName()).putBoolean("wanted",true).apply();
        record("Maintaining connection to "+target.getAddress());
        startForegroundService(new Intent(this,ConnectionService.class));
        notifyState("Connecting to "+target.getName());open();
    }
    private void open(){
        if(!wanted || device==null)return;
        main.removeCallbacks(retry);main.removeCallbacks(timeout);
        try{
            notifyState("Connecting to "+device.getName());
            gatt=device.connectGatt(getApplicationContext(),false,callback,BluetoothDevice.TRANSPORT_LE);
            main.postDelayed(timeout,25000);
        }catch(RuntimeException e){record("Connection attempt failed: "+e.getMessage());lost(gatt);}
    }
    private void record(String text){
        String line=new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"  "+text;
        received.add(line);if(received.size()>300)received.remove(0);
        android.util.Log.i("RadacatService",line);
        try(java.io.FileOutputStream f=openFileOutput("background-packets.txt",MODE_APPEND)){f.write((line+"\n").getBytes(StandardCharsets.UTF_8));}catch(java.io.IOException ignored){}
    }
    private void lost(BluetoothGatt old){
        if(old!=gatt)return;
        main.removeCallbacks(timeout);connected=false;gatt=null;
        if(old!=null)old.close();
        if(wanted){long delay=Math.min(30000,2000L<<Math.min(attempt++,4));record("Link lost; retry in "+delay+" ms");notifyState("Reconnecting to Radacat…");main.removeCallbacks(retry);main.postDelayed(retry,delay);}
    }
    private void release(){
        main.removeCallbacks(retry);main.removeCallbacks(timeout);connected=false;
        BluetoothGatt old=gatt;gatt=null;
        if(old!=null){old.disconnect();old.close();}
    }
    public void disconnect(){wanted=false;getSharedPreferences("connection",0).edit().putBoolean("wanted",false).apply();record("Disconnected by app/user; retries stopped");release();stopForeground(true);stopSelf();}
    @Override public void onDestroy(){wanted=false;release();super.onDestroy();}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){main.post(()->{
            if(g!=gatt)return;
            record("Bluetooth state="+state+" status="+status);
            if(status==0 && state==BluetoothProfile.STATE_CONNECTED){connected=true;attempt=0;main.removeCallbacks(timeout);notifyState("Connected to "+device.getName());if(observer==null)g.discoverServices();}
            BluetoothGattCallback cb=observer;
            if(cb!=null)cb.onConnectionStateChange(g,status,state);
            if(state==BluetoothProfile.STATE_DISCONNECTED || status!=0)lost(g);
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){BluetoothGattCallback cb=observer;if(cb!=null)cb.onServicesDiscovered(g,status);else if(status==0)main.post(()->{
            subscriptions.clear();for(BluetoothGattService s:g.getServices())for(BluetoothGattCharacteristic c:s.getCharacteristics()){
                String id=c.getUuid().toString();if(id.equals("0039f9f7-e86c-4a2f-b612-650358c82d07") || id.equals("264607e0-63b9-431e-95ae-b359a00e0141"))subscriptions.add(c);
            }subscribeNext(g);
        });}
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){BluetoothGattCallback cb=observer;if(cb!=null)cb.onCharacteristicRead(g,c,status);}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){BluetoothGattCallback cb=observer;if(cb!=null)cb.onCharacteristicWrite(g,c,status);}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){BluetoothGattCallback cb=observer;if(cb!=null)cb.onDescriptorWrite(g,d,status);else main.post(()->subscribeNext(g));}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){
            byte[] value=c.getValue()==null?new byte[0]:c.getValue().clone();String uuid=c.getUuid().toString();
            if(uuid.equals("0039f9f7-e86c-4a2f-b612-650358c82d07")){StringBuilder hex=new StringBuilder();for(byte b:value)hex.append(String.format(Locale.US,"%02x",b&255));main.post(()->{record("RX "+hex);incomingMessage(value);});}
            BluetoothGattCallback cb=observer;if(cb!=null)cb.onCharacteristicChanged(g,c);
        }
    };
}
