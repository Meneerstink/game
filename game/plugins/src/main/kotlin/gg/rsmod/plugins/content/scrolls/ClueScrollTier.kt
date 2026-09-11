package gg.rsmod.plugins.content.scrolls

import gg.rsmod.plugins.api.cfg.Items

/**
 * The four Treasure Trail difficulty tiers, mapped to this project's real 667-cache
 * scroll/casket item ids (`Items.kt`). Additional scroll ids that share the same
 * cache graphic elsewhere in the id ladder are reward-table/quest-item variants,
 * not additional entry points into this system, and are intentionally not wired here.
 */
enum class ClueScrollTier(
    val scrollId: Int,
    val casketId: Int,
    /**
     * Percent chance (0-100) that completing a step of this tier finishes the trail
     * (casket) rather than handing out another scroll of the same tier ("another
     * clue" chain). Sourced verbatim from Novite's `ScrollType` enum
     * (EASY=50, MEDIUM=40, HARD=35, ELITE=20).
     */
    val completionChance: Int,
) {
    EASY(Items.CLUE_SCROLL_EASY, Items.CASKET_EASY, 50),
    MEDIUM(Items.CLUE_SCROLL_MEDIUM, Items.CASKET_MEDIUM, 40),
    HARD(Items.CLUE_SCROLL_HARD, Items.CASKET_HARD, 35),
    ELITE(Items.CLUE_SCROLL_ELITE, Items.CASKET_ELITE, 20),
}
