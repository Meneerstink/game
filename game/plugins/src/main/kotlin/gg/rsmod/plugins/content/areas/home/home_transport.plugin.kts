package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import gg.rsmod.game.model.shop.PurchasePolicy

/**
 * R02.3/R02.4/HOME_DESIGN_2.png: home transport hub, in the SE quadrant ("VERVOER" in the
 * confirmed design) - south of the Pool & Altar area, on the way to the south gate/arrival.
 *
 * Corrected this pass: an earlier version of this file placed this hub in the SW quadrant
 * before the confirmed design image was actually reviewed - HOME_DESIGN_2.png clearly labels
 * "VERVOER" in the SE, with "PVM & MINIGAMES" in the SW instead.
 *
 * The design shows glider/minecart/balloon/carpet PROPS as visual identity for this quadrant -
 * those are now placed as real, verified decorative-only objects in `home_decor.plugin.kts`
 * (mine cart/party balloon/rug; a real "Gnome glider" id exists too but was skipped there as
 * too large/solid to safely fit this quadrant without visual confirmation). What IS real and
 * functional here:
 *
 * Full scope note: R02.3 lists eight historic transport networks (Spirit Tree, Fairy Ring,
 * Gnome Glider, Magic Carpet, balloon, minecart, charter/boat NPC, Wilderness lever/obelisk).
 * The Gnome Glider network is now real and live (`mechanics/travel/gnome_glider.plugin.kts`),
 * at its 6 real pilots' own original locations, not duplicated here. Spirit Tree, Fairy Ring,
 * Magic Carpet, balloon, minecart, charter/boat still need verified real network/destination
 * data this environment can't currently decode (same interface-cache limitation as R03.4 - see
 * OWNER_TASK_STATUS.md) - inventing station lists would be guessing, which is explicitly
 * disallowed. The Wilderness obelisk network already exists and works independently, in the
 * Wilderness itself (`areas/wilderness/wilderness_obelisk.plugin.kts`) - it doesn't belong
 * inside the safe home hub.
 *
 * A shop selling the charged teleport jewellery that already has verified, working
 * `Player.teleport(...)` handlers to real destinations (Warriors' Guild, Champions' Guild,
 * Monastery, Ranging Guild, Duel Arena, various minigame/skill-training locations, and Grand
 * Exchange/home via glory) - see the `items/jewellery` plugins. This connects players to the
 * existing, already-authentic destination network from a single home NPC, satisfying R02.4's
 * "connect central destinations" for everything this environment can currently verify; the
 * remaining networks stay an open, explicitly tracked gap.
 */
/**
 * The home PvP/PvM supplies shop, kept by the Quartermaster in the Grand Exchange home hall
 * (`grandexchange/ge_home_hall.plugin.kts`; owner 2026-09-22: "Create a dedicated PvP/PvM supplies NPC and place it
 * inside the new building", "also burning amulet in the shop").
 *
 * The keeper used to be Shopkeeper 530 - Rimmington's own general-store keeper - at the old Ferox home: binding this
 * shop to that id also turned the real Rimmington shopkeeper into a PK supplies seller and left Rimmington's general
 * store unbound (bulk_shops found the Trade slot taken). The Quartermaster is npc 11679 (cache "Shop assistant",
 * Talk-to/Trade, spawned nowhere else), renamed in both caches by NpcRenameTool (tx-20260922-141234).
 *
 * Every item below was checked to exist unnoted in this cache and to have a working route: food in `Food`, potions in
 * `Potion`, runes/combination runes through the spellbooks, blighted sacks in `BlightedSacks`, arrows through ranged
 * ammo, the house tab in `player_house.plugin.kts`, the burning amulet in `burning_amulet.plugin.kts`, the glory in
 * `amulet_of_glory.plugin.kts`, the rune pouch in `rune_pouch.plugin.kts`, Mithril/Adamant seeds in `flower_seeds.plugin.kts`
 * and moonlight antler bolts in `CrossbowType`. Owner 2026-09-30 refined the list: rune/dragon arrows only, no plain runes,
 * shark, summer pie and tuna potato out (manta ray, sea turtle and the karambwan combo food stay), Bastion/Battlemage as
 * the other live high-level potions (divine potions are removed). Prices use the same OSRS guide/mid-price table as the
 * Grand Exchange; the amount is the shop's stock, which restocks at the default rate.
 */
val QUARTERMASTER = gg.rsmod.plugins.content.areas.grandexchange.GeHomeHall.QUARTERMASTER
val HOME_SUPPLIES = "Home PK Supplies"

