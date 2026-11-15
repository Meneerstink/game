import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.jagex.graphics.FontMetrics;
import com.google.gson.*;
import java.io.File;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Source enum 1531 plus actual native p12 metrics; no client window or cache writer. */
class ClueStashFontProbe {
    static String sha(byte[] raw) throws Exception {
        var s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))s.append(String.format("%02x",b));return s.toString();
    }
    static String str(ByteBuffer b) { int start=b.position();while(b.get()!=0){}return new String(b.array(),start,b.position()-start-1,StandardCharsets.ISO_8859_1); }
    public static void main(String[] args) throws Exception {
        var report=new LinkedHashMap<String,Object>();var labels=new LinkedHashMap<Integer,String>();
        try(var source=new ModernCacheReader(new File(args[0]));var target=new ModernCacheReader(new File(args[1]))) {
            byte[] raw=source.file(2,8,1531);ByteBuffer buf=ByteBuffer.wrap(raw);
            int op;while((op=Byte.toUnsignedInt(buf.get()))!=0) {
                if(op==1 || op==2)buf.get();else if(op==3)str(buf);else if(op==4)buf.getInt();
                else if(op==5) { int count=Short.toUnsignedInt(buf.getShort());for(int i=0;i<count;i++)labels.put(buf.getInt(),str(buf)); }
                else if(op==6) { int count=Short.toUnsignedInt(buf.getShort());for(int i=0;i<count;i++){buf.getInt();buf.getInt();} }
                else throw new IllegalStateException("Enum opcode "+op);
            }
            int hash="p12_full".hashCode();
            int id=target.index(13).getGroups().values().stream().filter(g->g.getNameHash()==hash).findFirst().orElseThrow().getId();
            byte[] nativeRaw=target.file(13,id,0);FontMetrics metrics=new FontMetrics(nativeRaw);
            report.put("sourceEnumSha256",sha(raw));report.put("nativeMetricId",id);report.put("nativeMetricSha256",sha(nativeRaw));
            byte[] osrs=source.file(13,495,0);
            report.put("sourceFontSha256",sha(osrs));report.put("matchingAdvanceWidths",Arrays.equals(metrics.glyphWidths,Arrays.copyOf(osrs,256)));
            var rows=new LinkedHashMap<Integer,Object>();
            for(var e:labels.entrySet()) {
                var row=new LinkedHashMap<String,Object>();row.put("text",e.getValue());row.put("nativeLines143",metrics.paraHeight(e.getValue(),null,143));
                row.put("nativeWidth",metrics.stringWidth(e.getValue()));rows.put(e.getKey(),row);
            }
            report.put("labels",rows);
        }
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("Read "+labels.size()+" actual source labels with native client font metrics");
    }
}
