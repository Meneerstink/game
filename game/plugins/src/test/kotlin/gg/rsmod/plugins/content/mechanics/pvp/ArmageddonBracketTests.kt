package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.PROTECTION_FORFEITED_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DMM-ARMAGEDDON D8 (owner 20-11-2026): five combat brackets, PvP only inside your own bracket, never back down, and one hour of PvP
 * protection on every bracket reached after the release - once out of combat.
 */
class ArmageddonBracketTests {
    private class Account(level: Int) {
        val attrs = AttributeMap()
        val timers = TimerMap()
        var level = level
        var cycle = 1000
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>(relaxed = true)

        init {
            every { player.attr } returns attrs
            every { player.timers } returns timers
            every { player.combatLevel } answers { this@Account.level }
            every { world.currentCycle } answers { cycle }
            every { player.world } returns world
        }
    }

    @Test
    fun `the five brackets and their edges`() {
        assertEquals(Bracket.B3_50, DeadmanBrackets.forCombatLevel(3))
        assertEquals(Bracket.B3_50, DeadmanBrackets.forCombatLevel(50))
        assertEquals(Bracket.B51_70, DeadmanBrackets.forCombatLevel(51))
        assertEquals(Bracket.B71_90, DeadmanBrackets.forCombatLevel(90))
        assertEquals(Bracket.B91_110, DeadmanBrackets.forCombatLevel(91))
        assertEquals(Bracket.B111_PLUS, DeadmanBrackets.forCombatLevel(126))
        assertEquals(Bracket.B111_PLUS, DeadmanBrackets.forCombatLevel(138))
        assertEquals("111-138", Bracket.B111_PLUS.label)
    }

    @Test
    fun `the login records the bracket without protection`() {
        val a = Account(88)
        DeadmanBrackets.recordAtLogin(a.player)
        assertEquals(Bracket.B71_90.ordinal, a.attrs[DeadmanBrackets.REACHED])
        assertFalse(a.timers.has(NEW_PLAYER_PROTECTION_TIMER))
        // The same level again is no new bracket.
        assertNull(DeadmanBrackets.onCombatLevelRaised(a.player))
        assertFalse(a.timers.has(NEW_PLAYER_PROTECTION_TIMER))
    }

    @Test
    fun `a new bracket out of combat gives the hour at once, even after the start protection was forfeited`() {
        val a = Account(70)
        DeadmanBrackets.recordAtLogin(a.player)
        a.attrs[PROTECTION_FORFEITED_ATTR] = true
        a.level = 71
        assertEquals(Bracket.B71_90, DeadmanBrackets.onCombatLevelRaised(a.player))
        assertEquals(BeginnerProtection.PROTECTION_CYCLES, a.timers[NEW_PLAYER_PROTECTION_TIMER])
        assertEquals(6000, BeginnerProtection.PROTECTION_CYCLES, "one hour")
        assertEquals(false, a.attrs[PROTECTION_FORFEITED_ATTR])
        assertNull(a.attrs[DeadmanBrackets.PENDING_PROTECTION])
    }

    @Test
    fun `a new bracket in the middle of a fight waits until the player is out of combat`() {
        val a = Account(90)
        DeadmanBrackets.recordAtLogin(a.player)
        a.attrs[BankSecurity.LAST_COMBAT_CYCLE_ATTR] = a.cycle
        a.level = 91
        assertEquals(Bracket.B91_110, DeadmanBrackets.onCombatLevelRaised(a.player))
        assertFalse(a.timers.has(NEW_PLAYER_PROTECTION_TIMER), "no protection mid-fight")
        assertEquals(true, a.attrs[DeadmanBrackets.PENDING_PROTECTION])
        a.cycle += DeadmanBrackets.OUT_OF_COMBAT_CYCLES - 1
        assertFalse(DeadmanBrackets.grantPendingProtection(a.player))
        a.cycle += 1
        assertTrue(DeadmanBrackets.grantPendingProtection(a.player))
        assertEquals(BeginnerProtection.PROTECTION_CYCLES, a.timers[NEW_PLAYER_PROTECTION_TIMER])
        assertFalse(DeadmanBrackets.grantPendingProtection(a.player), "only once")
    }

    @Test
    fun `a bracket never goes down and PvP follows the bracket, not the level`() {
        val high = Account(60)
        DeadmanBrackets.recordAtLogin(high.player)
        high.level = 75
        DeadmanBrackets.onCombatLevelRaised(high.player)
        high.level = 68 // e.g. a lower combat level after a reset
        assertEquals(Bracket.B71_90, DeadmanBrackets.of(high.player))
        val seventy = Account(70)
        DeadmanBrackets.recordAtLogin(seventy.player)
        val eighty = Account(80)
        DeadmanBrackets.recordAtLogin(eighty.player)
        assertFalse(DeadmanBrackets.sameBracket(high.player, seventy.player))
        assertTrue(DeadmanBrackets.sameBracket(high.player, eighty.player))
        // A level-up that skips a bracket lands in the highest one, with one hour.
        val jump = Account(50)
        DeadmanBrackets.recordAtLogin(jump.player)
        jump.level = 95
        assertEquals(Bracket.B91_110, DeadmanBrackets.onCombatLevelRaised(jump.player))
        assertEquals(Bracket.B91_110.ordinal, jump.attrs[DeadmanBrackets.REACHED])
    }
}
