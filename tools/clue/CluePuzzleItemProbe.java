import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Exact selected clue/puzzle definitions, read-only. */
class CluePuzzleItemProbe {
    public static void main(String[] args) throws Exception {
        var result=new LinkedHashMap<Integer,Object>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            for(int id:new int[]{19886,19887,23172,23173}) {
                byte[] raw=Objects.requireNonNull(source.file(2,10,id));
                var sha=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))sha.append(String.format("%02x",b&255));
                result.put(id,Map.of("rawBase64",Base64.getEncoder().encodeToString(raw),"sha256",sha.toString()));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only selected source clue/puzzle definitions exported");
    }
}
