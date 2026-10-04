package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.Tile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RCV-010 D2: the follow recovery teleport fires exactly when Void `Follow.kt` repositions a follower - another plane,
 * or a Chebyshev distance above 15 - for every offset around the owner.
 */
class FamiliarFollowRecoveryTests {
    @Test
    fun `recovery teleport only beyond Chebyshev 12 (Darkan) or on another plane, whole grid`() {
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
        // Euclidean ceil distance 16 but Chebyshev 11 / 12: the old rule teleported; Darkan recalls only outside 12 (Chebyshev).
        listOf(11 to 11, 12 to 10, -12 to 9).forEach { (dx, dz) ->
            assertNull(Familiar.followRecoveryReason(Tile(owner.x + dx, owner.z + dz, 0), owner), "($dx,$dz)")
        }
        assertEquals("distance", Familiar.followRecoveryReason(Tile(owner.x + 13, owner.z, 0), owner))
    }

    @Test
    // The automatic follow recovery itself is silent (FamiliarPathingTests "an automatic recovery teleport plays no sound"); the
    // generic cue belongs to the player's own summon and Call.
    fun `summon and call have a generic arrival cue for every familiar`() {
        SummoningPouchData.values.forEach { data ->
            val cues = Familiar.arrivalSoundIds(data)
            assertTrue(cues.isNotEmpty(), "${data.name} has no recovery-arrival sound")
            assertEquals(Familiar.ARRIVAL_SOUND, cues.first(), "${data.name} lost the generic recovery cue")
        }
    }

    /**
     * Owner 2026-10-02: a Steel titan teleported to its owner played a loud hit (its spawn cue 4638). Call and the automatic
     * follow recovery are recalls, not summons: for every familiar they play the generic cue only, while the summon itself
     * keeps the familiar's own spawn cue.
     */
    @Test
    fun `a recall never replays the familiar's own spawn cue`() {
        SummoningPouchData.values.forEach { data ->
            assertEquals(listOf(Familiar.ARRIVAL_SOUND), Familiar.arrivalSoundIds(data, summoned = false), "${data.name} recall cues")
            FamiliarAudio.spawnSound(data)?.let { spawn ->
                assertTrue(spawn in Familiar.arrivalSoundIds(data, summoned = true), "${data.name} summon lost its spawn cue $spawn")
            }
        }
    }
}
