package gg.rsmod.game.model.entity

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * MOVEMENT_TYPE and MOVEMENT are player-only extended-info flags in this revision: `data\blocks.yml`
 * lists them under `players` only, because the 667 client's `NPCList.processExtendedInfo` has no
 * such flags. [Npc.addBlock] resolves its flag with `!!`, so moving an npc through [Pawn.moveTo] or
 * [Pawn.teleportTo] threw `NullPointerException` and killed whatever was moving it: every Deadman
 * city guard reposition (320 occurrences in one session's log, thrown out of the player cycle task),
 * Nex's "No escape" teleport, the Corporeal Beast's dark core, the Giant Mole's burrow and the
 * Strykewyrm relocation.
 */
class NpcMovementBlockTests {
    private val pawn = File("src/main/kotlin/gg/rsmod/game/model/entity/Pawn.kt").readText()

    @Test
    fun `movement update blocks are added for players only`() {
        assertTrue("private fun addMovementBlocks() {" in pawn)
        assertTrue("if (!entityType.isPlayer) {" in pawn)
    }

    @Test
    fun `both move routes go through the guarded helper`() {
        // Neither moveTo nor teleportTo may add the player-only blocks directly any more.
        val direct = pawn.lines().count { "addBlock(UpdateBlockType.MOVEMENT" in it }
        assertTrue(direct == 2, "expected the two addBlock calls to live only inside addMovementBlocks, found $direct")
        assertTrue(pawn.substringAfter("private fun addMovementBlocks() {").substringBefore("\n    }").contains("addBlock(UpdateBlockType.MOVEMENT_TYPE)"))
        assertTrue(pawn.lines().count { it.trim() == "addMovementBlocks()" } == 2)
    }

    @Test
    fun `a pawn list never indexes with a stale pawn index`() {
        val list = File("src/main/kotlin/gg/rsmod/game/model/PawnList.kt").readText()

        // A pawn removed from the world keeps index -1; both accessors must tolerate that.
        assertTrue("operator fun get(index: Int): T? = pawns.getOrNull(index)" in list)
        assertTrue("fun contains(pawn: T): Boolean = pawns.getOrNull(pawn.index) == pawn" in list)
    }
}
