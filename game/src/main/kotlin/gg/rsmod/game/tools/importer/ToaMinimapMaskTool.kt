package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N601: keep OSRS transparent terrain shapes AND their secondary/minimap colours. Sources: cache2686 overlay definitions,
 * native667 FloorOverlayType opcode7, Class306.loadBlended/loadUnblended and Static718.blendColour.
 * New slots were unused in all2271 local map squares (09-Nov2026); every plan/apply proves their usage again in BOTH caches.
 * Native63 already matches source63. All other definitions copy native41, adding only the exact source secondary RGB.
 * No model, height, underlay, flag, texture or client change. Usage: plan|apply.
 */
object ToaMinimapMaskTool {
    data class Mask(val local: Int, val colour: Int)

    val MASKS = linkedMapOf(
        63 to Mask(63, 0xb8b098),
        64 to Mask(16, 0x882222),
        72 to Mask(17, 0x660000),
        99 to Mask(45, 0x222222),
        123 to Mask(66, 0x450000),
        125 to Mask(78, 0x383830),
        137 to Mask(145, 0x2e2e2e),
        207 to Mask(157, 0x6b633c),
        219 to Mask(174, 0x2e2e42),
        230 to Mask(175, 0x665f47),
        233 to Mask(181, 0x212a3e),
        362 to Mask(245, 0x4e2c2a),
    )
    val ALLOCATED = MASKS.filterKeys { it != 63 }.values.map { it.local }.toSet()
    val TARGETS get() = ToaHiddenFloorTool.TARGETS
    fun isToa(squares: List<Int>): Boolean = squares.isNotEmpty() && squares.all { it in TARGETS }

    /** Reject a changed pinned definition instead of silently dropping that mask during a future import. */
    fun local(tileValue: Int, source: ModernOverlayDef?, squares: List<Int>): Int? {
        val mask = MASKS[tileValue - 1] ?: return null
        if (!isToa(squares)) return null
        check(source != null && source.rgb == 0xff00ff && source.texture == -1 && !source.hideUnderlay &&
            source.secondaryRgb == mask.colour) { "Source minimap mask ${tileValue - 1} changed" }
        return mask.local
    }

    fun validateNative(local: Int, def: Rev667OverlayDef) {
        val mask = MASKS.values.single { it.local == local }
        check(def.rgb == 0xff00ff && def.texture == -1 && !def.occludes && def.blendRgb == mask.colour) {
            "Native minimap mask $local differs from source"
        }
    }

    internal fun definition(template: ByteArray, colour: Int): ByteArray {
        ToaHiddenFloorTool.validateNative(Rev667FloorCodec.decodeOverlay(template))
        check(template.last() == 0.toByte())
        return template.copyOf(template.size - 1) +
            byteArrayOf(7, (colour shr 16).toByte(), (colour shr 8).toByte(), colour.toByte(), 0)
    }

    internal fun restore(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>): Pair<ByteArray, Int> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        var restored = 0
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val src = source[p][x][z]
            val mask = MASKS[src.overlayId - 1] ?: continue
            val tile = map.tiles[p][x][z]
            check(tile.height == src.height) { "Source height differs at $p/$x/$z" }
            check((tile.overlayId and 255) in setOf(0, mask.local + 1)) { "Conflicting mask at $p/$x/$z" }
            if ((tile.overlayId and 255) == mask.local + 1 && tile.overlayShape == src.overlayPath &&
                tile.overlayRotation == src.overlayRotation) continue
            tile.overlayId = mask.local + 1
            tile.overlayShape = src.overlayPath
            tile.overlayRotation = src.overlayRotation
            restored++
        }
        return Rev667TileCodec.encode(map) to restored
    }

    /** A reserved slot may occur only on a source-proven mask tile after an earlier successful apply. */
    internal fun validateUse(value: Int, source: ModernTile?, target: Rev667Tile, definitionMatches: Boolean) {
        if (value - 1 !in ALLOCATED) return
        val mask = source?.let { MASKS[it.overlayId - 1] }
        check(definitionMatches && source != null && mask?.local == value - 1 && target.height == source.height &&
            target.overlayShape == source.overlayPath && target.overlayRotation == source.overlayRotation) {
            "Reserved overlay ${value - 1} is used outside its verified ToA source masks"
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("plan", "apply")) { "Usage: plan|apply" }
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            MASKS.forEach { (id, mask) ->
                check(local(id + 1, ModernFloorDefs.decodeOverlay(requireNotNull(reader.file(2, 4, id))), TARGETS.keys.toList()) == mask.local)
            }
            val sourceMaps = TARGETS.mapValues { (id, _) -> ModernRegion.decodeTiles(requireNotNull(reader.file(5, id, 0))) }
            val sourcesByName = TARGETS.entries.associate { (id, name) -> name to sourceMaps.getValue(id) }
            val game = CacheLibrary(paths[0])
            val files = CacheLibrary(paths[1])
            try {
                val template = requireNotNull(game.data(2, 4, 41))
                check(template.contentEquals(files.data(2, 4, 41))) { "Native41 cache copies differ" }
                val expected = MASKS.values.associate { mask -> mask.local to definition(template, mask.colour) }
                val matches = MASKS.values.associate { mask ->
                    val before = game.data(2, 4, mask.local)
                    check(before.contentEquals(files.data(2, 4, mask.local))) { "Overlay ${mask.local} cache copies differ" }
                    if (mask.local == 63) {
                        validateNative(63, Rev667FloorCodec.decodeOverlay(requireNotNull(before)))
                        63 to true
                    } else mask.local to before.contentEquals(expected.getValue(mask.local))
                }
                val names = HashMap<Int, String>()
                for (x in 0..255) for (z in 0..255) "m${x}_$z".let { names[it.hashCode()] = it }
                var scanned = 0
                val otherMaps = files.index(5).archives().filter { it.hashName in names }.associate { it.id to it.hashName }
                val ownMaps = game.index(5).archives().filter { it.hashName in names }.associate { it.id to it.hashName }
                check(ownMaps == otherMaps) { "Map archive references differ between caches" }
                for ((id, hash) in ownMaps) {
                    val name = names.getValue(hash)
                    val bytes = requireNotNull(game.data(5, id, 0))
                    check(bytes.contentEquals(files.data(5, id, 0))) { "$name cache copies differ" }
                    val map = Rev667TileCodec.decode(bytes)
                    val src = sourcesByName[name]
                    for (p in 0..3) for (x in 0..63) for (z in 0..63) {
                        val tile = map.tiles[p][x][z]
                        val value = tile.overlayId and 255
                        validateUse(value, src?.get(p)?.get(x)?.get(z), tile, matches[value - 1] == true)
                    }
                    scanned++
                }
                println("MINIMAP_MASK_SLOTS maps=$scanned bothCopiesEqual=true allocated=$ALLOCATED")
                for ((local, after) in expected) {
                    if (local == 63) continue // Existing native definition, never rewritten.
                    val before = game.data(2, 4, local)
                    if (!before.contentEquals(after)) mutations += CacheMutation(2, 4, local, after,
                        "ToA transparent mask $local: exact source secondary colour", before?.let(CacheItemProbeTool::sha1))
                }
                for ((square, name) in TARGETS) {
                    val archive = requireNotNull(game.index(5).archive(name))
                    val before = requireNotNull(archive.file(0)?.data)
                    val (after, count) = restore(before, sourceMaps.getValue(square))
                    println("MINIMAP_MASK source=$square target=$name restored=$count")
                    if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                        "ToA $name: restore transparent secondary-colour masks", CacheItemProbeTool.sha1(before))
                }
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} mutations=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("MINIMAP_MASK transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
