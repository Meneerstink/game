package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * N529/N531/N522 kaart (owner 2026-11-02 'nieuwe revcave entrance staat niet op de minimap/worldmap'; GOUDEN REGEL: the whole class).
 *
 * Every OSRS loc import ([Rev667LocType.encodeFromModern]) dropped the modern map scene (OSRS op68) and map area (op82), so no
 * imported loc draws its minimap/world-map picture - the Revenant Caves caverns, the Web Chasm and every wilderness lair entrance
 * carry OSRS map scene 7 (the cave mouth) and show nothing here. [FeroxMinimapTool] repaired only the Ferox locs by hand.
 *
 * This tool does it for EVERY loc in the `loc_ids:` lists of `OSRS_IMPORT_MASTER.yml` (mode IMPORT): it compares each OSRS map-scene
 * frame (sprite group "mapscene", frame N) with every 667 MSIType sprite, and each OSRS map-area sprite with every 667
 * MapElementType sprite ([FeroxMapSpriteProbeTool] decoders, canvas-padded pixel compare), and only carries a value across when the
 * picture is identical or within [MAX_DIFF] (the Ferox proof threshold). Among identical candidates it prefers the same id, then the
 * id most used by 667 locs. Locs that already have an msi / mapelement keep it (field by field). Values without a proven 667
 * picture are reported as SOURCE_BLOCKED and left out - nothing is invented.
 *
 * `plan` prints the mapping table, every patch and the blocked values; `apply` writes both production caches in one
 * CacheTransaction (verify + rollback). The proven table is also [OsrsMapIconTable] for future imports.
 *
 * Usage: `./gradlew :game:runOsrsLocMapDataTool --args="plan|apply"`
 */
object OsrsLocMapDataTool {
    const val LOC_INDEX = 16
    const val MAX_DIFF = 0.01

    /** OSRS map area 652 (Death's Office tombstone) = local 1108, which DeathsOfficeMapImportTool puts on one chosen loc per entrance. */
    const val DEATHS_OFFICE_AREA = 652

    data class Entry(val upstream: Int, val local: Int, val section: String)

    /** All `loc_ids:` entries with mode IMPORT, per import section. */
    fun locEntries(file: File = File(FeroxMapDataProbeTool.ASSET_MAP)): List<Entry> {
        val out = ArrayList<Entry>()
        var section = ""
        var key = ""
        val entry = Regex("""upstream_id: (\d+), local_id: (\d+), mode: (\w+)""")
        file.forEachLine { line ->
            when {
                line.startsWith("  - name: ") -> {
                    section = line.removePrefix("  - name: ").trim()
                    key = ""
                }
                Regex("""^    (\w+):\s*$""").matches(line) -> key = line.trim().removeSuffix(":")
                key == "loc_ids" -> entry.find(line)?.let { m -> if (m.groupValues[3] == "IMPORT") out += Entry(m.groupValues[1].toInt(), m.groupValues[2].toInt(), section) }
            }
        }
        return out
    }

    class Mapping(val scenes: Map<Int, Int>, val areas: Map<Int, Int>, val blockedScenes: Map<Int, String>, val blockedAreas: Map<Int, String>)

