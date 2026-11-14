import gg.rsmod.game.tools.importer.ModernCacheReader;
import net.runelite.cache.definitions.loaders.InterfaceLoader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.nio.ByteBuffer;
import java.util.Base64;

/** Read-only OSRS UI evidence; RuneLite loader decodes the pinned cache's own component bytes. */
class ClueWidgetProbe {
    public static void main(String[] args) throws Exception {
        var groups = new LinkedHashMap<Integer, Object>();
        var models = new LinkedHashMap<Integer, String>();
        try (ModernCacheReader reader = new ModernCacheReader(new File(args[0]))) {
            var loader = new InterfaceLoader();
            for (int group : new int[]{73, 203, 306}) {
                var components = new LinkedHashMap<Integer, Object>();
                for (var component : reader.files(3, group).entrySet()) {
                    byte[] bytes = component.getValue();
                    Integer wideModel = null;
                    // The bundled RuneLite cache library predates OSRS rev237's
                    // u16 -> i32 widget model IDs. Current primary source:
                    // runelite/cache/definitions/loaders/InterfaceLoader.decodeIf3.
                    // Keep the pinned model value; only adapt the bytes for this older reader.
                    if (bytes[0] == -1 && bytes[1] == 6) {
                        wideModel = ByteBuffer.wrap(bytes, 19, 4).getInt();
                        byte[] narrow = new byte[bytes.length - 2];
                        System.arraycopy(bytes, 0, narrow, 0, 19);
                        int value = wideModel >= 0 && wideModel < 65535 ? wideModel : 65535;
                        narrow[19] = (byte)(value >>> 8);
                        narrow[20] = (byte)value;
                        System.arraycopy(bytes, 23, narrow, 21, bytes.length - 23);
                        bytes = narrow;
                    }
                    var definition = loader.load((group << 16) | component.getKey(), bytes);
                    if (wideModel != null) definition.modelId = wideModel;
                    if (wideModel != null && wideModel >= 0 && !models.containsKey(wideModel)) {
                        byte[] modelBytes = reader.file(7, wideModel, 0);
                        if (modelBytes == null) throw new IllegalStateException("Source UI model missing: " + wideModel);
                        models.put(wideModel, Base64.getEncoder().encodeToString(modelBytes));
                    }
                    components.put(component.getKey(), definition);
                }
                groups.put(group, components);
            }
        }
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(groups));
        Files.writeString(Path.of(args[1]).resolveSibling("osrs-clue-interface-models.json"), new GsonBuilder().create().toJson(models));
        System.out.println("Read OSRS clue UI groups " + groups.keySet());
    }
}
