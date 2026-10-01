package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.ShopItem
import gg.rsmod.plugins.content.mechanics.shops.PointCurrency

/*
 * The Breach Trader (owner 2026-09-23: "Eigen breach-shop NPC"): a clone of the OSRS Emblem Trader 308 - the npc that ran the
 * Deadman: Annihilation points shop - named "Breach Trader" (OsrsNpcImportTool clone batch breach-trader, tx-20260923-192958),
 * in the Grand Exchange home hall beside the 78 Store keepers. Currency: Breach Points ([BreachPoints], earned in breaches, plus
 * Archaic emblem trade-ins).
 *
 * Stock (owner 2026-09-29, the "Vier stores" layout, Breach section): the Breach shop funds Wilderness progress and PvP
 * consumables. Regular potions are NOT sold here any more (normal supplies belong to the Grand Exchange) and the three original
 * halos moved to the Loyalty shop only (rule: no item in two shops). The Blighted overload stays out (RemovedPotions). Loaded,
 * upgraded, (a)/(au), noted and damaged variants are never stocked: charging with Revenant ether, Dismantle, and the Claws/Fangs/
 * Skull upgrades are the existing item routes (osrs_bows / revenant_weapons plugins).
 *
 * Prices: the OSRS Annihilation prices (OSRS Wiki "Bounty Hunter Shop (Deadman Mode)") for every item that shop sold; the other
 * items have no OSRS points price, so those are owner-approved ADAPTED values (2026-09-29), scaled to the OSRS ones.
 * Not stocked because the item does not exist here yet: Blighted surge sack, Ring of wealth scroll, Saradomin's tear, Scroll of
 * redirection, Clue box, Scroll of imbuing and Guthixian icon (each needs its own behaviour, not just an item).
 */

val BREACH_TRADER = 14478
val BREACH_SHOP = "Breach Trader"

/** Stock: item to price in Breach Points. Potions are the (4) doses. */
val STOCK: List<Pair<Int, Int>> =
    listOf(
        // Blighted consumables (OSRS prices for the sacks; food and super restore ADAPTED).
        Items.BLIGHTED_MANTA_RAY to 5_000,
        Items.BLIGHTED_ANGLERFISH to 5_000,
        Items.BLIGHTED_KARAMBWAN to 3_000,
        Items.BLIGHTED_SUPER_RESTORE4 to 50_000,
        Items.BLIGHTED_ENTANGLE_SACK to 2_000,
        Items.BLIGHTED_TELEPORT_SPELL_SACK to 3_000,
        Items.BLIGHTED_VENGEANCE_SACK to 3_000,
        Items.BLIGHTED_ANCIENT_ICE_SACK to 2_500,
        // Breach tools (OSRS prices; Revenant ether ADAPTED).
        Items.REVENANT_ETHER to 250,
        Items.MAGIC_SHORTBOW_SCROLL to 100_000,
        Items.ORNATE_MAUL_HANDLE to 1_250_000,
        Items.TROUVER_PARCHMENT to 250_000,
        Items.ANCIENT_SCEPTRE to 1_000_000,
        // Wilderness weapons, uncharged base only (ADAPTED): players charge them with ether themselves.
        Items.CRAWS_BOW_U to 2_500_000,
        Items.THAMMARONS_SCEPTRE_U to 2_500_000,
        Items.VIGGORAS_CHAINMACE_U to 2_500_000,
        // Wilderness upgrade parts (ADAPTED): used on the uncharged base for Webweaver bow / Accursed sceptre / Ursine chainmace.
        Items.FANGS_OF_VENENATIS to 5_000_000,
        Items.SKULL_OF_VETION to 5_000_000,
        Items.CLAWS_OF_CALLISTO to 5_000_000,
        // Breach prestige: the OSRS halos imported for this shop (OsrsItemImportTool batch halos, tx-20260923-200943): Armadyl,
        // Bandos, Seren, Ancient, Brassica (OSRS price).
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

// Posted in the Royal Hall's PvP corner by data/world_edits.json (GeHomeHall: one source per npc).

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

// Owner 2026-09-30: no "Skull" option on this npc (cache option slot 5 cleared with NpcOptionsTool; PK skulls still come from
// attacking and the other skull sources - PvpSkull is unchanged).
