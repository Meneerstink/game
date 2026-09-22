package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs

/** Shared geometry and ids of the Grand Exchange home hall (`ge_home_hall.plugin.kts`). */
object GeHomeHall {
    /** Npc 11679 (cache "Shop assistant"), renamed "Quartermaster" in both caches (tx-20260922-141234). */
    const val QUARTERMASTER = 11679

    /** South-west tile of the 11 x 11 hall. */
    const val X = 3152
    const val Z = 3470
    const val SIZE = 11

    /** The non-combat quest variant has the same Lucien identity/BAS and keeps Talk-to without advertising Attack. */
    const val SERVICE_LUCIEN = Npcs.LUCIEN_273

    data class ServicePost(val npc: Int, val dx: Int, val dz: Int, val facing: Direction)

    /**
     * Balanced around the four walls so the west side is no longer a shoulder-to-shoulder row. Store keepers at
     * south offsets 1..3 remain owned by StoreNpcs and are deliberately not duplicated here.
     */
    val SERVICE_POSTS =
        listOf(
            ServicePost(Npcs.SIR_TIFFY_CASHIEN, 0, 1, Direction.EAST),
            ServicePost(Npcs.MAX, 0, 2, Direction.EAST),
            ServicePost(Npcs.AVA, 0, 3, Direction.EAST),
            ServicePost(QUARTERMASTER, 0, 5, Direction.EAST),
            ServicePost(Npcs.PIKKUPSTIX, 0, 7, Direction.EAST),
            ServicePost(Npcs.ALECK, 0, 8, Direction.EAST),
            ServicePost(Npcs.PARTY_PETE, 4, 0, Direction.NORTH),
            ServicePost(Npcs.KURADAL_9085, 5, 0, Direction.NORTH),
            ServicePost(Npcs.WISE_OLD_MAN, 6, 0, Direction.NORTH),
            ServicePost(Npcs.EVIL_DAVE, 7, 0, Direction.NORTH),
            ServicePost(Npcs.TOOL_LEPRECHAUN, 8, 0, Direction.NORTH),
            ServicePost(Npcs.DRUNKEN_DWARF, 9, 0, Direction.NORTH),
            ServicePost(Npcs.AZZANADRA, 1, 10, Direction.SOUTH),
            ServicePost(Npcs.ARCHAEOLOGIST, 2, 10, Direction.SOUTH),
            ServicePost(Npcs.ONEIROMANCER, 8, 10, Direction.SOUTH),
            ServicePost(Npcs.KING_NARNODE_SHAREEN, 9, 10, Direction.SOUTH),
            ServicePost(SERVICE_LUCIEN, 10, 1, Direction.WEST),
            ServicePost(Npcs.BOB, 10, 2, Direction.WEST),
            ServicePost(Npcs.MANDRITH, 10, 8, Direction.WEST),
            ServicePost(Npcs.PERDU, 10, 9, Direction.WEST),
        )

    val REMOVED_PENGUINS = setOf(Npcs.PENGUIN_5428, Npcs.PING, Npcs.PONG)

    /** Whether [tile] is inside the hall; its stalls are a deliberate layout the GE boot audit must not rearrange. */
    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in X until X + SIZE && tile.z in Z until Z + SIZE
}
