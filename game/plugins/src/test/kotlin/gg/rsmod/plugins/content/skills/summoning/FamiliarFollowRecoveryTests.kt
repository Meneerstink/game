package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.Tile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * RCV-010 D2: the follow recovery teleport fires exactly when Void `Follow.kt` repositions a follower - another plane,
 * or a Chebyshev distance above 15 - for every offset around the owner.
 */
class FamiliarFollowRecoveryTests {
    @Test
    fun `recovery teleport only beyond Chebyshev 15 or on another plane, whole grid`() {
        val owner = Tile(3200, 3200, 0)
        val wrong = mutableListOf<String>()
        for (dx in -20..20) {
            for (dz in -20..20) {
                val npc = Tile(owner.x + dx, owner.z + dz, 0)
                val expected = if (maxOf(abs(dx), abs(dz)) > Familiar.FOLLOW_RECOVERY_DISTANCE) "distance" else null
                val actual = Familiar.followRecoveryReason(npc, owner)
                if (actual != expected) wrong += "($dx,$dz) expected=$expected actual=$actual"
                val otherPlane = Familiar.followRecoveryReason(Tile(npc.x, npc.z, 1), owner)
                if (otherPlane != "plane") wrong += "($dx,$dz) plane change gave $otherPlane"
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `the old Euclidean cases now keep walking`() {
        val owner = Tile(3200, 3200, 0)
        // Euclidean ceil distance 16 but Chebyshev 11 / 12 / 15: the old rule teleported, Void walks.
        listOf(11 to 11, 12 to 10, 15 to 15, -13 to 9).forEach { (dx, dz) ->
            assertNull(Familiar.followRecoveryReason(Tile(owner.x + dx, owner.z + dz, 0), owner), "($dx,$dz)")
        }
        assertEquals("distance", Familiar.followRecoveryReason(Tile(owner.x + 16, owner.z, 0), owner))
    }
}
