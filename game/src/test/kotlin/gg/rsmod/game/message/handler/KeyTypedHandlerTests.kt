package gg.rsmod.game.message.handler

import gg.rsmod.game.message.impl.KeyTypedMessage
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.interf.InterfaceSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Owner A42 2026-10-11 ("interface hangt", Show Equipment Stats): the only inputs while the window "hung" were two typed keys.
 * Esc closes the open window exactly when the player's keybinding "Esc closes the current interface" is on (OSRS); with it off
 * the window stays open - the owner's own account had it off. Both paths, and the lock guard.
 */
class KeyTypedHandlerTests {
    private val original = ClientHud.escClosesInterface

    @AfterTest
    fun restore() {
        ClientHud.escClosesInterface = original
    }

    private fun player(modal: Int, lock: LockState = LockState.NONE): Client {
        val player = mockk<Client>(relaxed = true)
        val interfaces = mockk<InterfaceSet>(relaxed = true)
        every { interfaces.getModal() } returns modal
        every { player.interfaces } returns interfaces
        every { player.lock } returns lock
        every { player.username } returns "tester"
        return player
    }

    @Test
    fun `Esc closes the open window when the setting is on`() {
        ClientHud.escClosesInterface = { true }
        val player = player(modal = 667)
        KeyTypedHandler().handle(player, mockk<World>(relaxed = true), KeyTypedMessage(13))
        verify(exactly = 1) { player.closeInterfaceModal() }
    }

    @Test
    fun `Esc leaves the window open when the player switched Esc closes off, or while locked`() {
        ClientHud.escClosesInterface = { false }
        val off = player(modal = 667)
        KeyTypedHandler().handle(off, mockk<World>(relaxed = true), KeyTypedMessage(13))
        verify(exactly = 0) { off.closeInterfaceModal() }

        ClientHud.escClosesInterface = { true }
        val locked = player(modal = 667, lock = LockState.FULL)
        KeyTypedHandler().handle(locked, mockk<World>(relaxed = true), KeyTypedMessage(13))
        verify(exactly = 0) { locked.closeInterfaceModal() }

        // No window open: nothing to close, whatever the setting.
        val none = player(modal = -1)
        KeyTypedHandler().handle(none, mockk<World>(relaxed = true), KeyTypedMessage(13))
        verify(exactly = 0) { none.closeInterfaceModal() }
    }
}
