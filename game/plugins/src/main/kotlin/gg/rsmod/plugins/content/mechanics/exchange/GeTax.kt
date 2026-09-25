package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef

/**
 * OSRS Grand Exchange convenience fee (OSRS Wiki "Grand Exchange", section "Convenience fee and item sink", checked
 * 2026-09-25): 2 % of every item sold, rounded down, capped at 5,000,000 coins per item. Below 50 coins the fee
 * rounds to nothing. The seller pays it - it comes off the coins the sale earns.
 *
 * The exempt list is the wiki's, matched on the item name. "Energy potion" and "Watering can" link to the whole item
 * page, so every dose / fill level is exempt; the games necklace and ring of dueling are exempt only when full (8).
 */
object GeTax {
    const val RATE_PERCENT = 2
    const val CAP_PER_ITEM = 5_000_000

    private val EXEMPT_NAMES =
        setOf(
            "old school bond",
            "bronze arrow", "bronze dart", "iron arrow", "iron dart", "mind rune", "steel arrow", "steel dart",
            "bass", "bread", "cake", "cooked chicken", "cooked meat", "herring", "lobster", "mackerel", "meat pie",
            "pike", "salmon", "shrimps", "tuna",
            "ardougne teleport", "camelot teleport", "civitas illa fortis teleport", "falador teleport",
            "games necklace(8)", "kourend castle teleport", "lumbridge teleport", "ring of dueling(8)",
            "teleport to house", "varrock teleport",
            "chisel", "gardening trowel", "glassblowing pipe", "hammer", "needle", "pestle and mortar", "rake", "saw",
            "secateurs", "seed dibber", "shears", "spade",
        )

    private val EXEMPT_FAMILIES = listOf(Regex("energy potion\\(\\d\\)"), Regex("watering can(\\(\\d\\))?"))

    fun exempt(name: String?): Boolean {
        val key = name?.trim()?.lowercase() ?: return false
        return key in EXEMPT_NAMES || EXEMPT_FAMILIES.any { it.matches(key) }
    }

    fun exempt(def: ItemDef?): Boolean = exempt(def?.name)

    /** The fee on one item sold at [unitPrice]. */
    fun perItem(unitPrice: Int): Int = minOf(unitPrice.toLong() * RATE_PERCENT / 100, CAP_PER_ITEM.toLong()).toInt()
}
