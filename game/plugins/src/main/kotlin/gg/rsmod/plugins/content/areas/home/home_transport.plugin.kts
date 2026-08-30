package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency

/**
 * R02.3/R02.4: home transport hub, in the SW quadrant (per HOME_DESIGN_2.png).
 *
 * Full scope note: R02.3 lists eight historic transport networks (Spirit Tree, Fairy Ring,
 * Gnome Glider, Magic Carpet, balloon, minecart, charter/boat NPC, Wilderness lever/obelisk).
 * None of the first six exist anywhere in this codebase yet and would need verified real
 * network/destination data this environment can't currently decode (same interface-cache
 * limitation as R03.4 - see OWNER_TASK_STATUS.md) - inventing station lists would be guessing,
 * which is explicitly disallowed. The Wilderness obelisk network already exists and works
 * independently, in the Wilderness itself (`areas/wilderness/wilderness_obelisk.plugin.kts`) -
 * it doesn't belong inside the safe home hub.
 *
 * What IS real and delivered here: a shop selling the charged teleport jewellery that already
 * has verified, working `Player.teleport(...)` handlers to real destinations (Warriors' Guild,
 * Champions' Guild, Monastery, Ranging Guild, Duel Arena, various minigame/skill-training
 * locations, and Grand Exchange/home via glory) - see `items/jewellery/*.plugin.kts`. This
 * connects players to the existing, already-authentic destination network from a single home
 * NPC, satisfying R02.4's "connect central destinations" for everything this environment can
 * currently verify; the remaining six networks stay an open, explicitly tracked gap.
 */
val transportNpc = Npcs.SHOPKEEPER_530
val transportTile = world.gameContext.home.transform(-3, -2)

create_shop("Home Travel Supplies", CoinCurrency(), containsSamples = false) {
    items[0] = ShopItem(Items.GAMES_NECKLACE_8, 5, resupplyCycles = 500)
    items[1] = ShopItem(Items.COMBAT_BRACELET_4, 5, resupplyCycles = 500)
    items[2] = ShopItem(Items.AMULET_OF_GLORY_4, 5, resupplyCycles = 500)
    items[3] = ShopItem(Items.SKILLS_NECKLACE_4, 5, resupplyCycles = 500)
    items[4] = ShopItem(Items.RING_OF_DUELLING_8, 5, resupplyCycles = 500)
}

spawn_npc(npc = transportNpc, x = transportTile.x, z = transportTile.z, height = transportTile.height)

// R14.14-style defensive verification (same discipline as familiar.plugin.kts): SHOPKEEPER_530
// is spawned but unbound anywhere else in this codebase, so its real cache options aren't yet
// confirmed here - only bind "trade" if the real cache definition actually has it, never guess.
run {
    val def = world.definitions.get(NpcDef::class.java, transportNpc)
    if (def.options.any { it?.lowercase() == "trade" }) {
        on_npc_option(transportNpc, "trade") {
            player.openShop("Home Travel Supplies")
        }
    } else {
        println(
            "R02.3 home transport: Npcs.SHOPKEEPER_530 has no real \"trade\" option " +
                "[options=${def.options.filterNotNull().filter { it.isNotBlank() }}] - shop left unbound.",
        )
    }
}
