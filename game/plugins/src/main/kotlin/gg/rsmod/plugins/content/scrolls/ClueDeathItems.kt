package gg.rsmod.plugins.content.scrolls

/** Wiki Treasure Trails/Scroll box: unprotected clues and caskets go to the victim's grave, including deep Wilderness PvP. */
object ClueDeathItems {
    private val sourceBoxes = listOf(24362, 24363, 24364, 24365, 24366)
    fun contains(item: Int): Boolean = ClueOwnership.tier(item) != null || ClueScrollTier.values().any { it.casketId == item } ||
        (sourceBoxes + TreasureTrailTornParts.SOURCE_IDS).any { ClueItems.local(it) == item }
}
