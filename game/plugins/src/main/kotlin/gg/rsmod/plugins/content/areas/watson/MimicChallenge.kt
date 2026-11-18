package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import gg.rsmod.plugins.content.scrolls.ClueItems

/** The Mimic Wiki: five elite/six master attempts, one roll lost per failed attempt. */
object MimicChallenge {
    fun tier(item: Item): ClueScrollTier? = if (item.id != ClueItems.local(23184)) null else ClueScrollTier.values().firstOrNull {
        it.osrsCasketId == item.getAttr(ItemAttribute.MIMIC_SOURCE_TIER) && (it == ClueScrollTier.ELITE || it == ClueScrollTier.MASTER)
    }

    fun create(local: Int, source: ClueScrollTier): Item {
        require(source == ClueScrollTier.ELITE || source == ClueScrollTier.MASTER)
        return Item(local).putAttr(ItemAttribute.MIMIC_SOURCE_TIER, source.osrsCasketId)
            .putAttr(ItemAttribute.MIMIC_FAILURES, 0)
    }

    fun defeated(item: Item): Boolean = item.getAttr(ItemAttribute.MIMIC_DEFEATED) == 1
    fun failures(item: Item): Int = (item.getAttr(ItemAttribute.MIMIC_FAILURES) ?: 0).coerceAtLeast(0)
    fun maximumAttempts(item: Item): Int = when (tier(item)) {
        ClueScrollTier.ELITE -> 5
        ClueScrollTier.MASTER -> 6
        else -> 0 // An untagged legacy/staff item has no proven source tier.
    }
    fun remaining(item: Item): Int = (maximumAttempts(item) - failures(item)).coerceAtLeast(0)
    fun active(item: Item): Boolean = (item.getAttr(ItemAttribute.MIMIC_ATTEMPT_SERIAL) ?: 0) >
        (item.getAttr(ItemAttribute.MIMIC_SETTLED_SERIAL) ?: 0)

    fun begin(item: Item): Int? {
        if (tier(item) == null || defeated(item) || remaining(item) == 0 || active(item)) return null
        val serial = item.getAttr(ItemAttribute.MIMIC_ATTEMPT_SERIAL) ?: 0
        if (serial == Int.MAX_VALUE) return null
        return (serial + 1).also { item.putAttr(ItemAttribute.MIMIC_ATTEMPT_SERIAL, it) }
    }

    /** Death/exit/logout can all fire. The exact attempt token settles once. */
    fun settle(item: Item, token: Int, won: Boolean): Boolean {
        if (tier(item) == null || defeated(item) || remaining(item) == 0 || token <= 0) return false
        if (item.getAttr(ItemAttribute.MIMIC_ATTEMPT_SERIAL) != token ||
            (item.getAttr(ItemAttribute.MIMIC_SETTLED_SERIAL) ?: 0) >= token) return false
        item.putAttr(ItemAttribute.MIMIC_SETTLED_SERIAL, token)
        if (won) item.putAttr(ItemAttribute.MIMIC_DEFEATED, 1)
        else item.putAttr(ItemAttribute.MIMIC_FAILURES, failures(item) + 1)
        return true
    }

    /** A saved outstanding token represents a disconnected/crashed attempt, never a free retry. */
    fun recover(item: Item): Boolean = if (active(item)) settle(item, item.getAttr(ItemAttribute.MIMIC_ATTEMPT_SERIAL)!!, won = false) else false

    fun stompDamage(consecutiveTicks: Int): Int {
        require(consecutiveTicks > 0)
        return ((consecutiveTicks - 1) / 3 + 1).coerceAtMost(6)
    }
}
