package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/** N601: add native HD textures to reserved ToA colours. Never changes maps, models, RGB or other overlay fields.
 * Usage: :game:runToaOverlayTexturePatchTool --args="plan|apply". Apply BEFORE toa-map-hd --apply.
 */
object ToaOverlayTexturePatchTool {
    data class Patch(val texture: Int, val keepRgb: Int)

    // Source: tools/n601_material_proposals.json, reserved_overlay_texture_patches.
    val PATCHES: Map<Int, Patch> = mapOf(
        23 to Patch(301, 0xf5eeec),
        27 to Patch(301, 0x958b67),
        46 to Patch(301, 0x431d1b),
        127 to Patch(600, 0x212a3e),
        129 to Patch(408, 0x1c1919),
        147 to Patch(505, 0x7d7657),
        183 to Patch(408, 0x1c1b14),
        226 to Patch(301, 0x4e2c2a),
        237 to Patch(493, 0x660000),
        246 to Patch(301, 0x8c7973),
        248 to Patch(408, 0x12110e),
        250 to Patch(301, 0x993333),
        252 to Patch(505, 0x665f47),
        253 to Patch(408, 0x131111),
        254 to Patch(301, 0x788868),
    )

    val TARGET_NAMES: Set<String> = run {
        val raid = OsrsRegionImportTool.BATCHES.getValue("toa-map-hd")
        raid.squares.map { OsrsRegionImportTool.mapName(raid.target(it)) }.toSet() +
            listOf(13097, 13098, 13353, 13354, 13355, 13609, 13610, 13611).map { OsrsRegionImportTool.mapName(it) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: "plan"
        require(args.size <= 1 && mode in setOf("plan", "apply")) { "Usage: plan|apply" }
        val tx = plan(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE))
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing $mode." }
        if (mode == "plan") {
            println("DRY_RUN transaction=${tx.id} mutations=${tx.mutations.size} (nothing written)")
            return
        }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val errors = tx.verify()
        errors.forEach { println("VERIFY_ERROR: $it") }
        check(errors.isEmpty()) { "Verification failed; see journal ${tx.journalDir}." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }

    /** Repeat the full census in EACH target on every plan/apply. No cached research snapshot is trusted. */
    internal fun plan(
        targets: List<String>,
        patches: Map<Int, Patch> = PATCHES,
        journalRoot: File = File(CacheTransaction.DEFAULT_JOURNAL_ROOT),
    ): CacheTransaction {
        require(targets.isNotEmpty() && patches.isNotEmpty())
        val mutations = ArrayList<CacheMutation>()
        val names = HashMap<Int, String>()
        // Include underwater terrain too: these definitions are global.
        for (prefix in listOf("m", "um")) for (x in 0 until 256) for (z in 0 until 256) {
            val name = "$prefix${x}_$z"
            names[name.hashCode()] = name
        }
        targets.forEachIndexed { targetIndex, target ->
            val library = CacheLibrary(target)
            try {
                val maps = library.index(Rev667RegionProbeTool.MAP_INDEX)
                val scanned = census(maps.archives().asSequence().mapNotNull { archive ->
                    val name = names[archive.hashName] ?: return@mapNotNull null
                    name to (maps.archive(archive.id)?.file(0)?.data ?: error("$target: unreadable tile file $name; cannot prove scope"))
                }, patches.keys)
                check(scanned > 0) { "$target: no terrain files found; cannot prove scope" }
                println("CENSUS target=$target scanned=$scanned reservedSlots=${patches.size} allowedSquares=${TARGET_NAMES.size} outsideUses=0")
                patches.forEach { (slot, patch) ->
                    val before = library.data(Rev667RegionProbeTool.CONFIG_INDEX, Rev667RegionProbeTool.OVERLAY_GROUP, slot)
                        ?: error("$target: reserved overlay $slot missing")
                    check(library.data(9, patch.texture, 0) != null) { "$target: native texture ${patch.texture} missing" }
                    val after = patchOverlay(before, patch)
                    if (targetIndex == 0) {
                        mutations += CacheMutation(Rev667RegionProbeTool.CONFIG_INDEX, Rev667RegionProbeTool.OVERLAY_GROUP, slot,
                            after, "N601 overlay $slot texture=${patch.texture} keep_rgb=${"%06x".format(patch.keepRgb)}",
                            CacheItemProbeTool.sha1(before))
                    } else {
                        // Refuse divergent colours, flags or textures instead of silently repairing the second cache.
                        check(CacheItemProbeTool.sha1(before) == mutations.first { it.fileId == slot }.expectedCurrentSha1) {
                            "$target: overlay $slot differs between target caches"
                        }
                    }
                }
            } finally {
                library.close()
            }
        }
        return CacheTransaction(targets, mutations, journalRoot)
    }

    internal fun census(terrain: Sequence<Pair<String, ByteArray>>, slots: Set<Int>): Int {
        var scanned = 0
        val unreadable = mutableListOf<String>()
        terrain.forEach { (name, bytes) ->
            // Some 667 squares do not decode with Rev667TileCodec (buffer ends early). B2's python census (tools/n601_graphics_probe.py)
            // read all 2271 squares; report them here instead of aborting, and never accept an unreadable ToA/Necropolis target.
            val map = try { Rev667TileCodec.decode(bytes) } catch (e: Exception) {
                check(name !in TARGET_NAMES) { "ToA/Necropolis square $name does not decode; refusing" }
                unreadable += name; return@forEach
            }
            scanned++
            if (name !in TARGET_NAMES) {
                for (plane in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
                    val value = map.tiles[plane][x][z].overlayId and 0xFF
                    check(value == 0 || value - 1 !in slots) {
                        "Reserved overlay ${value - 1} used outside ToA/Necropolis: $name plane=$plane tile=$x,$z; refusing"
                    }
                }
            }
        }
        if (unreadable.isNotEmpty()) println("CENSUS_UNREADABLE ${unreadable.size} (covered by tools/n601_graphics_probe.py): ${unreadable.joinToString(" ")}")
        return scanned
    }

    /** Replace only opcodes 2/3; preserve the exact bytes of every non-texture opcode, including unknown-to-the-def flags. */
    internal fun patchOverlay(bytes: ByteArray, patch: Patch): ByteArray {
        require(patch.texture in 0..65534)
        val def = Rev667FloorCodec.decodeOverlay(bytes)
        check(def.rgb == patch.keepRgb) { "keep_rgb mismatch: expected ${patch.keepRgb.toString(16)}, found ${def.rgb.toString(16)}" }
        val out = ByteArrayOutputStream()
        var position = 0
        while (position < bytes.size) {
            val start = position
            val opcode = bytes[position++].toInt() and 0xFF
            if (opcode == 0) {
                check(position == bytes.size) { "Trailing overlay bytes" }
                out.write(3)
                out.write(patch.texture ushr 8)
                out.write(patch.texture and 0xFF)
                out.write(0)
                val result = out.toByteArray()
                check(Rev667FloorCodec.decodeOverlay(result).texture == patch.texture)
                return result
            }
            val width = when (opcode) {
                1, 7, 13 -> 3
                2, 11, 14, 16 -> 1
                3, 9 -> 2
                5, 8, 10, 12 -> 0
                else -> error("Unknown overlay opcode $opcode")
            }
            position += width
            check(position < bytes.size) { "Truncated overlay opcode $opcode" }
            if (opcode != 2 && opcode != 3) out.write(bytes, start, position - start)
        }
        error("Overlay terminator missing")
    }
}
