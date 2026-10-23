package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices

/**
 * The Quartermaster's stock (home PvP/PvM supplies, npc 11679 in the Grand Exchange home hall): ONE shop on one page (N222, owner
 * 2026-10-23: "alles moet op 1 pagina" - the window draws [Shop.CLIENT_VISIBLE_SLOTS] rows since ShopWindowScriptTool; it was 40,
 * which split the gear onto a second page).
 *
 * Owner 2026-10-03 (bug 205926, buglist E4/E5): "quartermaster needs to sell noted items" - a purchase of a consumable hands out
 * its banknote (the cache note of every item that has one, so a 500-buy takes one slot). N220 (owner 2026-10-23: "items worden
 * noted getoond"): the window shows the item itself, like every OSRS shop ([gg.rsmod.game.model.shop.ShopItem.deliverNoted]).
 * The Prayer potion is no longer stocked (owner decision; Super restore stays).
 *
 * Rows were checked against this cache when the shop was first built (food in `Food`, potions in `Potion`, combination runes
 * through the spellbooks, blighted sacks in `BlightedSacks`, the house tab in `player_house.plugin.kts`, the burning amulet,
 * glory and rune pouch plugins, Mithril/Adamant seeds in `flower_seeds.plugin.kts`, moonlight antler bolts in `CrossbowType`).
 * Owner 2026-09-30 refined the list: rune/dragon arrows only, no plain runes, no shark/summer pie/tuna potato, Bastion and
 * Battlemage as the other live high-level potions. Prices: the Grand Exchange's OSRS guide/mid-price table
 * ([OsrsGuidePrices.seed]) of the unnoted item, so a note costs exactly what the item costs.
 */
object QuartermasterSupplies {
    /**
     * Owner 2026-10-04 02:40 (bug 024055: "now there is no shop just a chat interface i did not ask for this"): Trade opens ONE
     * shop directly again - no "which supplies?" dialogue. Owner's choice the same night: the consumables as banknotes plus the
     * runes, sacks, arrows and one bolt type ([droppedForWindow] lists what made way for the old 40-row window), then the gear.
     */
    const val SHOP = "Home PK Supplies"

    /** N62 (owner 2026-10-22): 10,000 of every row. */
    const val ROW_STOCK = 10_000

    /**
     * Gear rows (owner 2026-10-22, a second page until N222 put them on the one page): N185 the Recipe for Disaster gloves Barrows / Adamant / Mithril (667 ids 7462 / 7459 / 7458,
     * all named "Gloves" in this cache), N186 the god capes (the imbued ones stay the Mage Arena II reward), N200 Ava's accumulator
     * and attractor. Untradeable in OSRS, so they cost their cache value (GeCoinCurrency falls back to it).
     */
    val gear: List<Pair<Int, Int>> =
        listOf(
            Items.GLOVES_7462 to ROW_STOCK, Items.GLOVES_7459 to ROW_STOCK, Items.GLOVES_7458 to ROW_STOCK,
            Items.SARADOMIN_CAPE to ROW_STOCK, Items.GUTHIX_CAPE to ROW_STOCK, Items.ZAMORAK_CAPE to ROW_STOCK,
            Items.AVAS_ACCUMULATOR to ROW_STOCK, Items.AVAS_ATTRACTOR to ROW_STOCK,
        )

    /** Food and potions (4 doses): every one of these has a banknote in this cache, so the noted shop sells only notes of them. */
    val foodAndPotions: List<Pair<Int, Int>> =
        listOf(
            // High-tier and combo food (owner 2026-09-30: no shark, summer pie or tuna potato).
            Items.MANTA_RAY to ROW_STOCK, Items.SEA_TURTLE to ROW_STOCK, Items.ANGLERFISH to ROW_STOCK, Items.DARK_CRAB to ROW_STOCK,
            // Halibut is back (owner 2026-10-11, bug 030542); the Stamina potion made way for it in the 40-row window.
            Items.HALIBUT to ROW_STOCK, Items.MARLIN to ROW_STOCK, Items.COOKED_KARAMBWAN to ROW_STOCK,
            // Potions (4 doses). Owner 2026-10-03 (E5): no Prayer potion. Owner 10-16 (N68): no Super attack / strength / defence.
            Items.SARADOMIN_BREW_4 to ROW_STOCK, Items.SUPER_RESTORE_4 to ROW_STOCK, Items.SUPER_COMBAT_POTION_4 to ROW_STOCK,
            Items.RANGING_POTION_4 to ROW_STOCK, Items.MAGIC_POTION_4 to ROW_STOCK,
            Items.BASTION_POTION_4 to ROW_STOCK, Items.BATTLEMAGE_POTION_4 to ROW_STOCK,
            Items.SANFEW_SERUM_4 to ROW_STOCK, Items.GUTHIX_REST_4 to ROW_STOCK,
            Items.EXTENDED_ANTIFIRE_4 to ROW_STOCK, Items.ANTI_VENOM_PLUS_4 to ROW_STOCK,
        )

