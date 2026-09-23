package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.model.Direction

/*
 * The gambling hall of the Grand Exchange (owner 2026-09-20: "create ... a beautifull spot at the grand exchange,
 * move the gamblers in the spot, make it beautifull").
 *
 * WHERE. The croupiers used to stand in the middle of the GE service row at z = 3500, shoulder to shoulder with the
 * bankers, clerks and shopkeepers - functional, but not a place. They now have their own pit in the open strip
 * against the Grand Exchange's north wall, x 3159-3171 by z 3507-3514. That strip was chosen because it is
 * genuinely empty: `runObjectPlacementProbeTool ... tile 3165 3510 0 6` reports NOT ONE placement in
 * x 3159-3171 / z 3508-3513 in this cache - no scenery, not even a ground decoration - so nothing of Varrock's own
 * is overwritten or hidden, and the walled backdrop at z 3516 closes the room off behind the dealers.
 *
 * WHAT. Four gaming tables in a row with a croupier standing behind each one, a paved pit in front of them, a
 * lit centrepiece with the cache's own rolling `Dice`, and torches marking the four corners and the entrance.
 * Players walk in from the Grand Exchange side (south), cross the open floor and talk to the dealer of the game they
 * want; the one-tile gaps between the tables (x 3161 / 3165 / 3169) are deliberately left open so the dealers can
 * be reached from either side of their table.
 *
 * THE TABLES ARE `Card table` (12975), NOT `Table` (593). Owner 2026-09-20, on the screenshot of this pit: "use
 * better tables". 593 is the plain two-by-two kitchen table Varrock's houses are furnished with; 12975 is the
 * cache's own 3x2 casino table, laid with cards and chips, and it carries animation 12623 so the table is alive
 * rather than a static prop. `runObjectDefProbeTool` confirms it advertises no options at all, which is the rule
 * everything placed here follows. It is one tile wider than 593, so the row runs on a four-tile pitch from x 3158
 * and the pit widened by two tiles at each end to hold it; `runRev667RegionProbeTool locs 12598` reports nothing
 * but ground decoration at plane 0 across x 3155-3178, so the wider pit still overwrites none of Varrock's own
 * scenery (a type-10 object and a type-22 ground decoration occupy different slots on a tile and coexist).
 *
 * EVERY OTHER ID IS CACHE-NATIVE TOO. The croupiers are the revision-667 `Gambler` npcs (2998-3003), the lighting
 * its `Standing torch` (724, flame animation 481), the centrepiece its animated `Dice` (16855, animation 4360), and
 * the north wall carries the same Grand Exchange banners used elsewhere in this Home.
 *
 * NOTHING PLACED HERE CARRIES A DEAD OPTION. Every decorative id was checked with `runObjectDefProbeTool` and only
 * option-less ids were used - that is why the crates are `Gambling crate` 62275 and not 62274, which advertises an
 * "Open" that no plugin answers, and why the pit is lit by `Standing torch` (no options) rather than `Brazier`
 * (1:'Investigate'). The native Grand Exchange paving now stays visible throughout the pit; every solid prop is laid
 * out so that no tile a player needs is ever enclosed.
 */

// ------------------------------------------------------------------ geometry

/** The row the four tables and their dealers occupy; the open social pit lies south of it. */
val PIT_WEST = 3157
val PIT_EAST = 3173
val PIT_SOUTH = 3507
val TABLE_Z = 3512
val DEALER_Z = 3514

/**
 * x of the south-west tile of each 3x2 card table. A table covers x..x+2, so the four sit on a four-tile pitch and
 * leave single gaps at x 3161, 3165 and 3169. The dealer of a table stands behind its middle column on [DEALER_Z].
 */
val DICE_TABLE_X = 3158
val MINES_TABLE_X = 3162
val BLACKJACK_TABLE_X = 3166
val FLOWER_TABLE_X = 3170

/** A card table is three tiles wide; its dealer stands behind the middle one. */
val TABLE_WIDTH = 3

val PIT_CENTRE = Tile(3165, 3510, 0)

// ------------------------------------------------------------------ the tables and their dealers

/**
 * One gaming station: a 3x2 card table with its croupier standing behind it, facing south into the pit.
 *
 * The table's south-west tile is [tableX], [TABLE_Z], so it covers x..x+2 by z 3512-3513 and the dealer stands
 * behind its middle column on [DEALER_Z]. The tile east of each table (x + 3) is left clear, so every dealer can be
 * walked up to from both sides even with the table blocking the front.
 */
fun station(
    npcId: Int,
    tableX: Int,
) {
    spawn_obj(obj = Objs.CARD_TABLE, x = tableX, z = TABLE_Z, type = 10, rot = 0)
    // `static` is the engine's own "this npc keeps its pose" flag: `PawnPathAction` only turns an npc to face the
    // player when it is NOT static, so a static dealer keeps looking south across the pit however players crowd
    // round him. Owner 2026-09-21: "the gambler npcs need to look south they now follow the player when u talk to
    // them". Nothing else reads the flag, so it costs the dealers nothing but their head-turn.
    spawn_npc(npc = npcId, x = tableX + TABLE_WIDTH / 2, z = DEALER_Z, direction = Direction.SOUTH, static = true)
}

station(Npcs.GAMBLER, DICE_TABLE_X)
station(Npcs.GAMBLER_3001, MINES_TABLE_X)
station(Npcs.GAMBLER_3002, BLACKJACK_TABLE_X)
station(Npcs.GAMBLER_3003, FLOWER_TABLE_X)

