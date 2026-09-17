package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.Gson
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import net.runelite.cache.IndexType
import net.runelite.cache.definitions.loaders.LocationsLoader
import net.runelite.cache.definitions.loaders.MapLoader
import net.runelite.cache.region.Region
import java.io.File

/**
 * Read-only full-world traversal census for a revision-667 cache.
 *
 * This deliberately starts from every cached loc definition and every decoded landscape
 * placement. Existing plugin search terms are not used as the source of truth. The output is
 * tab-separated so it can be inspected or imported without losing names/options containing
 * punctuation.
 *
 * Usage: <cachePath> <xteaPath> [outputPath]
 *
 * Output records:
 *  SUMMARY - decode and candidate totals;
 *  DEF - every definition whose name/options can represent traversal;
 *  PLACEMENT - every placement of such a definition in every decodable keyed region.
 */
object TraversalCacheAuditTool {
    private val optionWords =
        setOf(
            "open", "close", "unlock", "lock", "climb", "climb-up", "climb-down", "enter", "exit",
            "pass", "pass-through", "cross", "jump", "squeeze", "crawl", "swing", "grapple",
            "walk-across", "walk-through", "balance", "go-through", "push", "pull", "raise", "lower",
            "search", "clear", "slash", "burn", "cut", "mine", "pick", "operate", "enter-cave",
        )

    private val nameWords =
        listOf(
            "door", "gate", "fence", "stile", "ladder", "stair", "staircase", "trapdoor", "trap door",
            "cave", "tunnel", "hole", "portal", "lever", "rope", "grapple", "rock", "boulder", "wall",
            "gap", "crevice", "pipe", "ledge", "bridge", "log", "stepping stone", "chain", "window",
            "rubble", "vine", "web", "passage", "entrance", "exit", "swing", "obstacle", "manhole",
            "boat", "ship", "gangplank", "fairy ring",
        )

    private data class Candidate(
        val id: Int,
        val def: ObjectDef,
        val category: String,
    )

    private data class Placement(
        val id: Int,
        val x: Int,
        val z: Int,
        val plane: Int,
        val type: Int,
        val rotation: Int,
        val region: Int,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <cachePath> <xteaPath> [outputPath]" }
        val cachePath = args[0]
        val xteaPath = args[1]
        val outputPath = args.getOrNull(2)
        val output = StringBuilder()

        val library = CacheLibrary(cachePath)
        try {
            val definitions = DefinitionSet().also { it.load(library, ObjectDef::class.java) }
            @Suppress("UNCHECKED_CAST")
            val all = definitions.getAll(ObjectDef::class.java) as Map<Int, ObjectDef>
            val candidates =
                all.entries
                    .mapNotNull { (id, def) ->
                        val category = category(def) ?: return@mapNotNull null
                        Candidate(id, def, category)
                    }.sortedBy { it.id }
            val byId = candidates.associateBy { it.id }
            val keys = loadKeys(xteaPath)
            val placements = ArrayList<Placement>()
            var regionsSeen = 0
            var mapsPresent = 0
            var locationsPresent = 0
            var decodeFailures = 0

            keys.keys.sorted().forEach { regionId ->
                regionsSeen++
                val rx = regionId ushr 8
                val rz = regionId and 0xFF
                try {
                    val mapData = library.data(IndexType.MAPS.number, "m${rx}_$rz") ?: return@forEach
                    mapsPresent++
                    val landData = library.data(IndexType.MAPS.number, "l${rx}_$rz", keys.getValue(regionId)) ?: return@forEach
                    locationsPresent++
                    // Load both streams through the same RuneLite landscape path used by the
                    // production probe. The direct codec below retains exact placement metadata.
                    Region(regionId).apply {
                        loadTerrain(MapLoader().load(rx, rz, mapData))
                        loadLocations(LocationsLoader().load(rx, rz, landData))
                    }
                    Rev667LocCodec.decode(landData).forEach { loc ->
                        if (loc.id in byId) {
                            placements += Placement(
                                id = loc.id,
                                x = rx * 64 + loc.localX,
                                z = rz * 64 + loc.localZ,
                                plane = loc.plane,
                                type = loc.type,
                                rotation = loc.rotation,
                                region = regionId,
                            )
                        }
                    }
                } catch (_: Exception) {
                    decodeFailures++
                }
            }

            val placementById = placements.groupBy { it.id }
            val categoryCounts = candidates.groupingBy { it.category }.eachCount().toSortedMap()
            val placedCategoryCounts = placements.groupBy { byId.getValue(it.id).category }.mapValues { it.value.size }.toSortedMap()
            appendLine(output, "SUMMARY", "definitions=${all.size}", "candidateDefinitions=${candidates.size}",
                "candidatePlacements=${placements.size}", "regions=${keys.size}", "regionsSeen=$regionsSeen",
                "mapsPresent=$mapsPresent", "locationsPresent=$locationsPresent", "decodeFailures=$decodeFailures",
                "categories=${categoryCounts.entries.joinToString(",") { "${it.key}:${it.value}" }}",
                "placedCategories=${placedCategoryCounts.entries.joinToString(",") { "${it.key}:${it.value}" }}")
            candidates.forEach { candidate ->
                val def = candidate.def
                val opts = def.options.mapIndexedNotNull { index, value -> value?.takeIf { it.isNotBlank() }?.let { "${index + 1}:$it" } }
                val placed = placementById[candidate.id].orEmpty()
                appendLine(output, "DEF", "id=${candidate.id}", "category=${candidate.category}", "name=${clean(def.name)}",
                    "size=${def.width}x${def.length}", "interactive=${def.interactive}", "solid=${def.solid}",
                    "blockwalk=${def.blockwalk}", "blockrange=${def.blockrange}", "breakroutefinding=${def.breakroutefinding}",
                    "clipMask=${def.clipMask}", "varbit=${def.varbit}", "varp=${def.varp}",
                    "transforms=${def.transforms?.joinToString(",").orEmpty()}", "options=${clean(opts.joinToString("|"))}",
                    "placements=${placed.size}", "regions=${placed.map { it.region }.distinct().sorted().joinToString(",")}")
            }
            placements.sortedWith(compareBy<Placement> { it.id }.thenBy { it.plane }.thenBy { it.x }.thenBy { it.z }).forEach {
                val candidate = byId.getValue(it.id)
                appendLine(output, "PLACEMENT", "id=${it.id}", "category=${candidate.category}", "name=${clean(candidate.def.name)}",
                    "x=${it.x}", "z=${it.z}", "plane=${it.plane}", "type=${it.type}", "rotation=${it.rotation}", "region=${it.region}",
                    "options=${clean(candidate.def.options.mapIndexedNotNull { i, v -> v?.takeIf(String::isNotBlank)?.let { o -> "${i + 1}:$o" } }.joinToString("|"))}")
            }
        } finally {
            library.close()
        }

        outputPath?.let { File(it).writeText(output.toString()) } ?: print(output.toString())
        println("TRAVERSAL_AUDIT_WRITTEN=${outputPath ?: "stdout"} BYTES=${output.length}")
    }

