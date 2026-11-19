package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import java.io.File

/**
 * N601: OSRS overlay41 is a transparent mask, not an absent overlay. Dropping it makes the 667 terrain builder
 * turn shape0 into underlay-only shape12, exposing grey ground around the Wardens floor. Native overlay41 has
 * the same RGB ff00ff, no texture/secondary colour and no occlusion. Restore the source mask and its shape;
 * retain every height, underlay, flag and environment byte. No definition or client change.
 * Sources: cache2686 overlay41 and ToA terrain; 667 FloorOverlayType.colour, Class306.loadBlended/loadUnblended.
 * Usage: plan|apply [cox|necropolis]
 * necropolis (TOA-1911 punt 4, 19-11): same hidden-overlay mapping as cox on the Necropolis exterior squares (OsrsRegionImportTool
 * 'necropolis' dropped them -> world map + minimap showed the sand underlay where OSRS draws dark buildings/pyramid and brown paths).
 * cox (16-11 GRAFISCH, eigenaar 19:5x): same mask in the Chambers of Xeric squares (ToaUpperTileFlagTool 'cox', +0,+24). The CoX
 * batches dropped it and 'cox-map-floors' then laid the black void overlay (tile value 52) on those tiles, hiding the lava planes
 * that OSRS shows through the mask (Tekton/Vanguard: OSRS yellow lava, ours black/beige). Only where the OSRS source tile IS the mask.
 */
object ToaHiddenFloorTool {
    const val SOURCE_OVERLAY = 41
    const val LOCAL_OVERLAY = 41
    val TARGETS = ToaUpperTileFlagTool.targets("raid") + ToaUpperTileFlagTool.targets("lobby")
    val COX_TARGETS = ToaUpperTileFlagTool.targets("cox")
    val NECRO_TARGETS = ToaUpperTileFlagTool.targets("necropolis")

    /** CoX: the plain black void overlay (tile value) that 'cox-map-floors' put over the dropped masks; may be replaced by the mask. */
    const val COX_VOID_TILE_VALUE = 52

    /** Only the uncoloured source mask: other hidden overlays have separate minimap colours, left for their own repair. */
    fun preserveMask(tileValue: Int, source: ModernOverlayDef?, squares: List<Int>): Boolean =
        tileValue == SOURCE_OVERLAY + 1 && source != null && source.rgb == 0xFF00FF && source.texture == -1 &&
            !source.hideUnderlay && source.secondaryRgb == -1 && squares.isNotEmpty() && squares.all { it in TARGETS || it in COX_TARGETS || it in NECRO_TARGETS }

    fun validateNative(def: Rev667OverlayDef) {
        check(def.rgb == 0xFF00FF && def.texture == -1 && !def.occludes && def.blendRgb == -1) {
            "Native overlay41 is no longer the transparent, uncoloured source mask"
        }
    }

    /**
     * CoX: every OSRS HIDDEN overlay (rgb ff00ff, no texture) -> the 667 overlay with the SAME definition (rgb ff00ff, no texture and
     * blend colour == the OSRS secondary/minimap colour, or none). Tile values (def + 1). 667 Class306 hides such a tile's faces (colour -1)
     * and keeps the blend colour for the minimap, like OSRS. OSRS hidden defs without a matching 667 def are reported, never guessed.
     */
    fun hiddenMap(osrs: Map<Int, ModernOverlayDef>, local: Map<Int, Rev667OverlayDef>): Pair<Map<Int, Int>, List<Int>> {
        val out = HashMap<Int, Int>()
        val missing = ArrayList<Int>()
        for ((id, def) in osrs.toSortedMap()) {
            if (def.rgb != 0xFF00FF || def.texture != -1) continue
            val want = def.secondaryRgb
            fun ok(l: Rev667OverlayDef?) = l != null && l.rgb == 0xFF00FF && l.texture == -1 && l.blendRgb == want
            var hit = if (ok(local[id])) id else local.keys.sorted().firstOrNull { ok(local[it]) }
            if (hit == null && want != -1) {
                // 16-11 21:4x: geen exacte 667-def en geen vrij slot meer -> een verborgen 667-def waarvan alleen de MINIMAPkleur hooguit 8 per
                // kanaal afwijkt (scene = gat, gelijk aan OSRS). Altijd gemeld als BIJNA, nooit stil.
                fun near(l: Rev667OverlayDef?) = l != null && l.rgb == 0xFF00FF && l.texture == -1 && l.blendRgb != -1 &&
                    (0..2).all { s -> kotlin.math.abs(((l.blendRgb shr (s * 8)) and 255) - ((want shr (s * 8)) and 255)) <= 8 }
                hit = local.keys.sorted().firstOrNull { near(local[it]) }
                if (hit != null) println("HIDDEN_MAP BIJNA osrs def $id 2e %06x -> 667 def $hit 2e %06x (alleen minimapkleur)".format(want, local[hit]!!.blendRgb))
            }
            if (hit == null) missing += id else out[id + 1] = hit + 1
        }
        return out to missing
    }

