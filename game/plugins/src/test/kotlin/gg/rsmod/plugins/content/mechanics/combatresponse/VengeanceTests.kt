package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR
import gg.rsmod.game.model.combat.DamageMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.plugins.api.ChatMessageType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [Vengeance] (P6, 2026-09-02 autonomous run - see RSPS_DECISIONS.md for the full
 * sourcing note): activation/cooldown gating, the single-use 75%-of-damage-taken (minimum 1)
 * reflect, and correct kill-attribution via the attacker's [DamageMap].
 */
class VengeanceTests {
    @Test
    fun `activate primes vengeance and starts the cooldown timer`() {
        val player = newPlayer()

        assertTrue(Vengeance.activate(player))

        assertTrue(Vengeance.isActive(player))
        assertEquals(Vengeance.COOLDOWN_TICKS, player.timers[VENGEANCE_COOLDOWN])
    }

    @Test
    fun `activate is a no-op while still on cooldown`() {
        val player = newPlayer()
        Vengeance.activate(player)
        player.attr.remove(VENGEANCE_ACTIVE_ATTR) // simulate it having already been consumed

        assertFalse(Vengeance.activate(player))
        assertFalse(Vengeance.isActive(player))
    }

    @Test
    fun `inactive vengeance does not reflect incoming damage`() {
        val attacker = newPlayer()
        val target = newPlayer()

        Vengeance.onIncomingHit(attacker, target, damage = 100)

        assertEquals(0, attacker.damageMap.getDamageFrom(target))
    }

    @Test
    fun `active vengeance reflects 75 percent of the damage, rounded down, and consumes itself`() {
        val attacker = newPlayer()
        val target = newPlayer()
        Vengeance.activate(target)

        Vengeance.onIncomingHit(attacker, target, damage = 99)

        // floor(99 * 0.75) = 74
        assertEquals(74, attacker.damageMap.getDamageFrom(target))
        assertFalse(Vengeance.isActive(target), "Vengeance is single-use and must consume itself on trigger")
        // Player.message() is an extension function that funnels through the real, abstract
        // Player.write(vararg Message) - verified at that level since mockk can't intercept
        // top-level extension-function calls directly.
        verify {
            target.write(
                MessageGameMessage(type = ChatMessageType.GAME_MESSAGE.id, message = "Taste vengeance!", username = null),
            )
        }
    }

    @Test
    fun `active vengeance reflects a minimum of 1 damage`() {
        val attacker = newPlayer()
        val target = newPlayer()
        Vengeance.activate(target)

        Vengeance.onIncomingHit(attacker, target, damage = 1)

        assertEquals(1, attacker.damageMap.getDamageFrom(target))
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.damageMap } returns DamageMap()
        return player
    }
}
