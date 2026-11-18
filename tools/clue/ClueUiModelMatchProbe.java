import gg.rsmod.game.tools.importer.*;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only search for existing low UI meshes, comparing the actual 667 version-dependent geometry units. */
class ClueUiModelMatchProbe {
    static String geometry(ModelData m) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        int divisor = 0;
        for (int[] a : new int[][]{m.getVertexX(),m.getVertexY(),m.getVertexZ()})
            for (int v : a) divisor = gcd(divisor,Math.abs(v));
        divisor = Math.max(1,divisor);
        for (int[] a : new int[][]{m.getVertexX(),m.getVertexY(),m.getVertexZ()})
            for (int v : a) put(md, v/divisor);
        for (int[] a : new int[][]{m.getFaceA(),m.getFaceB(),m.getFaceC()})
            for (int v : a) put(md,v);
        for (short v : m.getFaceColour()) put(md, v & 65535);
        return Base64.getEncoder().encodeToString(md.digest());
    }
    static int gcd(int a,int b) { while(b!=0) { int c=a%b; a=b;b=c; } return a; }
    static void put(MessageDigest md, int v) { md.update((byte)(v>>>24));md.update((byte)(v>>>16));md.update((byte)(v>>>8));md.update((byte)v); }
    public static void main(String[] args) throws Exception {
        var hashes = new LinkedHashMap<String,Integer>();
        var matches = new LinkedHashMap<Integer,List<Object>>();
        try (var source = new ModernCacheReader(new File(args[0])); var target = new ModernCacheReader(new File(args[1]))) {
            for (int id : new int[]{3395,1226}) {
                var model = ModernModelDecoder.INSTANCE.decode(source.file(7,id,0),false);
                hashes.put(geometry(model),id);matches.put(id,new ArrayList<>());
            }
            int scanned=0,unsupported=0;
            for (int id : target.index(7).getGroups().keySet()) {
                if (id >= 65535) continue;
                byte[] raw = target.file(7,id,0);
                if (raw == null) continue;
                try {
                    var model = Rev667ModelDecoder.INSTANCE.decode(raw,true);
                    Integer osrs=hashes.get(geometry(model));
                    if (osrs!=null) {
                        var row=new LinkedHashMap<String,Object>();
                        row.put("local",id);row.put("version",model.getVersion());
                        row.put("textures",model.getFaceTexture());row.put("sha1",CacheItemProbeTool.INSTANCE.sha1(raw));
                        matches.get(osrs).add(row);
                    }
                    scanned++;
                } catch (IllegalArgumentException|IllegalStateException e) { unsupported++; }
            }
            System.out.println("Read-only low UI model search: decoded="+scanned+" unsupported="+unsupported+" matches="+matches);
        }
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(matches)+"\n");
    }
}
