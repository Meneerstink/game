package gg.rsmod.game.model.collision

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import io.netty.buffer.Unpooled
import net.runelite.cache.IndexType
import net.runelite.cache.definitions.loaders.LocationsLoader
import net.runelite.cache.definitions.loaders.MapLoader
import net.runelite.cache.region.Region
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RCV-012 B1 (owner live: the whole Nex room and the area around the Nex bank are stuck without noclip; the bridge-level diff is
 * zero for every God Wars / Nex square). The 667 client (LocType.decode, LocTypeList.list, MapRegion loc loading) only lets ground
 * decoration block with blockwalk == 1 (opcode 27) and clears blockwalk for opcode 74; the server blocked every interactive ground
 * decoration without opcode 17 and ignored opcodes 27 and 74.
 */
class LocCollisionRuleTests {
    private fun decode(vararg bytes: Int): ObjectDef =
        ObjectDef(1).also { it.decode(Unpooled.wrappedBuffer(ByteArray(bytes.size) { i -> bytes[i].toByte() })) }

    @Test
    fun `decode keeps the client blockwalk, blockrange and breakroutefinding`() {
        decode(0).let { assertEquals(2, it.blockwalk); assertTrue(it.solid); assertTrue(it.impenetrable); assertFalse(it.blocksGroundDecor) }
        decode(17, 0).let { assertEquals(0, it.blockwalk); assertFalse(it.solid); assertFalse(it.impenetrable) }
        decode(18, 0).let { assertEquals(2, it.blockwalk); assertTrue(it.solid); assertFalse(it.impenetrable) }
        decode(27, 0).let { assertEquals(1, it.blockwalk); assertTrue(it.solid); assertTrue(it.blocksGroundDecor) }
        // Opcode 74 never makes a loc walkable on the server (doors, booths, counters, gates, walls); ground decoration keeps its rule.
        decode(27, 74, 0).let { assertTrue(it.solid); assertFalse(it.impenetrable); assertFalse(it.blocksGroundDecor) }
        decode(74, 0).let { assertTrue(it.solid) }
    }

    /**
     * The server rule: MapRegion loc loading from the client fields, except that opcode 74 (breakroutefinding) only
     * matters for ground decoration. The client clears blockwalk for opcode 74 so its route finder can plan a path to
     * such a loc, but movement is server-authoritative, and on the server that clearing made every opcode-74 door, bank
     * booth, counter, gate and wall piece walk-through (owner 2026-09-24: "noclippen lukt in mijn hele server").
     */
    private fun expectedBlocks(blockwalk: Int, breakroutefinding: Boolean, shape: Int): Boolean =
        when (shape) {
            22 -> blockwalk == 1 && !breakroutefinding
            in 4..8 -> false
            else -> blockwalk != 0
        }

    /** The server rule before this fix, evidence only. */
    private fun legacyBlocks(def: ObjectDef, shape: Int): Boolean {
        val solid = def.blockwalk != 0
        return when (shape) {
            22 -> def.interactive && solid
            in 4..8 -> false
            else -> solid
        }
    }

    private class XteaEntry(val mapsquare: Int = 0, val key: IntArray = IntArray(4))

    @Test
    fun `every loc blocks walking by the server rule`() {
        val cache = CacheLibrary(File("../data/cache").path)
        val definitions = DefinitionSet().also { it.load(cache, ObjectDef::class.java) }
        val keys =
            com.google.gson.Gson().fromJson(File("../data/xteas/xteas.json").readText(), Array<XteaEntry>::class.java)
                .associate { it.mapsquare to it.key }
        val probe = setOf(11345, 11346, 11347, 11601, 11602, 11603, 11319, 11320, 11321, 11575, 11576, 11577, 11578, 14131, 14231)
        val offenders = mutableListOf<String>()
        var placements = 0
        var changed = 0
        for (x in 0 until 128) for (z in 0 until 256) {
            val id = (x shl 8) or z
            val mapData = cache.data(IndexType.MAPS.number, "m${x}_$z") ?: continue
            val land =
                try {
                    cache.data(IndexType.MAPS.number, "l${x}_$z", keys[id]) ?: continue
                } catch (e: Exception) {
                    continue
                }
            val r = Region(id)
            try {
                r.loadTerrain(MapLoader().load(x, z, mapData))
                r.loadLocations(LocationsLoader().load(x, z, land))
            } catch (e: Exception) {
                continue
            }
            var squareChanged = 0
            val samples = mutableListOf<String>()
            r.locations.forEach { loc ->
                val def = definitions.getNullable(ObjectDef::class.java, loc.id) ?: return@forEach
                placements++
                val server = CollisionUpdate.blocksWalk(def, loc.type)
                if (server != expectedBlocks(def.blockwalk, def.breakroutefinding, loc.type) && offenders.size < 20) {
                    offenders += "${loc.id} shape ${loc.type} at ${loc.position}"
                }
                if (def.breakroutefinding && loc.type != 22 && def.blockwalk != 0 && id in probe && samples.size < 8) {
                    samples += "opcode74-now-solid ${loc.id}'${def.name}' shape ${loc.type} ${loc.position.x},${loc.position.y},${loc.position.z}"
                }
                if (server != legacyBlocks(def, loc.type)) {
                    squareChanged++
                    if (samples.size < 8) samples += "${loc.id}'${def.name}' shape ${loc.type} ${loc.position.x},${loc.position.y},${loc.position.z} legacy=${!server}"
                }
            }
            changed += squareChanged
            if (id in probe) println("LOC-COLLISION-PROBE square $id: locs=${r.locations.size} walkBlockChanged=$squareChanged sample=$samples")
        }
        println("LOC-COLLISION-PROBE placements=$placements walkBlockChanged=$changed")
        assertTrue(placements > 1_000_000, "placements read: $placements")
        assertTrue(offenders.isEmpty(), "locs whose server walk blocking differs from the server rule: $offenders")
    }

    @Test
    fun `every loc definition decodes to its terminator`() {
        val cache = CacheLibrary(File("../data/cache").path)
        // ArchiveType.OBJECT: index 16, archive = id >> 8, file = id & 0xFF.
        val short = mutableListOf<Int>()
        var total = 0
        val archives = cache.index(16).archives().size
        for (id in 0 until archives * 256) {
            val data = cache.data(16, id ushr 8, id and 0xFF) ?: continue
            total++
            val buf = Unpooled.wrappedBuffer(data)
            ObjectDef(id).decode(buf)
            if (buf.readableBytes() != 0 && short.size < 20) short += id
        }
        println("LOC-DECODE-PROBE definitions=$total leftover=${short.size} sample=$short")
        assertTrue(total > 10_000, "loc definitions read: $total")
        assertTrue(short.isEmpty(), "loc definitions with bytes after the terminator: $short")
    }
}
