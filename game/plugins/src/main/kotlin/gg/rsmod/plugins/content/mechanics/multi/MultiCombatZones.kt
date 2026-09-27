package gg.rsmod.plugins.content.mechanics.multi

import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.io.File

/**
 * The multicombat areas of OSRS Deadman: Annihilation, tile-exact (owner decision 2026-09-27: "DMM map everywhere").
 *
 * Source: OSRS Wiki `Module:DeadmanAnnihilationMulticombatZones.json` (the GeoJSON behind "Map:DeadmanAnnihilationMulticombatZones",
 * shown on the "Multicombat area" page), saved as `data/cfg/areas/dmm-multicombat-zones.json` on 2026-09-27: 164 polygons on
 * the surface of Gielinor and Prifddinas, corners on tile edges. A tile is multi when its centre lies inside a polygon.
 *
 * The map covers the surface only ([covers]); dungeons and instances (God Wars, Clan Wars, boss rooms ...) keep the region /
 * chunk entries of `data/cfg/areas/multi.yml`. The previous surface entries of that file disagreed with this map at 16 of the 29
 * localised breach locations (e.g. Fountain of Rune, Tithe Farm, Arceuus: multi in OSRS, single here).
 */
object MultiCombatZones {
    const val PATH = "./data/cfg/areas/dmm-multicombat-zones.json"

    private val tiles = IntOpenHashSet()

    @Volatile
    var loaded = false
        private set

    val size: Int get() = tiles.size

    private fun key(
        x: Int,
        z: Int,
    ): Int = (x shl 16) or (z and 0xFFFF)

    /** The surface of Gielinor (z below 4500) and Prifddinas: the area the wiki map describes. */
    fun covers(
        x: Int,
        z: Int,
    ): Boolean = z < 4500 || (x in 3100..3400 && z in 5950..6250)

    fun contains(
        x: Int,
        z: Int,
    ): Boolean = tiles.contains(key(x, z))

    fun load(file: File = File(PATH)): Int {
        if (!file.exists()) return 0
        val root = file.reader().use { com.google.gson.Gson().fromJson(it, JsonObject::class.java) }
        tiles.clear()
        root.getAsJsonArray("features").forEach { feature ->
            val geometry = (feature as JsonObject).getAsJsonObject("geometry")
            geometry.getAsJsonArray("coordinates").forEach { ring ->
                val points = ring.asJsonArray.map { p -> p.asJsonArray.let { it[0].asDouble to it[1].asDouble } }
                rasterize(points)
            }
        }
        loaded = true
        return tiles.size
    }

    /** Fills every tile whose centre lies inside the ring (even-odd scanline over the ring's rows). */
    private fun rasterize(ring: List<Pair<Double, Double>>) {
        if (ring.size < 3) return
        val minZ = ring.minOf { it.second }.toInt()
        val maxZ = ring.maxOf { it.second }.toInt()
        for (z in minZ until maxZ) {
            val cz = z + 0.5
            val crossings = ArrayList<Double>()
            for (i in ring.indices) {
                val (x1, z1) = ring[i]
                val (x2, z2) = ring[(i + 1) % ring.size]
                if ((z1 > cz) != (z2 > cz)) crossings += x1 + (cz - z1) * (x2 - x1) / (z2 - z1)
            }
            crossings.sort()
            var i = 0
            while (i + 1 < crossings.size) {
                val from = Math.ceil(crossings[i] - 0.5).toInt()
                val to = Math.floor(crossings[i + 1] - 0.5).toInt()
                for (x in from..to) tiles.add(key(x, z))
                i += 2
            }
        }
    }
}
