package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Shared geometry and ids of the Grand Exchange home hall, the Royal Hall (`ge_home_hall.plugin.kts`).
 *
 * Owner 2026-09-25: the hall stands in the middle of the exchange, centred on the exchange's own fountain. Its walls,
 * carpet and furniture live in `data/cfg/home_decor.txt`; the floor is [WIDTH] x [DEPTH] tiles from ([X], [Z]), with a
 * doorway in the middle of every wall ([SOUTH_DOOR] in the north and south walls, [SIDE_DOOR] in the west and east ones).
 *
 * Owner 2026-09-27 ("richt hem professioneel in"): the ground floor holds the services, one theme per marble corner -
 * south-east "death and repair" (Death's Domain, Bob, a deposit box), south-west "skills" (the Skilling Basement, Max,
 * Kuradal, the Wise Old Man, Pikkupstix), north-west "PvP" (Skully, the Quartermaster, Perdu, the Breach Trader,
 * Mandrith), north-east "stores" (Loyalty, Donator and Deadman Store, Ava, Aleck) - and the quest start beside the south
 * entrance (Quest Guide, Archaeologist, Oneiromancer, Azzanadra). The arms of the carpet cross stay free. The upper floor
 * is the casino and lounge: a card table with its dealer and a gilded sign in each marble corner, the thrones and the
 * banquet table with King Narnode, Party Pete and the Drunken Dwarf.
 *
 * ONE SOURCE PER NPC. Every hall npc is posted by a single `npc_spawn` line of `data/world_edits.json` (so the owner moves
 * it himself in developer mode); no code spawns one. The exceptions own their npc for a reason: Skully and his Loot Chest
 * (SkullyRoster, the chest comes with him) and the Quest Guide (`data/cfg/new_player.yml`, the intro sends players to it).
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

    /** The Breach Trader (breach_shop.plugin.kts). */
    const val BREACH_TRADER = 14478

    /** The 78 Store keepers (StoreNpcs) and Loyalty's Xuan. */
    const val DONATOR_STORE = 14436
    const val DEADMAN_STORE = 14437

    /** The casino dealers (casino_grandexchange.plugin.kts): Dice, Mines, Blackjack, Flower Poker. */
    val CASINO_DEALERS = setOf(Npcs.GAMBLER, Npcs.GAMBLER_3001, Npcs.GAMBLER_3002, Npcs.GAMBLER_3003)

    /** Every npc world_edits.json posts in the hall, each exactly once; the value is its cache size (tiles per side). */
    val HALL_NPCS: Map<Int, Int> =
        mapOf(
            Npcs.BOB to 1, Npcs.ARCHAEOLOGIST to 1, Npcs.ONEIROMANCER to 1, Npcs.AZZANADRA to 2,
            Npcs.MAX to 1, Npcs.KURADAL_9085 to 1, Npcs.WISE_OLD_MAN to 1, Npcs.PIKKUPSTIX to 1,
            QUARTERMASTER to 1, Npcs.PERDU to 1, BREACH_TRADER to 1, Npcs.MANDRITH to 1,
            Npcs.XUAN to 1, DONATOR_STORE to 1, DEADMAN_STORE to 1, Npcs.AVA to 1, Npcs.ALECK to 1,
            Npcs.KING_NARNODE_SHAREEN to 1, Npcs.PARTY_PETE to 1, Npcs.DRUNKEN_DWARF to 1,
        ) + CASINO_DEALERS.associateWith { 1 }

    /** Npcs that left the hall for good (owner: claim-only npcs, the penguins, Sir Tiffy and the Tool Leprechaun). */
    val REMOVED = setOf(Npcs.EVIL_DAVE, Npcs.LUCIEN, Npcs.LUCIEN_273, Npcs.SIR_TIFFY_CASHIEN, Npcs.TOOL_LEPRECHAUN, Npcs.PENGUIN_5428, Npcs.PING, Npcs.PONG)

    /**
     * The two white marble spiral staircases (62779, 2 x 2, south-west tile [base]) to the upper floor, with their tops
     * (62780) on the same tile of level 1. [floor] is where a player coming down lands (on the rug in front of the stairs), [gallery]
     * where one going up lands (on the gallery beside the stairwell).
     */
    class Staircase(val base: Tile, val floor: Tile, val gallery: Tile)

    const val STAIRS_BOTTOM = 62779
    const val STAIRS_TOP = 62780

    val STAIRS =
        listOf(
            Staircase(Tile(X + 1, Z + 10, 0), Tile(X + 2, Z + 9, 0), Tile(X + 1, Z + 9, 1)),
            Staircase(Tile(X + 11, Z + 10, 0), Tile(X + 11, Z + 9, 0), Tile(X + 12, Z + 9, 1)),
        )

    /** Whether [tile] is inside the hall; its npcs are a deliberate layout the GE boot audit must not rearrange. */
    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in X until X + WIDTH && tile.z in Z until Z + DEPTH
}
