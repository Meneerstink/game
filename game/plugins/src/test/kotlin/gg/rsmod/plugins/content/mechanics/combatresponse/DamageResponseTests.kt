package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.REFLECTING_DAMAGE_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Coverage for [DamageResponse] (P6, 2026-09-02 autonomous run - see RSPS_DECISIONS.md): the
 * single deterministic dispatch order for reflect/recoil-style effects, and its reentrancy
 * guard against a reflected hit ever re-triggering the same dispatch.
 */
class DamageResponseTests {
    @Test
    fun `dispatches to every response source in the documented fixed order`() {
        mockkObject(AncientCurses, Vengeance, RingOfRecoil, DharokDamnedReflect)
        try {
            val attacker = newPlayer()
            val target = newPlayer()

            DamageResponse.onIncomingHit(attacker, target, CombatClass.MELEE, damage = 10)

            verifyOrder {
                AncientCurses.onIncomingHit(attacker, target, CombatClass.MELEE, 10)
                Vengeance.onIncomingHit(attacker, target, 10)
                RingOfRecoil.onIncomingHit(attacker, target, 10)
                DharokDamnedReflect.onIncomingHit(attacker, target, 10)
            }
        } finally {
            unmockkObject(AncientCurses, Vengeance, RingOfRecoil, DharokDamnedReflect)
        }
    }

    @Test
    fun `zero or negative damage never reaches any response source`() {
        mockkObject(AncientCurses, Vengeance, RingOfRecoil, DharokDamnedReflect)
        try {
            val attacker = newPlayer()
            val target = newPlayer()

            DamageResponse.onIncomingHit(attacker, target, CombatClass.MELEE, damage = 0)
            DamageResponse.onIncomingHit(attacker, target, CombatClass.MELEE, damage = -5)

            verify(exactly = 0) { AncientCurses.onIncomingHit(any(), any(), any(), any()) }
            verify(exactly = 0) { Vengeance.onIncomingHit(any(), any(), any()) }
            verify(exactly = 0) { RingOfRecoil.onIncomingHit(any(), any(), any()) }
            verify(exactly = 0) { DharokDamnedReflect.onIncomingHit(any(), any(), any()) }
        } finally {
            unmockkObject(AncientCurses, Vengeance, RingOfRecoil, DharokDamnedReflect)
        }
    }

    @Test
    fun `the reentrancy guard blocks a second dispatch while one is already in progress`() {
        val attacker = newPlayer()
        val target = newPlayer()
        target.attr[REFLECTING_DAMAGE_ATTR] = true

        mockkObject(AncientCurses, Vengeance, RingOfRecoil, DharokDamnedReflect)
        try {
            DamageResponse.onIncomingHit(attacker, target, CombatClass.MELEE, damage = 10)

            verify(exactly = 0) { AncientCurses.onIncomingHit(any(), any(), any(), any()) }
        } finally {
            unmockkObject(AncientCurses, Vengeance, RingOfRecoil, DharokDamnedReflect)
        }
    }

    @Test
    fun `the reentrancy guard is released once dispatch finishes, allowing the next hit through`() {
        val attacker = newPlayer()
        val target = newPlayer()

        DamageResponse.onIncomingHit(attacker, target, CombatClass.MELEE, damage = 10)

        assertNull(target.attr[REFLECTING_DAMAGE_ATTR])
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.isDead() } returns false
        return player
    }
}
