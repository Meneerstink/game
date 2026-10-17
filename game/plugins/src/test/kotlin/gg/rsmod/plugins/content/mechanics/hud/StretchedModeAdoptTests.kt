package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A2 round 3 / N93 (owner 2026-10-17: "nieuwe spelers zien zwart in de intro vermoedelijk door stretched mode"; VRAGEN 24 "leek
 * mis te gaan met de plugin stretched mode"). RuneLite keeps Stretched Mode in the CLIENT's config: logging in to an account,
 * new or old, never switches it off. Here every login pushed the account's values - defaults included - so a new player who had
 * switched Stretched Mode on at the login screen (in a maximised window) got it switched OFF by his first login: the game fell
 * back to 765x503 in a black window under the intro's dark opening (seen 2026-10-17, screens\team\N93\after-3-b-*).
 *
 * Now a Stretched Mode setting the account never set is not pushed; the client is asked to hand its own value over
 * ("cfgadopt"), which it sends as an ordinary `hud set`, so the account remembers it from then on. A value the account set -
 * also back to the default - is stored and pushed as before. Reset and the HUD tour keep working.
 */
class StretchedModeAdoptTests {
    private val keys =
        listOf(
            "stretchedmode.enabled",
            "stretchedmode.keepAspectRatio",
            "stretchedmode.increasedPerformance",
            "stretchedmode.integerScaling",
            "stretchedmode.scalingFactor",
        )

    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        return player
    }

    private fun sent(block: () -> Unit): List<String> {
        val sent = mutableListOf<String>()
        ClientHud.observer = { _, payload -> sent += payload }
        try {
            block()
        } finally {
            ClientHud.observer = null
        }
        return sent
    }

    /** Every key=value the cfg payloads carry. */
    private fun pushed(sent: List<String>): Map<String, String> =
        sent.filter { it.startsWith("cfg|") }.flatMap { it.removePrefix("cfg|").split(';') }.filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun adopted(sent: List<String>): Set<String> =
        sent.filter { it.startsWith("cfgadopt|") }.flatMap { it.removePrefix("cfgadopt|").split(',') }.filter { it.isNotEmpty() }.toSet()

    @AfterTest
    fun clear() {
        ClientHud.observer = null
    }

    @Test
    fun `a new account's login never pushes a Stretched Mode value - the client hands its own over`() {
        val sent = sent { ClientHudConfig.login(player()) }
        val values = pushed(sent)
        keys.forEach { key -> assertTrue(key !in values, "the login push carries $key=${values[key]} for an account that never set it") }
        assertEquals(keys.toSet(), adopted(sent), "the client is asked for every Stretched Mode value the account never set")
        // The other plugins are pushed as before.
        assertEquals("true", values["boosts.enabled"])
    }

    @Test
    fun `a value the account set is stored and pushed - also when it is the default`() {
        val player = player()
        assertTrue(ClientHudConfig.set(player, "stretchedmode.enabled", "false"))
        assertTrue(ClientHudConfig.set(player, "stretchedmode.scalingFactor", "50"))
        assertTrue(ClientHudConfig.set(player, "stretchedmode.integerScaling", "true"))
        val sent = sent { ClientHudConfig.login(player) }
        val values = pushed(sent)
        assertEquals("false", values["stretchedmode.enabled"], "an account that switched Stretched Mode off keeps it off")
        assertEquals("50", values["stretchedmode.scalingFactor"])
        assertEquals("true", values["stretchedmode.integerScaling"])
        assertEquals(setOf("stretchedmode.keepAspectRatio", "stretchedmode.increasedPerformance"), adopted(sent))
    }

    @Test
    fun `the owner's saved settings come back unchanged`() {
        val player = player()
        assertTrue(ClientHudConfig.set(player, "stretchedmode.enabled", "true"))
        assertTrue(ClientHudConfig.set(player, "stretchedmode.scalingFactor", "0"))
        val values = pushed(sent { ClientHudConfig.login(player) })
        assertEquals("true", values["stretchedmode.enabled"])
        assertEquals("0", values["stretchedmode.scalingFactor"])
    }

    @Test
    fun `Reset sends RuneLite's defaults and keeps the plugin as it is`() {
        val player = player()
        ClientHudConfig.set(player, "stretchedmode.enabled", "true")
        ClientHudConfig.set(player, "stretchedmode.scalingFactor", "80")
        ClientHudConfig.set(player, "stretchedmode.keepAspectRatio", "true")
        ClientHudConfig.reset(player, "stretchedmode")
        val values = pushed(sent { ClientHudConfig.push(player) })
        assertEquals("true", values["stretchedmode.enabled"])
        assertEquals("50", values["stretchedmode.scalingFactor"])
        assertEquals("false", values["stretchedmode.keepAspectRatio"])
        assertEquals("false", values["stretchedmode.integerScaling"])
        assertEquals("false", values["stretchedmode.increasedPerformance"])
    }

    @Test
    fun `Reset of everything sends every default, Stretched Mode off included`() {
        val player = player()
        ClientHudConfig.set(player, "stretchedmode.enabled", "true")
        ClientHudConfig.set(player, "stretchedmode.scalingFactor", "80")
        ClientHudConfig.reset(player)
        val sent = sent { ClientHudConfig.push(player) }
        val values = pushed(sent)
        keys.forEach { key -> assertEquals(ClientHudConfig.setting(key)!!.default, values[key], key) }
        assertTrue(adopted(sent).isEmpty())
    }

    @Test
    fun `the HUD tour's default push leaves the player's Stretched Mode alone`() {
        val values = pushed(sent { ClientHudConfig.pushDefaults(player()) })
        keys.forEach { key -> assertTrue(key !in values, "the tour pushes $key=${values[key]}") }
        assertEquals("true", values["boosts.enabled"])
    }
}
