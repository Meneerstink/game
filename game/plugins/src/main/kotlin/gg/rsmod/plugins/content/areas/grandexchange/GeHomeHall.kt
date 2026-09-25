package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Shared geometry and ids of the Grand Exchange home hall (`ge_home_hall.plugin.kts`).
 *
 * Owner 2026-09-25: the hall is the Royal Hall in the middle of the exchange ("i want the building to move in the middel
 * of grand exchange"), centred on the exchange's own fountain in place of its planters and canopy; the north-east booth it
 * first replaced is restored. Its walls, carpet and furniture live in `data/cfg/home_decor.txt`; the floor is [WIDTH] x
 * [DEPTH] tiles from ([X], [Z]), with doorways in the south wall ([SOUTH_DOOR]), the west wall and the east wall
 * ([SIDE_DOOR]). Every stall stands on the edge of the floor against a wall, facing in, with free marble in front of it;
 * the four corners hold suits of armour.
 */
object GeHomeHall {
    /** Npc 11679 (cache "Shop assistant"), renamed "Quartermaster" in both caches (tx-20260922-141234). */
    const val QUARTERMASTER = 11679

    /** South-west floor tile of the hall. */
    const val X = 3158
    const val Z = 3486
    const val WIDTH = 14
    const val DEPTH = 12

    /** Doorway offsets: dx in the south wall, dz in the west and east walls. */
    val SOUTH_DOOR = 5..8
    val SIDE_DOOR = 4..7

    /** The non-combat quest variant has the same Lucien identity/BAS and keeps Talk-to without advertising Attack. */
    const val SERVICE_LUCIEN = Npcs.LUCIEN_273

    data class ServicePost(val npc: Int, val dx: Int, val dz: Int, val facing: Direction)

    /**
     * West and east walls hold six stalls each (either side of their doorway), the north wall six (either side of the
     * thrones), the south wall two west of the entrance. The rest of the south row belongs to the Breach Trader
     * (breach_shop, dx 3) and the 78 Store keepers (StoreNpcs, dx 10-12), which are deliberately not duplicated here.
     */
    val SERVICE_POSTS =
        listOf(
            ServicePost(Npcs.SIR_TIFFY_CASHIEN, 0, 1, Direction.EAST),
            ServicePost(Npcs.MAX, 0, 2, Direction.EAST),
            ServicePost(Npcs.AVA, 0, 3, Direction.EAST),
            ServicePost(Npcs.PIKKUPSTIX, 0, 8, Direction.EAST),
            ServicePost(Npcs.ALECK, 0, 9, Direction.EAST),
            ServicePost(Npcs.PARTY_PETE, 0, 10, Direction.EAST),
            ServicePost(Npcs.WISE_OLD_MAN, 13, 1, Direction.WEST),
            ServicePost(Npcs.EVIL_DAVE, 13, 2, Direction.WEST),
            ServicePost(Npcs.TOOL_LEPRECHAUN, 13, 3, Direction.WEST),
            ServicePost(SERVICE_LUCIEN, 13, 8, Direction.WEST),
            ServicePost(Npcs.BOB, 13, 9, Direction.WEST),
            ServicePost(Npcs.MANDRITH, 13, 10, Direction.WEST),
            ServicePost(Npcs.KURADAL_9085, 1, 11, Direction.SOUTH),
            ServicePost(Npcs.AZZANADRA, 2, 11, Direction.SOUTH),
            ServicePost(Npcs.ARCHAEOLOGIST, 3, 11, Direction.SOUTH),
            ServicePost(Npcs.ONEIROMANCER, 10, 11, Direction.SOUTH),
            ServicePost(Npcs.KING_NARNODE_SHAREEN, 11, 11, Direction.SOUTH),
            ServicePost(Npcs.PERDU, 12, 11, Direction.SOUTH),
            ServicePost(QUARTERMASTER, 1, 0, Direction.NORTH),
            ServicePost(Npcs.DRUNKEN_DWARF, 2, 0, Direction.NORTH),
        )

    val REMOVED_PENGUINS = setOf(Npcs.PENGUIN_5428, Npcs.PING, Npcs.PONG)

    /** Whether [tile] is inside the hall; its stalls are a deliberate layout the GE boot audit must not rearrange. */
    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in X until X + WIDTH && tile.z in Z until Z + DEPTH
}
