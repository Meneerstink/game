package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * N462 ToA map (2026-11-06): the rev-667 client hides every non-interactive, non-blocking ground decoration when the graphics option
 * "Ground decoration" is off (MapRegion: groundDecor == 0 && NONINTERACTIVE && blockwalk != 1 && !forcedecor) - and the 78 default
 * profile (RspsGraphicsProfile, MID) has it off. OSRS has no such option: its ground decorations always draw, and in the ToA rooms they
 * ARE the floor (Kephri: 578 shape-22 locs - the light tiles showed as dark underlay in the test client). This adds rev-667 loc opcode 73
 * (forcedecor) to every loc type an OSRS region import created (OSRS_IMPORT_MASTER.yml block "OSRS region import '<name>'"), so those
 * locs draw like OSRS whatever the option. Only the opcode is added; every other byte of a definition stays. Pinned on the current bytes.
 *
 * Usage: `./gradlew :game:runLocForceDecorTool --args="<import name> plan|apply"`
 */
object LocForceDecorTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2 && args[1] in setOf("plan", "apply")) { "Usage: <import name> plan|apply" }
        val (name, mode) = args
        val ledger = File(FeroxImportTool.ASSET_MAP).readText()
        // N462 (2026-11-06): `tx:<transaction>` = the locs an OsrsLocImportTool batch recorded (fx_kind osrs_loc_loc, that transaction) -
        // the ToA locs the raid places itself (Scabaras/warning tiles, Kephri dung ...) are ground decoration too.
        val localIds =
            if (name.startsWith("tx:")) {
                val tx = name.removePrefix("tx:")
                Regex("""- fx_kind: osrs_loc_loc\s+upstream_fx_id: \d+\s+local_fx_id: (\d+)\s+status: IMPORTED_BY_OSRS_LOC_TOOL\s+transaction: ${Regex.escape(tx)}\b""")
                    .findAll(ledger).map { it.groupValues[1].toInt() }.toList()
            } else {
                val header = "OSRS region import '$name'"
                require(ledger.contains(header)) { "no ledger block $header" }
                val block = ledger.substringAfter(header).substringAfter("loc_ids:").substringBefore("    models:")
                Regex("""upstream_id: (\d+), local_id: (\d+)""").findAll(block).map { it.groupValues[2].toInt() }.toList()
            }
        require(localIds.isNotEmpty()) { "no loc ids for $name" }
        val mutations = ArrayList<CacheMutation>()
        var already = 0
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        try {
            for (id in localIds) {
                val data = library.data(FeroxImportTool.LOC_INDEX, id ushr 8, id and 0xFF) ?: error("loc $id missing")
                require(data.isNotEmpty() && data.last().toInt() == 0) { "loc $id does not end with opcode 0" }
                if (hasForceDecor(data)) { already++; continue }
                val bytes = data.copyOf(data.size + 1)
                bytes[data.size - 1] = 73
                bytes[data.size] = 0
                check(hasForceDecor(bytes)) { "loc $id: opcode 73 not readable after the edit" }
                Rev667LocType.decode(id, bytes) // full opcode walk: throws on a malformed definition
                mutations += CacheMutation(FeroxImportTool.LOC_INDEX, id ushr 8, id and 0xFF, bytes, "$name loc $id forcedecor (op 73)", CacheItemProbeTool.sha1(data))
            }
        } finally {
            library.close()
        }
        println("LOCS ${localIds.size} from '$name': add op73 to ${mutations.size}, already ${already}")
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        println("PREFLIGHT transaction=${tx.id} mutations=${mutations.size} outcomes=${preflight.groupingBy { it.outcome }.eachCount()}")
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") return
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes")
    }

    /** True when the definition carries opcode 73, found by walking the opcodes with [Rev667LocType.decode]'s own table. */
    private fun hasForceDecor(data: ByteArray): Boolean = Rev667LocType.decode(-1, data).opcodes.contains(73)
}
