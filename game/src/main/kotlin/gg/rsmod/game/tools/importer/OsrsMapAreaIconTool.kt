package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * OSRS map areas (world map + minimap function icons, OSRS loc op82) that have no 667 picture ([OsrsMapIconTable.BLOCKED_AREAS]) and
 * belong to imported content: the OSRS sprite of the area (OSRS config group 35 op1, cache 2686) is copied unchanged into the 667
 * sprite index as a new sprite, a new 667 MapElementType (config group 36: op1 sprite, as [DeathsOfficeMapImportTool] made 1108 for
 * area 652) points at it, and the imported icon locs get that mapelement (loc op107, [FeroxMinimapTool.patched]).
 *
 * [ICONS] (dossier COX.txt 22:3x / N618): area 91 = the raid icon (gameval TOX_TILES_RED_9_RAID_ICON 46200 at the Chambers of Xeric
 * steps, cox-entrance import local 66582); area 1015 = TRANSPORTATION_ICON_BLS 56208 (fairy ring BLS, local 66594).
 * Nothing is drawn by hand: sprite bytes, area -> sprite and loc placement are OSRS's own.
 *
 * Usage: `kc_tool.sh OsrsMapAreaIconTool [--apply]`
 */
object OsrsMapAreaIconTool {
    const val CONFIG_INDEX = 2
    const val AREA_GROUP = 35
    const val MAP_ELEMENT_GROUP = 36
    const val SPRITE_INDEX = 8

    /** OSRS area -> the local loc ids that carry it (from data/cfg/cox/ids.tsv). */
    // 65762 = the ToA entrance's RAID_ICON 46201 (OSRS 3355,2714 plane 1, square 13354; N618 16-11: our Necropolis import kept it as 65762,
    // same def as 46201, without the area) - the same raid icon as CoX.
    val ICONS = linkedMapOf(91 to listOf(66582, 65762), 1015 to listOf(66594))

    /** Areas whose OSRS sprite already has a pixel-proven 667 mapelement (OsrsMapIconTable: OSRS sprite 1504 = mapelement 612). */
    // 91 -> 1149: created by this tool on 15-11 (tx-20261115-212554, sprite 8954) - reused for the next raid-icon locs (ToA, N618).
    val REUSE = mapOf(1015 to 612, 91 to 1149)

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val mutations = mutableListOf<CacheMutation>()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val source = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        try {
            var nextSprite = (library.index(SPRITE_INDEX).archiveIds().maxOrNull() ?: error("no sprites")) + 1
            var nextElement = (library.index(CONFIG_INDEX).archive(MAP_ELEMENT_GROUP)?.fileIds()?.maxOrNull() ?: error("no mapelements")) + 1
            ICONS.forEach { (area, locs) ->
                val areaBytes = source.files(CONFIG_INDEX, AREA_GROUP)[area] ?: error("OSRS area $area missing")
                val (osrsSprite, name) = FeroxMapSpriteProbeTool.osrsArea(areaBytes)
                check(osrsSprite >= 0) { "OSRS area $area has no sprite" }
                val spriteBytes = source.file(SPRITE_INDEX, osrsSprite, 0) ?: error("OSRS sprite $osrsSprite missing")
                val frames = FeroxMapSpriteProbeTool.decodeSprites(spriteBytes)
                val reused = REUSE[area]
                val element = reused ?: nextElement++
                if (reused == null) {
                    val sprite = nextSprite++
                    mutations += CacheMutation(SPRITE_INDEX, sprite, 0, spriteBytes, "sprite osrs:$osrsSprite (map area $area '$name') -> 667:$sprite")
                    val elementBytes = byteArrayOf(1, (sprite ushr 8).toByte(), sprite.toByte(), 0)
                    mutations += CacheMutation(CONFIG_INDEX, MAP_ELEMENT_GROUP, element, elementBytes, "mapelement $element: OSRS map area $area '$name'")
                    println("PLAN area $area '$name' osrsSprite=$osrsSprite ${frames.firstOrNull()?.let { "${it.w}x${it.h}" }} -> sprite $sprite mapelement $element")
                } else println("PLAN area $area osrsSprite=$osrsSprite -> existing mapelement $element (OsrsMapIconTable proof)")
                locs.forEach { loc ->
                    val current = library.data(FeroxImportTool.LOC_INDEX, loc ushr 8, loc and 0xFF) ?: error("loc $loc missing")
                    val before = Rev667LocType.decode(loc, current)
                    if (before.mapElement == element) { println("SKIP loc $loc already has mapelement $element"); return@forEach }
                    check(before.mapElement == -1) { "loc $loc already has mapelement ${before.mapElement}" }
                    val after = FeroxMinimapTool.patched(current, null, element)
                    check(Rev667LocType.decode(loc, after).mapElement == element) { "loc $loc patch failed" }
                    mutations += CacheMutation(FeroxImportTool.LOC_INDEX, loc ushr 8, loc and 0xFF, after, "loc $loc '${before.name}' mapelement $element",
                        CacheItemProbeTool.sha1(current))
                    println("PLAN loc $loc '${before.name}' -> mapelement $element")
                }
            }
        } finally {
            library.close()
            source.close()
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
    }
}
