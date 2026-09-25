package gg.rsmod.game.model.region

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The zones a client receives spawned objects for must be exactly the 104 x 104 map it has loaded. ChunkCoords hold the
 * chunk index minus 6, and treating them as a plain index shifted the zones 48 tiles away: the Royal Hall's objects never
 * reached the client (owner 2026-09-26: "i cant see the building now").
 */
class ChunkBuildAreaTests {
    private fun baseFor(tile: Tile) = (((tile.x shr 3) - (Chunk.MAX_VIEWPORT shr 4)) shl 3) to (((tile.z shr 3) - (Chunk.MAX_VIEWPORT shr 4)) shl 3)

    @Test
    fun `the build area holds every chunk of the loaded map and nothing else`() {
        val player = Tile(3164, 3498)
        val (baseX, baseZ) = baseFor(player)
        val chunks = Chunk.buildAreaChunks(baseX, baseZ)
        assertEquals(Chunk.CHUNKS_PER_REGION * Chunk.CHUNKS_PER_REGION, chunks.toSet().size)
        for (x in baseX until baseX + Chunk.MAX_VIEWPORT step 3) {
            for (z in baseZ until baseZ + Chunk.MAX_VIEWPORT step 3) {
                assertTrue(Tile(x, z).chunkCoords in chunks, "tile $x,$z of the map is missing")
            }
        }
        assertTrue(Tile(baseX - 1, baseZ).chunkCoords !in chunks)
        assertTrue(Tile(baseX + Chunk.MAX_VIEWPORT, baseZ).chunkCoords !in chunks)
    }

    @Test
    fun `the royal hall is inside the map of a player standing in it`() {
        val (baseX, baseZ) = baseFor(Tile(3164, 3491))
        val chunks = Chunk.buildAreaChunks(baseX, baseZ)
        for (x in 3153..3176) for (z in 3481..3502) assertTrue(Tile(x, z).chunkCoords in chunks)
    }
}
