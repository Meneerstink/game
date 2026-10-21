package gg.rsmod.plugins.content.mechanics.exchange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * N35 (owner VRAGEN 9 B): a Crystal shard costs the Enhanced crystal teleport seed price / 150. Fixed books only: the live book
 * changes with the daily price sync. The withdrawn formula `max(1, (hourly ?: guide ?: 0) / 150)` sold shards for 1 coin on a
 * missing, zero or negative seed price and let an invalid hourly value block a valid guide price (T3-T9 pin both).
 */
class CrystalShardPriceTests {
    private val seed = CrystalShardPrice.SEED_OSRS_ID

    private fun book(
        hourly: Int? = null,
        guide: Int? = null,
        names: Map<Int, String> = mapOf(seed to "Enhanced crystal teleport seed"),
    ) = OsrsPriceBook(
        hourly = if (hourly == null) emptyMap() else mapOf(seed to hourly),
        idMap = emptyMap(),
        osrsNames = names,
        guide = OsrsGuidePrices.Table(if (guide == null) emptyMap() else mapOf("Enhanced crystal teleport seed" to guide), "fixture"),
    )

    @Test
    fun `T1 a valid hourly seed price wins over the guide`() {
        assertEquals(20_000, CrystalShardPrice.of(book(hourly = 3_000_000, guide = 1_500_000)))
    }

    @Test
    fun `T2 a missing hourly price falls back to the guide`() {
        assertEquals(20_209, CrystalShardPrice.of(book(guide = 3_031_395)))
    }

    @Test
    fun `T3 a zero hourly price does not block the guide`() {
        assertEquals(20_209, CrystalShardPrice.of(book(hourly = 0, guide = 3_031_395)))
    }

    @Test
    fun `T4 a negative hourly price does not block the guide`() {
        assertEquals(20_209, CrystalShardPrice.of(book(hourly = -5, guide = 3_031_395)))
    }

    @Test
    fun `T5 a positive hourly price under 150 floors to 0 and does not block the guide`() {
        assertEquals(20_209, CrystalShardPrice.of(book(hourly = 100, guide = 3_031_395)))
    }

    @Test
    fun `T6 to T9 no valid seed price at all means no price, never 1 coin and never a default`() {
        assertNull(CrystalShardPrice.of(book()), "T6 both missing")
        assertNull(CrystalShardPrice.of(book(hourly = 0, guide = 0)), "T7 both zero")
        assertNull(CrystalShardPrice.of(book(hourly = -1, guide = -1)), "T8 both negative")
        assertNull(CrystalShardPrice.of(book(hourly = 149, guide = 149)), "T9 both under 150")
        assertNull(CrystalShardPrice.of(OsrsPriceBook(emptyMap(), emptyMap(), emptyMap(), OsrsGuidePrices.Table(emptyMap(), ""))), "empty book")
    }

    @Test
    fun `T10 the guide is found by the OSRS name map and by the literal seed name`() {
        assertEquals(20_209, CrystalShardPrice.of(book(guide = 3_031_395, names = mapOf(seed to "Enhanced crystal teleport seed"))))
        assertEquals(20_209, CrystalShardPrice.of(book(guide = 3_031_395, names = emptyMap())), "no osrsNames entry: literal name")
    }

    @Test
    fun `the price is the integer floor of seed over 150 and exactly 150 gives 1`() {
        assertEquals(1, CrystalShardPrice.of(book(hourly = 150)))
        assertEquals(1, CrystalShardPrice.of(book(hourly = 299)))
        assertEquals(2, CrystalShardPrice.of(book(hourly = 300)))
        assertEquals(21_155, CrystalShardPrice.of(book(hourly = 3_173_253)), "the checked-in hourly 3,173,253 / 150")
    }

    @Test
    fun `the checked-in data prices a shard from the seed, not from the cache value 10000`() {
        val price = CrystalShardPrice.of(OsrsPriceBook.checkedIn())
        // Positive and in the range of seed price / 150 (about 20k); the daily sync moves the exact number, so no equality here.
        kotlin.test.assertTrue(price != null && price in 5_000..100_000 && price != 10_000, "checked-in shard price was $price")
    }
}
