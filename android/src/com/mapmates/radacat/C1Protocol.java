package com.mapmates.radacat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Short, unencrypted text format from newsdk/g/c/a.s and newsdk/b/b.b. */
public final class C1Protocol {
    public static String publicText(byte[] p,int group){
        if(p.length<25 || (p[0]!=3 && p[0]!=22) || (p[3]&255)+4!=p.length || crc(p,4,16)!=(p[16]&255))return null;
        if((p[4]&255)!=0 || (p[5]&128)!=0 || (p[17]&255)!=p.length-17 || ByteBuffer.wrap(p).getInt(8)!=group)return null;
        try{
            String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(p,25,p.length-25)).toString();
            if(text.isEmpty())return null;
            for(int i=0;i<text.length();i++)if(Character.isISOControl(text.charAt(i)) && text.charAt(i)!='\n' && text.charAt(i)!='\t')return null;
            return text;
        }catch(java.nio.charset.CharacterCodingException e){return null;}
    }
    public static String namedText(String name,String message){
        name=name.trim();
        if(name.isEmpty() || name.contains("\n") || name.contains("\r") || name.contains(":"))throw new IllegalArgumentException("Enter a name without colons or line breaks.");
        if(name.getBytes(StandardCharsets.UTF_8).length>16)throw new IllegalArgumentException("Keep your name within 16 UTF-8 bytes.");
        if(message.trim().isEmpty())throw new IllegalArgumentException("Enter a message.");
        String combined=name+": "+message;
        int length=combined.getBytes(StandardCharsets.UTF_8).length;
        if(length>42)throw new IllegalArgumentException("Name and message use "+length+" of 42 UTF-8 bytes. Shorten the message.");
        return combined;
    }
    public static int crc(byte[] data, int start, int end) {
        int crc=0;
        for(int i=start;i<end;i++) {
            crc ^= data[i]&255;
            for(int bit=0;bit<8;bit++) crc=(crc&1)!=0?(crc>>>1)^0x8c:crc>>>1;
        }
        return crc;
    }
    public static byte[] text(String text,int source,int group,int channel,int sequence,int messageId) {
        byte[] value=text.getBytes(StandardCharsets.UTF_8);
        if(value.length<1 || value.length>42)throw new IllegalArgumentException("Use 1–42 UTF-8 bytes.");
        if(channel<0 || channel>59)throw new IllegalArgumentException("Channel must be 0–59.");
        ByteBuffer b=ByteBuffer.allocate(25+value.length);
        b.put((byte)2).put((byte)channel).put((byte)(channel+60)).put((byte)(21+value.length));
        b.put((byte)0).put((byte)0x60);
        b.order(ByteOrder.LITTLE_ENDIAN).putShort((short)((sequence&31)<<11));
        b.order(ByteOrder.BIG_ENDIAN).putInt(group).putInt(source);
        b.put((byte)crc(b.array(),4,16));
        b.put((byte)(value.length+8)).putInt(source).put((byte)0);
        b.order(ByteOrder.LITTLE_ENDIAN).putShort((short)messageId).put(value);
        return b.array();
    }
    public static String describe(byte[] p) {
        if(p.length==8 && p[0]==4)return "Device status response (delivery unconfirmed)";
        if(p.length<17)return "Unparsed packet ("+p.length+" bytes)";
        if((p[3]&255)+4!=p.length)return "Invalid packet length";
        if(crc(p,4,16)!=(p[16]&255))return "Header CRC failed · raw capture retained";
        int type=(p[4]>>>4)&3, subtype=p[4]&15;
        ByteBuffer b=ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN);
        long group=b.getInt(8)&0xffffffffL, source=b.getInt(12)&0xffffffffL;
        int seq=((p[6]&255)|((p[7]&255)<<8))>>>11;
        String header="channel "+(p[1]&255)+" · type "+type+"/"+subtype+" · seq "+seq+" · from "+source+" · to "+group;
        if(type==0 && subtype==0 && p.length>=25 && (p[5]&0x80)==0 && (p[17]&255)==p.length-17) {
            // Text is only interpreted as UTF-8 when group mode is explicitly unencrypted.
            return "Text-format packet · "+header;
        }
        return "C1 packet · "+header;
    }
}
