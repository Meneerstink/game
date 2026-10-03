package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices

/**
 * Pikkupstix's familiar pouch and scroll shops (owner 2026-10-03, bug 210035: "please let pikkupstix sell all summoning familiar
 * pouches and his scrolls").
 *
 * The stock is every pouch of [SummoningPouchData] and every scroll of [SummoningScrollData] - the familiars this server can
 * actually summon and the special moves it knows - in level order, so a new familiar row is stocked without touching the shop.
 * 78 pouches and 67 scrolls do not fit one shop window (interface 620 draws at most [Shop.CLIENT_VISIBLE_SLOTS] rows), so each
 * list is split into the fewest equal shops that fit ([pages]); Pikkupstix's Trade asks which one to open.
 *
 * Not stocked: pouches the 667 cache has but this server has no familiar for (Meerkats 19622, Ghast 21444), the Wolf Whistle
 * quest's own Spirit wolf pouch 12526 and the Daemonheim-only Dungeoneering pouches 17935..17994.
 *
 * Price: [OsrsGuidePrices.seed] (pouches and scrolls are not OSRS items, so the 667 cache value), raised to the high-alchemy
 * value by the coin currency - buying and alching never prints coins.
 */
object SummoningShopStock {
    const val POUCH_STOCK = 1_000
    const val SCROLL_STOCK = 10_000

    /** One shop page: its title, the dialogue line that opens it and its rows (item id to base stock). */
    data class Page(val title: String, val option: String, val stock: List<Pair<Int, Int>>)

    /** Every summonable pouch, lowest level first. */
    val pouches: List<Pair<Int, Int>> by lazy {
        SummoningPouchData.values.sortedWith(compareBy({ it.level }, { it.pouch })).map { it.pouch }.distinct().map { it to levelOfPouch(it) }
    }

    /** Every special-move scroll, lowest level first. */
    val scrolls: List<Pair<Int, Int>> by lazy {
        SummoningScrollData.values.sortedWith(compareBy({ it.level }, { it.scroll })).map { it.scroll }.distinct().map { it to levelOfScroll(it) }
    }

    private fun levelOfPouch(pouch: Int) = SummoningPouchData.values.filter { it.pouch == pouch }.minOf { it.level }

    private fun levelOfScroll(scroll: Int) = SummoningScrollData.values.filter { it.scroll == scroll }.minOf { it.level }

    /** The shop pages, pouches first. */
    val pages: List<Page> by lazy { split("Familiar pouches", "Pikkupstix's Pouches", pouches, POUCH_STOCK) + split("Summoning scrolls", "Pikkupstix's Scrolls", scrolls, SCROLL_STOCK) }

    /**
     * [items] (id to summoning level) in the fewest pages of at most [Shop.CLIENT_VISIBLE_SLOTS] rows, as equal as possible, each
     * named after its level range.
     */
    private fun split(
        label: String,
        titleBase: String,
        items: List<Pair<Int, Int>>,
        stock: Int,
    ): List<Page> {
        if (items.isEmpty()) return emptyList()
        val count = (items.size + Shop.CLIENT_VISIBLE_SLOTS - 1) / Shop.CLIENT_VISIBLE_SLOTS
        val size = (items.size + count - 1) / count
        return items.chunked(size).map { chunk ->
            val range = "levels ${chunk.first().second}-${chunk.last().second}"
            Page("$titleBase ($range)", "$label ($range).", chunk.map { it.first to stock })
        }
    }

    /** Price of one [item]: its OSRS guide price or cache value (0 for an unknown id). */
    fun price(
        definitions: DefinitionSet,
        item: Int,
    ): Int = definitions.getNullable(ItemDef::class.java, item)?.let { OsrsGuidePrices.seed(it) } ?: 0
}
