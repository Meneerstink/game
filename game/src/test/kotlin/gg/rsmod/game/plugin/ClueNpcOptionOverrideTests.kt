package gg.rsmod.game.plugin

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.ref.WeakReference
import kotlin.test.*

/** Existing quest/shop dialogue must survive the contextual Treasure Trail route. */
class ClueNpcOptionOverrideTests {
    private val repo = PluginRepository(mockk<World>(relaxed = true))
    private val npc = mockk<Npc>(relaxed = true).also { every { it.id } returns 801 }
    private val player = account()
    private val ordinary: Plugin.() -> Unit = {}

    private fun account(): Player = mockk<Player>(relaxed = true).also {
        every { it.attr } returns AttributeMap()
        it.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
    }

    private fun interact(option: Int): Boolean = repo.executeNpc(player, npc.id, option)

    @Test fun `without clue handlers ordinary dialogue is unchanged`() {
        repo.bindNpc(801, 1, plugin = ordinary)
        assertTrue(interact(1))
        verify(exactly = 1) { player.executePlugin(ordinary) }
        assertEquals(setOf(1), repo.boundNpcOptions(801))
    }

    @Test fun `unmatched clue and other option fall back to their ordinary plugin`() {
        repo.bindNpc(801, 1, plugin = ordinary)
        repo.bindNpc(801, 2, plugin = ordinary)
        var hasClue = false
        repo.bindNpcOverride { p, n, opt -> p === player && n === npc && opt == 1 && hasClue }
        assertTrue(interact(1))
        hasClue = true
        assertTrue(interact(2))
        verify(exactly = 2) { player.executePlugin(ordinary) }
        assertFalse(interact(5)) // A clue must not fabricate a handler for every advertised option.
    }

    @Test fun `handled clue suppresses ordinary dialogue once and receives the actual NPC`() {
        repo.bindNpc(801, 1, plugin = ordinary)
        var calls = 0
        repo.bindNpcOverride { p, n, opt ->
            assertSame(player, p); assertSame(npc, n); assertEquals(1, opt)
            calls++; true
        }
        repo.bindNpcOverride { _, _, _ -> fail("Already handled; no second consumer") }
        assertTrue(interact(1))
        assertEquals(1, calls)
        verify(exactly = 0) { player.executePlugin(ordinary) }
    }

    @Test fun `another account cannot borrow the matching players contextual dialogue`() {
        val second = account()
        repo.bindNpc(801, 1, plugin = ordinary)
        repo.bindNpcOverride { p, n, opt -> p === player && n === npc && opt == 1 }
        assertTrue(repo.executeNpc(second, npc.id, 1))
        verify(exactly = 1) { second.executePlugin(ordinary) }
    }

    @Test fun `missing unrelated and transformed target references cannot claim another NPC`() {
        repo.bindNpc(801, 1, plugin = ordinary)
        var calls = 0
        repo.bindNpcOverride { _, actual, _ -> assertSame(npc, actual); calls++; true }
        player.attr.remove(INTERACTING_NPC_ATTR)
        assertTrue(interact(1))
        val unrelated = mockk<Npc>(relaxed = true)
        every { unrelated.id } returns 802
        every { unrelated.getTransform(player) } returns 803
        player.attr[INTERACTING_NPC_ATTR] = WeakReference(unrelated)
        assertTrue(interact(1))
        assertEquals(0, calls)
        verify(exactly = 2) { player.executePlugin(ordinary) }
        // Normal transformed-ID fallback retains the actual scene NPC, not a
        // fabricated NPC made from the transformed definition ID.
        player.attr[INTERACTING_NPC_ATTR] = WeakReference(npc)
        every { npc.getTransform(player) } returns 999
        assertTrue(repo.executeNpc(player, 999, 1))
        assertEquals(1, calls)
    }
}
