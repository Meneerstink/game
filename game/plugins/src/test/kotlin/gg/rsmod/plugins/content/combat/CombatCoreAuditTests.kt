package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.DEADMAN_LOGOUT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.items.osrs.DragonClaws
import gg.rsmod.plugins.content.mechanics.prayer.Redemption
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit A2 combat-core fixes (C-03 .. C-16, X-10) that can be checked without a cache. */
class CombatCoreAuditTests {
    private val weapons = "src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons"

    private fun read(path: String) = File(path).readText()

    @Test
    fun `C-03 the Armadyl godsword special is x1_375 damage and x2 accuracy against slash`() {
        val ags = read("$weapons/armadyl_godsword.plugin.kts")
        assertTrue("getMaxHit(player, target, specialAttackMultiplier = 1.375)" in ags)
        assertTrue("getAccuracyAgainst(player, target, specialAttackMultiplier = 2.0, defenceStyle = gg.rsmod.game.model.combat.StyleType.SLASH)" in ags)
    }

    @Test
    fun `C-04 and I-10 the slash specials roll against slash whatever style is selected`() {
        listOf(
            "armadyl_godsword.plugin.kts", "bandos_godsword.plugin.kts", "saradomin_godsword.plugin.kts", "zamorak_godsword.plugin.kts",
            "dragonequipment/dragon_dagger.plugin.kts", "dragonequipment/dragon_longsword.plugin.kts", "saradominsword/saradomin_sword.plugin.kts",
        ).forEach { file ->
            val source = read("$weapons/$file")
            assertFalse("MeleeCombatFormula.getAccuracy(" in source, "$file still rolls against the selected style")
            assertTrue("StyleType.SLASH" in source, "$file")
        }
        // I-10: the Dragon longsword special has no accuracy bonus (the x1.25 accuracy is the (bh) variant).
        assertTrue("specialAttackMultiplier = 1.0, defenceStyle" in read("$weapons/dragonequipment/dragon_longsword.plugin.kts"))
    }

    @Test
    fun `C-06 dragon claws hit on 1, 1, 2, 2`() {
        assertEquals(listOf(1, 1, 2, 2), DragonClaws.HIT_DELAYS.toList())
        assertTrue("DragonClaws.HIT_DELAYS[i]" in read("$weapons/dragonequipment/dragon_claws.plugin.kts"))
    }

    @Test
    fun `C-10 the npc-boxing exception is gone`() {
        assertFalse("boxedByOrdinaryNpc" in read("src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts"))
    }

    @Test
    fun `X-10 the logout hold is extended, never shortened, and armed by hits and damage over time`() {
        val player = mockk<Player>(relaxed = true)
        every { player.timers } returns TimerMap()
        Combat.holdLogout(player)
        assertEquals(Combat.LOGOUT_HOLD_TICKS, player.timers[DEADMAN_LOGOUT_TIMER])
        player.timers[DEADMAN_LOGOUT_TIMER] = 30
        Combat.holdLogout(player)
        assertEquals(30, player.timers[DEADMAN_LOGOUT_TIMER])
        Combat.holdLogout(player, 40)
        assertEquals(40, player.timers[DEADMAN_LOGOUT_TIMER])

        val combat = read("src/main/kotlin/gg/rsmod/plugins/content/combat/Combat.kt")
        assertTrue("(target as? Player)?.let { holdLogout(it) }" in combat, "bosses included")
        val pawnExt = read("src/main/kotlin/gg/rsmod/plugins/content/combat/PawnExt.kt")
        assertTrue("Combat.holdLogout(target, delay + Combat.LOGOUT_HOLD_TICKS)" in pawnExt && "hit.addAction { Combat.holdLogout(target) }" in pawnExt)
        listOf("poison_plugin.plugin.kts", "venom_plugin.plugin.kts").forEach {
            assertTrue("Combat.holdLogout(pawn)" in read("src/main/kotlin/gg/rsmod/plugins/content/mechanics/poison/$it"), it)
        }
    }

    @Test
    fun `C-16 Redemption triggers below 10 percent without integer division`() {
        assertTrue(Redemption.belowThreshold(9, 99))
        assertFalse(Redemption.belowThreshold(10, 99))
        assertTrue(Redemption.belowThreshold(9, 100))
        assertFalse(Redemption.belowThreshold(10, 100))
    }
}