    fun buildMapping(
        library: CacheLibrary,
        modern: ModernCacheReader,
        scenes: Set<Int>,
        areas: Set<Int>,
        locUse: Map<Int, Int>,
    ): Mapping {
        val localSprites = HashMap<Int, FeroxMapSpriteProbeTool.Frame?>()
        fun localFrame(id: Int) = localSprites.getOrPut(id) { runCatching { library.data(8, id, 0)?.let { FeroxMapSpriteProbeTool.decodeSprites(it).firstOrNull() } }.getOrNull() }
        val msi = library.index(2).archive(34)!!.fileIds().associateWith { runCatching { FeroxMapSpriteProbeTool.msiImage(library.data(2, 34, it)!!) }.getOrDefault(-1) }
        val elements = library.index(2).archive(36)!!.fileIds().associateWith { runCatching { FeroxMapSpriteProbeTool.mapElementSprite(library.data(2, 36, it)!!) }.getOrDefault(-1) }

        fun pick(source: FeroxMapSpriteProbeTool.Frame?, sourceId: Int, candidates: Map<Int, Int>): Pair<Int?, String> {
            if (source == null || source.argb.none { it ushr 24 != 0 }) return null to "empty source frame"
            val scored = candidates.mapNotNull { (id, sprite) -> if (sprite < 0) null else localFrame(sprite)?.let { id to it.diff(source) } }.sortedBy { it.second }
            val best = scored.firstOrNull() ?: return null to "no 667 candidates"
            if (best.second > MAX_DIFF) return null to "nearest ${best.first} diff ${"%.3f".format(best.second)}"
            val tied = scored.filter { it.second <= best.second + 1e-9 }.map { it.first }
            val chosen = tied.firstOrNull { it == sourceId } ?: tied.maxByOrNull { locUse[it] ?: 0 }!!
            return chosen to "diff ${"%.3f".format(best.second)} tied=$tied"
        }

        val sceneGroup = modern.index(8).groups.values.first { it.nameHash == FeroxMapSpriteProbeTool.nameHash("mapscene") }
        val osrsScenes = FeroxMapSpriteProbeTool.decodeSprites(modern.files(8, sceneGroup.id)[0]!!)
        val sceneMap = HashMap<Int, Int>()
        val blockedScenes = HashMap<Int, String>()
        scenes.sorted().forEach { s ->
            val (id, why) = pick(osrsScenes.getOrNull(s), s, msi)
            if (id != null) sceneMap[s] = id else blockedScenes[s] = why
            println("SCENE osrs=$s -> msi=${id ?: "BLOCKED"} ($why)")
        }
        val osrsAreas = modern.files(2, 35)
        val areaMap = HashMap<Int, Int>()
        val blockedAreas = HashMap<Int, String>()
        areas.sorted().forEach { a ->
            if (a == DEATHS_OFFICE_AREA) {
                blockedAreas[a] = "Death's Office icon: DeathsOfficeMapImportTool places 1108 on exactly one loc per entrance"
                println("AREA osrs=$a -> SKIPPED (DeathsOfficeMapImportTool owns it)")
                return@forEach
            }
            val (sprite, name) = osrsAreas[a]?.let { FeroxMapSpriteProbeTool.osrsArea(it) } ?: (-1 to null)
            val frame = if (sprite >= 0) runCatching { FeroxMapSpriteProbeTool.decodeSprites(modern.files(8, sprite)[0]!!).firstOrNull() }.getOrNull() else null
            val picked = pick(frame, -1, elements)
            // Bank 5, pub 33 and 40 sit just above MAX_DIFF (0.013-0.020); FeroxMinimapTool proved them on a contact sheet.
            val (id, why) = if (picked.first == null && a in FeroxMinimapTool.AREA_TO_MAP_ELEMENT) FeroxMinimapTool.AREA_TO_MAP_ELEMENT.getValue(a) to "Ferox contact-sheet proof, ${picked.second}" else picked
            // T8 dagrun 2026-11-06: OSRS quest-start icons (sprite 1454, areas 792/831) match our hall-quest icon 1116 pixel for pixel,
            // but the owner wants no standard quest icons on the map (N227) - they are skipped like a value without a picture.
            if (id == QuestMapIconTool.HALL_QUEST_ELEMENT) {
                blockedAreas[a] = "'$name' quest icon (owner N227: no standard quest icons on the map)"
                println("AREA osrs=$a '$name' sprite=$sprite -> SKIPPED (quest icon, N227)")
                return@forEach
            }
            if (id != null) areaMap[a] = id else blockedAreas[a] = "'$name' $why"
            println("AREA osrs=$a '$name' sprite=$sprite -> mapelement=${id ?: "BLOCKED"} ($why)")
        }
        return Mapping(sceneMap, areaMap, blockedScenes, blockedAreas)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val entries = locEntries()
        println("ENTRIES ${entries.size} (mode IMPORT, ${entries.map { it.section }.distinct().size} sections)")
        val library = CacheLibrary(FeroxImportTool.GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            ModernCacheReader(File(FeroxImportTool.MODERN_CACHE)).use { modern ->
                val modernDefs = modern.files(2, 6)
                val local = Rev667RegionProbeTool.locTypes(library)
                val locUse = local.values.filter { it.mapElement >= 0 }.groupingBy { it.mapElement }.eachCount()
                val sources = entries.associate { e -> e.local to (modernDefs[e.upstream]?.let { ModernObjectDef.decode(e.upstream, it) }) }
                val mapping =
                    buildMapping(
                        library, modern,
                        sources.values.mapNotNull { it?.mapSceneId?.takeIf { s -> s >= 0 } }.toSet(),
                        sources.values.mapNotNull { it?.mapAreaId?.takeIf { a -> a >= 0 } }.toSet(),
                        locUse,
                    )
                check(mapping.scenes == OsrsMapIconTable.SCENE_TO_MSI && mapping.areas == OsrsMapIconTable.AREA_TO_MAP_ELEMENT) {
                    "pixel proof differs from OsrsMapIconTable: scenes=${mapping.scenes.toSortedMap()} areas=${mapping.areas.toSortedMap()}"
                }
                var already = 0
                var blocked = 0
                entries.distinctBy { it.local }.sortedBy { it.local }.forEach { e ->
                    val src = sources[e.local] ?: return@forEach
                    if (src.mapSceneId < 0 && src.mapAreaId < 0) return@forEach
                    val group = e.local ushr 8
                    val file = e.local and 0xFF
                    val current = library.data(LOC_INDEX, group, file) ?: run { println("MISSING local loc ${e.local}"); return@forEach }
                    val before = Rev667LocType.decode(e.local, current)
                    val msi = if (src.mapSceneId >= 0 && before.msi == -1) mapping.scenes[src.mapSceneId] else null
                    val element = if (src.mapAreaId >= 0 && before.mapElement == -1) mapping.areas[src.mapAreaId] else null
                    if (src.mapSceneId >= 0 && before.msi != -1 || src.mapAreaId >= 0 && before.mapElement != -1) already++
                    if (src.mapSceneId >= 0 && before.msi == -1 && msi == null || src.mapAreaId >= 0 && before.mapElement == -1 && element == null) blocked++
                    if (msi == null && element == null) return@forEach
                    check(current.last() == 0.toByte()) { "loc ${e.local} does not end with opcode 0" }
                    val updated = FeroxMinimapTool.patched(current, msi, element)
                    val after = Rev667LocType.decode(e.local, updated)
                    check(
                        after.msi == (msi ?: before.msi) && after.mapElement == (element ?: before.mapElement) &&
                            after.name == before.name && after.allModels == before.allModels && after.options.toList() == before.options.toList(),
                    ) { "re-decode mismatch for loc ${e.local}" }
                    println("LOC ${e.local} <- osrs ${e.upstream} '${before.name}' msi=${after.msi} mapelement=${after.mapElement} [${e.section.take(60)}]")
                    mutations += CacheMutation(LOC_INDEX, group, file, updated, "osrs map data loc ${e.local} msi=${after.msi} mapelement=${after.mapElement}", CacheItemProbeTool.sha1(current))
                }
                println("SUMMARY patch=${mutations.size} alreadySet=$already blockedValues=$blocked")
                println("TABLE scenes=${mapping.scenes.toSortedMap()}")
                println("TABLE areas=${mapping.areas.toSortedMap()}")
                println("BLOCKED scenes=${mapping.blockedScenes.toSortedMap()}")
                println("BLOCKED areas=${mapping.blockedAreas.toSortedMap()}")
            }
        } finally {
            library.close()
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val tx = CacheTransaction(listOf(FeroxImportTool.GAME_CACHE, FeroxImportTool.FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} mutations=${mutations.size} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
