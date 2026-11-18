import gg.rsmod.game.tools.importer.*;
import com.displee.cache.CacheLibrary;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Existing furniture in Watson/Observatory; reads source and both target caches only. */
class ClueBoardLocationsProbe {
    public static void main(String[] args) throws Exception {
        var out=new LinkedHashMap<String,Object>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            var rows=new ArrayList<Object>();
            for(int id:new int[]{6567,6615,26634}) {
                byte[] raw=source.file(2,6,id);
                rows.add(Map.of("id",id,"definition",ModernObjectDef.Companion.decode(id,raw)));
            }
            out.put("sourceDefinitions",rows);
            var placements=new ArrayList<Object>();
            for(var loc:ModernRegion.Companion.decodeLocs(source.files(5,6455).get(1)))
                if(loc.getId()==6567 || loc.getId()==6615) placements.add(Map.of("id",loc.getId(),"x",1600+loc.getLocalX(),
                    "z",3520+loc.getLocalZ(),"plane",loc.getPlane(),"type",loc.getType(),"rotation",loc.getRotation()));
            out.put("sourceWatsonPlacements",placements);
        }
        for(int i=1;i<=2;i++) {
            var cache=new CacheLibrary(args[i],false,null);var rows=new ArrayList<Object>();
            var keys=Rev667RegionProbeTool.INSTANCE.loadKeys(new File("C:/RSPS/game/game/data/xteas"));
            try {
                for(int square:new int[]{6455,9777}) {
                    String name="l"+(square>>>8)+"_"+(square&255);
                    var archive=cache.index(5).archive(name);if(archive==null)continue;
                    byte[] bytes=cache.data(5,archive.getId(),0,keys.get(square));
                    if(bytes==null)throw new IllegalStateException("Locations unavailable "+name+" target"+i);
                    for(var loc:Rev667LocCodec.INSTANCE.decode(bytes)) {
                        byte[] raw=cache.data(16,loc.getId()>>>8,loc.getId()&255,null);
                        if(raw==null)continue;
                        var def=Rev667LocType.Companion.decode(loc.getId(),raw);
                        if(!def.getName().equals("Treasure Trail Statistics") && !def.getName().equals("Pinboard"))continue;
                        rows.add(Map.of("id",loc.getId(),"definition",def,"sha1",CacheItemProbeTool.INSTANCE.sha1(raw),
                            "x",(square>>>8)*64+loc.getLocalX(),"z",(square&255)*64+loc.getLocalZ(),
                            "plane",loc.getPlane(),"type",loc.getType(),"rotation",loc.getRotation()));
                    }
                }
            } finally {cache.close();}
            out.put("target"+i,rows);
        }
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(out)+"\n");
        System.out.println("Read-only furniture proof written");
    }
}
