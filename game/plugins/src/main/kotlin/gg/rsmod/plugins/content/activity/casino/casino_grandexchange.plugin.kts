package gg.rsmod.plugins.content.activity.casino

/*
 * The casino (owner 2026-09-20: "create ... a beautifull spot at the grand exchange, move the gamblers in the spot").
 *
 * Owner 2026-09-27: the casino moved into the Royal Hall's upper floor - one card table (12975) per marble corner with its
 * dealer behind it and a gilded sign named after the game (RoyalHallLocTool.CASINO_SIGNS), and the gold activity star on
 * the minimap once (RoyalHallLocTool.CASINO_MARKER). The old pit against the exchange's north wall (tables, dealers,
 * torches, banners, dice and crates spawned here) is gone. The tables and signs are dressing in data/cfg/home_decor.txt,
 * each dealer is posted once by data/world_edits.json (GeHomeHall: one source per npc), with the owner's own names.
 *
 * What stays here: every dealer's Talk-to, which opens his game.
 */

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
