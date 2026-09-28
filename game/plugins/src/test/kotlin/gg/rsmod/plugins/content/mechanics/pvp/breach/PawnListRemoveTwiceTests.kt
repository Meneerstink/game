package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.entity.Npc
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Deadman audit 2026-09-28 (breach conformance familiar phase): a familiar whose owner logged out during its despawn animation
 * was removed from the world twice; the second `World.remove` threw ArrayIndexOutOfBounds (index -1) in `PawnList.remove` and
 * logged "Error with plugin!". A removed pawn (index -1) or any out-of-range index now simply returns false, as documented.
 */
class PawnListRemoveTwiceTests {
    @Test
    fun `removing a pawn that is no longer registered returns false instead of throwing`() {
        val list = PawnList(arrayOfNulls<Npc>(8))
        listOf(-1, 8, 99).forEach { index ->
            val gone = mockk<Npc>(relaxed = true) { every { this@mockk.index } returns index }
            assertFalse(list.remove(gone), "index $index")
        }
    }
}
