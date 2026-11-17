import gg.rsmod.game.tools.importer.*;
import java.nio.file.*;
import java.util.*;

/** Research adapter: installs an in-memory named batch in the EXISTING importers. Never writes source/tool files. */
public final class Dmm2PreflightRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("kind ids sandbox");
        Path sandbox = Paths.get(args[2]).toRealPath();
        Path expected = Paths.get("build/dmm2/cache-plan").toRealPath();
        if (!sandbox.equals(expected)) throw new IllegalArgumentException("Not the isolated cache-plan directory");
        for (String target : OsrsItemImportTool.INSTANCE.getTARGETS()) {
            if (!Paths.get(target).toRealPath().startsWith(sandbox)) throw new IllegalStateException("Unsafe target: " + target);
        }
        if (!Paths.get(OsrsItemImportTool.INSTANCE.getASSET_MAP()).toRealPath().startsWith(sandbox)) throw new IllegalStateException("Unsafe ledger");
        List<Integer> ids = new ArrayList<>();
        for (String id : args[1].split(",")) ids.add(Integer.parseInt(id));
        String batch = "dmm2-review-only";
        System.out.println("DRY_RUN_ONLY kind=" + args[0] + " upstream=" + ids + " targets=" + OsrsItemImportTool.INSTANCE.getTARGETS());
        // All main() calls deliberately omit --apply. No command-line passthrough exists.
        switch (args[0]) {
            case "item":
                List<OsrsItemImportTool.Spec> specs = new ArrayList<>();
                for (int id : ids) specs.add(new OsrsItemImportTool.Spec(id, true, Collections.emptyMap(), -1, -1, Collections.emptyMap()));
                OsrsItemImportTool.INSTANCE.getBATCHES().put(batch, specs);
                OsrsItemImportTool.main(new String[]{batch}); break;
            case "npc":
                OsrsNpcImportTool.INSTANCE.getBATCHES().put(batch, ids);
                OsrsNpcImportTool.main(new String[]{batch}); break;
            case "npc-skeletal":
                List<kotlin.Pair<Integer, List<Integer>>> jobs = new ArrayList<>();
                for (int id : ids) jobs.add(new kotlin.Pair<>(id, Collections.emptyList()));
                OsrsPetImportTool.INSTANCE.getSKELETAL_NPC_BATCHES().put(batch, jobs);
                OsrsPetImportTool.main(new String[]{"skeletal-npc", batch}); break;
            case "npc-sidecar":
                if (ids.size() < 5 || (ids.size() - 5) % 2 != 0) throw new IllegalArgumentException("owner, four models, local/source sequence pairs");
                List<String> sidecar = new ArrayList<>(Arrays.asList("cache", "plan", ids.get(0).toString(),
                    ids.get(1) + "," + ids.get(2) + "," + ids.get(3) + "," + ids.get(4)));
                for (int i = 5; i < ids.size(); i += 2) sidecar.add(ids.get(i) + "=" + ids.get(i + 1));
                SkeletalSidecarExportTool.main(sidecar.toArray(new String[0])); break;
            case "seq":
                OsrsFxImportTool.INSTANCE.getSEQ_BATCHES().put(batch, ids);
                OsrsFxImportTool.main(new String[]{batch}); break;
            case "spotanim":
                OsrsFxImportTool.INSTANCE.getBATCHES().put(batch, ids);
                OsrsFxImportTool.main(new String[]{batch}); break;
            case "loc":
                OsrsLocImportTool.INSTANCE.getBATCHES().put(batch, ids);
                OsrsLocImportTool.main(new String[]{batch}); break;
            case "interface":
                OsrsInterfacePortTool.main(new String[]{args[1]}); break;
            default: throw new IllegalArgumentException("Unsupported importer kind " + args[0]);
        }
    }
}
