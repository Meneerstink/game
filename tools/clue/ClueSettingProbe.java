import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only struct/selector proof for source Settings. Never accesses a target cache. */
class ClueSettingProbe {
    static String hash(byte[] raw) throws Exception {
        var out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format("%02x",b));return out.toString();
    }
    public static void main(String[] args) throws Exception {
        var rows=new LinkedHashMap<String,Object>();
        try(var reader=new ModernCacheReader(new File(args[0]))) {
            for(String value:args[2].split(",")) {
                int id=Integer.parseInt(value); byte[] raw=reader.file(2,34,id);
                if(raw==null)throw new IllegalStateException("Missing source struct "+id);
                var buffer=ByteBuffer.wrap(raw); var params=new TreeMap<Integer,Object>();
                while(buffer.hasRemaining()) {
                    int opcode=buffer.get()&255;
                    if(opcode==0){if(buffer.hasRemaining())throw new IllegalStateException("Trailing struct bytes");break;}
                    if(opcode!=249)throw new IllegalStateException("Unknown struct opcode "+opcode);
                    int count=buffer.get()&255;
                    for(int i=0;i<count;i++) {
                        boolean string=buffer.get()==1;int key=(buffer.get()&255)<<16|(buffer.get()&255)<<8|(buffer.get()&255);
                        if(string){int start=buffer.position();while(buffer.get()!=0){}params.put(key,new String(raw,start,buffer.position()-start-1,StandardCharsets.ISO_8859_1));}
                        else params.put(key,buffer.getInt());
                    }
                }
                rows.put(value,Map.of("index",2,"group",34,"file",id,"sha256",hash(raw),"params",params));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n");
        System.out.println("Read-only source Settings structs: "+rows.keySet());
    }
}
