import gg.rsmod.game.tools.importer.*;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Read only the requested Wiki-pinned NPC IDs from source cache 2686. */
class ClueNpcSourceProbe {
    public static void main(String[] args) throws Exception {
        var result=new LinkedHashMap<Integer,Object>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            var files=source.files(2,9);
            System.out.println("Source NPC definitions="+files.size()+" maximum="+Collections.max(files.keySet()));
            for(String text:args[2].split(",")) {
                int id=Integer.parseInt(text); byte[] bytes=files.get(id);
                result.put(id,bytes==null ? Map.of("missing",true) : OsrsNpcProbeTool.INSTANCE.decodeOsrs(id,bytes));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Requested source NPC evidence written: "+args[1]);
    }
}