    /**
     * Teleports and utility. Sold noted where the cache has a note (glory(6) 24123 - an OSRS import with
     * its note, owner A31 night run 2026-10-12). The rest has no banknote in this cache
     * (the OSRS-imported burning amulet 23849..23853 was imported without one: consecutive ids, no note def links to it;
     * likewise the house tab, rune pouch and seeds), so the noted shop hands those out as themselves - a boot PROBLEM line
     * names every non-stacking one, and QuartermasterSuppliesTests proves no note exists for each.
     */
    val utility: List<Pair<Int, Int>> =
        listOf(
            Items.TELEPORT_TO_HOUSE to ROW_STOCK, Items.BURNING_AMULET_5 to ROW_STOCK, Items.AMULET_OF_GLORY_6 to ROW_STOCK,
            // N190 (owner 2026-10-22): a Rune pouch note at its OSRS GE price; a banker or GE clerk turns it into a rune pouch.
            Items.RUNE_POUCH_NOTE to ROW_STOCK, Items.MITHRIL_SEEDS to ROW_STOCK, Items.ADAMANT_SEEDS to ROW_STOCK,
        )

    /** Food, potions, teleports and utility - the rows that are sold both unnoted and noted. */
    val consumables: List<Pair<Int, Int>> = (foodAndPotions + utility).distinctBy { it.first }

    /** Runes, spell sacks, arrows and bolts: all stackable already, so one shop serves both needs. */
    val ammunition: List<Pair<Int, Int>> =
        listOf(
            // Combination runes only (owner 2026-09-30: no plain runes); the Wilderness spells come from the blighted sacks.
            Items.MIST_RUNE to ROW_STOCK, Items.DUST_RUNE to ROW_STOCK, Items.MUD_RUNE to ROW_STOCK, Items.SMOKE_RUNE to ROW_STOCK,
            Items.STEAM_RUNE to ROW_STOCK, Items.LAVA_RUNE to ROW_STOCK,
            // Owner 10-16 (N63): Blood, Wrath and Soul runes too (they took the window rows of the three super potions, N68).
            Items.BLOOD_RUNE to ROW_STOCK, Items.WRATH_RUNE to ROW_STOCK, Items.SOUL_RUNE to ROW_STOCK,
            // Blighted spell sacks.
            Items.BLIGHTED_ANCIENT_ICE_SACK to ROW_STOCK, Items.BLIGHTED_ENTANGLE_SACK to ROW_STOCK,
            Items.BLIGHTED_TELEPORT_SPELL_SACK to ROW_STOCK, Items.BLIGHTED_VENGEANCE_SACK to ROW_STOCK,
            // Arrows: dragon only (owner 2026-10-22, N192: "remove rune arrow").
            Items.DRAGON_ARROW to ROW_STOCK,
            // Crossbow ammunition (owner 2026-10-04: "one shop, noted + top 40"; N192 2026-10-22: Opal dragon bolts (e) too).
            Items.RUBY_DRAGON_BOLTS_E to ROW_STOCK, Items.OPAL_DRAGON_BOLTS_E to ROW_STOCK,
        ).distinctBy { it.first }

    /**
     * Rows dropped so the single shop fits the 40-row window (2026-10-04; Stamina potion for Halibut 2026-10-11). Glory(4) made way
     * for glory(6) (owner A31, night run 2026-10-12): the window was full, (6) is the same amulet with two more charges and costs
     * less on the OSRS GE (10,139 against 11,807); glory(4) stays on the GE and is what a rubbed (6) becomes after two teleports.
     */
    val droppedForWindow: List<Int> =
        listOf(
            Items.STAMINA_POTION_4,
            Items.AMULET_OF_GLORY_4,
            Items.RUNITE_BOLTS, Items.DIAMOND_BOLTS_E, Items.RUBY_BOLTS_E, Items.DRAGON_BOLTS_E, Items.ONYX_BOLTS_E,
            Items.DRAGON_BOLTS, Items.DIAMOND_DRAGON_BOLTS_E, Items.DRAGONSTONE_DRAGON_BOLTS_E,
            Items.MOONLIGHT_ANTLER_BOLTS,
        )

    /** The one shop's stock, in window order: consumables (handed out noted where a note exists), the stackables, the gear. */
    val stock: List<Pair<Int, Int>> = (consumables + ammunition + gear).distinctBy { it.first }

    /** The rows a purchase hands out as banknotes: the consumables. */
    val deliveredNoted: Set<Int> = consumables.map { it.first }.toSet()

    /**
     * One stocked row: the item the window shows ([item], never a note), its stock, the price of one, and the id a purchase hands
     * out ([delivered]: the banknote for a consumable that has one, otherwise [item]).
     */
    data class Row(val item: Int, val amount: Int, val price: Int, val delivered: Int)

    /** The rows of [stock] whose item exists unnoted in this cache. */
    fun rows(
        definitions: DefinitionSet,
        stock: List<Pair<Int, Int>> = this.stock,
    ): List<Row> =
        stock.mapNotNull { (itemId, amount) ->
            val def = definitions.getNullable(ItemDef::class.java, itemId) ?: return@mapNotNull null
            if (def.name.isBlank() || def.noted) return@mapNotNull null
            Row(itemId, amount, OsrsGuidePrices.seed(def), if (itemId in deliveredNoted) notedId(definitions, def) else itemId)
        }

    /** The banknote of [def] when it has one and does not stack by itself; otherwise the item itself. */
    fun notedId(
        definitions: DefinitionSet,
        def: ItemDef,
    ): Int = gg.rsmod.plugins.content.mechanics.shops.ItemCurrency.noteOf(definitions, def)

    /**
     * Restock per resupply tick (every [Shop.DEFAULT_RESUPPLY_CYCLES] cycles): 1% of the base stock, at least 1, so a bulk buy of
     * noted supplies refills in about 25 minutes instead of hours at the default 1 per tick.
     */
    fun resupplyAmount(amount: Int): Int = maxOf(Shop.DEFAULT_RESUPPLY_AMOUNT, amount / 100)
}
