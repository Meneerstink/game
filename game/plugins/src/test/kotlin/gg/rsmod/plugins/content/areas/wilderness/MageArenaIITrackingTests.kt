package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N415: the symbol's readings (Quest Helper bands + Wiki Strange device) and its spawn map (Wiki, 17 circles of radius 5). */
class MageArenaIITrackingTests {
    @Test
    fun `temperature bands follow the recorded OSRS edges`() {
        assertEquals("visibly shaking and burns to the touch", MageArenaIITracking.temperature(0))
        assertEquals("visibly shaking and burns to the touch", MageArenaIITracking.temperature(14))
        assertEquals("incredibly hot", MageArenaIITracking.temperature(15))
        assertEquals("incredibly hot", MageArenaIITracking.temperature(29))
        assertEquals("very hot", MageArenaIITracking.temperature(30))
        assertEquals("hot", MageArenaIITracking.temperature(70))
        assertEquals("warm", MageArenaIITracking.temperature(100))
        assertEquals("cold", MageArenaIITracking.temperature(150))
        assertEquals("very cold", MageArenaIITracking.temperature(200))
        assertEquals("very cold", MageArenaIITracking.temperature(499))
        assertEquals("ice cold", MageArenaIITracking.temperature(500))
    }

    @Test
    fun `the Wiki spawn map has 17 distinct above-ground Wilderness centres`() {
        assertEquals(17, MageArenaIITracking.POINTS.size)
        assertEquals(17, MageArenaIITracking.POINTS.toSet().size)
        MageArenaIITracking.POINTS.forEach { assertTrue(it.z in 3520..3967 && it.x in 2944..3391, "$it") }
    }

    @Test
    fun `the symbol works only above ground in the Wilderness`() {
        assertFalse(MageArenaIITracking.usableAt(Tile(3093, 3493))) // Edgeville
        assertFalse(MageArenaIITracking.usableAt(Tile(3200, 10100))) // Revenant caves (underground)
    }

    @Test
    fun `45 minutes of Wilderness time is 250 steps of 18 ticks`() {
        assertEquals(4500, MageArenaIITracking.ROTATION_STEPS * MageArenaIITracking.STEP_TICKS)
    }
}
