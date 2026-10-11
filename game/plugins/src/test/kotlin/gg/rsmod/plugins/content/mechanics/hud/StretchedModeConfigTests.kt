package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner 2026-10-11: Stretched Mode and its settings are kept with the account (hud_settings) for every player, and the
 * plugin's Reset button resets its settings like RuneLite's - it never switches the plugin off.
 */
class StretchedModeConfigTests {
    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        return player
    }

    @Test
    fun `every Stretched Mode setting is stored with the RuneLite defaults and ranges`() {
        val player = player()
        assertEquals("false", ClientHudConfig.get(player, "stretchedmode.enabled"))
        assertEquals("50", ClientHudConfig.get(player, "stretchedmode.scalingFactor"))
        listOf(
            "stretchedmode.enabled" to "true",
            "stretchedmode.keepAspectRatio" to "true",
            "stretchedmode.increasedPerformance" to "true",
            "stretchedmode.integerScaling" to "true",
            "stretchedmode.scalingFactor" to "84",
        ).forEach { (key, value) ->
            assert(ClientHudConfig.set(player, key, value)) { key }
            assertEquals(value, ClientHudConfig.get(player, key))
        }
        // RuneLite's scalingFactor has no @Range: 0 .. Int.MAX_VALUE (night run 2026-10-12, the test matrix asks for 200 %).
        assert(ClientHudConfig.set(player, "stretchedmode.scalingFactor", "200"))
        assertEquals("200", ClientHudConfig.get(player, "stretchedmode.scalingFactor"))
        assert(!ClientHudConfig.set(player, "stretchedmode.scalingFactor", "-1"))
        assertEquals("200", ClientHudConfig.get(player, "stretchedmode.scalingFactor"))
    }

    @Test
    fun `Reset puts the settings back but keeps the plugin on`() {
        val player = player()
        ClientHudConfig.set(player, "stretchedmode.enabled", "true")
        ClientHudConfig.set(player, "stretchedmode.integerScaling", "true")
        ClientHudConfig.set(player, "stretchedmode.scalingFactor", "80")
        ClientHudConfig.reset(player, "stretchedmode")
        assertEquals("true", ClientHudConfig.get(player, "stretchedmode.enabled"))
        assertEquals("false", ClientHudConfig.get(player, "stretchedmode.integerScaling"))
        assertEquals("50", ClientHudConfig.get(player, "stretchedmode.scalingFactor"))
    }
}
