package gg.rsmod.plugins.content.inter.gameframe

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * ROOT_CAUSES #143: the client drops an interface's server-set events when it is re-mounted, so every re-mount of the
 * inventory tab must send the item ops again - 2026-10-02 the GE's Back button re-opened 679 without them and a left click
 * on the Pest control teleport (any item) sent nothing.
 */
class TabEventsTests {
    private fun player(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.interfaces.displayMode } returns DisplayMode.RESIZABLE_NORMAL
        return player
    }

    @Test
    fun `re-opening the inventory tab sends its item ops again (GE Back, shops, price checker, side panels)`() {
        TabEvents.install()
        val player = player()
        player.openInterface(dest = InterfaceDestination.INVENTORY_TAB)
        verify(exactly = 1) { player.sendInventoryEvents() }
    }

    @Test
    fun `re-opening the inventory with an explicit interface id goes through the same hook`() {
        TabEvents.install()
        val player = player()
        player.openInterface(InterfaceDestination.INVENTORY_TAB.interfaceId, InterfaceDestination.INVENTORY_TAB)
        verify(exactly = 1) { player.sendInventoryEvents() }
    }

    @Test
    fun `another tab does not touch the inventory`() {
        TabEvents.install()
        val player = player()
        player.openInterface(dest = InterfaceDestination.EMOTES_TAB)
        verify(exactly = 0) { player.sendInventoryEvents() }
    }
}
