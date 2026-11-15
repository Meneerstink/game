import gg.rsmod.game.tools.importer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Real server-encoded fixtures for the independently compiled client decoder. No cache access. */
class ClueInterfaceFixtureProbe {
    public static void main(String[] args) throws Exception {
        var source=new ObjectMapper().readTree(new File(args[0]));
        var fonts=Map.of(497,999); var sprites=Map.of(539,9000,540,9001);
        Path out=Path.of(args[1]); Files.createDirectories(out);
        for (String mode: new String[]{"legacy","wide","missing"}) {
            var models=mode.equals("legacy") ? Map.of(3395,60001,1226,60002) :
                mode.equals("wide") ? Map.of(3395,66084,1226,66085) : Map.of(3395,-1,1226,-1);
            var component=ClueInterfaceImportTool.INSTANCE.components(73,source.get("73"),fonts,models,sprites).get(2);
            Files.write(out.resolve(mode+".bin"),OsrsInterfacePortTool.INSTANCE.encode(component));
        }
        var close=ClueInterfaceImportTool.INSTANCE.components(73,source.get("73"),fonts,
            Map.of(3395,66084,1226,66085),sprites).get(4);
        Files.write(out.resolve("close.bin"),OsrsInterfacePortTool.INSTANCE.encode(close));
        System.out.println("Server IF3 fixtures exported: "+out);
    }
}
