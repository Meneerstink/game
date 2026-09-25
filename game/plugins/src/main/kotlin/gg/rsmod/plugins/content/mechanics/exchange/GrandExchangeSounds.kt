package gg.rsmod.plugins.content.mechanics.exchange

/**
 * Grand Exchange sounds.
 *
 * Ids and names: 2009scape `org.rs09.consts.Sounds` (GE_TRADE_ERROR_4039 .. GE_DOWN_AMOUNT_4045) and its GE handlers
 * (`StockMarket.confirmOffer`, collect, `GrandExchangeInterface` sets, `GrandExchangeTimer` jingle 284). Every id
 * exists in the pristine revision-667 cache (synth index 4, jingle index 11). The +/- mapping of UP/DOWN_AMOUNT follows
 * their names; 2009scape itself leaves the quantity/price buttons to its client scripts.
 */
object GrandExchangeSounds {
    const val TRADE_ERROR = 4039
    const val COLLECT_ITEMS = 4040
    const val UP_AMOUNT = 4041
    const val COLLECT_COINS = 4042
    const val PLACE_ITEM = 4043
    const val TRADE_OK = 4044
    const val DOWN_AMOUNT = 4045

    /** Plays when offers were updated (filled) and at login when something waits in the collection box. */
    const val OFFER_UPDATED_JINGLE = 284

    /** UP_AMOUNT when [after] is above [before], DOWN_AMOUNT when below, nothing when unchanged. */
    fun amountChange(
        before: Int,
        after: Int,
    ): Int? =
        when {
            after > before -> UP_AMOUNT
            after < before -> DOWN_AMOUNT
            else -> null
        }
}
