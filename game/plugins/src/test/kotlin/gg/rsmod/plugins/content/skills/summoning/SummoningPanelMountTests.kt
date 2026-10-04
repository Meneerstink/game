package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfOpenSubMessage
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.interf.listener.InterfaceListener
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Owner 2026-09-30 ("Summoning-tab knippert / verdwijnt"): a second `IF_OPENSUB` of interface 662 on a slot that already
 * holds it makes the client close it (server events wiped, gameframe sub-change hook, rebuild with baked flags) and open it
 * again. [SummoningUi.restorePanel] runs on login, every window-mode switch, every gameframe rebuild and every side-inventory
 * mount, so it must mount 662 only when the slot does not hold it yet.
 */
class SummoningPanelMountTests {
    private fun player(mode: DisplayMode): Pair<Player, MutableList<IfOpenSubMessage>> {
        val opens = mutableListOf<IfOpenSubMessage>()
        val interfaces =
            InterfaceSet(
                object : InterfaceListener {
                    override fun onInterfaceOpen(interfaceId: Int) = Unit

                    override fun onInterfaceClose(interfaceId: Int) = Unit
                },
            )
        interfaces.displayMode = mode
        val player = mockk<Player>(relaxed = true)
        every { player.interfaces } returns interfaces
        // Familiar.current / Pets.current read the player's attributes: no follower out.
        every { player.attr } returns gg.rsmod.game.model.attr.AttributeMap()
        every { player.write(*anyVararg<Message>()) } answers {
            firstArg<Array<Message>>().filterIsInstance<IfOpenSubMessage>().forEach { opens += it }
        }
        return player to opens
    }

    @Test
    fun `the panel is mounted once and never re-mounted while it is in its slot`() {
        for ((mode, parent, child) in listOf(Triple(DisplayMode.FIXED, 548, 221), Triple(DisplayMode.RESIZABLE_NORMAL, 746, 107))) {
            val (player, opens) = player(mode)
            SummoningUi.restorePanel(player)
            SummoningUi.restorePanel(player)
            SummoningUi.restorePanel(player)
            assertEquals("$mode: 662 must be sent exactly once", 1, opens.count { it.component == SummoningUi.PANEL })
            assertEquals("$mode: mounted on slot 95", SummoningUi.PANEL, player.interfaces.getInterfaceAt(parent, child))
        }
    }

    @Test
    fun `a window-mode switch mounts the panel on the new gameframe`() {
        val (player, opens) = player(DisplayMode.FIXED)
        SummoningUi.restorePanel(player)
        player.interfaces.displayMode = DisplayMode.RESIZABLE_NORMAL
        SummoningUi.restorePanel(player)
        assertEquals(2, opens.count { it.component == SummoningUi.PANEL })
        assertEquals(SummoningUi.PANEL, player.interfaces.getInterfaceAt(746, 107))
    }

    @Test
    fun `a gameframe rebuild with a pet out never focuses the panel`() {
        // Owner 2026-10-04 032111: restorePanel runs on every bank/shop/GE/equipment-stats mount and close
        // (GameframeRebuild.rearm); focusing tab 8 there made VARC 168 reopen the Summoning panel when the screen closed.
        val (player, _) = player(DisplayMode.RESIZABLE_NORMAL)
        val scripts = mutableListOf<gg.rsmod.game.message.impl.RunClientScriptMessage>()
        every { player.write(*anyVararg<Message>()) } answers {
            firstArg<Array<Message>>().filterIsInstance<gg.rsmod.game.message.impl.RunClientScriptMessage>().forEach { scripts += it }
        }
        val pet = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        every { player.world.npcs.contains(pet) } returns true
        player.attr[Pets.PET_ATTR] = java.lang.ref.WeakReference(pet)
        SummoningUi.restorePanel(player)
        SummoningUi.restorePanel(player)
        assertEquals("no focusTab (clientscript 115) from a rebuild", 0, scripts.count { it.id == 115 })
    }
}
