import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.ClippingMask;
import com.google.gson.*;
import gg.rsmod.game.tools.importer.ModernCacheReader;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Actual native q8 metrics and paragraph renderer, no toolkit/window or target writer. */
class ClueBoardFontRegression {
    static void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
    static class Recorder extends Font {
        final Set<Integer> rows=new TreeSet<>();
        Recorder(FontMetrics metrics) { super(null,metrics); }
        protected void fa(char c,int x,int y,int colour,boolean shadow) { if(!shadow)rows.add(y); }
        protected void renderSymbol(char c,int x,int y,int colour,boolean shadow,ClippingMask mask,int dx,int dy) { fa(c,x,y,colour,shadow); }
    }
    public static void main(String[] args) throws Exception {
        var json=new JsonParser().parse(Files.readString(Path.of(args[0]))).getAsJsonArray();
        var names=new ArrayList<String>();json.forEach(n->names.add(n.getAsString()));
        var report=new LinkedHashMap<String,Object>();
        try(var target=new ModernCacheReader(new File(args[1]))) {
            int hash="q8_full".hashCode();
            int id=target.index(13).getGroups().values().stream().filter(g->g.getNameHash()==hash).findFirst().orElseThrow().getId();
            byte[] raw=target.file(13,id,0);var metrics=new FontMetrics(raw);var recorder=new Recorder(metrics);
            int maxWidth=names.stream().mapToInt(metrics::stringWidth).max().orElseThrow();
            check(maxWidth<343,"a contributor wraps in source625:7");
            int height=gg.rsmod.game.tools.importer.ClueInterfaceImportTool.BOARD_CREDIT_HEIGHT;
            int bottom=(names.size()-1)*16+metrics.paddingTop+metrics.paddingBottom;
            System.out.println("q8 metrics: top="+metrics.paddingTop+", bottom="+metrics.paddingBottom+", spacing="+metrics.verticalSpacing+", finalInk="+bottom);
            check(bottom<=height,"last glyph exceeds extended text component");
            int rendered=recorder.renderLines(String.join("<br>",names),0,0,0,0,343,height,1,0,0,16,0xffffff,-1,null,null,null);
            check(rendered==87 && recorder.rows.size()==87,"all87source names must actually render");
            check(bottom>1200,"regression fixture must exercise old clipped tail");
            var digest=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))digest.append(String.format("%02x",b));
            report.put("nativeFont",id);report.put("fontSha256",digest.toString());report.put("sourceLineSpacing",16);
            report.put("sourceTextWidth",343);report.put("maxNameWidth",maxWidth);report.put("renderedLines",rendered);
            report.put("glyphRows",recorder.rows.size());report.put("finalInkBottom",bottom);report.put("extendedHeight",height);
            report.put("note","Native paragraph execution, no toolkit/window/cache writes or loaded-client acceptance");
        }
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("Native q8 paragraph:87names render without wrapping; final ink fits1396px, exceeds old1200px");
    }
}
