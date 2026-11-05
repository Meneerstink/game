package gg.rsmod.plugins.content.inter.slayer

import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.entity.Player
import io.mockk.*
import kotlin.test.*

class SlayerTasksTests {
    private fun player(points: Int): Player = mockk<Player>(relaxed = true).also {
        every { it.attr } returns AttributeMap()
        it.attr[SLAYER_ASSIGNMENT] = SlayerAssignment.SKELETON.identifier
        it.attr[SLAYER_AMOUNT] = 1
        it.attr[SLAYER_POINTS] = points
    }
    @Test fun `cancel on the final task costs thirty points once and duplicate clicks are free`() {
        val p = player(30)
        SlayerTasksUi.button(p, SlayerTasksUi.CANCEL)
        SlayerTasksUi.button(p, SlayerTasksUi.CANCEL)
        assertEquals(0, p.attr[SLAYER_POINTS])
        assertNull(p.attr[SLAYER_ASSIGNMENT])
    }
    @Test fun `block keeps existing five-slot policy and refuses without charging when full or short of points`() {
        for (case in listOf("success", "full", "short")) {
            val p = player(if (case == "short") 99 else 100)
            if (case == "full") p.attr[BLOCKED_TASKS] = mutableListOf("a", "b", "c", "d", "e")
            SlayerTasksUi.button(p, SlayerTasksUi.BLOCK)
            if (case == "success") {
                assertEquals(listOf(SlayerAssignment.SKELETON.identifier), p.attr[BLOCKED_TASKS])
                assertEquals(0, p.attr[SLAYER_POINTS])
                assertNull(p.attr[SLAYER_ASSIGNMENT])
            } else {
                assertEquals(if (case == "short") 99 else 100, p.attr[SLAYER_POINTS])
                assertNotNull(p.attr[SLAYER_ASSIGNMENT])
            }
        }
    }
}
