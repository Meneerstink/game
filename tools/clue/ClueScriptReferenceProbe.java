import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Find source script constants for one source interface, read-only. No interpretation of gameplay from donors. */
class ClueScriptReferenceProbe {
    public static void main(String[] args) throws Exception {
        int group=Integer.parseInt(args[2]);
        var rows=new ArrayList<Object>();
        try(var reader=new ModernCacheReader(new File(args[0]))) {
            for(int script:reader.index(12).getGroups().keySet()) {
                byte[] raw=reader.file(12,script,0); if(raw==null) continue;
                var components=new TreeSet<Integer>();
                for(int i=0;i<raw.length-3;i++) {
                    int constant=ByteBuffer.wrap(raw,i,4).getInt();
                    if((constant>>>16)==group && (constant&65535)<256) components.add(constant&65535);
                }
                if(components.isEmpty()) continue;
                var row=new LinkedHashMap<String,Object>();row.put("script",script);row.put("components",components);
                StringBuilder hash=new StringBuilder();
                for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format("%02x",b));
                row.put("sha256",hash.toString());rows.add(row);
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n");
        System.out.println("Source interface "+group+" referenced by "+rows.size()+" candidate scripts");
    }
}
