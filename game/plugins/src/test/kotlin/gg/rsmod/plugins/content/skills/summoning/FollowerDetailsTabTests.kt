package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfSetEventsMessage
import gg.rsmod.game.message.impl.IfSetHideMessage
import gg.rsmod.game.message.impl.IfSetSpriteMessage
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's standing requirement for Follower Details, stated as a test rather than only as a
 * comment: it lives in the spare gameframe tab (the empty one under the Skills tab) and is
 * **always** visible there - "if the player has no familiar active the follower details still needs
 * to be visible in the empty tab but it has to be empty".
 *
 * Two halves, and this file covers the first:
 *
 *  * The tab is *armed* - op1 enabled, button and icon unhidden, icon sprite set - unconditionally,
 *    with no reference to whether a familiar exists. That is what makes it always visible.
 *  * What the tab *opens* is blanked rather than hidden when no familiar is out; that is
 *    [SummoningUi.refreshPanel]'s job and is covered by the Summoning UI tests.
 *
 * Arming has to be re-done on every gameframe rebuild, because each rebuild restores the cache's
 * baked flags, which for this button are "no op, icon hidden". `familiar.plugin.kts` calls
 * [FollowerDetailsTab.install] on login and `runetek5.plugin.kts` calls it on every real window-mode
 * change, which is why a fullscreen switch used to lose the tab.
 */
class FollowerDetailsTabTests {
    private data class Varc(val id: Int, val value: Int)

    private class Captured {
        val events = mutableListOf<IfSetEventsMessage>()
        val hides = mutableListOf<IfSetHideMessage>()
        val sprites = mutableListOf<IfSetSpriteMessage>()
        // RCV-012: setVarc sends VarcSmallMessage whenever the value fits a byte (varc 823 = 2), VarcLargeMessage otherwise.
        val varcs = mutableListOf<Varc>()
    }

    private fun install(): Captured {
        val captured = Captured()
        val player = mockk<Player>(relaxed = true)
        // setVarc stores into Player.varcs as well as sending the message; a relaxed mock's list is empty.
        every { player.varcs } returns MutableList(4096) { 0 }
        every { player.setEvents(any(), any(), any(), any(), any()) } answers {
            captured.events +=
                IfSetEventsMessage(
                    hash = ((firstArg<Int>() shl 16) or secondArg<Int>()),
                    fromChild = thirdArg(),
                    toChild = arg(3),
                    setting = arg(4),
                )
        }
        // One stub for every `write`, dispatching on the message type: separate `capture` stubs for
        // the same overload would shadow each other and only the last would ever run.
        every { player.write(*anyVararg<Message>()) } answers {
            firstArg<Array<Message>>().forEach { message ->
                when (message) {
                    is IfSetHideMessage -> captured.hides += message
                    is IfSetSpriteMessage -> captured.sprites += message
                    is IfSetEventsMessage -> captured.events += message
                    is gg.rsmod.game.message.impl.VarcLargeMessage -> captured.varcs += Varc(message.id, message.value)
                    is gg.rsmod.game.message.impl.VarcSmallMessage -> captured.varcs += Varc(message.id, message.value)
                    else -> Unit
                }
            }
        }
        FollowerDetailsTab.install(player)
        return captured
    }

    /**
     * Cache proof (clientscript 1766): unless VARC 823 == 2 the gameframe's own tab refresh sets the icon
     * graphic to -1 and clears every op on this tab, which is why it vanished and could never be clicked.
     */
    @Test
    fun `install enables the spare tab through varc 823 = 2`() {
        val captured = install()
        val varc = captured.varcs.firstOrNull { it.id == 823 }
        assertTrue("install never set varc 823 - script 1766 would hide the tab and clear its ops", varc != null)
        assertEquals("varc 823 must be 2 for script 1766 to show the tab and its op1", 2, varc!!.value)
    }

    /** Both layout modes must be armed together, or the tab vanishes on a resize. */
    @Test
    fun `install arms the spare tab in both the fixed and the resizable gameframe`() {
        assertEquals(
            "FollowerDetailsTab must arm exactly the fixed (548:99) and resizable (746:47) spare tabs",
            listOf(548 to 99, 746 to 47),
            FollowerDetailsTab.buttons,
        )
    }

    @Test
    fun `install enables op1 on every armed tab button`() {
        val captured = install()
        FollowerDetailsTab.clickTargets.forEach { (pane, button) ->
            val hash = (pane shl 16) or button
            val message = captured.events.firstOrNull { it.hash == hash }
            assertTrue("no IF_SETEVENTS sent for $pane:$button - the tab surface would not be clickable", message != null)
            assertEquals("$pane:$button must have op1 enabled (bit 1)", 0x2, message!!.setting)
        }
    }

    @Test
    fun `click targets include the overlaid icon as well as the tab graphic`() {
        assertEquals(
            listOf(548 to 99, 548 to 107, 746 to 47, 746 to 31),
            FollowerDetailsTab.clickTargets,
        )
    }

    /**
     * The button, its icon and the icon's sprite all have to be pushed, because the cache bakes the
     * button with no op and the icon hidden. A missing unhide here is a tab the player cannot see.
     */
    @Test
    fun `install unhides the button and its icon and sets the summon icon sprite`() {
        val captured = install()
        val expected =
            listOf(
                (548 shl 16) or 99 to "fixed button",
                (548 shl 16) or 107 to "fixed icon",
                (746 shl 16) or 47 to "resizable button",
                (746 shl 16) or 31 to "resizable icon",
            )
        expected.forEach { (hash, label) ->
            val hide = captured.hides.firstOrNull { it.hash == hash }
            assertTrue("$label was never unhidden", hide != null)
            assertEquals("$label must be visible", false, hide!!.hidden)
        }
        listOf((548 shl 16) or 107, (746 shl 16) or 31).forEach { hash ->
            val sprite = captured.sprites.firstOrNull { it.hash == hash }
            assertTrue("no icon sprite pushed for hash $hash", sprite != null)
            assertEquals("the tab icon must be the cache's summon icon, not the Summoning skill icon", 1200, sprite!!.sprite)
        }
    }

    /**
     * The point of the whole requirement: nothing [FollowerDetailsTab.install] does may depend on a
     * familiar being out. The player mock here has no familiar and no world at all, so if install
     * consulted either it would throw rather than quietly arm nothing.
     */
    @Test
    fun `install is unconditional - it never asks whether a familiar is summoned`() {
        val captured = install()
        assertEquals("both panes and their icons must be armed with no familiar out", 4, captured.events.size)
        assertEquals("both buttons and both icons must be unhidden with no familiar out", 4, captured.hides.size)
    }
}
