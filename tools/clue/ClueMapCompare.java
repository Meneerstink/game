import gg.rsmod.game.tools.importer.*;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only comparison of map glyph placements/cameras and actual model bytes. */
class ClueMapCompare {
    static int field(Object value,String name) throws Exception {
        var field=value.getClass().getDeclaredField(name); field.setAccessible(true);
        return ((Number)field.get(value)).intValue();
    }
    static String hash(byte[] bytes) throws Exception {
        if(bytes==null)return null;
        var s=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));
        return s.toString();
    }
    public static void main(String[] args) throws Exception {
        var source=new JsonParser().parse(Files.readString(Path.of(args[2]))).getAsJsonObject();
        var result=new LinkedHashMap<String,Object>();
        var sourceGlyphs=new LinkedHashMap<Integer,List<List<Integer>>>();
        var localGlyphs=new LinkedHashMap<Integer,List<List<Integer>>>();
        var ids=new TreeSet<Integer>();
        for(var group:source.entrySet()) {
            var glyphs=new ArrayList<List<Integer>>();
            for(var component:group.getValue().getAsJsonObject().entrySet()) {
                var c=component.getValue().getAsJsonObject();
                if(c.get("type").getAsInt()!=6)continue;
                var row=new ArrayList<Integer>();
                for(String key:new String[]{"modelId","originalX","originalY","originalWidth","originalHeight","rotationX","rotationY","rotationZ","modelZoom"})row.add(c.get(key).getAsInt());
                glyphs.add(row);ids.add(row.get(0));
            }
            sourceGlyphs.put(Integer.parseInt(group.getKey()),glyphs);
        }
        var decode=InterfaceHookProbeTool.class.getDeclaredMethod("decodeComponent",byte[].class); decode.setAccessible(true);
        try(var osrs=new ModernCacheReader(new File(args[0])); var local=new ModernCacheReader(new File(args[1]))) {
            for(int group=337;group<=363;group++) {
                if(!local.index(3).getGroups().containsKey(group))continue;
                var glyphs=new ArrayList<List<Integer>>();
                for(var component:local.files(3,group).entrySet()) {
                    byte[] bytes=component.getValue();
                    if((bytes[1]&0x3F)!=6)continue;
                    var c=decode.invoke(InterfaceHookProbeTool.INSTANCE,(Object)bytes);
                    var row=new ArrayList<Integer>();
                    for(String key:new String[]{"modelId","x","y","width","height"})row.add(field(c,key));
                    for(int n:InterfaceHookProbeTool.INSTANCE.componentModelCamera(bytes))row.add(n);
                    glyphs.add(row);ids.add(row.get(0));
                }
                localGlyphs.put(group,glyphs);
            }
            var assets=new LinkedHashMap<Integer,Object>();
            for(int id:ids) {
                byte[] a=osrs.file(7,id,0), b=local.file(7,id,0);
                var row=new LinkedHashMap<String,Object>(); row.put("sourceSha256",hash(a)); row.put("targetSha256",hash(b));
                row.put("identicalBytes",a!=null && b!=null && Arrays.equals(a,b));
                assets.put(id,row);
            }
            result.put("modelAssets",assets);
        }
        var matches=new LinkedHashMap<Integer,List<Integer>>();
        for(var local:localGlyphs.entrySet()) {
            var same=new ArrayList<Integer>();
            for(var osrs:sourceGlyphs.entrySet())if(local.getValue().equals(osrs.getValue()))same.add(osrs.getKey());
            matches.put(local.getKey(),same);
        }
        result.put("sourceGlyphs",sourceGlyphs); result.put("targetGlyphs",localGlyphs);result.put("identicalLayoutMatches",matches);
        // Source labels read X/Z/Y, whereas the native decoder labels the same
        // raw tuple X/Y/Z. A diagram signature is a navigation aid only; it does
        // not establish mesh equivalence, a dig point, or rendering acceptance.
        var diagramMatches=new LinkedHashMap<Integer,List<Integer>>();
        for(var nativeMap:localGlyphs.entrySet()) {
            var a=new ArrayList<String>();
            for(var g:nativeMap.getValue())if(g.get(0)!=3395)a.add(Arrays.asList(g.get(0),g.get(5),g.get(6),g.get(7),g.get(8)).toString());
            Collections.sort(a);
            var candidates=new ArrayList<Integer>();
            for(var sourceMap:sourceGlyphs.entrySet()) {
                var b=new ArrayList<String>();
                for(var g:sourceMap.getValue())if(g.get(0)!=3395)b.add(Arrays.asList(g.get(0),g.get(5),g.get(7),g.get(6),g.get(8)).toString());
                Collections.sort(b);if(!a.isEmpty() && a.equals(b))candidates.add(sourceMap.getKey());
            }
            diagramMatches.put(nativeMap.getKey(),candidates);
        }
        result.put("cameraGlyphCandidates",diagramMatches);
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result)+"\n");
        for(var match:matches.entrySet())System.out.println(match.getKey()+" -> "+match.getValue());
    }
}
