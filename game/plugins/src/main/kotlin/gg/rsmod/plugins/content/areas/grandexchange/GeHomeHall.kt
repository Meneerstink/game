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

    /** One stall: npc [npc] on floor offset ([dx], [dz]) of level [level] (1 = the gallery lounge), facing [facing]. */
    data class ServicePost(val npc: Int, val dx: Int, val dz: Int, val facing: Direction, val level: Int = 0)

    /**
     * Ground floor: west and east walls hold five stalls each (either side of their doorway), the north wall one either
     * side of the throne dais (the corners beside it hold the spiral staircases), the south wall four (two here, the
     * Breach Trader at dx 3 in breach_shop and Kuradal) west of the entrance and Perdu plus the three 78 Store keepers
     * (StoreNpcs, dx 10-12) east of it. Every ground-floor stall stands behind a marble counter. The gallery lounge
     * above the entrance (level 1) hosts the four npcs players visit least.
     */
    val SERVICE_POSTS =
        listOf(
            ServicePost(Npcs.SIR_TIFFY_CASHIEN, 0, 1, Direction.EAST),
            ServicePost(Npcs.MAX, 0, 2, Direction.EAST),
            ServicePost(Npcs.AVA, 0, 3, Direction.EAST),
            ServicePost(Npcs.PIKKUPSTIX, 0, 8, Direction.EAST),
            ServicePost(Npcs.ALECK, 0, 9, Direction.EAST),
            ServicePost(Npcs.WISE_OLD_MAN, 13, 1, Direction.WEST),
            ServicePost(Npcs.EVIL_DAVE, 13, 2, Direction.WEST),
            ServicePost(Npcs.TOOL_LEPRECHAUN, 13, 3, Direction.WEST),
            ServicePost(SERVICE_LUCIEN, 13, 8, Direction.WEST),
            ServicePost(Npcs.BOB, 13, 9, Direction.WEST),
            ServicePost(Npcs.AZZANADRA, 3, 11, Direction.SOUTH),
            ServicePost(Npcs.MANDRITH, 10, 11, Direction.SOUTH),
            ServicePost(QUARTERMASTER, 1, 0, Direction.NORTH),
            ServicePost(Npcs.DRUNKEN_DWARF, 2, 0, Direction.NORTH),
            ServicePost(Npcs.KURADAL_9085, 4, 0, Direction.NORTH),
            ServicePost(Npcs.PERDU, 9, 0, Direction.NORTH),
            ServicePost(Npcs.PARTY_PETE, 3, 0, Direction.NORTH, level = 1),
            ServicePost(Npcs.KING_NARNODE_SHAREEN, 5, 0, Direction.NORTH, level = 1),
            ServicePost(Npcs.ONEIROMANCER, 8, 0, Direction.NORTH, level = 1),
            ServicePost(Npcs.ARCHAEOLOGIST, 10, 0, Direction.NORTH, level = 1),
        )

    /**
     * The two white spiral staircases (1739, 2 x 2, south-west tile [base]) to the gallery, with their tops (1740) on the
     * same tile of level 1. [floor] is where a player coming down lands (on the rug in front of the stairs), [gallery]
     * where one going up lands (on the gallery beside the stairwell).
     */
    class Staircase(val base: Tile, val floor: Tile, val gallery: Tile)

    const val STAIRS_BOTTOM = 1739
    const val STAIRS_TOP = 1740

    val STAIRS =
        listOf(
            Staircase(Tile(X + 1, Z + 10, 0), Tile(X + 2, Z + 9, 0), Tile(X + 1, Z + 9, 1)),
            Staircase(Tile(X + 11, Z + 10, 0), Tile(X + 11, Z + 9, 0), Tile(X + 12, Z + 9, 1)),
        )

    val REMOVED_PENGUINS = setOf(Npcs.PENGUIN_5428, Npcs.PING, Npcs.PONG)

    /** Every stall has a marble counter in front of it; its npc is talked to across it, like a banker across a booth. */
    const val COUNTER_REACH = 2

    /** The Breach Trader (breach_shop.plugin.kts), whose south-row stall also has a counter. */
    const val BREACH_TRADER = 14478

    /** Whether [tile] is inside the hall; its stalls are a deliberate layout the GE boot audit must not rearrange. */
    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in X until X + WIDTH && tile.z in Z until Z + DEPTH
}
