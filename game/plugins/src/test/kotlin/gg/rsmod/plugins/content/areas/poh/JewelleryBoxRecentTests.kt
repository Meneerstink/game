package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.testutil.BacklogBundleWorld
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTaskSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JewelleryBoxRecentTests {
    @Test
    fun `first use has no previous destination and every box button can be remembered`() {
        val p = BacklogBundleWorld.player()
        assertNull(JewelleryBoxRecent.last(p))
        JewelleryBoxRecent.repeat(p)
        assertTrue(p.interfaces.isVisible(PohTeleportUi.JEWELLERY_BOX), "first use opens the chooser")
        val destinations = JewelleryBoxRecent.destinations()
        assertTrue(destinations.size >= 20)
        assertEquals(destinations.size, destinations.map { it.name }.distinct().size, "saved names must be unique")
        destinations.forEach {
            JewelleryBoxRecent.remember(p, it)
            assertEquals(it, JewelleryBoxRecent.last(p))
            assertNull(JewelleryBoxRecent.last(p, destinations - it), "a lesser box cannot repeat an unavailable destination")
        }
    }

    @Test
    fun `stable persistent key survives attribute reconstruction and rejects unknown destinations`() {
        val p = BacklogBundleWorld.player()
        val leaf = JewelleryBoxRecent.destinations().last()
        JewelleryBoxRecent.remember(p, leaf)
        assertEquals(leaf.name, p.attr.toPersistentMap()[JewelleryBoxRecent.LAST.persistenceKey])
        val restored = BacklogBundleWorld.player()
        restored.attr[AttributeKey<String>(persistenceKey = assertNotNull(JewelleryBoxRecent.LAST.persistenceKey))] = p.attr[JewelleryBoxRecent.LAST]!!
        assertEquals(leaf, JewelleryBoxRecent.last(restored))
        restored.attr[JewelleryBoxRecent.LAST] = "removed destination"
        assertNull(JewelleryBoxRecent.last(restored))
        JewelleryBoxRecent.remember(restored, PohTeleports.leaves().first { it.name == "Home" })
        assertEquals("removed destination", restored.attr[JewelleryBoxRecent.LAST], "nexus-only destinations cannot be remembered")
    }

    @Test
    fun `refused teleport never overwrites previous destination`() {
        val p = BacklogBundleWorld.player()
        val leaves = JewelleryBoxRecent.destinations()
        JewelleryBoxRecent.remember(p, leaves.first())
        p.timers[TELEBLOCK_TIMER] = 50
        PohTeleportUi.go(p, PohTeleportUi.JEWELLERY_BOX, leaves.last())
        assertEquals(leaves.first(), JewelleryBoxRecent.last(p))
    }

    @Test
    fun `box selection is remembered only after approval and a nexus click cannot replace it`() {
        val p = BacklogBundleWorld.player()
        val leaves = JewelleryBoxRecent.destinations()
        JewelleryBoxRecent.remember(p, leaves.first())
        PvpSkull.applyTestSkull(p)
        PohTeleportUi.go(p, PohTeleportUi.JEWELLERY_BOX, leaves.last())
        assertEquals(leaves.first(), JewelleryBoxRecent.last(p))
        assertTrue(SevenSecondAction.isActive(p))
        SevenSecondAction.complete(p)
        assertEquals(leaves.last(), JewelleryBoxRecent.last(p))
        val queues = Pawn::class.java.getDeclaredField("queues").apply { isAccessible = true }.get(p) as QueueTaskSet
        queues.terminateTasks()
        p.unlock()
        PohTeleportUi.go(p, PohTeleportUi.NEXUS, leaves.first())
        SevenSecondAction.complete(p)
        assertEquals(leaves.last(), JewelleryBoxRecent.last(p))
        queues.terminateTasks()
        p.unlock()
    }
}
