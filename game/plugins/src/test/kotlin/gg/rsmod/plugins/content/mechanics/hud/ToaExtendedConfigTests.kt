package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N610 (owner 13-11 'toa extended plugin van runelite volledig geen namaak maar exact'): the RuneLite ToA Extended config
 * (ca.plugins.toaextended.ToaExtendedConfig) is stored with the account like every other plugin: same keys, defaults, @Range and
 * the two @Alpha colours as aarrggbb.
 */
class ToaExtendedConfigTests {
    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        return player
    }

    @Test
    fun `the plugin is off by default and has all 114 settings`() {
        val player = player()
        assertEquals("false", ClientHudConfig.get(player, "toaextended.enabled"))
        assertEquals(115, ClientHudConfig.SETTINGS.count { it.key.startsWith("toaextended.") })
        assert("toaextended" in ClientHudConfig.PLUGINS)
    }

    @Test
    fun `defaults, ranges and alpha colours match ToaExtendedConfig`() {
        val player = player()
        assertEquals("3", ClientHudConfig.get(player, "toaextended.akkhaRadiusDistance"))
        assertEquals("12", ClientHudConfig.get(player, "toaextended.fontSize"))
        assertEquals("14ff0000", ClientHudConfig.get(player, "toaextended.dangerFillColor"))
        assertEquals("0a000000", ClientHudConfig.get(player, "toaextended.tileFillColor"))
        // @Range(max = 7) and @Range(min = 12)
        assert(ClientHudConfig.set(player, "toaextended.akkhaRadiusDistance", "7"))
        assert(!ClientHudConfig.set(player, "toaextended.akkhaRadiusDistance", "8"))
        assert(!ClientHudConfig.set(player, "toaextended.fontSize", "11"))
        assert(ClientHudConfig.set(player, "toaextended.fontSize", "16"))
        // a colour keeps its alpha; an opaque rrggbb colour still works, a 7-digit one does not
        assert(ClientHudConfig.set(player, "toaextended.dangerFillColor", "#80FF00FF"))
        assertEquals("80ff00ff", ClientHudConfig.get(player, "toaextended.dangerFillColor"))
        assert(ClientHudConfig.set(player, "toaextended.dangerFillColor", "ff0000"))
        assert(!ClientHudConfig.set(player, "toaextended.dangerFillColor", "1234567"))
        assert(!ClientHudConfig.set(player, "toaextended.dangerFillColor", "zz000000"))
    }

    @Test
    fun `Reset puts the ToA Extended settings back`() {
        val player = player()
        ClientHudConfig.set(player, "toaextended.enabled", "true")
        ClientHudConfig.set(player, "toaextended.fontSize", "20")
        ClientHudConfig.reset(player, "toaextended")
        assertEquals("12", ClientHudConfig.get(player, "toaextended.fontSize"))
    }
}