    internal fun restore(bytes: ByteArray, source: Array<Array<Array<ModernTile>>>, replaceable: Set<Int> = emptySet(),
                         valueMap: Map<Int, Int> = mapOf(SOURCE_OVERLAY + 1 to LOCAL_OVERLAY + 1)): Pair<ByteArray, IntArray> {
        val map = Rev667TileCodec.decode(bytes)
        check(Rev667TileCodec.encode(map).contentEquals(bytes)) { "Tile roundtrip failed" }
        val counts = IntArray(4)
        for (p in 0..3) for (x in 0..63) for (z in 0..63) {
            val src = source[p][x][z]
            val want = valueMap[src.overlayId] ?: continue
            val tile = map.tiles[p][x][z]
            check(tile.height == src.height) { "Source height differs at $p/$x/$z" }
            check((tile.overlayId and 255) in setOf(0, want) + replaceable) { "Unexpected overlay ${tile.overlayId and 255} at $p/$x/$z (want $want)" }
            if ((tile.overlayId and 255) == want && tile.overlayShape == src.overlayPath &&
                tile.overlayRotation == src.overlayRotation) continue
            tile.overlayId = want
            tile.overlayShape = src.overlayPath
            tile.overlayRotation = src.overlayRotation
            counts[p]++
        }
        return Rev667TileCodec.encode(map) to counts
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 1..2 && args[0] in setOf("plan", "apply") && (args.size == 1 || args[1] in setOf("cox", "necropolis"))) { "Usage: plan|apply [cox|necropolis]" }
        val necro = args.getOrNull(1) == "necropolis"
        val cox = args.getOrNull(1) == "cox" || necro
        val targets = if (necro) NECRO_TARGETS else if (cox) COX_TARGETS else TARGETS
        val replaceable = if (cox && !necro) setOf(COX_VOID_TILE_VALUE) else emptySet()
        var valueMap: Map<Int, Int> = mapOf(SOURCE_OVERLAY + 1 to LOCAL_OVERLAY + 1)
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        ModernCacheReader(File(RspsPaths.root("import-source/openrs2-2686/cache"))).use { reader ->
            val mask = ModernFloorDefs.decodeOverlay(requireNotNull(reader.file(2, 4, SOURCE_OVERLAY)))
            check(preserveMask(SOURCE_OVERLAY + 1, mask, targets.keys.toList())) { "Source overlay41 changed" }
            val game = CacheLibrary(paths[0])
            val files = CacheLibrary(paths[1])
            try {
                for (cache in listOf(game, files))
                    validateNative(Rev667FloorCodec.decodeOverlay(requireNotNull(cache.data(2, 4, LOCAL_OVERLAY))))
                if (cox) {
                    val osrsDefs = reader.files(2, 4).mapValues { ModernFloorDefs.decodeOverlay(it.value) }
                    val localDefs = (0..254).mapNotNull { id -> game.data(2, 4, id)?.let { id to Rev667FloorCodec.decodeOverlay(it) } }.toMap()
                    val (m, missing) = hiddenMap(osrsDefs, localDefs)
                    valueMap = m
                    println("HIDDEN_MAP osrs->667 tile values ${m.toSortedMap()} geen-667-gelijke=$missing")
                }
                for ((square, name) in targets) {
                    val sourceBytes = reader.file(5, square, 0) ?: run { println("HIDDEN_FLOOR source=$square missing - skipped"); null } ?: continue
                    val source = ModernRegion.decodeTiles(sourceBytes)
                    val archive = requireNotNull(game.index(5).archive(name)) { "Missing $name" }
                    val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                    val before = requireNotNull(archive.file(0)?.data)
                    check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                    val (after, counts) = restore(before, source, replaceable, valueMap)
                    println("HIDDEN_FLOOR source=$square target=$name restored=${counts.toList()}")
                    if (!before.contentEquals(after)) mutations += CacheMutation(5, archive.id, 0, after,
                        "${if (necro) "Necropolis" else if (cox) "CoX" else "ToA"} $name: restore OSRS hidden overlays", CacheItemProbeTool.sha1(before))
                }
            } finally { game.close(); files.close() }
        }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("HIDDEN_FLOOR transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
