import gg.rsmod.game.tools.importer.*;
import com.displee.cache.CacheLibrary;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Both scenes and actual object menus, read only. Map keys are never printed. */
class ClueSearchSceneProbe {
    static String hash(byte[] raw) throws Exception {
        var out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format("%02x",b));
        return out.toString();
    }
    public static void main(String[] args) throws Exception {
        var rows=new JsonParser().parse(Files.readString(Path.of(args[3]))).getAsJsonArray();
        var result=new ArrayList<Object>();
        var keys=Rev667RegionProbeTool.INSTANCE.loadKeys(new File(args[2]));
        var target=new CacheLibrary(args[1],false,null);
        try(var source=new ModernCacheReader(new File(args[0]))) {
            for(var value:rows) {
                var row=value.getAsJsonObject();
                if (row.has("action") && !row.get("action").getAsString().equals("SEARCH")) continue;
                var sourceLocs=new ArrayList<Object>(); var nativeLocs=new ArrayList<Object>();
                for(var point:row.getAsJsonArray("points")) {
                    var p=point.getAsJsonArray();int x=p.get(0).getAsInt(),z=p.get(1).getAsInt(),h=p.get(2).getAsInt();
                    int rx=x>>6,rz=z>>6,region=rx<<8|rz;
                    var files=source.files(5,region);var sourceTiles=ModernRegion.Companion.decodeTiles(files.get(0));
                    for(var loc:ModernRegion.Companion.decodeLocs(files.get(1))) {
                        int ax=rx*64+loc.getLocalX(),az=rz*64+loc.getLocalZ();
                        int plane=loc.getPlane();
                        if(plane>0 && (sourceTiles[1][loc.getLocalX()][loc.getLocalZ()].getSettings()&2)!=0)plane--;
                        if(plane!=h || Math.max(Math.abs(x-ax),Math.abs(z-az))>1)continue;
                        byte[] raw=source.file(2,6,loc.getId());var def=ModernObjectDef.Companion.decode(loc.getId(),raw);
                        sourceLocs.add(Map.of("id",loc.getId(),"point",new int[]{ax,az,loc.getPlane()},"effectivePlane",plane,
                            "type",loc.getType(),"rotation",loc.getRotation(),"definition",def,"sha256",hash(raw)));
                    }
                    byte[] terrain=Rev667RegionProbeTool.INSTANCE.regionGroupData(target,"m"+rx+"_"+rz,null);
                    byte[] locations=Rev667RegionProbeTool.INSTANCE.regionGroupData(target,"l"+rx+"_"+rz,keys.get(region));
                    if(terrain==null || locations==null)throw new IllegalStateException("Missing native square "+region);
                    var nativeTiles=Rev667TileCodec.INSTANCE.decode(terrain).getTiles();
                    for(var loc:Rev667LocCodec.INSTANCE.decode(locations)) {
                        int ax=rx*64+loc.getLocalX(),az=rz*64+loc.getLocalZ();
                        int plane=loc.getPlane();
                        if(plane>0 && (nativeTiles[1][loc.getLocalX()][loc.getLocalZ()].getFlags()&2)!=0)plane--;
                        if(plane!=h || Math.max(Math.abs(x-ax),Math.abs(z-az))>1)continue;
                        byte[] raw=target.data(16,loc.getId()>>8,loc.getId()&255,null);
                        var def=Rev667LocType.Companion.decode(loc.getId(),raw);
                        var entry=new LinkedHashMap<String,Object>();entry.put("id",loc.getId());entry.put("point",new int[]{ax,az,loc.getPlane()});
                        entry.put("effectivePlane",plane);entry.put("type",loc.getType());entry.put("rotation",loc.getRotation());
                        entry.put("definition",def);entry.put("sha256",hash(raw));
                        nativeLocs.add(entry);
                    }
                }
                result.add(Map.of("key",row.get("key").getAsString(),"source",sourceLocs,"native",nativeLocs));
            }
        } finally { target.close(); }
        Files.writeString(Path.of(args[4]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only source/native search scenes: "+result.size()+" -> "+args[4]);
    }
}
