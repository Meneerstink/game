package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.plugins.api.cfg.Items

/**
 * CoX: OSRS reward item -> our item (same pattern as ToaLootItems). Earlier OSRS imports keep their own local ids (OSRS_IMPORT_MASTER.yml
 * upstream_item_id -> local_item_id, names checked 15-11 18:1x); the CoX item batch comes from data/cfg/cox/ids.tsv (CoxIds); the old
 * items every revision shares keep their number. Null = not imported yet (the chest/log then leaves it out - never a guessed id).
 */
object CoxLootItems {
    private val EARLIER_IMPORTS = mapOf(
        20851 to 23983, // Olmlet
        20997 to 22326, // Twisted bow
        21043 to 22927, // Kodai insignia
        21018 to 22876, // Ancestral hat
        21021 to 22878, // Ancestral robe top
        21024 to 22880, // Ancestral robe bottom
        21034 to 24127, // Dexterous prayer scroll
        21079 to 24128, // Arcane prayer scroll
        21012 to 22634, // Dragon hunter crossbow
        21000 to 22931, // Twisted buckler
        24670 to 22882, // Twisted ancestral colour kit
        13652 to Items.DRAGON_CLAWS, // OSRS Dragon claws = the rev-667 Dragon claws 14484 (same item)
        12073 to Items.CLUE_SCROLL_ELITE, // OSRS Clue scroll (elite) -> our elite clue
    )

    /** Highest item id of the revision-667 cache that OSRS shares unchanged (coins, runes, gems such as onyx 6573, seeds). */
    private const val SHARED_BELOW = 14_000

    fun local(osrsItem: Int): Int? =
        EARLIER_IMPORTS[osrsItem] ?: CoxIds.item(osrsItem) ?: osrsItem.takeIf { it < SHARED_BELOW }

    /**
     * The OSRS Collection log page 'Chambers of Xeric' in cache order: cache2686 enum 2139 (23 slots = OSRS Wiki 'The Chambers of Xeric
     * has 23 collection log items'; found 15-11 with work/claude-scripts/cox_enums.py, proven on the ToA enum 4805).
     */
    val LOG_ITEMS = listOf(20851, 22386, 20997, 21003, 21043, 13652, 21018, 21021, 21024, 21015, 21034, 21079, 21012, 21000, 21047, 21027,
        6573, 24670, 22388, 22390, 22392, 22394, 22396)
}
