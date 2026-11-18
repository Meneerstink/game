import gg.rsmod.game.tools.importer.ModernCacheReader;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.nio.ByteBuffer;

/** Decodes the pinned cache's actual clue UI scripts, read-only. */
class ClueScriptProbe {
    public static void main(String[] args) throws Exception {
        var all=new LinkedHashMap<Integer,Object>();
        try(var reader=new ModernCacheReader(new File(args[0]))) {
            for(String text:args[2].split(",")) {
                int id=Integer.parseInt(text);
                byte[] bytes=reader.file(12,id,0);
                if(bytes==null) throw new IllegalStateException("Missing script "+id);
                // RuneLite ScriptLoader rev237: two new u16 long counts in the footer.
                // Our bundled reader predates them. These scripts use no long locals/args;
                // remove only those four proven zero bytes before using the older decoder.
                int switchLength=Short.toUnsignedInt(ByteBuffer.wrap(bytes,bytes.length-2,2).getShort());
                int end=bytes.length-2-switchLength-16;
                if(ByteBuffer.wrap(bytes,end+8,2).getShort()!=0 || ByteBuffer.wrap(bytes,end+14,2).getShort()!=0)
                    throw new IllegalStateException("Long operands need the modern loader: "+id);
                byte[] narrow=new byte[bytes.length-4];
                System.arraycopy(bytes,0,narrow,0,end+8);
                System.arraycopy(bytes,end+10,narrow,end+8,4);
                System.arraycopy(bytes,end+16,narrow,end+12,bytes.length-end-16);
                bytes=narrow;
                var script=new ScriptLoader().load(id,bytes);
                all.put(id,script);
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(all)+"\n");
        System.out.println("Read-only scripts "+all.keySet()+" -> "+args[1]);
    }
}
