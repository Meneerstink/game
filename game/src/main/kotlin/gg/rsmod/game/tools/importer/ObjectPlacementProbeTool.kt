package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.google.gson.Gson
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import net.runelite.cache.IndexType
import net.runelite.cache.definitions.loaders.LocationsLoader
import net.runelite.cache.definitions.loaders.MapLoader
import net.runelite.cache.region.Region
import java.io.File

/**
 * Read-only diagnostic answering "which object is actually standing there", the placement-side
 * counterpart of [ObjectDefProbeTool].
 *
 * [ObjectDefProbeTool] reads definitions, which is enough to explain a wrong option index but not
 * enough to fix "the shortcut in Edgeville does nothing": that needs the id of the object placed at
 * that spot, and a definition search for `Underwall tunnel` returns six ids scattered across the
 * world with no way to tell which one is the one being clicked. The landscape files hold the answer
 * and nothing outside a running server could read them.
 *
 * Modes (the xtea path is the same `data/xteas` the server loads):
 *  * `<cachePath> <xteaPath> id <objectId> [objectId ...]` - every placement of those ids.
 *  * `<cachePath> <xteaPath> name <substring>` - every placement of every object whose name matches.
 *  * `<cachePath> <xteaPath> tile <x> <z> [plane] [radius]` - every object at or around a tile.
 *
 * Output is one `PLACEMENT` line per location: id, name, the option list, the absolute tile, and the
 * `type`/`orientation` the landscape stores, since `on_obj_option` binds by id and option index
 * while collision and door swings depend on the type.
 */
object ObjectPlacementProbeTool {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 3) {
            "Usage: <cachePath> <xteaPath> id <objectId> [objectId ...] | " +
                "<cachePath> <xteaPath> name <substring> | <cachePath> <xteaPath> tile <x> <z> [plane] [radius]"
        }
        val cachePath = args[0]
        val xteaPath = args[1]
        val mode = args[2]

        val library = CacheLibrary(cachePath)
        try {
            val definitions = DefinitionSet()
            definitions.load(library, ObjectDef::class.java)
            @Suppress("UNCHECKED_CAST")
            val objects = definitions.getAll(ObjectDef::class.java) as Map<Int, ObjectDef>

            val keys = loadKeys(xteaPath)
            println("KEYS REGIONS=${keys.size} SOURCE='$xteaPath'")

            val wanted: Set<Int>
            val centre: Tile?
            val radius: Int

            when (mode) {
                "id" -> {
                    require(args.size >= 4) { "Usage: <cachePath> <xteaPath> id <objectId> [objectId ...]" }
                    wanted = args.drop(3).map { it.toInt() }.toSet()
                    centre = null
                    radius = 0
                }

                "name" -> {
                    require(args.size >= 4) { "Usage: <cachePath> <xteaPath> name <substring>" }
                    val needle = args.drop(3).joinToString(" ").lowercase()
                    wanted =
                        objects.keys
                            .filter { objects.getValue(it).name.lowercase().contains(needle) }
                            .toSet()
                    centre = null
                    radius = 0
                    println("NAME_SEARCH='$needle' MATCHING_DEFS=${wanted.size}")
                }

                "tile" -> {
                    require(args.size >= 5) { "Usage: <cachePath> <xteaPath> tile <x> <z> [plane] [radius]" }
                    wanted = emptySet()
                    centre = Tile(args[3].toInt(), args[4].toInt(), args.getOrNull(5)?.toInt() ?: 0)
                    radius = args.getOrNull(6)?.toInt() ?: 0
                }

                else -> throw IllegalArgumentException("Unknown mode '$mode'. Expected id, name or tile.")
            }

            /*
             * A tile search only has to decode the one region that contains it; anything else has to
             * sweep the world, which is the whole reason the region list comes from the key file.
             */
            val regions =
                if (centre != null) {
                    listOf(((centre.x shr 6) shl 8) or (centre.z shr 6))
                } else {
                    keys.keys.sorted()
                }

            var scanned = 0
            var hits = 0
            regions.forEach { region ->
                val locations = locations(library, region, keys[region] ?: EMPTY_KEYS) ?: return@forEach
                scanned++
                locations.forEach { loc ->
                    val tile = Tile(loc.position.x, loc.position.y, loc.position.z)
                    val matches =
                        if (centre != null) {
                            tile.height == centre.height &&
                                Math.abs(tile.x - centre.x) <= radius &&
                                Math.abs(tile.z - centre.z) <= radius
                        } else {
                            loc.id in wanted
                        }
                    if (matches) {
                        hits++
                        val def = objects[loc.id]
                        println(
                            "PLACEMENT id=${loc.id} name='${def?.name ?: "?"}' " +
                                "options=${describeOptions(def)} " +
                                "tile=${tile.x},${tile.z},${tile.height} " +
                                "type=${loc.type} orientation=${loc.orientation} region=$region",
                        )
                    }
                }
            }

            println("PLACEMENTS MODE=$mode REGIONS_DECODED=$scanned HITS=$hits")
        } finally {
            library.close()
        }
    }

    /**
     * Decodes one region's landscape, or null when it has none or its keys are wrong.
     *
     * A wrong key does not fail loudly - it produces garbage the loader throws on - so a failed
     * region is skipped and counted rather than aborting a world sweep.
     */
    private fun locations(
        library: CacheLibrary,
        region: Int,
        keys: IntArray,
    ): List<net.runelite.cache.region.Location>? {
        val x = region shr 8
        val z = region and 0xFF
        return try {
            val mapData = library.data(IndexType.MAPS.number, "m${x}_$z") ?: return null
            val landData = library.data(IndexType.MAPS.number, "l${x}_$z", keys) ?: return null

            val cacheRegion = Region(region)
            cacheRegion.loadTerrain(MapLoader().load(x, z, mapData))
            cacheRegion.loadLocations(LocationsLoader().load(x, z, landData))
            cacheRegion.locations
        } catch (e: Exception) {
            null
        }
    }

    private fun describeOptions(def: ObjectDef?): String {
        if (def == null) {
            return "[]"
        }
        val options =
            def.options
                .mapIndexed { index, option -> index to option }
                .filter { !it.second.isNullOrBlank() && it.second != "null" }
                .joinToString(", ") { "${it.first + 1}:'${it.second}'" }
        return "[$options]"
    }

    private fun loadKeys(path: String): Map<Int, IntArray> {
        val file = File(path)
        val single = if (file.isDirectory) File(file, "xteas.json") else file
        require(single.exists()) { "No xtea key file at ${single.absolutePath}" }
        val entries = Gson().fromJson(single.readText(), Array<XteaEntry>::class.java)
        return entries.associate { it.mapsquare to it.key }
    }

    private data class XteaEntry(
        val mapsquare: Int,
        val key: IntArray,
    )

    private val EMPTY_KEYS = IntArray(4)
}
