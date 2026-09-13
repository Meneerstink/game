package gg.rsmod.plugins.content.mechanics.exchange

/**
 * Side of a [GrandExchangeOffer].
 */
enum class OfferType {
    BUY,
    SELL,
}

enum class OfferStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

/**
 * A single buy/sell offer in the Grand Exchange order book.
 *
 * Coins/items owed to the offer's owner are escrowed on this object as
 * [collectableCoins] / [collectableItems] rather than paid out immediately,
 * so collection (`::ge_collect`) is a single, always-safe operation that can
 * be retried if the player's inventory is full - matching the confirmed
 * "no item duplication or silent item loss" economy requirement.
 *
 * @param username the player's lowercase login name (stable across
 * relogs/renames-of-display-name), used to look up a player's own offers.
 * @param pricePerItem the price offered per unit; for a resting order this is
 * also the price other offers can match against.
 * @param slot the offer box (0..5) the offer occupies on the player's Grand Exchange screen until it has been
 * finished and fully collected (RCV-010 C3).
 */
data class GrandExchangeOffer(
    val id: Long,
    val username: String,
    val type: OfferType,
    val itemId: Int,
    val pricePerItem: Int,
    val totalQuantity: Int,
    var quantityFilled: Int = 0,
    var status: OfferStatus = OfferStatus.ACTIVE,
    var collectableCoins: Long = 0,
    var collectableItems: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val slot: Int = 0,
) {
    val remaining: Int
        get() = totalQuantity - quantityFilled
}
