package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.tools.importer.WildernessWorldMapTool
import gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaves
import gg.rsmod.plugins.content.areas.wilderness.bosses.ScorpiaPit
import gg.rsmod.plugins.content.areas.wilderness.bosses.WildernessLairs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N529/N531: the world-map snapshot gets the server-placed Wilderness entrances only through WildernessWorldMapTool.SERVER_LOCS.
 * A moved or added cavern / lair entrance here without a rebuilt world map fails.
 */
class WildernessWorldMapLocsTests {
    @Test
    fun `world map tool mirrors every server placed wilderness entrance`() {
        val plugins =
            RevenantCaves.SURFACE.map { listOf(it.id, it.tile.x, it.tile.z, it.rot) } +
                WildernessLairs.SURFACE.map { listOf(it.id, it.tile.x, it.tile.z, it.rot) } +
                ScorpiaPit.CAVERNS.map { listOf(ScorpiaPit.CAVERN, it.tile.x, it.tile.z, it.rot) }
        val tool = WildernessWorldMapTool.SERVER_LOCS.map { listOf(it.id, it.x, it.z, it.rot) }
        assertEquals(plugins.map { it.toString() }.sorted(), tool.map { it.toString() }.sorted())
    }
}
