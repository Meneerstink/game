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

    @Test
    fun `Audit D-06 - only a valid single-combat kill of another player earns the grace`() {
        val killer = newPlayer()
        val victim = newPlayer()
        for (verdict in ValidPkKill.Verdict.values().filter { !it.valid }) {
            assertFalse(KillGrace.grantForKill(killer, victim, verdict, multiCombat = false), "$verdict must not grant")
            assertFalse(KillGrace.isProtected(killer), "$verdict must not grant")
        }
        assertFalse(KillGrace.grantForKill(killer, victim, ValidPkKill.Verdict.VALID, multiCombat = true), "multi-combat")
        assertFalse(KillGrace.grantForKill(killer, killer, ValidPkKill.Verdict.VALID, multiCombat = false), "self")
        assertFalse(KillGrace.isProtected(killer))

        assertTrue(KillGrace.grantForKill(killer, victim, ValidPkKill.Verdict.VALID, multiCombat = false))
        assertTrue(KillGrace.isProtected(killer))
        assertTrue(KillGrace.earnedFrom(killer, victim))
    }

    @Test
    fun `Audit D-05 - only hits of the lethal attack are trailing hits, the respawned victim is a new attack`() {
        val killer = newPlayer()
        val victim = newPlayer()
        val someoneElse = newPlayer()
        every { killer.world.currentCycle } returns 1_000
        KillGrace.grant(killer, victim)

        assertTrue(KillGrace.isTrailingHit(killer, victim), "same cycle as the kill: a trailing hit of the lethal attack")
        assertFalse(KillGrace.isTrailingHit(killer, someoneElse), "another player is always a new attack")

        every { killer.world.currentCycle } returns 1_000 + KillGrace.TRAILING_HIT_CYCLES + 1
        every { victim.isDead() } returns true
        assertTrue(KillGrace.isTrailingHit(killer, victim), "still dead: nothing can be a new attack on them yet")
        every { victim.isDead() } returns false
        assertFalse(KillGrace.isTrailingHit(killer, victim), "respawned: hitting them again is a new attack")

        KillGrace.endEarly(killer)
        assertFalse(KillGrace.earnedFrom(killer, victim), "ending the grace forgets the victim too")
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<gg.rsmod.game.model.World>(relaxed = true)
        every { player.world } returns world
        every { player.timers } returns TimerMap()
        every { player.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        return player
    }
}
