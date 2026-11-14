import gg.rsmod.game.tools.importer.*;
import java.io.File;

/** Read-only source arena evidence: every loc with its decoded source name. */
class ClueArenaProbe {
    public static void main(String[] args) throws Exception {
        try (var reader = new ModernCacheReader(new File(args[0]))) {
            var files = reader.files(5,10819);
            var locs = ModernRegion.Companion.decodeLocs(files.get(1));
            var defs = reader.files(2,6);
            for (var loc : locs) {
                var def = ModernObjectDef.Companion.decode(loc.getId(),defs.get(loc.getId()));
                System.out.println(loc.getId()+"\t"+def.getName()+"\t"+(2688+loc.getLocalX())+"\t"+(4288+loc.getLocalZ())+"\t"+loc.getPlane()+"\t"+loc.getType()+"\t"+loc.getRotation());
            }
        }
    }
}
