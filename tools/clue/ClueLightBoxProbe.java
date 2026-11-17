import gg.rsmod.game.tools.importer.ModernCacheReader;
import gg.rsmod.game.tools.importer.ModernItemDefDecoder;
import gg.rsmod.game.tools.importer.OsrsFxImportTool;
import com.displee.cache.CacheLibrary;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Source-only item/model evidence for CS2 lightbox_draw (1339). */
class ClueLightBoxProbe {
    static String hash(byte[] bytes) throws Exception {
        StringBuilder out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        Gson gson=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        Map<Integer,Object> items=new LinkedHashMap<>(), models=new LinkedHashMap<>(), sounds=new LinkedHashMap<>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            for(int id:new int[]{20355,20356,20357}) {
                byte[] bytes=Objects.requireNonNull(source.file(2,10,id));
                JsonObject def=gson.toJsonTree(ModernItemDefDecoder.INSTANCE.decode(id,bytes)).getAsJsonObject();
                int model=def.get("inventoryModel").getAsInt();
                items.put(id,Map.of("definition",def,"sha256",hash(bytes)));
                models.put(model,hash(Objects.requireNonNull(source.file(7,model,0))));
            }
            byte[] sequence=Objects.requireNonNull(source.file(2,12,3162));
            var death=OsrsFxImportTool.INSTANCE.decodeOsrsSeq(sequence,Map.of());
            // Wiki File:Killerwatt attack.wav/id576 and File:Killerwatt death.wav/id580.
            Set<Integer> ids=new LinkedHashSet<>();ids.add(576);ids.add(580);
            for(int[] sound:death.getSoundList())ids.add(sound[1]);
            for(int id:ids) {
                byte[] bytes=Objects.requireNonNull(source.file(4,id,0));
                Map<String,Object> row=new LinkedHashMap<>();row.put("sourceSha256",hash(bytes));
                for(String target:new String[]{"C:/RSPS/game/game/data/cache","C:/RSPS/file-server/cache"}) {
                    var cache=new CacheLibrary(target,false,null);try {
                        byte[] nativeBytes=cache.data(4,id,0,null);
                        row.put(target,nativeBytes==null?"absent":hash(nativeBytes));
                    } finally {cache.close();}
                }
                sounds.put(id,row);
            }
            sounds.put(-1,Map.of("deathSourceSequence",3162,"sha256",hash(sequence),"frameSounds",death.getSoundList()));
        }
        Files.writeString(Path.of(args[1]),gson.toJson(Map.of("cache",2686,"items",items,"modelSha256",models,"soundEvidence",sounds))+"\n");
        System.out.println("Read-only lightbox evidence: "+items.size()+" items, "+models.size()+" models");
    }
}
