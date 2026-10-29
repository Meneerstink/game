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
    fun `shot put follows the OSRS Wiki calculator`() {
        val w = WarriorsGuildAnimator
        // 99 Strength, 100% run, standing throw, 22lb: X = 207 -> 13 yards, 16 tokens, 144.9 XP, 20.7 energy, 82.8% success.
        val x = w.shotX(99, 100, WarriorsGuildAnimator.Throw.STANDING, 22)
        assertEquals(207, x)
        assertEquals(13, w.shotDistance(x))
        assertEquals(16, w.shotTokens(22, w.shotDistance(x)))
        assertEquals(144.9, w.shotXp(x), 0.001)
        assertEquals(20.7, w.shotEnergy(x), 0.001)
        assertEquals(0.828, w.shotSuccess(x), 0.001)
        assertEquals(1, w.shotDistance(57))
        assertEquals(2, w.shotDistance(58))
        assertEquals(14, w.shotDistance(216))
        assertEquals(1 + 5, w.shotTokens(18, 5))
    }

    @Test
    fun `keg balance - five kegs on their own varbits, barrels per keg, falls only when tired`() {
        val w = WarriorsGuildAnimator
        assertEquals((15669..15673).toSet(), w.KEG_VARBITS.keys)
        assertEquals((2252..2256).toSet(), w.KEG_VARBITS.values.toSet())
        assertEquals(8860, w.barrel(1))
        assertEquals(8864, w.barrel(w.MAX_KEGS))
        assertEquals(0.0, w.kegFallChance(100.0), 0.0)
        assertEquals(0.0, w.kegFallChance(25.0), 0.0)
        assertEquals(0.1, w.kegFallChance(0.0), 0.0001)
        assertEquals(1.0, w.kegDrain(5), 0.0001)
        assertEquals(6.0, w.kegXp(5), 0.0)
        assertEquals(true, w.inKegRoom(gg.rsmod.game.model.Tile(2870, 3538, 1)))
        assertEquals(false, w.inKegRoom(gg.rsmod.game.model.Tile(2861, 3544, 1)))
        // Tokens an hour at five kegs stay under the catapult's 750 (OSRS Wiki).
        assertEquals(true, 6000 / w.KEG_REWARD_EVERY * 5 / w.KEG_TOKEN_PROGRESS < 750)
    }

    @Test
    fun `catapult - four shots, four defences, OSRS reward, target inside the room`() {
        val w = WarriorsGuildAnimator
        assertEquals(4, w.BLOCK_ANIMS.size)
        assertEquals(w.BLOCK_ANIMS.size, w.DEFENCE_BUTTONS.count())
        assertEquals(true, w.blocks(2, 2))
        assertEquals(false, w.blocks(1, 2))
        assertEquals(10.0, w.CATAPULT_XP, 0.0)
        assertEquals(1, w.CATAPULT_TOKENS)
        assertEquals(8, w.CATAPULT_EVERY)
        assertEquals(true, w.inCatapultRoom(w.CATAPULT_TARGET))
        assertEquals(true, w.inCatapultRoom(w.CATAPULT_FROM))
        assertEquals(false, w.inKegRoom(w.CATAPULT_TARGET))
    }

    @Test
    fun `a kill gives the whole set back, or all but one piece`() {
        val rune = WarriorsGuildAnimator.Set.RUNE
        assertEquals(rune.pieces, WarriorsGuildAnimator.returned(rune, -1))
        assertEquals(listOf(Items.RUNE_PLATEBODY, Items.RUNE_PLATELEGS), WarriorsGuildAnimator.returned(rune, 0))
        assertEquals(listOf(Items.RUNE_FULL_HELM, Items.RUNE_PLATELEGS), WarriorsGuildAnimator.returned(rune, 1))
    }
}
