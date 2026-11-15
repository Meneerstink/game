package gg.rsmod.plugins.content.scrolls

/** Wiki Treasure Trails/Scroll box: unprotected clues and caskets go to the victim's grave, including deep Wilderness PvP. */
object ClueDeathItems {
    private val sourceBoxes = listOf(24362, 24363, 24364, 24365, 24366)
    fun contains(item: Int): Boolean = ClueScrollTier.values().any { it.scrollId == item || it.casketId == item } ||
        (sourceBoxes + TreasureTrailTornParts.SOURCE_IDS).any { ClueItems.local(it) == item }
}
