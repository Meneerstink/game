import gg.rsmod.game.tools.importer.*;
import java.io.File;
import java.nio.charset.StandardCharsets;

/** Read-only search of pinned interface text and an explicitly supplied scene. No target writer. */
class ClueSceneProbe {
    public static void main(String[] args) throws Exception {
        try (var reader = new ModernCacheReader(new File(args[0]))) {
            for (int group : reader.index(3).getGroups().keySet()) {
                for (var entry : reader.files(3, group).entrySet()) {
                    String text = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
                    for (String needle : new String[]{"Watson", "Treasure Trail", "S.T.A.S.H", "Scroll box", "Light box"}) {
                        int at = text.indexOf(needle);
                        if (at >= 0) System.out.println("UI " + group + ":" + entry.getKey() + " " + text.substring(at, Math.min(text.length(),at+110)).replaceAll("[\\p{Cntrl}]", " "));
                    }
                }
            }
            for (int script : reader.index(12).getGroups().keySet()) {
                byte[] raw = reader.file(12, script, 0);
                if (raw == null) continue;
                String text = new String(raw, StandardCharsets.ISO_8859_1);
                for (String needle : new String[]{"Watson", "clues given", "Treasure Trails", "Scroll Box Capacity"}) {
                    int at = text.indexOf(needle);
                    if (at >= 0) System.out.println("SCRIPT " + script + " " + text.substring(at, Math.min(text.length(),at+150)).replaceAll("[\\p{Cntrl}]", " "));
                }
            }
            if (args.length > 2) {
                int id = Integer.parseInt(args[2]);
                var maps = reader.index(5);
                System.out.println("MAP_INDEX names=" + maps.getHasNames() + " sample=" + maps.getGroups().keySet().stream().limit(12).collect(java.util.stream.Collectors.toList()));
                if (maps.getGroups().containsKey(id)) System.out.println("REGION_GROUP " + id + " files=" + maps.getGroups().get(id).getFileIds());
                var keys = ModernRegionProbeTool.INSTANCE.loadKeys(new File(args[1]));
                // RuneLite RegionLoader.loadMapDef/loadLocDef: unnamed maps use
                // region ID as group, terrain file 0 / location file 1, unencrypted.
                var files = maps.getHasNames() ? null : reader.files(5, id);
                var region = files == null ? ModernRegion.Companion.load(reader, id, keys.get(id)) :
                    new ModernRegion(id >> 8, id & 255, ModernRegion.Companion.decodeTiles(files.get(0)),
                        ModernRegion.Companion.decodeLocs(files.get(1)), files.get(0).length, files.get(1).length);
                System.out.println("SCENE region=" + id + " locs=" + region.getLocs().size());
                for (var loc : region.getLocs()) {
                    if (loc.getId() >= 34675 && loc.getId() <= 34733)
                        System.out.println("LOC " + loc.getId() + " at " + (region.getRx()*64+loc.getLocalX()) + "," + (region.getRz()*64+loc.getLocalZ()) + "," + loc.getPlane() + " type=" + loc.getType() + " rot=" + loc.getRotation());
                }
            }
        }
    }
}
