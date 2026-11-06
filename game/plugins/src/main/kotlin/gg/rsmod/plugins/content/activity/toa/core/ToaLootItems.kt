package gg.rsmod.plugins.content.activity.toa.core

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.activity.toa.ToaIds

/**
 * OSRS reward item -> our item. Earlier OSRS imports keep their own local ids (GPT-A 20-item-manifest.tsv "bij-ons" column / Items.kt);
 * the ToA imports come from data/cfg/toa/ids.tsv (ToaIds); the old items every revision shares keep their number. Null = not imported yet
 * (the chest then leaves that line out and logs it - never a guessed id).
 */
object ToaLootItems {
    private val EARLIER_IMPORTS = mapOf(
        26219 to Items.OSMUMTENS_FANG,
        25975 to Items.LIGHTBEARER,
        25985 to 22933, // Elidinis' ward (20-item-manifest.tsv)
        27226 to Items.MASORI_MASK,
        27229 to Items.MASORI_BODY,
        27232 to Items.MASORI_CHAPS,
        27279 to 23748, // Thread of Elidinis
        27352 to 24022, // Tumeken's guardian
        27372 to 23067, // Masori crafting kit (20-item-manifest.tsv)
        27255 to 22937, // Menaphite ornament kit
        12073 to Items.CLUE_SCROLL_ELITE, // OSRS Clue scroll (elite) -> our elite clue
    )

    /** Highest item id of the revision-667 cache that OSRS shares unchanged (coins 995 ... dragon dart tip 11232, seeds, gems). */
    private const val SHARED_BELOW = 14_000

    fun local(osrsItem: Int): Int? =
        EARLIER_IMPORTS[osrsItem] ?: ToaIds.item(osrsItem) ?: osrsItem.takeIf { it < SHARED_BELOW }
}
