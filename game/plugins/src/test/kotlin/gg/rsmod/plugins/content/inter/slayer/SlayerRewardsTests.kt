package gg.rsmod.plugins.content.inter.slayer

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.tools.importer.SlayerRewardsInterfaceImportTool as T
import gg.rsmod.plugins.content.skills.slayer.Krystilia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N522 2e: the OSRS Slayer Rewards screen - its data against Krystilia and its layout against slayer_rewards_unlock_draw. */
class SlayerRewardsTests {
    @Test
    fun `the Extend tab lists exactly the Krystilia tasks that have an extended range`() {
        val extendable = Krystilia.TASKS.filter { it.extended != null }.map { it.assignment.identifier }.toSet()
        val listed = T.EXTENDS.map { SlayerAssignment.valueOf(it.assignment).identifier }.toSet()
        assertEquals(extendable, listed)
    }

    @Test
    fun `every Unlock entry has an effect here and the OSRS points of dbtable 117`() {
        assertEquals(listOf(7, 6, 17, 19, 52), T.UNLOCKS.map { it.id })
        assertEquals(listOf(300, 150, 80, 200, 0), T.UNLOCKS.map { it.points })
        assertEquals(listOf(false, false, true, true, true), T.UNLOCKS.map { it.canDisable })
    }

    @Test
    fun `boxes follow slayer_rewards_unlock_draw - two columns of 224, pairs share the taller height plus 42, 3 px between pairs`() {
        // Fake metrics: every description is 2 lines except the second (1 line) -> pair heights 22+42 = 64.
        val (boxes, height) = T.place(T.UNLOCKS, T.BOX_FIRST) { text, _ -> if (text.startsWith("Learn to craft")) 1 else 2 }
        assertEquals(listOf(0, 227, 0, 227, 0), boxes.map { it.x })
        assertEquals(224, boxes[0].w)
        assertEquals(22 + 42, boxes[0].h)
        assertEquals(boxes[0].h, boxes[1].h)
        assertEquals(12, boxes[1].descHeight)
        assertEquals(boxes[0].h + 3, boxes[2].y)
        assertEquals(boxes[4].y + boxes[4].h, height)
        assertTrue(boxes.map { it.first } == T.UNLOCKS.indices.map { T.BOX_FIRST + it * T.BOX_STRIDE })
    }

    @Test
    fun `the component ids are contiguous and every box part fits its stride`() {
        assertEquals(T.BOX_FIRST + (T.UNLOCKS.size + T.EXTENDS.size) * T.BOX_STRIDE, T.COMPONENT_COUNT)
        assertTrue(T.PART_DISABLE_OP < T.BOX_STRIDE)
        assertTrue(T.CONFIRM_OPS_FIRST + T.CONFIRM_OPS.size <= T.BOX_FIRST)
    }
}
