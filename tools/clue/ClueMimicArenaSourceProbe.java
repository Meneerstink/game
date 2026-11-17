import gg.rsmod.game.tools.importer.*;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Pinned arena payloads and existing native sound comparison; strictly read-only. */
class ClueMimicArenaSourceProbe {
    static String sha(byte[] raw) throws Exception {
        if(raw==null)return null;
        var text=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))text.append(String.format("%02x",b));return text.toString();
    }
    public static void main(String[] args) throws Exception {
        var report=new LinkedHashMap<String,Object>();
        try(var source=new ModernCacheReader(new File(args[0]));
            var game=new ModernCacheReader(new File(args[1]));
            var client=new ModernCacheReader(new File(args[2]))) {
            var terrain=source.file(5,10819,0);var locs=source.file(5,10819,1);
            report.put("cache",2686);report.put("sourceRegion",10819);
            report.put("terrainSha256",sha(terrain));report.put("locationsSha256",sha(locs));
            report.put("terrainBase64",Base64.getEncoder().encodeToString(terrain));
            report.put("locationsBase64",Base64.getEncoder().encodeToString(locs));
            var placements=ModernRegion.Companion.decodeLocs(locs);report.put("placements",placements);
            var definitions=new TreeMap<Integer,Object>();var sounds=new TreeMap<Integer,Object>();
            for(var placement:placements) {
                int id=placement.getId();if(definitions.containsKey(id))continue;
                var definition=ModernObjectDef.Companion.decode(id,source.file(2,6,id));definitions.put(id,definition);
                if(definition.getAmbientSoundId()>=0) {
                    int sound=definition.getAmbientSoundId();byte[] raw=source.file(4,sound,0);
                    sounds.put(sound,Map.of("sourceSha256",sha(raw),"gameSameIdIdentical",Arrays.equals(raw,game.file(4,sound,0)),
                        "clientSameIdIdentical",Arrays.equals(raw,client.file(4,sound,0))));
                }
            }
            report.put("definitions",definitions);report.put("ambientSounds",sounds);
        }
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("Read-only source arena terrain/250 placements/43 definitions and ambient sound comparison saved");
    }
}
