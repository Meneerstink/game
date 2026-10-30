package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** N471: the Void Knights sell the wearable deflector 19712, and a held or logged 19711 becomes 19712 at login. */
class VoidDeflectorMigrationTests {
    @Test
    fun `the reward is the wearable deflector`() {
        assertEquals(listOf(19712), PestControlRewards.Reward.VOID_DEFLECTOR.items.toList())
        assertEquals(19711, VoidDeflectorMigration.UNWEARABLE)
        assertEquals(19712, VoidDeflectorMigration.WEARABLE)
        assertEquals(Items.VOID_KNIGHT_DEFLECTOR_19712, VoidDeflectorMigration.WEARABLE)
    }

    @Test
    fun `a held 19711 becomes 19712 and nothing else changes`() {
        val container = mockk<ItemContainer>(relaxed = true)
        every { container.capacity } returns 3
        every { container[0] } returns Item(Items.COINS_995, 50)
        every { container[1] } returns Item(19711, 1)
        every { container[2] } returns null
        val placed = slot<Item>()
        every { container[1] = capture(placed) } returns Unit

        assertEquals(1, VoidDeflectorMigration.migrate(container))
        assertEquals(19712, placed.captured.id)
        assertEquals(1, placed.captured.amount)
        verify(exactly = 0) { container[0] = any() }
    }

    @Test
    fun `the collection log entry moves to 19712 and keeps its count and kill count`() {
        assertEquals("995:3:0;19712:1:7", VoidDeflectorMigration.migratedLog("995:3:0;19711:1:7"))
        // Both present (e.g. bought one of each): one entry, counts added, the 19712 kill count kept.
        assertEquals("19712:3:2", VoidDeflectorMigration.migratedLog("19711:1:7;19712:2:2"))
        assertNull(VoidDeflectorMigration.migratedLog("995:3:0;19712:1:7"), "nothing to move")
        assertNull(VoidDeflectorMigration.migratedLog(null))
    }

    @Test
    fun `the new-in-your-log list renames 19711 too`() {
        assertEquals("19712:pest_control:0;995:barrows:4", VoidDeflectorMigration.migratedRecent("19711:pest_control:0;995:barrows:4"))
        assertNull(VoidDeflectorMigration.migratedRecent("995:barrows:4"))
        assertNull(VoidDeflectorMigration.migratedRecent(""))
    }
}
