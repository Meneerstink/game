package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N503 (owner 2026-11-01 D5/D7/D8): how long PvP death loot lies on the ground. */
class PvpLootTimesTests {
    private val killer = mockk<Player>(relaxed = true).also { every { it.uid } returns PlayerUID(7) }
    private val wilderness = Tile(3100, 3600) // level 11
    private val outside = Tile(3164, 3486) // Grand Exchange

    @Test
    fun `D5 a killer has the loot alone for 1 minute, then everyone 4 more in the Wilderness`() {
        val loot = PvpLootTimes.groundItem(Item(4151), wilderness, killer)
        assertFalse(loot.isPublic())
        assertTrue(loot.isOwnedBy(killer))
        assertEquals(100, loot.publicCycles)
        assertEquals(500, loot.despawnCycles)
    }

    @Test
    fun `D5 outside the Wilderness everyone has 60 more minutes`() {
        val loot = PvpLootTimes.groundItem(Item(4151), outside, killer)
        assertEquals(100, loot.publicCycles)
        assertEquals(6100, loot.despawnCycles)
    }

    @Test
    fun `D7 and D8 without a killer the loot is public at once - 60 minutes outside, 4 in the Wilderness`() {
        val guard = PvpLootTimes.groundItem(Item(995, 1_000_000), outside, null)
        assertTrue(guard.isPublic())
        assertEquals(0, guard.publicCycles)
        assertEquals(6000, guard.despawnCycles)
        val offline = PvpLootTimes.groundItem(Item(4151), wilderness, null)
        assertTrue(offline.isPublic())
        assertEquals(400, offline.despawnCycles)
    }

    @Test
    fun `a loot key that falls to the ground gets the same times`() {
        val key = PvpLootTimes.groundItem(26651, 1, outside, killer)
        assertEquals(100, key.publicCycles)
        assertEquals(6100, key.despawnCycles)
    }
}
