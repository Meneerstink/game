import gg.rsmod.game.tools.importer.ModernCacheReader;
import gg.rsmod.game.tools.importer.ModernItemDefDecoder;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Base64;

/** Read-only export from pinned OSRS config 2/10; no CacheLibrary or target writer. */
class ClueCacheInventory {
    public static void main(String[] args) throws Exception {
        try (ModernCacheReader reader = new ModernCacheReader(new File(args[0]))) {
            var items = new LinkedHashMap<Integer, Object>();
            for (var entry : reader.files(2, 10).entrySet()) {
                items.put(entry.getKey(), ModernItemDefDecoder.INSTANCE.decode(entry.getKey(), entry.getValue()));
            }
            Path out = Path.of(args[1]);
            Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().create().toJson(items));
            var enums = new LinkedHashMap<Integer, String>();
            for (var entry : reader.files(2, 8).entrySet()) {
                enums.put(entry.getKey(), Base64.getEncoder().encodeToString(entry.getValue()));
            }
            Files.writeString(out.resolveSibling("osrs-enums.json"), new GsonBuilder().create().toJson(enums));
            System.out.println("Read " + items.size() + " OSRS definitions into " + out);
        }
    }
}
