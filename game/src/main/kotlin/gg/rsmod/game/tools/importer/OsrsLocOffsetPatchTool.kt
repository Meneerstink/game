package gg.rsmod.game.tools.importer

import java.io.File

/**
 * Re-encodes an already imported OSRS loc type onto its SAME local id with a height offset (rev-667 loc opcode 71), for an OSRS
 * loc that only works on the OSRS ground it was built for (N522, owner 2026-11-01: only the boss lairs and the Revenant caves are
 * imported, the rest of the Wilderness map stays).
 *
 * The OSRS "Volcanic Forge" 26755 is a 4-vertex lava-glow quad (model 13861) 304 units below its tile, shrunk to 1/4 height and
 * contoured to the ground: in OSRS it sits in the volcano mouth, a tile without floor. On the rev-667 volcano that tile is lava
 * floor, so the quad lies 76 units under it - invisible and impossible to click (test client 2026-11-01: "Use shard ->" finds
 * nothing). The patch lifts the same model to just above the lava surface; nothing else of the definition changes.
 *
 * Usage: `./gradlew :game:runOsrsLocOffsetPatchTool --args="<batch> [--apply]"`
 */
object OsrsLocOffsetPatchTool {
    class Patch(val osrsId: Int, val localId: Int, val models: Map<Int, Int>, val offsetHeight: Int, val currentSha1: String, val note: String)

    val BATCHES: Map<String, Patch> =
        mapOf(
            // Model 13861 -> local 5088 and loc 26755 -> 62958: OsrsRegionImportTool batch volcanic-forge, tx-20261101-223411.
            // -80 = the quad's 304 x 32/128 = 76 units below the ground, plus 4 so it does not z-fight with the lava floor.
            // currentSha1 = the bytes written by tx-20261101-223411 (the patch refuses anything else).
            "volcanic-forge-height" to Patch(26755, 62958, mapOf(13861 to 5088), -80, "1f9e48ff8a6537761a239c828fe8116a24e74521", "Volcanic Forge on the rev-667 lava surface"),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val name = args.getOrNull(0) ?: error("Usage: <batch> [--apply]; batches: ${BATCHES.keys}")
        val patch = BATCHES[name] ?: error("Unknown batch $name; batches: ${BATCHES.keys}")
        val apply = "--apply" in args
        val dropped = ArrayList<String>()
        val seqs = OsrsFxImportTool.existingFx(File(FeroxImportTool.ASSET_MAP)).filterKeys { it.startsWith("seq:") }
        val bytes =
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
                val def = ModernObjectDef.decode(patch.osrsId, modern.files(2, 6)[patch.osrsId] ?: error("modern loc ${patch.osrsId} missing"))
                println("OSRS loc ${patch.osrsId} '${def.name}' offsetHeight=${def.offsetHeight} models=${def.models} -> ${patch.offsetHeight}")
                def.offsetHeight = patch.offsetHeight
                Rev667LocType.encodeFromModern(def, { patch.models[it] ?: error("model $it not mapped") }, dropped) { seqs["seq:$it"] }
            }
        dropped.distinct().forEach { println("DROPPED $it") }
        val mutation =
            CacheMutation(FeroxImportTool.LOC_INDEX, patch.localId ushr 8, patch.localId and 0xFF, bytes, "$name loc osrs=${patch.osrsId} -> ${patch.localId} offsetHeight ${patch.offsetHeight}", expectedCurrentSha1 = patch.currentSha1)
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), listOf(mutation))
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (!apply) {
            println("DRY_RUN transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK")
        File(FeroxImportTool.ASSET_MAP).appendText(
            "  - name: OSRS loc offset patch '$name' - ${patch.note}\n    transaction_id: ${tx.id}\n" +
                "    loc: { upstream_id: ${patch.osrsId}, local_id: ${patch.localId}, offset_height: ${patch.offsetHeight} }\n",
        )
    }
}