val homePkSupplyStock =
    listOf(
        // Arrows (owner 2026-09-30: rune and dragon only).
        Items.RUNE_ARROW to 2_000, Items.DRAGON_ARROW to 1_000,
        // Combination runes only (owner 2026-09-30: no plain runes); every elemental pair is covered, the Wilderness
        // spells themselves come from the blighted sacks below.
        Items.MIST_RUNE to 5_000, Items.DUST_RUNE to 5_000, Items.MUD_RUNE to 5_000, Items.SMOKE_RUNE to 5_000,
        Items.STEAM_RUNE to 5_000, Items.LAVA_RUNE to 5_000,
        // Blighted spell sacks.
        Items.BLIGHTED_ANCIENT_ICE_SACK to 5_000, Items.BLIGHTED_ENTANGLE_SACK to 5_000,
        Items.BLIGHTED_TELEPORT_SPELL_SACK to 2_000, Items.BLIGHTED_VENGEANCE_SACK to 5_000,
        // High-tier and combo food (owner 2026-09-30: no shark, summer pie or tuna potato).
        Items.MANTA_RAY to 1_000, Items.SEA_TURTLE to 1_000, Items.ANGLERFISH to 1_000, Items.DARK_CRAB to 1_000,
        Items.MARLIN to 1_000, Items.HALIBUT to 1_000, Items.COOKED_KARAMBWAN to 2_000,
        // Potions (4 doses).
        Items.SARADOMIN_BREW_4 to 1_000, Items.SUPER_RESTORE_4 to 1_000, Items.SUPER_COMBAT_POTION_4 to 500,
        Items.SUPER_ATTACK_4 to 500, Items.SUPER_STRENGTH_4 to 500, Items.SUPER_DEFENCE_4 to 500,
        Items.RANGING_POTION_4 to 500, Items.MAGIC_POTION_4 to 500, Items.PRAYER_POTION_4 to 1_000,
        Items.BASTION_POTION_4 to 300, Items.BATTLEMAGE_POTION_4 to 300,
        Items.SANFEW_SERUM_4 to 300, Items.GUTHIX_REST_4 to 300, Items.STAMINA_POTION_4 to 300,
        Items.EXTENDED_ANTIFIRE_4 to 300, Items.ANTI_VENOM_PLUS_4 to 300,
        // Teleports and utility.
        Items.TELEPORT_TO_HOUSE to 1_000, Items.BURNING_AMULET_5 to 200, Items.AMULET_OF_GLORY_4 to 200,
        Items.RUNE_POUCH to 50, Items.MITHRIL_SEEDS to 1_000, Items.ADAMANT_SEEDS to 1_000,
        // Crossbow ammunition.
        Items.RUNITE_BOLTS to 3_000, Items.DIAMOND_BOLTS_E to 1_000, Items.RUBY_BOLTS_E to 1_000,
        Items.DRAGON_BOLTS_E to 1_000, Items.ONYX_BOLTS_E to 300, Items.DRAGON_BOLTS to 1_000,
        Items.OPAL_DRAGON_BOLTS_E to 500, Items.DIAMOND_DRAGON_BOLTS_E to 500, Items.RUBY_DRAGON_BOLTS_E to 500,
        Items.DRAGONSTONE_DRAGON_BOLTS_E to 300, Items.MOONLIGHT_ANTLER_BOLTS to 500,
    ).distinctBy { it.first }

val availableHomePkSupplyStock = homePkSupplyStock.filter { (itemId, _) ->
    val def = world.definitions.getNullable(ItemDef::class.java, itemId)
    def != null && def.name.isNotBlank() && !def.noted
}

create_shop(
    HOME_SUPPLIES,
    CoinCurrency(),
    stockSize = maxOf(40, availableHomePkSupplyStock.size),
    containsSamples = false,
    purchasePolicy = PurchasePolicy.BUY_NONE,
) {
    availableHomePkSupplyStock.forEachIndexed { index, (itemId, amount) ->
        val def = world.definitions.get(ItemDef::class.java, itemId)
        val price = OsrsGuidePrices.seed(def)
        // A 0 price is refused at purchase ("not currently available") - name it at boot (house tabs, owner session 2026-10-01).
        if (price <= 0) gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "shop '$HOME_SUPPLIES' item $itemId '${def.name}' has no price (no OSRS guide price, cache value ${def.cost})" }
        items[index] = ShopItem(itemId, amount, sellPrice = price)
    }
}

on_npc_option(QUARTERMASTER, "trade") {
    player.openShop(HOME_SUPPLIES)
}

on_npc_option(QUARTERMASTER, "talk-to") {
    player.queue {
        chatNpc(
            "Heading out? Nobody leaves this hall under-supplied.",
            "Food, potions, runes, arrows and bolts - all of it",
            "stocked for the Wilderness and the bosses alike.",
            facialExpression = FacialExpression.CALM_TALK,
        )
        when (options("Let's see your supplies.", "What do you stock?", "Nothing for now.")) {
            FIRST_OPTION -> player.openShop(HOME_SUPPLIES)
            SECOND_OPTION -> {
                chatPlayer("What do you stock?", facialExpression = FacialExpression.THINKING)
                chatNpc(
                    "Brews, restores and super combats. Anglerfish, dark",
                    "crabs, mantas and karambwans for combo eating, and",
                    "combination runes for your elemental spells.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Blighted sacks for the Wilderness, rune and dragon",
                    "arrows, enchanted bolts, rune pouches, glories, house",
                    "tabs, burning amulets and seeds. Have a look.",
                    facialExpression = FacialExpression.HAPPY,
                )
                player.openShop(HOME_SUPPLIES)
            }
        }
    }
}
