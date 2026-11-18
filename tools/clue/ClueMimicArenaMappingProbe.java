import gg.rsmod.game.tools.importer.*;
import gg.rsmod.game.fs.def.ObjectDef;
import io.netty.buffer.Unpooled;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Certify BOTH installed rooms and their six named definitions before publishing runtime identities. */
class ClueMimicArenaMappingProbe {
    static String sha(byte[] b) throws Exception {
        var out=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))out.append(String.format("%02x",x));return out.toString();
    }
    static void check(boolean ok,String why) {if(!ok)throw new IllegalStateException(why);}
    static List<String> keys(List<Rev667Loc> locs) {
        var out=new ArrayList<String>();for(var l:locs)out.add(l.getId()+":"+l.getLocalX()+":"+l.getLocalZ()+":"+l.getPlane()+":"+l.getType()+":"+l.getRotation());
        Collections.sort(out);return out;
    }
    static int group(ModernCacheReader reader,String name) {
        return reader.index(5).getGroups().values().stream().filter(g->g.getNameHash()==name.hashCode()).findFirst().orElseThrow().getId();
    }
    public static void main(String[] args) throws Exception {
        var parser=new JsonParser();var candidate=parser.parse(Files.readString(Path.of(args[0]))).getAsJsonObject();
        int region=candidate.get("region").getAsInt();check(region!=10819&&region>=0&&region<=65535,"Invalid relocated source");
        var mapping=new TreeMap<Integer,Integer>();candidate.getAsJsonObject("locals").entrySet().forEach(e->mapping.put(Integer.parseInt(e.getKey()),e.getValue().getAsInt()));
        var original=parser.parse(Files.readString(Path.of(args[3]))).getAsJsonObject();
        var expected=new ArrayList<Rev667Loc>();
        for(var l:ModernRegion.Companion.decodeLocs(Base64.getDecoder().decode(original.get("locationsBase64").getAsString())))
            expected.add(new Rev667Loc(mapping.get(l.getId()),l.getLocalX(),l.getLocalZ(),l.getPlane(),l.getType(),l.getRotation()));
        check(expected.size()==250,"Incomplete source scene");
        var metadata=parser.parse(Files.readString(Path.of(args[4]))).getAsJsonArray();check(metadata.size()==6,"Incomplete named scenery");
        var proof=new LinkedHashMap<String,Object>();var cacheProof=new ArrayList<Object>();var payloads=new ArrayList<Map<String,String>>();
        for(int index=1;index<=2;index++)try(var cache=new ModernCacheReader(new File(args[index]))) {
            String suffix=(region>>>8)+"_"+(region&255);int terrain=group(cache,"m"+suffix);int locations=group(cache,"l"+suffix);
            byte[] locs=cache.file(5,locations,0);check(keys(Rev667LocCodec.INSTANCE.decode(locs)).equals(keys(expected)),"Applied250placements differ from mapped source");
            var hashes=new TreeMap<String,String>();hashes.put("terrain",sha(cache.file(5,terrain,0)));hashes.put("locations",sha(locs));
            for(var entry:metadata) {
                var row=entry.getAsJsonObject();int source=row.get("source").getAsInt();int local=mapping.get(source);
                byte[] raw=cache.file(16,local>>>8,local&255);check(raw!=null,"Missing scenery"+source);
                var def=new ObjectDef(local);def.decode(Unpooled.wrappedBuffer(raw));
                check(def.getName().equals(row.get("name").getAsString()),"Wrong named scenery"+source);
                var options=row.getAsJsonArray("options");for(int slot=0;slot<5;slot++) {
                    String actual=def.getOptions()[slot];if(actual!=null&&actual.isBlank())actual=null;
                    String wanted=options.get(slot).isJsonNull()?null:options.get(slot).getAsString();
                    check(Objects.equals(actual,wanted),"Wrong scenery option"+source+":"+slot);
                }
                hashes.put("loc"+source,sha(raw));
            }
            payloads.add(hashes);cacheProof.add(Map.of("cache",args[index],"terrainGroup",terrain,"locGroup",locations,"hashes",hashes));
        }
        check(payloads.get(0).equals(payloads.get(1)),"Both caches must contain identical room/scenery payloads");
        proof.put("verified",true);proof.put("region",region);proof.put("placements",250);proof.put("namedDefinitions",6);proof.put("targets",cacheProof);
        Files.writeString(Path.of(args[5]),new GsonBuilder().setPrettyPrinting().create().toJson(proof)+"\n");
        System.out.println("Verified both installed250-placement rooms and all6named scenery definitions; read-only");
    }
}
