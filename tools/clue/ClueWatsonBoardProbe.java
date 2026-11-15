import gg.rsmod.game.tools.importer.*;
import com.displee.cache.CacheLibrary;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only proof of the existing imported noticeboard; no cache transactions. */
class ClueWatsonBoardProbe {
    static String hash(byte[] raw) throws Exception {
        var out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format("%02x",b));return out.toString();
    }
    public static void main(String[] args) throws Exception {
        var result=new LinkedHashMap<String,Object>();
        var target=new CacheLibrary(args[1],false,null);
        try(var source=new ModernCacheReader(new File(args[0]))) {
            var files=source.files(5,6455);
            var sourceLocs=ModernRegion.Companion.decodeLocs(files.get(1));
            var selected=new ArrayList<Object>();
            for(var loc:sourceLocs) if(loc.getId()==29718) selected.add(Map.of("id",loc.getId(),"x",1600+loc.getLocalX(),
                "z",3520+loc.getLocalZ(),"plane",loc.getPlane(),"type",loc.getType(),"rotation",loc.getRotation()));
            result.put("sourcePlacements",selected);
            byte[] raw=source.file(2,6,29718);result.put("source",ModernObjectDef.Companion.decode(29718,raw));result.put("sourceSha256",hash(raw));
            byte[] locations=target.data(5,target.index(5).archive("l25_55").getId(),0,null);
            var nativeLocs=new ArrayList<Object>();
            for(var loc:Rev667LocCodec.INSTANCE.decode(locations)) if(loc.getId()==62735)
                nativeLocs.add(Map.of("id",loc.getId(),"x",1600+loc.getLocalX(),"z",3520+loc.getLocalZ(),
                    "plane",loc.getPlane(),"type",loc.getType(),"rotation",loc.getRotation()));
            result.put("nativePlacements",nativeLocs);
            byte[] bytes=target.data(16,62735>>>8,62735&255,null);
            result.put("native",Rev667LocType.Companion.decode(62735,bytes));result.put("nativeSha256",hash(bytes));
        } finally { target.close(); }
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only Watson noticeboard proof written");
    }
}
