package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.shops.PointCurrency

/*
 * The Breach Trader (owner 2026-09-23: "Eigen breach-shop NPC"): a clone of the OSRS Emblem Trader 308 - the npc that ran the
 * Deadman: Annihilation points shop - named "Breach Trader" (OsrsNpcImportTool clone batch breach-trader, tx-20260923-192958),
 * in the Grand Exchange home hall beside the 78 Store keepers. Currency: Breach Points ([BreachPoints], earned in breaches, plus
 * Archaic emblem trade-ins).
 *
 * Stock and prices: OSRS Wiki "Bounty Hunter Shop (Deadman Mode)", Annihilation event stock, read 2026-09-23 - every listed item
 * this server has. Not stocked because the item does not exist here yet: Blighted surge sack, Ring of wealth scroll, Saradomin's
 * tear, Rune pouch note, Looting bag note, Scroll of redirection, Clue box, Scroll of imbuing and Guthixian icon (each needs its own
 * behaviour, not just an item). The five missing halos were imported for it (batch halos).
 */

val BREACH_TRADER = 14478
val BREACH_SHOP = "Breach Trader"

/** OSRS Annihilation stock: item to price in points. Potions are the (4) doses. */
val STOCK: List<Pair<Int, Int>> =
    listOf(
        Items.SUPER_ATTACK_4 to 15_000,
        Items.SUPER_STRENGTH_4 to 15_000,
        Items.SUPER_DEFENCE_4 to 15_000,
        Items.RANGING_POTION_4 to 30_000,
        Items.MAGIC_POTION_4 to 20_000,
        Items.SUPER_COMBAT_POTION_4 to 75_000,
        Items.PRAYER_POTION_4 to 15_000,
        Items.SARADOMIN_BREW_4 to 75_000,
        Items.SUPER_RESTORE_4 to 50_000,
        Items.ZAMORAK_BREW_4 to 20_000,
        Items.STAMINA_POTION_4 to 50_000,
        Items.EXTENDED_ANTIFIRE_4 to 25_000,
        Items.ANTIPOISON_4_5952 to 15_000, // Antipoison++ (OSRS "Antidote++")
        Items.ANTI_VENOM_4 to 100_000,
        Items.BLIGHTED_ENTANGLE_SACK to 2_000,
        Items.BLIGHTED_TELEPORT_SPELL_SACK to 3_000,
        Items.BLIGHTED_VENGEANCE_SACK to 3_000,
        Items.BLIGHTED_ANCIENT_ICE_SACK to 2_500,
        Items.MAGIC_SHORTBOW_SCROLL to 100_000,
        Items.ORNATE_MAUL_HANDLE to 1_250_000,
        Items.TROUVER_PARCHMENT to 250_000,
        Items.ANCIENT_SCEPTRE to 1_000_000,
        Items.SARADOMIN_HALO to 500_000,
        Items.ZAMORAK_HALO to 500_000,
        Items.GUTHIX_HALO to 500_000,
        // OSRS halos imported for this shop (OsrsItemImportTool batch halos, tx-20260923-200943): Armadyl, Bandos, Seren, Ancient,
        // Brassica.
        23858 to 500_000,
        23859 to 500_000,
        23860 to 500_000,
        23861 to 500_000,
        23862 to 500_000,
    )

create_shop(
    BREACH_SHOP,
    currency = PointCurrency("breach point", "breach points", BreachPoints.BALANCE),
    purchasePolicy = PurchasePolicy.BUY_NONE,
    containsSamples = false,
) {
    STOCK.forEachIndexed { slot, (item, price) -> items[slot] = ShopItem(item, amount = 1_000, sellPrice = price) }
}

spawn_npc(npc = BREACH_TRADER, x = 3156, z = 3470, walkRadius = 0, direction = Direction.NORTH)

on_npc_option(npc = BREACH_TRADER, option = "talk-to") {
    player.queue {
        chatNpc(
            "Breach Points buy my wares. You earn them by damaging breach monsters, and I'll take any Archaic emblem you bring me.",
            wrap = true,
        )
        when (options("Show me your rewards.", "How many Breach Points do I have?", "Never mind.")) {
            FIRST_OPTION -> player.openShop(BREACH_SHOP)
            SECOND_OPTION ->
                chatNpc("You have ${"%,d".format(BreachPoints.balance(player))} Breach Points.", wrap = true)
        }
    }
}

on_npc_option(npc = BREACH_TRADER, option = "rewards") {
    player.openShop(BREACH_SHOP)
}

/** The Emblem Trader's own "Skull" option, kept on the clone: a PK skull on request after a confirmation. */
on_npc_option(npc = BREACH_TRADER, option = "skull") {
    player.queue {
        if (PvpSkull.isSkulled(player)) {
            chatNpc("You're already skulled.")
            return@queue
        }
        when (options("Give me a PK skull. (The guards here will attack!)", "No thanks.", title = "Get a PK skull?")) {
            FIRST_OPTION -> PvpSkull.applyTestSkull(player)
        }
    }
}
