import com.mapmates.radacat.C1Protocol;
import com.mapmates.radacat.MessageGate;
import com.mapmates.radacat.GeoLinks;
public class ProtocolTest {
    public static void main(String[] args) {
        String location=C1Protocol.namedText("abcdefghijklmnop",GeoLinks.coordinate(-90,-180));
        if(location.getBytes().length!=42)throw new AssertionError("Worst-case coordinates must fit");
        if(GeoLinks.url("99.00000","180.00000")!=null)throw new AssertionError("Invalid map link");
        if(!GeoLinks.url("52.12345","13.12345").contains("query=52.12345,13.12345"))throw new AssertionError("Map link");
        byte[] received=C1Protocol.text("Test-PC: Hello phone!",424242,2222,0,0,1);received[0]=3;
        if(!"Test-PC: Hello phone!".equals(C1Protocol.publicText(received,2222)))throw new AssertionError("Readable received text");
        MessageGate gate=new MessageGate();
        if(!gate.accept(received,0))throw new AssertionError("First notification");
        received[2]=99;if(gate.accept(received,1000))throw new AssertionError("Repeated radio packet alert");
        received[23]=2;if(!gate.accept(received,2000))throw new AssertionError("New message ID");
        if(!gate.accept(received,302001))throw new AssertionError("Dedup expiry");
        if(C1Protocol.publicText(received,1111)!=null)throw new AssertionError("Wrong group");
        received[8]^=1;if(C1Protocol.publicText(received,2222)!=null)throw new AssertionError("Corrupted message");
        if(!C1Protocol.namedText(" Test ","Hello").equals("Test: Hello"))throw new AssertionError("Name prefix");
        try{C1Protocol.namedText("Test",new String(new char[37]).replace('\0','x'));throw new AssertionError("Combined byte limit");}catch(IllegalArgumentException expected){}
        try{C1Protocol.namedText("A:B","Hi");throw new AssertionError("Invalid name");}catch(IllegalArgumentException expected){}
        byte[] packet=C1Protocol.text("AAAA",1234567,2222,0,6,1);
        if(packet.length!=29 || (packet[3]&255)!=25)throw new AssertionError("Framing");
        if(packet[1]!=0 || packet[2]!=60 || packet[5]!=96 || packet[7]!=48)throw new AssertionError("Channel/header/sequence");
        if(!C1Protocol.describe(packet).startsWith("Text-format"))throw new AssertionError("Valid CRC");
        if(C1Protocol.crc("123456789".getBytes(),0,9)!=0xa1)throw new AssertionError("CRC-8/MAXIM known vector");
        packet[8]^=1;
        if(!C1Protocol.describe(packet).startsWith("Header CRC failed"))throw new AssertionError("Corruption");
        try {C1Protocol.text("",1,2222,0,0,0);throw new AssertionError("Empty input");}catch(IllegalArgumentException expected){}
        System.out.println("Protocol checks passed: framing, channel mapping, sequence, known CRC vector, corruption detection.");
    }
}