    private fun category(def: ObjectDef): String? {
        val name = def.name.lowercase()
        val options = def.options.filterNotNull().map { it.lowercase().trim() }
        val nameMatches = nameWords.filter { it in name }
        val optionMatches = options.filter { option -> optionWords.any { word -> option == word || option.startsWith("$word ") } }
        if (nameMatches.isEmpty() && optionMatches.isEmpty()) return null
        val text = (nameMatches + optionMatches).joinToString(" ")
        return when {
            "trapdoor" in text || "trap door" in text || "manhole" in text -> "trapdoor"
            "door" in text -> "door"
            "gate" in text -> "gate"
            "ladder" in text -> "ladder"
            "stair" in text || "staircase" in text -> "stairs"
            "fence" in text || "stile" in text -> "fence_stile"
            "portal" in text || "entrance" in text || "exit" in text || "cave" in text || "tunnel" in text || "passage" in text || "hole" in text -> "entrance_passage"
            "rope" in text || "grapple" in text || "chain" in text -> "rope_grapple_chain"
            "rock" in text || "boulder" in text || "wall" in text || "gap" in text || "crevice" in text || "ledge" in text || "rubble" in text || "vine" in text || "web" in text -> "rock_wall_gap"
            "pipe" in text -> "pipe"
            "bridge" in text || "log" in text || "stepping stone" in text || "swing" in text || "boat" in text || "ship" in text || "gangplank" in text -> "bridge_log_stones"
            "lever" in text -> "lever"
            "window" in text -> "window"
            "fairy ring" in text -> "fairy_ring"
            else -> "option_only"
        }
    }

    private fun clean(value: String): String = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun appendLine(builder: StringBuilder, type: String, vararg fields: String) {
        builder.append(type).append('\t').append(fields.joinToString("\t")).append('\n')
    }

    private fun loadKeys(path: String): Map<Int, IntArray> {
        val file = if (File(path).isDirectory) File(path, "xteas.json") else File(path)
        require(file.exists()) { "No xtea key file at ${file.absolutePath}" }
        val entries = Gson().fromJson(file.readText(), Array<XteaEntry>::class.java) ?: emptyArray()
        return entries.associate { it.mapsquare to it.key }
    }

    private data class XteaEntry(val mapsquare: Int, val key: IntArray)
}
