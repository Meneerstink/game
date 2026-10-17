package gg.rsmod.game.message

import gg.rsmod.game.message.encoder.RebuildLoginEncoder
import gg.rsmod.game.message.encoder.RebuildNormalEncoder
import gg.rsmod.game.message.impl.RebuildLoginMessage
import gg.rsmod.game.message.impl.RebuildNormalMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.service.xtea.XteaKeyService
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * N41 (2026-10-17): ::tele 3121 3123 0 (Tutorial Island) crashed the 667 client with
 * "Static434.rebuildNormal:42 ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2" (client error T2).
 *
 * The 667 client (2011scape-client Static434.rebuildNormal) reads (packetSize - pos) / 16 key blocks and then visits
 * EVERY region x = (chunkX - 6) / 8 .. (chunkX + 6) / 8, z = (chunkZ - 6) / 8 .. (chunkZ + 6) / 8 (mapWidth 104 >> 4 = 6),
 * writing zoneIds[n++]. It has no Tutorial Island exception (that rule exists only in the OSRS client; rsmod copied it).
 * Donors (Novite DefaultGameEncoder.sendMapRegion, Void RegionLoading, Darkan WorldEncoder.sendMapRegion) send one block
 * per region with no exception. This class test pins that contract for every map position, for both the rebuild-normal
 * packet (region change) and the rebuild-login packet (login), so no region of the map can drop a block again.
 */
class RebuildNormalPacketTests {
    /** The region ids the 667 client expects, in its loop order (Static434.java:40-41, Java '/' like the client). */
    private fun clientRegions(
        chunkX: Int,
        chunkZ: Int,
    ): IntArray {
        val half = 104 shr 4
        val out = ArrayList<Int>(9)
        for (x in (chunkX - half) / 8..(chunkX + half) / 8) {
            for (z in (chunkZ - half) / 8..(chunkZ + half) / 8) {
                out += (x shl 8) + z
            }
        }
        return out.toIntArray()
    }

    /** A key service whose first key int of every region is the region id, so the block order is visible on the wire. */
    private fun taggedKeys(): XteaKeyService {
        val service = XteaKeyService()
        val field = XteaKeyService::class.java.getDeclaredField("keys")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(service) as Int2ObjectOpenHashMap<IntArray>
        for (x in 0..130) {
            for (z in 0..255) {
                val region = (x shl 8) + z
                map[region] = intArrayOf(region, 1, 2, 3)
            }
        }
        return service
    }

    private fun blockRegions(bytes: ByteArray): IntArray {
        val buf = ByteBuffer.wrap(bytes)
        return IntArray(bytes.size / 16) { i -> buf.getInt(i * 16) }
    }

    @Test
    fun `rebuild-normal sends one key block per client region for every map position`() {
        val encoder = RebuildNormalEncoder()
        val keys = taggedKeys()
        val wrong = ArrayList<String>()
        // cx/cz >= 6: below that Java '/' and the server's 'shr 3' round negative numbers differently (map edge).
        // cz <= 2040 keeps region z <= 255 (whole surface + every underground/instance band of the 667 map).
        for (cx in 6..1017) {
            for (cz in 6..2040) {
                val bytes = encoder.extractBytes(RebuildNormalMessage(0, 0, cx, cz, keys), "xteas")
                val expected = clientRegions(cx, cz)
                if (bytes.size % 16 != 0 || !blockRegions(bytes).contentEquals(expected)) {
                    if (wrong.size < 20) {
                        wrong +=
                            "chunk $cx,$cz (tile ${cx shl 3},${cz shl 3}): client expects ${expected.size} blocks " +
                            "${expected.toList()}, server sent ${bytes.size / 16} ${blockRegions(bytes).toList()}"
                    } else if (wrong.size == 20) {
                        wrong += "..."
                    }
                }
            }
        }
        assertTrue("rebuild-normal key blocks differ from the 667 client loop:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun `rebuild-normal without a key service still sends a zero block per region`() {
        val bytes = RebuildNormalEncoder().extractBytes(RebuildNormalMessage(0, 0, 3121 shr 3, 3123 shr 3, null), "xteas")
        assertEquals(clientRegions(3121 shr 3, 3123 shr 3).size * 16, bytes.size)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun `rebuild-login sends one key block per client region on Tutorial Island`() {
        val keys = taggedKeys()
        // Surface (mapsquares 12336 / 12592) and underground (12436): every place the OSRS-only skip used to fire.
        val tiles = listOf(Tile(3121, 3123), Tile(3094, 3107), Tile(3141, 3086), Tile(3111, 9518), Tile(3080, 9502))
        for (tile in tiles) {
            val message = RebuildLoginMessage(0, 1, 1, tile, IntArray(2048), keys)
            val bytes = RebuildLoginEncoder().extractBytes(message, "xteas")
            assertEquals(
                "rebuild-login at ${tile.x},${tile.z}",
                clientRegions(tile.x shr 3, tile.z shr 3).toList(),
                blockRegions(bytes).toList(),
            )
        }
    }
}
