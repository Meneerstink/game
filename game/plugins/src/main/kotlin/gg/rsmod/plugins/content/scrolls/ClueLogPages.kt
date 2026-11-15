package gg.rsmod.plugins.content.scrolls

import gg.rsmod.plugins.content.quests.tab.CollectionLogPages

/** Seven complete source-enum pages; a missing import is an error, never a shorter log. */
object ClueLogPages {
    const val SHARED = "clue_shared"
    const val CASES = "clue_cases"

    fun build(catalogue: ClueLogCatalogue = ClueLogCatalogue.current, local: (Int) -> Int? = ClueItems::local): List<CollectionLogPages.Page> {
        val tiers = ClueScrollTier.values().map { tier ->
            CollectionLogPages.Page(tier.key, CollectionLogPages.Category.CLUES, "${tier.displayName} Treasure Trails", listOf(tier.key),
                catalogue.pages.getValue(tier.name.lowercase()).osrsItems.map { checkNotNull(local(it)) { "CLUE log item $it not imported" } },
                "${tier.displayName} clues completed")
        }
        return tiers + listOf(
            CollectionLogPages.Page(SHARED, CollectionLogPages.Category.CLUES, "Shared Treasure Trails", ClueScrollTier.values().map { it.key },
                catalogue.pages.getValue("shared").osrsItems.map { checkNotNull(local(it)) { "CLUE shared log item $it not imported" } }, "Clues completed"),
            CollectionLogPages.Page(CASES, CollectionLogPages.Category.CLUES, "Scroll Cases", ClueScrollTier.values().map { it.key },
                catalogue.pages.getValue("cases").osrsItems.map { checkNotNull(local(it)) { "CLUE case log item $it not imported" } }, "Clues completed"),
        )
    }
}
