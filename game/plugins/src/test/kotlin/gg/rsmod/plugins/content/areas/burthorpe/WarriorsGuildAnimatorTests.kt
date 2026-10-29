package gg.rsmod.plugins.content.areas.burthorpe

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.XpMode
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
    fun `the cyclops room is east of its door on the top floor, the lobby is not`() {
        assertEquals(false, WarriorsGuildAnimator.inCyclopsRoom(WarriorsGuildAnimator.OUTSIDE_CYCLOPS))
        assertEquals(true, WarriorsGuildAnimator.inCyclopsRoom(WarriorsGuildAnimator.CYCLOPS_DOOR))
        assertEquals(false, WarriorsGuildAnimator.inCyclopsRoom(gg.rsmod.game.model.Tile(2846, 3536, 2)))
        assertEquals(true, WarriorsGuildAnimator.inCyclopsRoom(gg.rsmod.game.model.Tile(2848, 3536, 2)))
        assertEquals(false, WarriorsGuildAnimator.inCyclopsRoom(gg.rsmod.game.model.Tile(2848, 3536, 0)))
        assertEquals(100, WarriorsGuildAnimator.TOKENS_TO_ENTER)
        assertEquals(10, WarriorsGuildAnimator.TOKENS_PER_MINUTE)
    }

    @Test
    fun `each dummy wants its own attack style`() {
        assertEquals(true, WarriorsGuildAnimator.rightStyle(15624, XpMode.ATTACK_XP, StyleType.SLASH))
        assertEquals(false, WarriorsGuildAnimator.rightStyle(15624, XpMode.STRENGTH_XP, StyleType.SLASH))
        assertEquals(true, WarriorsGuildAnimator.rightStyle(15625, XpMode.STRENGTH_XP, StyleType.SLASH))
        assertEquals(true, WarriorsGuildAnimator.rightStyle(15628, XpMode.ATTACK_XP, StyleType.CRUSH))
        assertEquals(true, WarriorsGuildAnimator.rightStyle(15629, XpMode.DEFENCE_XP, StyleType.STAB))
        assertEquals(true, WarriorsGuildAnimator.rightStyle(15630, XpMode.DEFENCE_XP, StyleType.CRUSH))
        assertEquals(true, WarriorsGuildAnimator.rightStyle(15627, XpMode.SHARED_XP, StyleType.STAB))
        assertEquals(8, WarriorsGuildAnimator.DUMMY_POSTS.size)
    }

    @Test
    fun `a kill gives the whole set back, or all but one piece`() {
        val rune = WarriorsGuildAnimator.Set.RUNE
        assertEquals(rune.pieces, WarriorsGuildAnimator.returned(rune, -1))
        assertEquals(listOf(Items.RUNE_PLATEBODY, Items.RUNE_PLATELEGS), WarriorsGuildAnimator.returned(rune, 0))
        assertEquals(listOf(Items.RUNE_FULL_HELM, Items.RUNE_PLATELEGS), WarriorsGuildAnimator.returned(rune, 1))
    }
}
