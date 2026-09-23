package gg.rsmod.game.fs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.model.Tile
import net.runelite.cache.IndexType
import net.runelite.cache.definitions.loaders.MapLoader
import net.runelite.cache.region.Region
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-012 B1/B1b/B2 (owner live: cannot walk into Barrows, the Nex bank or the Trollheim path without noclip).
 *
 * The 667 client builds its collision maps with the bridge flag of LEVEL 1 (Class306.method7881: `tileFlags[1][x][z] & 0x2`
 * moves a blocked flag from level L to L - 1; MapRegion does the same for locs). The server used each tile's own level flag plus
 * a `(setting & 3) == 3` rule, so server and client collision differed wherever bridge flags exist. This test re-implements
 * the client rule independently and compares it with [DefinitionSet.blockedTerrain] for every map square in the cache.
 */
class BridgeCollisionLevelTests {
    private val cache = CacheLibrary(File("../data/cache").path)

    private fun region(id: Int): Region? {
        val x = id shr 8
        val z = id and 0xFF
        val data = cache.data(IndexType.MAPS.number, "m${x}_$z") ?: return null
        return Region(id).also { it.loadTerrain(MapLoader().load(x, z, data)) }
    }

    /** Class306.method7881, transcribed. */
    private fun clientBlocked(r: Region): Set<Tile> {
        val out = hashSetOf<Tile>()
        for (level in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
            if ((r.getTileSetting(level, lx, lz).toInt() and 0x1) != 0) {
                var actual = level
                if ((r.getTileSetting(1, lx, lz).toInt() and 0x2) != 0) actual = level - 1
                if (actual >= 0) out += Tile(r.baseX + lx, r.baseY + lz, actual)
            }
        }
        return out
    }

    /** The server rule before this fix (DefinitionSet.createRegion up to 2026-09-14), for the evidence report only. */
    private fun legacyBlocked(r: Region): Set<Tile> {
        val blocked = hashSetOf<Tile>()
        for (level in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
            val s = r.getTileSetting(level, lx, lz).toInt()
            val tile = Tile(r.baseX + lx, r.baseY + lz, level)
            if ((s and 1) == 1) blocked += tile
            if ((s and 3) == 3) blocked += tile.transform(-1)
            if ((s and 2) == 2 && s != 3) blocked -= tile.transform(-1)
        }
        return blocked
    }

    @Test
    fun `server terrain collision equals the 667 client for every map square`() {
        val sites = PROBE_SQUARES.entries.associate { (id, name) -> name to id }
        sites.forEach { (name, id) ->
            val r = region(id) ?: return@forEach println("BRIDGE-PROBE $name region $id: no map data")
            val client = clientBlocked(r)
            val legacy = legacyBlocked(r)
            val bridged = (0 until 64).sumOf { lx -> (0 until 64).count { lz -> (r.getTileSetting(1, lx, lz).toInt() and 2) != 0 } }
            println(
                "BRIDGE-PROBE $name region $id: level-1 bridge tiles=$bridged legacyOnlyBlocked=${(legacy - client).size} " +
                    "clientOnlyBlocked=${(client - legacy).size} byLevel=${(legacy - client).groupingBy { it.height }.eachCount()} " +
                    "sample=${(legacy - client).take(6)}",
            )
        }
        val definitions = DefinitionSet()
        val offenders = mutableListOf<Int>()
        var squares = 0
        for (x in 0 until 128) for (z in 0 until 256) {
            val id = (x shl 8) or z
            val r = region(id) ?: continue
            squares++
            if (definitions.blockedTerrain(r) != clientBlocked(r)) offenders += id
        }
        println("BRIDGE-PROBE squares=$squares")
        assertTrue(squares > 1000, "map squares read: $squares")
        assertTrue(offenders.isEmpty(), "map squares whose server collision differs from the client: ${offenders.take(20)}")
    }

    private companion object {
        /** Evidence squares printed by both probes: Barrows, every God Wars / Nex square and the Trollheim mountain squares. */
        val PROBE_SQUARES: Map<Int, String> =
            linkedMapOf(14131 to "Barrows surface", 14231 to "Barrows crypts") +
                listOf(11345, 11346, 11347, 11601, 11602, 11603).associateWith { "God Wars / Nex $it" } +
                (listOf(11319, 11320, 11321, 11575, 11576, 11577, 11578, 11831, 11832, 11833)).associateWith { "Trollheim $it" }
    }

    private class XteaEntry(val mapsquare: Int = 0, val key: IntArray = IntArray(4))

    @Test
    fun `every loc sits on the 667 client collision level`() {
        val keys =
            com.google.gson.Gson().fromJson(File("../data/xteas/xteas.json").readText(), Array<XteaEntry>::class.java)
                .associate { it.mapsquare to it.key }
        val sites = PROBE_SQUARES
        val definitions = DefinitionSet()
        val offenders = mutableListOf<String>()
        var locs = 0
        for (x in 0 until 128) for (z in 0 until 256) {
            val id = (x shl 8) or z
            val r = region(id) ?: continue
            val land =
                try {
                    cache.data(IndexType.MAPS.number, "l${x}_$z", keys[id]) ?: continue
                } catch (e: Exception) {
                    continue
                }
            try {
                r.loadLocations(net.runelite.cache.definitions.loaders.LocationsLoader().load(x, z, land))
            } catch (e: Exception) {
                continue
            }
            // Legacy server rule (own-level bridge flag, Tile.transform wraps level 0 - 1 to 3 and 3 + 1 to 0), evidence only.
            val bridges = hashSetOf<Tile>()
            for (level in 0 until 4) for (lx in 0 until 64) for (lz in 0 until 64) {
                if ((r.getTileSetting(level, lx, lz).toInt() and 2) == 2) bridges += Tile(r.baseX + lx, r.baseY + lz, level)
            }
            var moved = 0
            val samples = mutableListOf<String>()
            r.locations.forEach { loc ->
                locs++
                val p = loc.position
                val tile = Tile(p.x, p.y, p.z)
                val legacy = if (bridges.contains(tile.transform(1))) null else if (bridges.contains(tile)) tile.transform(-1) else tile
                val clientLevel = if ((r.getTileSetting(1, p.x - r.baseX, p.y - r.baseY).toInt() and 0x2) != 0) p.z - 1 else p.z
                val client = if (clientLevel >= 0) Tile(p.x, p.y, clientLevel) else null
                if (definitions.collisionTile(r, p.x, p.y, p.z) != client && offenders.size < 20) offenders += "$id loc ${loc.id} at $tile"
                if (legacy != client) {
                    moved++
                    if (samples.size < 6) samples += "${loc.id}@${p.x},${p.y},${p.z}->${legacy?.height}/${client?.height}"
                }
            }
            sites[id]?.let { println("BRIDGE-PROBE LOCS ${it} region $id: locs=${r.locations.size} legacyDiffers=$moved sample=$samples") }
        }
        println("BRIDGE-PROBE LOCS total=$locs")
        assertTrue(locs > 100_000, "locs read: $locs")
        assertTrue(offenders.isEmpty(), "locs whose server level differs from the client: $offenders")
    }

    @Test
    fun `createRegion places terrain and locs on the client collision level`() {
        val source = File("src/main/kotlin/gg/rsmod/game/fs/DefinitionSet.kt").readText()
        assertTrue("val blocked = blockedTerrain(cacheRegion)" in source)
        assertTrue("collisionTile(cacheRegion, loc.position.x, loc.position.y, loc.position.z) ?: return@forEach" in source)
        assertTrue("UNKNOWN_TILE" !in source)
    }
}
