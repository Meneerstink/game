package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices

/**
 * The Quartermaster's stock (home PvP/PvM supplies, npc 11679 in the Grand Exchange home hall), split over shops that each fit
 * the client's shop window.
 *
 * Shop window limit: interface 620 draws inv 4, whose cache size is 40 (config 2/5 file 4 = `02 0028`), and its build script
 * 2716/2723 loops `INV_SIZE(4)` with exactly 40 price varcs (946..985). Every stock row past index 39 is never drawn: the old
 * single 52-row shop hid all eleven bolt rows (bug run 2026-10-03). So every shop here holds at most [Shop.CLIENT_VISIBLE_SLOTS].
 *
 * Owner 2026-10-03 (bug 205926, buglist E4/E5): "quartermaster needs to sell noted items" - the consumables are also sold as
 * banknotes ([CONSUMABLES_NOTED]: same rows, same prices, the cache note of every item that has one, so a 500-buy takes one
 * slot) - and the Prayer potion is no longer stocked (owner decision; Super restore stays).
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
     * runes, sacks, arrows and one bolt type, exactly the 40 rows the window draws ([droppedForWindow] lists what made way).
     */
    const val SHOP = "Home PK Supplies"

    /** Food and potions (4 doses): every one of these has a banknote in this cache, so the noted shop sells only notes of them. */
    val foodAndPotions: List<Pair<Int, Int>> =
        listOf(
            // High-tier and combo food (owner 2026-09-30: no shark, summer pie or tuna potato).
            Items.MANTA_RAY to 1_000, Items.SEA_TURTLE to 1_000, Items.ANGLERFISH to 1_000, Items.DARK_CRAB to 1_000,
            // Halibut is back (owner 2026-10-11, bug 030542); the Stamina potion made way for it in the 40-row window.
            Items.HALIBUT to 1_000, Items.MARLIN to 1_000, Items.COOKED_KARAMBWAN to 2_000,
            // Potions (4 doses). Owner 2026-10-03 (E5): no Prayer potion. Owner 10-16 (N68): no Super attack / strength / defence.
            Items.SARADOMIN_BREW_4 to 1_000, Items.SUPER_RESTORE_4 to 1_000, Items.SUPER_COMBAT_POTION_4 to 500,
            Items.RANGING_POTION_4 to 500, Items.MAGIC_POTION_4 to 500,
            Items.BASTION_POTION_4 to 300, Items.BATTLEMAGE_POTION_4 to 300,
            Items.SANFEW_SERUM_4 to 300, Items.GUTHIX_REST_4 to 300,
            Items.EXTENDED_ANTIFIRE_4 to 300, Items.ANTI_VENOM_PLUS_4 to 300,
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
            Items.TELEPORT_TO_HOUSE to 1_000, Items.BURNING_AMULET_5 to 200, Items.AMULET_OF_GLORY_6 to 200,
            Items.RUNE_POUCH to 50, Items.MITHRIL_SEEDS to 1_000, Items.ADAMANT_SEEDS to 1_000,
        )

    /** Food, potions, teleports and utility - the rows that are sold both unnoted and noted. */
    val consumables: List<Pair<Int, Int>> = (foodAndPotions + utility).distinctBy { it.first }

    /** Runes, spell sacks, arrows and bolts: all stackable already, so one shop serves both needs. */
    val ammunition: List<Pair<Int, Int>> =
        listOf(
            // Combination runes only (owner 2026-09-30: no plain runes); the Wilderness spells come from the blighted sacks.
            Items.MIST_RUNE to 5_000, Items.DUST_RUNE to 5_000, Items.MUD_RUNE to 5_000, Items.SMOKE_RUNE to 5_000,
            Items.STEAM_RUNE to 5_000, Items.LAVA_RUNE to 5_000,
            // Owner 10-16 (N63): Blood, Wrath and Soul runes too (they took the window rows of the three super potions, N68).
            Items.BLOOD_RUNE to 5_000, Items.WRATH_RUNE to 5_000, Items.SOUL_RUNE to 5_000,
            // Blighted spell sacks.
            Items.BLIGHTED_ANCIENT_ICE_SACK to 5_000, Items.BLIGHTED_ENTANGLE_SACK to 5_000,
            Items.BLIGHTED_TELEPORT_SPELL_SACK to 2_000, Items.BLIGHTED_VENGEANCE_SACK to 5_000,
            // Arrows (owner 2026-09-30: rune and dragon only).
            Items.RUNE_ARROW to 2_000, Items.DRAGON_ARROW to 1_000,
            // Crossbow ammunition: the one bolt row that fits the window (owner 2026-10-04: "one shop, noted + top 40").
            Items.RUBY_DRAGON_BOLTS_E to 500,
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
            Items.DRAGON_BOLTS, Items.OPAL_DRAGON_BOLTS_E, Items.DIAMOND_DRAGON_BOLTS_E, Items.DRAGONSTONE_DRAGON_BOLTS_E,
            Items.MOONLIGHT_ANTLER_BOLTS,
        )

    /** The single shop's stock, in window order: consumables (sold noted where a note exists), then the stackables. */
    val stock: List<Pair<Int, Int>> = (consumables + ammunition).distinctBy { it.first }

    /** One stocked row: the id the shop hands out, its stock, and the price of one (the unnoted item's price). */
    data class Row(val item: Int, val amount: Int, val price: Int, val unnoted: Int)

    /** The rows of [stock] whose item exists unnoted in this cache. [noted]: hand out the cache note where the item has one. */
    fun rows(
        definitions: DefinitionSet,
        stock: List<Pair<Int, Int>>,
        noted: Boolean,
    ): List<Row> =
        stock.mapNotNull { (itemId, amount) ->
            val def = definitions.getNullable(ItemDef::class.java, itemId) ?: return@mapNotNull null
            if (def.name.isBlank() || def.noted) return@mapNotNull null
            Row(if (noted) notedId(definitions, def) else itemId, amount, OsrsGuidePrices.seed(def), itemId)
        }

    /** The banknote of [def] when it has one and does not stack by itself; otherwise the item itself. */
    fun notedId(
        definitions: DefinitionSet,
        def: ItemDef,
    ): Int {
        if (def.stacks || def.noteTemplateId != 0 || def.noteLinkId <= 0) return def.id
        val note = definitions.getNullable(ItemDef::class.java, def.noteLinkId) ?: return def.id
        return if (note.noted && note.noteLinkId == def.id) note.id else def.id
    }

    /**
     * Restock per resupply tick (every [Shop.DEFAULT_RESUPPLY_CYCLES] cycles): 1% of the base stock, at least 1, so a bulk buy of
     * noted supplies refills in about 25 minutes instead of hours at the default 1 per tick.
     */
    fun resupplyAmount(amount: Int): Int = maxOf(Shop.DEFAULT_RESUPPLY_AMOUNT, amount / 100)
}
