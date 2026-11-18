import gg.rsmod.game.tools.importer.*;
import com.displee.cache.CacheLibrary;
import com.google.gson.GsonBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only geometry/camera evidence for source306 and its installed 667 port. */
class CluePuzzleRenderProbe {
    static String sha(byte[] raw) throws Exception {
        var out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(raw)) out.append(String.format("%02x", b & 255));
        return out.toString();
    }
    static ModelData decode(byte[] raw) {
        try { return ModernModelDecoder.INSTANCE.decode(raw, false); }
        catch (IllegalStateException ex) { return Rev667ModelDecoder.INSTANCE.decode(raw, false); }
    }
    static Object mesh(byte[] raw) throws Exception {
        var m = decode(raw);
        return Map.of("sha256", sha(raw), "description", m.describe(),
            "x", m.getVertexX(), "y", m.getVertexY(), "z", m.getVertexZ());
    }
    public static void main(String[] args) throws Exception {
        var result = new LinkedHashMap<String,Object>();
        var mapping = new HashMap<Integer,Integer>();
        for (String row : Files.readAllLines(Path.of(args[2]))) {
            String[] c = row.split("\t");
            if (c.length == 3 && c[0].equals("model")) mapping.put(Integer.parseInt(c[1]),Integer.parseInt(c[2]));
        }
        var target = new CacheLibrary(args[1],false,null);
        try (var source = new ModernCacheReader(new File(args[0]))) {
            for (int id : new int[]{3438,4695,3413,4156,4180,31686,31723,31692,37000}) {
                int local = mapping.get(id);
                byte[] raw = target.data(local < 65536 ? 7 : 37,local & 65535,0,null);
                result.put(Integer.toString(id),Map.of("source",mesh(source.file(7,id,0)),"local",local,"target",mesh(raw)));
            }
            if (args.length>4) {
                var root=new ObjectMapper().readTree(new File(args[4]));
                var pictures=CluePuzzleInterfaceBake.INSTANCE.pictures(root.get("puzzlePictures"));
                var sprites=new HashMap<Integer,Integer>();
                int iface=-1;
                for(String row:Files.readAllLines(Path.of(args[2]))) {
                    String[] c=row.split("\t");
                    if(c.length!=3)continue;
                    if(c[0].equals("sprite"))sprites.put(Integer.parseInt(c[1]),Integer.parseInt(c[2]));
                    if(c[0].equals("interface") && c[1].equals("306"))iface=Integer.parseInt(c[2]);
                }
                if(iface<0)throw new IllegalStateException("No applied source306 mapping");
                var changes=new LinkedHashMap<String,Object>();
                var ids=new LinkedHashSet<Integer>(Arrays.asList(3438,4695));
                for(var picture:pictures)ids.addAll(picture.getModels());
                for(int id:ids) {
                    var decoded=decode(source.file(7,id,0));
                    CluePuzzleInterfaceBake.INSTANCE.adaptFrameModel(id,decoded,root.get("306"));
                    byte[] expected=Rev667ModelEncoder.INSTANCE.encode(decoded);
                    int local=mapping.get(id),index=local<65536?7:37,group=local&65535;
                    byte[] before=target.data(index,group,0,null);
                    if(!Arrays.equals(expected,before))changes.put(index+":"+group+":0",Map.of("sourceModel",id,"localModel",local,"beforeSha256",sha(before),"afterSha256",sha(expected)));
                }
                for(var c:CluePuzzleInterfaceBake.INSTANCE.components(root.get("306"),pictures,mapping,sprites)) {
                    byte[] expected=OsrsInterfacePortTool.INSTANCE.encode(c),before=target.data(3,iface,c.getId(),null);
                    if(!Arrays.equals(expected,before))changes.put("3:"+iface+":"+c.getId(),Map.of("beforeSha256",sha(before),"afterSha256",sha(expected)));
                }
                for(int id:new int[]{539,540}) {
                    int local=sprites.get(id);
                    byte[] expected=source.file(8,id,0),before=target.data(8,local,0,null);
                    if(!Arrays.equals(expected,before))changes.put("8:"+local+":0",Map.of("beforeSha256",sha(before),"afterSha256",sha(expected)));
                }
                var expectedKeys=new HashSet<String>(Arrays.asList("3:"+iface+":2","3:"+iface+":3",
                    "37:"+(mapping.get(3438)&65535)+":0","37:"+(mapping.get(4695)&65535)+":0"));
                if(!changes.keySet().equals(expectedKeys))throw new AssertionError("Unexpected replacements: "+changes.keySet());
                result.put("repair",Map.of("componentsChecked",199,"modelsChecked",ids.size(),"spritesChecked",2,"changes",changes));
                System.out.println("Exact repair: "+changes.keySet()+"; all other components/models unchanged");
            }
        } finally { target.close(); }
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only source/target model evidence: " + result.size());
    }
}
