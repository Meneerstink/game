import gg.rsmod.game.tools.importer.ModernCacheReader;
import gg.rsmod.game.tools.importer.ModernModelDecoder;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only geometry and raw component hashes for the nine selected source maps. */
class ClueMapGeometryProbe {
    static String hash(byte[] bytes) throws Exception {
        var out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            out.append(String.format("%02x", b & 255));
        return out.toString();
    }

    public static void main(String[] args) throws Exception {
        var widgets = new JsonParser().parse(Files.readString(Path.of(args[1]))).getAsJsonObject();
        var components = new LinkedHashMap<String, Object>();
        var meshes = new LinkedHashMap<Integer, Object>();
        var modelHashes = new LinkedHashMap<Integer, String>();
        try (var cache = new ModernCacheReader(new File(args[0]))) {
            for (var group : widgets.entrySet()) {
                int id = Integer.parseInt(group.getKey());
                var hashes = new LinkedHashMap<Integer, String>();
                var raw = cache.files(3, id);
                if (raw.size() != group.getValue().getAsJsonObject().size())
                    throw new IllegalStateException("Source widget count changed: " + id);
                for (var row : group.getValue().getAsJsonObject().entrySet()) {
                    int child = Integer.parseInt(row.getKey());
                    hashes.put(child, hash(raw.get(child)));
                    var widget = row.getValue().getAsJsonObject();
                    if (widget.get("type").getAsInt() != 6) continue;
                    int model = widget.get("modelId").getAsInt();
                    if (meshes.containsKey(model)) continue;
                    byte[] bytes = cache.file(7, model, 0);
                    meshes.put(model, ModernModelDecoder.INSTANCE.decode(bytes, false));
                    modelHashes.put(model, hash(bytes));
                }
                components.put(group.getKey(), hashes);
            }
        }
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(Path.of(args[2]), gson.toJson(Map.of("source", args[0],
            "components", components, "modelHashes", modelHashes)) + "\n");
        Files.writeString(Path.of(args[3]), new Gson().toJson(meshes) + "\n");
        System.out.println("Read-only map evidence: groups=" + components.size() + ", meshes=" + meshes.size());
    }
}
