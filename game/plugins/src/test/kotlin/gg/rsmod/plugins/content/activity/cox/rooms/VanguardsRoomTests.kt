package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.entity.Npc
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VanguardsRoomTests {
    @Test fun `one to four players allow exactly forty percent but not one hitpoint more`() {
        for (size in 1..4) {
            assertFalse(VanguardsRoom.exceedsBalance(60, 100, 100, size))
            assertTrue(VanguardsRoom.exceedsBalance(59, 100, 100, size))
            assertFalse(VanguardsRoom.exceedsBalance(61, 101, 101, size))
            assertTrue(VanguardsRoom.exceedsBalance(60, 101, 101, size))
        }
    }

    @Test fun `five or more players use an exact third rather than thirty three percent`() {
        for (size in listOf(5, 6, 100)) {
            assertFalse(VanguardsRoom.exceedsBalance(200, 300, 300, size))
            assertTrue(VanguardsRoom.exceedsBalance(199, 300, 300, size))
            assertFalse(VanguardsRoom.exceedsBalance(201, 301, 301, size))
            assertTrue(VanguardsRoom.exceedsBalance(200, 301, 301, size))
        }
    }

    @Test fun `balance multiplication does not overflow for large health values`() {
        assertFalse(VanguardsRoom.exceedsBalance(1_200_000_000, 2_000_000_000, 2_000_000_000, 4))
        assertTrue(VanguardsRoom.exceedsBalance(1_199_999_999, 2_000_000_000, 2_000_000_000, 4))
    }

    @Test fun `in flight multi hits land during shuffle but leave one hitpoint`() {
        val room = VanguardsRoom(mockk(relaxed = true), 0, mockk(relaxed = true))
        val npc = mockk<Npc>(relaxed = true)
        every { npc.getCurrentLifepoints() } returns 10
        collection(room, "vanguards").add(npc)
        collection(room, "shelled").add(npc)
        val stage = VanguardsRoom::class.java.getDeclaredField("stage").also { it.isAccessible = true }
        stage.set(room, stage.type.enumConstants.first { (it as Enum<*>).name == "SHUFFLING" })
        val hit = Hit.Builder().addHit(6, 0).addHit(6, 0).build()
        room.incoming(npc, hit)
        assertEquals(listOf(6, 3), hit.hitmarks.map { it.damage })
        every { npc.getCurrentLifepoints() } returns 1
        room.incoming(npc, hit)
        assertEquals(listOf(0, 0), hit.hitmarks.map { it.damage })
    }

    @Suppress("UNCHECKED_CAST")
    private fun collection(room: VanguardsRoom, name: String): MutableCollection<Npc> =
        VanguardsRoom::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(room) as MutableCollection<Npc>
}
