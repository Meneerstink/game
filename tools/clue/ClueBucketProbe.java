import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Strictly read-only source/native bytes for the selected jail search-menu correction. */
class ClueBucketProbe {
    static Map<String,Object> row(byte[] raw) throws Exception {
        if(raw==null)throw new IllegalStateException("Missing bucket definition");
        var sha=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))sha.append(String.format("%02x",b&255));
        return Map.of("rawBase64",Base64.getEncoder().encodeToString(raw),"sha256",sha.toString());
    }
    public static void main(String[] args) throws Exception {
        var result=new LinkedHashMap<String,Object>();
        try(var source=new ModernCacheReader(new File(args[0]));var nativeCache=new ModernCacheReader(new File(args[1]))) {
            result.put("osrsId",9568);result.put("localId",9568);
            result.put("source",row(source.file(2,6,9568)));
            result.put("native",row(nativeCache.file(16,9568>>8,9568&255)));
        }
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only bucket9568 definition pair exported");
    }
}
