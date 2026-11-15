import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Source byte order, independent of RuneLite's rotationY/rotationZ field labels. */
class ClueCameraProbe {
    public static void main(String[] args) throws Exception {
        var result=new LinkedHashMap<Integer,Object>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            for(int group:new int[]{73,203,351,354}) {
                var rows=new LinkedHashMap<Integer,Object>();
                for(var entry:source.files(3,group).entrySet()) {
                    byte[] raw=entry.getValue();
                    if(raw[0]!=-1 || raw[1]!=6)continue;
                    var camera=ByteBuffer.wrap(raw,27,8);
                    int[] tuple=new int[4];for(int i=0;i<4;i++)tuple[i]=Short.toUnsignedInt(camera.getShort());
                    var sha=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))sha.append(String.format("%02x",b&255));
                    rows.put(entry.getKey(),Map.of("rawBase64",Base64.getEncoder().encodeToString(raw),
                        "sha256",sha.toString(),"sourceCameraOffset",27,"rawCamera",tuple));
                }
                result.put(group,rows);
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only raw OSRS model-camera proof exported: "+args[1]);
    }
}
