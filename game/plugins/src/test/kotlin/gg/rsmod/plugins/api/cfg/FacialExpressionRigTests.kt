package gg.rsmod.plugins.api.cfg

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every dialogue pose exists once per head rig (owner 2026-09-22: npc dialogue heads "still not fitting"): native 667 heads
 * are skinned for base 2165 (the 97xx/98xx HD poses), OSRS-imported heads for base 82 (the classic 588-617 poses). Swapping
 * the two families, as happened on 2026-09-21, moves no vertex of the head it is played on.
 */
class FacialExpressionRigTests {
    private val adult = FacialExpression.values().filter { it != FacialExpression.NONE && !it.name.startsWith("CHILD_") }

    @Test
    fun `every adult pose has an HD pose on the 2165 head rig`() {
        assertEquals(emptyList(), adult.filter { it.hd !in 9742..9878 })
    }

    @Test
    fun `every adult pose has a classic pose on the 82 head rig`() {
        assertEquals(emptyList(), adult.filter { it.classic !in 588..617 })
    }

    @Test
    fun `the default talking pose is the HD one`() {
        assertEquals(FacialExpression.HAPPY_TALKING.hd, FacialExpression.HAPPY_TALKING.animationId)
    }
}
