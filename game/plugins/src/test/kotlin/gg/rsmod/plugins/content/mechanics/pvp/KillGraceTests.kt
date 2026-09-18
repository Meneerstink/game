package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deadman PvP guards plan (2026-09-16) coverage for [KillGrace]: "killing someone in a
 * single-combat zone grants the killer a 1-minute period where they cannot be attacked;
 * attacking, dying or logging out ends it early."
 */
class KillGraceTests {
    @Test
    fun `grant makes the killer protected for exactly the 1-minute duration`() {
        val killer = newPlayer()
        assertFalse(KillGrace.isProtected(killer))

        KillGrace.grant(killer)

        assertTrue(KillGrace.isProtected(killer))
        kotlin.test.assertEquals(100, killer.timers[KillGrace.GRACE_TIMER])
    }

    @Test
    fun `endEarly clears an active grace period`() {
        val killer = newPlayer()
        KillGrace.grant(killer)
        assertTrue(KillGrace.isProtected(killer))

        KillGrace.endEarly(killer)

        assertFalse(KillGrace.isProtected(killer))
    }

    @Test
    fun `endEarly is a safe no-op when no grace period is active`() {
        val player = newPlayer()
        KillGrace.endEarly(player)
        assertFalse(KillGrace.isProtected(player))
    }

    @Test
    fun `the grace timer is configured to clear on death, per the owner spec`() {
        assertTrue(KillGrace.GRACE_TIMER.resetOnDeath, "dying must end the grace period early")
    }

    @Test
    fun `the grace remembers whose death earned it, so trailing hits on that victim cannot end it`() {
        // Owner 2026-09-18 ("sometimes no timer after a kill"): the lethal hit's own action and the trailing hits of
        // claws / double hits registered after the grant and ended it as "attacking someone new".
        val killer = newPlayer()
        val victim = newPlayer()
        val someoneElse = newPlayer()
        KillGrace.grant(killer, victim)
        assertTrue(KillGrace.earnedFrom(killer, victim))
        assertFalse(KillGrace.earnedFrom(killer, someoneElse))
        kotlin.test.assertEquals(100, KillGrace.cyclesLeft(killer))
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.timers } returns TimerMap()
        every { player.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        return player
    }
}
