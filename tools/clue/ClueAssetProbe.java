import gg.rsmod.game.tools.importer.*;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only geometry/audio/sprite comparison. Numeric equality is never accepted as asset identity. */
class ClueAssetProbe {
    static String sha(byte[] b) throws Exception {
        if (b == null) return null;
        StringBuilder s = new StringBuilder();
        for (byte v : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", v));
        return s.toString();
    }
    public static void main(String[] args) throws Exception {
        var report = new LinkedHashMap<String,Object>();
        try (var osrs = new ModernCacheReader(new File(args[0])); var local = new ModernCacheReader(new File(args[1]))) {
            for (int id : new int[]{3395,1226,3438,4695}) {
                byte[] a = osrs.file(7,id,0), b = local.file(7,id,0);
                var row = new LinkedHashMap<String,Object>(); row.put("sourceSha256",sha(a)); row.put("targetSha256",sha(b));
                try {
                    ModelData source;
                    try { source = ModernModelDecoder.INSTANCE.decode(a,false); }
                    catch (Throwable old) { source = Rev667ModelDecoder.INSTANCE.decode(a,false); }
                    var target = Rev667ModelDecoder.INSTANCE.decode(b,true);
                    row.put("differences", ModelConvertTool.INSTANCE.compare(source,target));
                } catch (Throwable error) { row.put("decodeError",error.toString()); }
                report.put("model:"+id,row);
            }
            for (int[] ref : new int[][]{{8,539},{8,540},{11,193}}) {
                byte[] a=osrs.file(ref[0],ref[1],0), b=local.file(ref[0],ref[1],0);
                var row=new LinkedHashMap<String,Object>(); row.put("sourceSha256",sha(a)); row.put("targetSha256",sha(b));
                row.put("identical",a!=null && b!=null && Arrays.equals(a,b));
                report.put(ref[0]+":"+ref[1],row);
            }
        }
        Files.writeString(Path.of(args[2]), new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("Read-only asset comparison written: "+args[2]);
    }
}