// ------------------------------------------------------------------ dressing

// Corner torches frame the pit; the two at PIT_SOUTH flank its open entrance.
listOf(
    Tile(PIT_WEST, TABLE_Z + 1, 0),
    Tile(PIT_EAST, TABLE_Z + 1, 0),
    Tile(PIT_WEST, TABLE_Z - 4, 0),
    Tile(PIT_EAST, TABLE_Z - 4, 0),
    Tile(PIT_CENTRE.x - 2, PIT_SOUTH, 0),
    Tile(PIT_CENTRE.x + 2, PIT_SOUTH, 0),
    // Flanking the centrepiece, so the middle of the pit is lit rather than empty.
    Tile(PIT_CENTRE.x - 1, PIT_CENTRE.z, 0),
    Tile(PIT_CENTRE.x + 1, PIT_CENTRE.z, 0),
).forEach { spawn_obj(obj = Objs.STANDING_TORCH, x = it.x, z = it.z, type = 10, rot = 0) }

// Cache-native Grand Exchange wall banners make the dealer row feel built into the surrounding exchange.
listOf(3159, 3163, 3167, 3171).forEach { x ->
    spawn_obj(obj = 60279, x = x, z = 3516, type = 4, rot = 1)
}

// The centrepiece: the cache's own dice, which roll on animation 4360 for as long as the hall stands.
spawn_obj(obj = Objs.DICE_16855, x = PIT_CENTRE.x, z = PIT_CENTRE.z, type = 10, rot = 0)

// House crates at either end of the table row. 62275 is the option-less variant of the Gambling crate.
spawn_obj(obj = Objs.GAMBLING_CRATE_62275, x = PIT_WEST, z = TABLE_Z, type = 10, rot = 0)
spawn_obj(obj = Objs.GAMBLING_CRATE_62275, x = PIT_EAST, z = TABLE_Z, type = 10, rot = 3)

/*
 * The dealers are placed by hand, in a layout whose whole point is where each one stands. `ge_home_audit` walks the
 * Grand Exchange one tick after boot and relocates any npc it finds pressed against another or standing somewhere a
 * customer cannot reach; the layout above satisfies both rules (the dealers are three tiles apart and each has three
 * free neighbours), but a later change to the pit must not be able to scatter them across the plaza. Marking them
 * the same way Skully and the 78 Store npcs are marked - `respawnOverride` set - makes the audit skip them, which is
 * exactly what "code-placed npcs have their own placement rules" means there.
 */
val DEALER_IDS = setOf(Npcs.GAMBLER, Npcs.GAMBLER_3001, Npcs.GAMBLER_3002, Npcs.GAMBLER_3003)

on_world_init {
    // Not queued: the static spawns above are already in the world by the time on_world_init runs (StoreNpcs reads
    // them here the same way), while the audit does its work from a `world.queue { wait(1) }`. Tagging straight
    // away means the mark is always set before the audit looks, whatever order the plugins happen to load in.
    var tagged = 0
    world.npcs.forEach {
        if (it.id in DEALER_IDS && it.tile.z == DEALER_Z && it.tile.height == 0) {
            it.respawnOverride = true
            tagged++
        }
    }
    println("casino_grandexchange: $tagged dealers pinned at the Grand Exchange gambling hall.")
}

/**
 * Binds a croupier's talk option only if the cache actually gives that npc the option.
 *
 * A revision-667 npc menu has five slots and an npc that was never meant to be talked to may not carry one;
 * asking `on_npc_option` for a missing option throws out of `PluginRepository.init` and takes the whole boot down
 * (owner 2026-09-20 - the ring of shadows did exactly that). So the definition is read first, and a croupier
 * without a talk option simply is not bound rather than bringing the server down with it.
 */
fun bindCroupier(
    npcId: Int,
    game: CasinoGame,
) {
    val def = world.definitions.get(NpcDef::class.java, npcId)
    val option = def.options.filterNotNull().firstOrNull { it.equals("Talk-to", ignoreCase = true) } ?: return
    on_npc_option(npc = npcId, option = option) {
        player.queue {
            chatNpc("Care for a game of ${game.displayName}? Every round here is provably fair.", wrap = true)
            val choice =
                options(
                    "Play ${game.displayName}.",
                    "How does provably fair work?",
                    "Show my recent rounds.",
                    "No thanks.",
                )
            when (choice) {
                1 -> CasinoScreens.open(player, game)
                2 -> {
                    chatNpc(
                        "Before you bet I show you a hash of my secret seed. You pick your own seed. " +
                            "When you retire the seed I reveal mine, and you can replay every round yourself.",
                        wrap = true,
                    )
                    CasinoDialogs.showFairness(this, player)
                }
                3 -> {
                    val rows = CasinoHistory.recent(player)
                    if (rows.isEmpty()) {
                        chatNpc("You have not played here yet.", wrap = true)
                    } else {
                        rows.take(5).forEach { row ->
                            player.message("${row.game.displayName}: ${CasinoWallet.format(row.profit)} (nonce ${row.nonce}) ${row.detail}")
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

bindCroupier(Npcs.GAMBLER, CasinoGame.DICE)
bindCroupier(Npcs.GAMBLER_3001, CasinoGame.MINES)
bindCroupier(Npcs.GAMBLER_3002, CasinoGame.BLACKJACK)
bindCroupier(Npcs.GAMBLER_3003, CasinoGame.FLOWER_POKER)
