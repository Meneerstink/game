package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.NEW_PLAYER_PROTECTION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N23 (VRAGEN 19 = A, owner 2026-10-17): a protected player who clicks Attack on another player is asked whether he gives up his
 * beginner protection - only when the attack would otherwise be allowed. The dialog was unreachable: the engine's Attack handler
 * (OpPlayer2Handler) attacked directly and the plugin's on_player_option("Attack") never ran.
 */
class BeginnerProtectionForfeitTests {
    @Test
    fun `a protected attacker is asked only when the fight would otherwise be allowed`() {
        val attacker = player(protected = true)
        val target = player(protected = false)
        assertTrue(BeginnerProtection.needsForfeitConfirmation(attacker, target) { _, _ -> true })
        assertFalse(BeginnerProtection.needsForfeitConfirmation(attacker, target) { _, _ -> false }, "safe zone / level range: plain refusal")
        assertFalse(BeginnerProtection.needsForfeitConfirmation(player(protected = false), target) { _, _ -> true }, "unprotected: a normal attack")

        BeginnerProtection.forfeit(attacker)
        assertFalse(BeginnerProtection.isProtected(attacker))
        assertFalse(BeginnerProtection.needsForfeitConfirmation(attacker, target) { _, _ -> true }, "forfeited: never asked again")
    }

    @Test
    fun `the engine Attack click goes through the hook, which can take it over`() {
        val handler = File("../src/main/kotlin/gg/rsmod/game/message/handler/OpPlayer2Handler.kt").readText()
        val hook = handler.indexOf("if (beforeAttack?.invoke(client, other) == false) return")
        assertTrue(hook >= 0 && hook < handler.indexOf("client.attack(other)"), "the hook runs before the attack and can stop it")

        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts").readText()
        assertTrue("BeginnerProtection.needsForfeitConfirmation(player, target)" in plugin, "the combat plugin asks through the hook")
        assertFalse("on_player_option(\"Attack\")" in plugin, "no dead Attack option handler left")
    }

    @Test
    fun `the rules without the attacker's own protection still respect the target's`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/AreaState.kt").readText()
        assertTrue("!BeginnerProtection.isProtected(attacker) && canFightIgnoringOwnProtection(attacker, target)" in source)
        val body = source.substringAfter("fun canFightIgnoringOwnProtection(").substringBefore("fun ")
        assertTrue("BeginnerProtection.isProtected(target)" in body, "a protected target is never attackable")
    }

    private fun player(protected: Boolean): Player {
        val p = mockk<Player>(relaxed = true)
        val timers = TimerMap()
        if (protected) timers[NEW_PLAYER_PROTECTION_TIMER] = BeginnerProtection.PROTECTION_CYCLES
        every { p.timers } returns timers
        every { p.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        return p
    }
}
