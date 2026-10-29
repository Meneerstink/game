package gg.rsmod.plugins.content.areas.burthorpe

import gg.rsmod.plugins.api.cfg.Items
import kotlin.test.Test
import kotlin.test.assertEquals

/** K06 N372 part 1: the Magical animator's sets, npcs and tokens (OSRS Wiki "Animated Armour") and the piece that may break. */
class WarriorsGuildAnimatorTests {
    @Test
    fun `every metal has its own animated armour and the OSRS tokens`() {
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 40), WarriorsGuildAnimator.Set.values().map { it.tokens })
        assertEquals((4278..4284).toList(), WarriorsGuildAnimator.Set.values().map { it.npc })
        assertEquals(WarriorsGuildAnimator.Set.RUNE, WarriorsGuildAnimator.setOf(Items.RUNE_PLATELEGS))
        assertEquals(WarriorsGuildAnimator.Set.BRONZE, WarriorsGuildAnimator.setOf(Items.BRONZE_FULL_HELM))
        assertEquals(null, WarriorsGuildAnimator.setOf(Items.DRAGON_PLATELEGS))
        assertEquals(WarriorsGuildAnimator.Set.MITHRIL, WarriorsGuildAnimator.setOfNpc(4282))
    }

    @Test
    fun `a kill gives the whole set back, or all but one piece`() {
        val rune = WarriorsGuildAnimator.Set.RUNE
        assertEquals(rune.pieces, WarriorsGuildAnimator.returned(rune, -1))
        assertEquals(listOf(Items.RUNE_PLATEBODY, Items.RUNE_PLATELEGS), WarriorsGuildAnimator.returned(rune, 0))
        assertEquals(listOf(Items.RUNE_FULL_HELM, Items.RUNE_PLATELEGS), WarriorsGuildAnimator.returned(rune, 1))
    }
}
