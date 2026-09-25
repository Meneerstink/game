package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Shared geometry and ids of the Grand Exchange home hall (`ge_home_hall.plugin.kts`).
 *
 * Owner 2026-09-25: the hall is the Royal Hall in the north-east corner of the exchange, in place of the north-east bank
 * booth and that section of the ring colonnade (the open-air hall on the south-west lawn is gone). Its walls, carpet and
 * furniture live in `data/cfg/home_decor.txt`; the floor is [WIDTH] x [DEPTH] tiles from ([X], [Z]), with doorways in
 * the south wall (dx 5..7), the west wall and the east wall (dz 5..7). Every stall stands on the edge of the floor
 * against a wall, facing in, with free carpet in front of it; the four corners hold suits of armour.
 */
object GeHomeHall {
    /** Npc 11679 (cache "Shop assistant"), renamed "Quartermaster" in both caches (tx-20260922-141234). */
    const val QUARTERMASTER = 11679

    /** South-west floor tile of the hall. */
    const val X = 3176
    const val Z = 3503
    const val WIDTH = 13
    const val DEPTH = 12

    /** The non-combat quest variant has the same Lucien identity/BAS and keeps Talk-to without advertising Attack. */
    const val SERVICE_LUCIEN = Npcs.LUCIEN_273

    data class ServicePost(val npc: Int, val dx: Int, val dz: Int, val facing: Direction)

    /**
     * West and east walls hold seven stalls each (either side of their doorway), the north wall six (either side of the
     * throne). The south row beside the entrance belongs to the bank staff (spawns_12598), the 78 Store keepers
     * (StoreNpcs) and the Breach Trader (breach_shop), which are deliberately not duplicated here.
     */
    val SERVICE_POSTS =
        listOf(
            ServicePost(Npcs.SIR_TIFFY_CASHIEN, 0, 1, Direction.EAST),
            ServicePost(Npcs.MAX, 0, 2, Direction.EAST),
            ServicePost(Npcs.AVA, 0, 3, Direction.EAST),
            ServicePost(QUARTERMASTER, 0, 4, Direction.EAST),
            ServicePost(Npcs.PIKKUPSTIX, 0, 8, Direction.EAST),
            ServicePost(Npcs.ALECK, 0, 9, Direction.EAST),
            ServicePost(Npcs.PARTY_PETE, 0, 10, Direction.EAST),
            ServicePost(Npcs.WISE_OLD_MAN, 12, 1, Direction.WEST),
            ServicePost(Npcs.EVIL_DAVE, 12, 2, Direction.WEST),
            ServicePost(Npcs.TOOL_LEPRECHAUN, 12, 3, Direction.WEST),
            ServicePost(Npcs.DRUNKEN_DWARF, 12, 4, Direction.WEST),
            ServicePost(SERVICE_LUCIEN, 12, 8, Direction.WEST),
            ServicePost(Npcs.BOB, 12, 9, Direction.WEST),
            ServicePost(Npcs.MANDRITH, 12, 10, Direction.WEST),
            ServicePost(Npcs.KURADAL_9085, 1, 11, Direction.SOUTH),
            ServicePost(Npcs.AZZANADRA, 2, 11, Direction.SOUTH),
            ServicePost(Npcs.ARCHAEOLOGIST, 3, 11, Direction.SOUTH),
            ServicePost(Npcs.ONEIROMANCER, 9, 11, Direction.SOUTH),
            ServicePost(Npcs.KING_NARNODE_SHAREEN, 10, 11, Direction.SOUTH),
            ServicePost(Npcs.PERDU, 11, 11, Direction.SOUTH),
        )

    val REMOVED_PENGUINS = setOf(Npcs.PENGUIN_5428, Npcs.PING, Npcs.PONG)

    /** Whether [tile] is inside the hall; its stalls are a deliberate layout the GE boot audit must not rearrange. */
    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in X until X + WIDTH && tile.z in Z until Z + DEPTH
}
