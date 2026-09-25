package gg.rsmod.plugins.content.mechanics.exchange

/**
 * One executed player-to-player trade feeding the guide price.
 *
 * [pair] names the two accounts ([GeGuidePrice.pairKey]); it is null only for prices carried over from the old
 * `guide_prices.json`, which did not record who traded.
 */
data class GeTradeSample(
    val price: Int = 0,
    val pair: String? = null,
    val atMs: Long = 0L,
)

/**
 * The guide price of one item as stored in `data/ge/guide_index.json`.
 *
 * [price] is 0 until enough independent trades exist ([GeGuidePrice.MIN_PAIRS]); callers then fall back to the OSRS
 * seed. [anchorPrice]/[anchorMs] are the price at the start of the current movement window.
 *
 * Every constructor parameter has a default so Gson builds it through the no-argument constructor (a missing field
 * then keeps its default instead of becoming null).
 */
data class GeGuideIndex(
    var price: Int = 0,
    var anchorPrice: Int = 0,
    var anchorMs: Long = 0L,
    var samples: MutableList<GeTradeSample> = mutableListOf(),
)

/**
 * Audit E-01: the guide price used to be the mean of the last 20 fills of any kind, each clamped to ±5 % of the current
 * guide. A player could fill against their own offers (or the house could fill them) at +5 % over and over and walk
 * the guide up for free, and the house then bought everything at that guide. The guide is now:
 *
 *  - fed only by trades between two *different* accounts (the book no longer matches an account against itself, and
 *    house fills never count);
 *  - the median of the last [SAMPLES] such trades, keeping only the latest trade per pair of accounts, so one pair
 *    trading back and forth is one sample and cannot outvote the market;
 *  - left at the OSRS seed until [MIN_PAIRS] distinct pairs have traded;
 *  - moved at most ±5 % ([GrandExchangeInterface.priceRange]) per [WINDOW_MS] of wall-clock time (the same clock as the
 *    buy-limit window).
 *
 * The house no longer trades at the guide at all ([GeHousePricing]), so the guide only drives the offer screen's
 * suggestion, the price checker and risk values.
 */
object GeGuidePrice {
    const val SAMPLES = 20
    const val MIN_PAIRS = 3
    const val WINDOW_MS = 60L * 60 * 1000

    /** Order-independent key for the two accounts of a trade. */
    fun pairKey(
        a: String,
        b: String,
    ): String = if (a <= b) "$a|$b" else "$b|$a"

    /** Median of [prices] (mean of the two middle values for an even count, computed in Long). */
    fun median(prices: List<Int>): Int {
        require(prices.isNotEmpty())
        val sorted = prices.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else ((sorted[mid - 1].toLong() + sorted[mid]) / 2).toInt()
    }

    /**
     * Adds [sample] to [index] and moves its guide. [seed] is the item's OSRS seed (null when unknown, e.g. in unit
     * tests without a cache): it is the starting point the first established guide may move away from.
     */
    fun record(
        index: GeGuideIndex,
        sample: GeTradeSample,
        seed: Int?,
        now: Long,
    ) {
        if (sample.pair != null) index.samples.removeAll { it.pair == sample.pair }
        index.samples.add(sample)
        while (index.samples.size > SAMPLES) index.samples.removeAt(0)
        if (index.samples.size < MIN_PAIRS) return

        val target = median(index.samples.map { it.price }).coerceAtLeast(1)
        val current = if (index.price > 0) index.price else seed?.coerceAtLeast(1)
        if (current == null) {
            // No seed to anchor on: the first established guide is the median itself.
            index.price = target
            index.anchorPrice = target
            index.anchorMs = now
            return
        }
        if (index.anchorPrice <= 0 || now - index.anchorMs >= WINDOW_MS || now < index.anchorMs) {
            index.anchorPrice = current
            index.anchorMs = now
        }
        val range = GrandExchangeInterface.priceRange(index.anchorPrice)
        index.price = target.coerceIn(range.first, range.last)
    }

    /** The guide of an item: its established price, otherwise [fallback] (the OSRS seed / cache value). */
    fun guide(
        index: GeGuideIndex?,
        fallback: Int,
    ): Int = (if (index == null || index.price <= 0) fallback else index.price).coerceAtLeast(1)

    /**
     * Old `guide_prices.json` (last 20 prices per item, no parties) -> index. The old prices may contain wash trades
     * (the E-01 exploit), so the migrated guide starts no further than ±5 % from the item's seed when one is known.
     */
    fun migrate(
        prices: List<Int>,
        seed: Int?,
        now: Long,
    ): GeGuideIndex {
        val index = GeGuideIndex(samples = prices.takeLast(SAMPLES).map { GeTradeSample(it, null, 0L) }.toMutableList())
        if (prices.isEmpty()) return index
        val target = median(prices.takeLast(SAMPLES)).coerceAtLeast(1)
        if (seed == null) {
            index.price = target
            index.anchorPrice = target
        } else {
            val range = GrandExchangeInterface.priceRange(seed.coerceAtLeast(1))
            index.price = target.coerceIn(range.first, range.last)
            index.anchorPrice = seed.coerceAtLeast(1)
        }
        index.anchorMs = now
        return index
    }
}
