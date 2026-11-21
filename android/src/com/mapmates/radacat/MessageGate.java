package com.mapmates.radacat;
import java.util.*;
/** Deduplicates logical text messages across repeated radio packets. */
public final class MessageGate {
    private final LinkedHashMap<String,Long> seen=new LinkedHashMap<>();
    public boolean accept(byte[] packet,long now){
        if(packet.length<25)return false;
        String key=Arrays.toString(Arrays.copyOfRange(packet,12,16))+Arrays.toString(Arrays.copyOfRange(packet,18,packet.length));
        Long previous=seen.get(key);
        if(previous!=null && now-previous<300000)return false;
        seen.put(key,now);if(seen.size()>256)seen.remove(seen.keySet().iterator().next());return true;
    }
}
