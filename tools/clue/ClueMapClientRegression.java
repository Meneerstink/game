import com.jagex.core.io.Packet;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.rsmod.game.tools.importer.ClueInterfaceImportTool;
import gg.rsmod.game.tools.importer.OsrsInterfacePortTool;
import java.io.File;
import java.util.*;

/** Independent native Component decoder against the source JSON; no client window/cache access. */
class ClueMapClientRegression {
    static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        var source = new ObjectMapper().readTree(new File(args[0]));
        int total = 0;
        for (int group : ClueInterfaceImportTool.INSTANCE.getMAP_GROUPS()) {
            var rows = source.get(Integer.toString(group));
            var models = new HashMap<Integer,Integer>();
            var sprites = Map.of(539, 9000, 540, 9001);
            for (var row : rows) if (row.get("type").asInt() == 6)
                models.put(row.get("modelId").asInt(), row.get("modelId").asInt()+70000);
            var encoded = ClueInterfaceImportTool.INSTANCE.components(group, rows,
                Map.of(494, 100, 495, 101, 496, 102, 497, 103), models, sprites);
            for (var fixture : encoded) {
                var row = rows.get(Integer.toString(fixture.getId()));
                byte[] raw = OsrsInterfacePortTool.INSTANCE.encode(fixture);
                var packet = new Packet(raw);
                var c = new Component();
                c.id = (group << 16) | fixture.getId();
                c.slot = c.id;
                c.decode(packet);
                String at = group+":"+fixture.getId();
                require(packet.pos == raw.length, at+" alignment");
                require(c.type == row.get("type").asInt(), at+" type");
                require(c.originalX == row.get("originalX").asInt() && c.originalY == row.get("originalY").asInt(), at+" origin");
                require(c.originalWidth == row.get("originalWidth").asInt() && c.originalHeight == row.get("originalHeight").asInt(), at+" dimensions");
                int parent = row.get("parentId").asInt();
                require(c.layer == parent, at+" parent");
                if (c.type == 6) {
                    require(c.obj == models.get(row.get("modelId").asInt()), at+" wide model");
                    require(c.xan2d == row.get("rotationX").asInt() && c.yan2d == row.get("rotationZ").asInt() &&
                        c.zan2d == row.get("rotationY").asInt() && c.zoom2d == row.get("modelZoom").asInt(), at+" raw camera order");
                    require(c.modelOriginX == row.get("offsetX2d").asInt() && c.modelOriginY == row.get("offsetY2d").asInt(), at+" model origin");
                }
                if (row.has("actions") && row.get("actions").isArray() && row.get("actions").size() > 0) {
                    require(Arrays.equals(c.ops, new String[]{"Close"}), at+" Close");
                    require(Arrays.equals(c.onMouseOver, new Object[]{44,-2147483645,9001}), at+" hover");
                    require(Arrays.equals(c.onMouseLeave, new Object[]{44,-2147483645,9000}), at+" leave");
                }
                total++;
            }
        }
        System.out.println("CLUE map source/native decoder regression: "+total+" components, 9 groups OK");
    }
}
