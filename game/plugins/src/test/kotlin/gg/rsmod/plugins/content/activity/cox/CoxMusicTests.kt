package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.instance.InstancedMap
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class CoxMusicTests {
    @Test fun `music follows each raider's actual floor even after party progress reaches Olm`() {
        val map = mockk<InstancedMap>()
        every { map.area } returns Area(6400, 0, 6527, 127)
        val raid = CoxRaid(1, CoxLayout(null, false, listOf(emptyList(), emptyList())), map, mockk<World>(), "test")
        raid.progress = 4
        assertEquals(CoxMusic.UPPER_DEPTHS, CoxMusic.at(raid, Tile(6410, 10, 3))?.id)
        assertEquals(CoxMusic.LOWER_DEPTHS, CoxMusic.at(raid, Tile(6410, 10, 2))?.id)
        // A raider dying at Olm returns to the end of floor 1, where Lower Depths must resume.
        assertEquals("Lower Depths", CoxMusic.at(raid, Tile(6410, 40, 2))?.name)
        for (plane in 0..3) assertEquals(CoxMusic.FIRE_IN_THE_DEEP, CoxMusic.at(raid, Tile(6416, 90, plane))?.id)
        assertNull(CoxMusic.at(raid, Tile(6300, 10, 3)))
        assertNull(CoxMusic.at(raid, Tile(6410, 10, 0)), "an unused plane is not an upper floor")
        assertNull(CoxMusic.at(raid, Tile(6460, 90, 0)), "void beside Olm is not his chamber")
    }
}
