package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
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
 * The home PvP/PvM supplies shops, kept by the Quartermaster in the Grand Exchange home hall
 * (`grandexchange/ge_home_hall.plugin.kts`; owner 2026-09-22: "Create a dedicated PvP/PvM supplies NPC and place it
 * inside the new building", "also burning amulet in the shop").
 *
 * The keeper used to be Shopkeeper 530 - Rimmington's own general-store keeper - at the old Ferox home: binding this
 * shop to that id also turned the real Rimmington shopkeeper into a PK supplies seller and left Rimmington's general
 * store unbound (bulk_shops found the Trade slot taken). The Quartermaster is npc 11679 (cache "Shop assistant",
 * Talk-to/Trade, spawned nowhere else), renamed in both caches by NpcRenameTool (tx-20260922-141234).
 *
 * Stock and prices: [QuartermasterSupplies]. ONE shop on one page that Trade opens directly (owner 2026-10-04 02:40, bug 024055:
 * the three-shop "which supplies?" dialogue of 2026-10-03 was not wanted; N222 2026-10-23: no second page): consumables (bought
 * as banknotes where the cache has a note), then runes, sacks, arrows, one bolt type and the gear. The amount restocks 1% of it
 * per resupply tick.
 */
val QUARTERMASTER = gg.rsmod.plugins.content.areas.grandexchange.GeHomeHall.QUARTERMASTER

fun createSupplyShop(title: String) {
    val rows = QuartermasterSupplies.rows(world.definitions)
    create_shop(
        title,
        // N191 (owner 2026-10-22): the Grand Exchange price of the moment, like the GE itself.
        gg.rsmod.plugins.content.mechanics.shops.GeCoinCurrency(),
        stockSize = gg.rsmod.game.model.shop.Shop.CLIENT_VISIBLE_SLOTS,
        containsSamples = false,
        purchasePolicy = PurchasePolicy.BUY_NONE,
    ) {
        rows.take(gg.rsmod.game.model.shop.Shop.CLIENT_VISIBLE_SLOTS).forEachIndexed { index, row ->
            // A 0 price is refused at purchase ("not currently available") - name it at boot (house tabs, owner session 2026-10-01).
            if (row.price <= 0) gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "shop '$title' item ${row.item} has no price (no OSRS guide price, no cache value)" }
            if (row.item in QuartermasterSupplies.deliveredNoted && row.delivered == row.item && !world.definitions.get(ItemDef::class.java, row.item).stacks) {
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.ITEM) { "shop '$title' item ${row.item} has no banknote in this cache - sold unnoted" }
            }
            // N220: the window shows the item, a purchase hands out its banknote.
            items[index] =
                ShopItem(
                    row.item,
                    row.amount,
                    sellPrice = row.price,
                    resupplyAmount = QuartermasterSupplies.resupplyAmount(row.amount),
                    deliverNoted = row.delivered != row.item,
                )
        }
    }
}

createSupplyShop(QuartermasterSupplies.SHOP)

on_npc_option(QUARTERMASTER, "trade") {
    player.openShop(QuartermasterSupplies.SHOP)
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
            FIRST_OPTION -> player.openShop(QuartermasterSupplies.SHOP)
            SECOND_OPTION -> {
                chatPlayer("What do you stock?", facialExpression = FacialExpression.THINKING)
                chatNpc(
                    "Brews, restores and super combats. Anglerfish, dark",
                    "crabs, mantas and karambwans for combo eating - all",
                    "as banknotes, so you can stock a whole bank.",
                    facialExpression = FacialExpression.CALM_TALK,
                )
                chatNpc(
                    "Combination runes, blighted sacks, dragon arrows,",
                    "ruby and opal dragon bolts, rune pouches, glories, house",
                    "tabs, burning amulets and seeds - and gloves, god",
                    "capes and Ava's devices. Have a look.",
                    facialExpression = FacialExpression.HAPPY,
                )
                player.openShop(QuartermasterSupplies.SHOP)
            }
        }
    }
}
