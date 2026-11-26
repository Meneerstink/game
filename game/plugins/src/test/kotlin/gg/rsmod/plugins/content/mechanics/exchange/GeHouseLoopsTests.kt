package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TRIDENT-FULL-ARBITRAGE: no instant item conversion gains gold through the house. */
class GeHouseLoopsTests {
    @Test
    fun `trident of the seas full uncharges into the uncharged trident and 2500 charges of runes`() {
        val routes = GeHouseLoops.routesOf(Items.TRIDENT_OF_THE_SEAS_FULL)
        val uncharge = routes.single { it.breaks }
        assertEquals(
            mapOf(Items.UNCHARGED_TRIDENT to 1, Items.DEATH_RUNE to 2_500, Items.CHAOS_RUNE to 2_500, Items.FIRE_RUNE to 12_500),
            uncharge.parts,
        )
        val charge = routes.single { it.makes }
        assertEquals(25_000, charge.parts[Items.COINS_995])
    }

    @Test
    fun `the 25-11 trident round trip no longer pays`() {
        // Prices of the find (testserver 25-11 21:0x): the house sold the (full) trident for 812,384 gp and paid 848,615 gp
        // for its parts. Part prices chosen so they add up to that bid.
        val parts =
            mapOf(
                Items.UNCHARGED_TRIDENT to GeHouseQuote(70_000, 61_115),
                Items.DEATH_RUNE to GeHouseQuote(200, 200),
                Items.CHAOS_RUNE to GeHouseQuote(90, 90),
                Items.FIRE_RUNE to GeHouseQuote(5, 5),
            )
        val bounded = GeHouseLoops.bound(Items.TRIDENT_OF_THE_SEAS_FULL, GeHouseQuote(812_384, 812_384)) { parts[it] }
        assertEquals(848_615, bounded.ask)
        assertTrue(bounded.bid <= bounded.ask)
    }

    @Test
    fun `a make needing a part the house does not sell leaves the bid alone`() {
        val bounded = GeHouseLoops.bound(Items.TRIDENT_OF_THE_SEAS_FULL, GeHouseQuote(800_000, 800_000)) { null }
        assertEquals(800_000, bounded.bid)
        assertEquals(800_000, bounded.ask)
    }

    @Test
    fun `no conversion route gains anything at the real house prices`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(library, ItemDef::class.java)
            val base = HashMap<Int, GeHouseQuote?>()
            fun baseOf(id: Int): GeHouseQuote? =
                base.getOrPut(id) {
                    val def = definitions.getNullable(ItemDef::class.java, id) ?: return@getOrPut null
                    if (def.noted) return@getOrPut null
                    val seed = GePrice.seed(definitions, def)
                    if (seed <= 0) null else GeHousePricing.quote(seed, def.cost)
                }
            fun quoteOf(id: Int): GeHouseQuote? = if (id == Items.COINS_995) GeHouseQuote(1, 1) else baseOf(id)?.let { GeHouseLoops.bound(id, it, ::baseOf) }
            var checked = 0
            val gains = mutableListOf<String>()
            for (route in GeHouseLoops.ROUTES) {
                val whole = quoteOf(route.whole) ?: continue
                assertTrue(whole.bid in 0..whole.ask, "${route.whole}: bid ${whole.bid} ask ${whole.ask}")
                if (route.breaks) {
                    val partsBid = route.parts.entries.sumOf { (id, n) -> (quoteOf(id)?.bid ?: 0).toLong() * n }
                    if (partsBid > whole.ask) gains += "break ${route.whole}: ask ${whole.ask} < parts $partsBid"
                }
                if (route.makes) {
                    val asks = route.parts.entries.map { (id, n) -> quoteOf(id)?.ask?.toLong()?.times(n) }
                    if (asks.all { it != null }) {
                        val partsAsk = asks.sumOf { it!! }
                        if (whole.bid > partsAsk) gains += "make ${route.whole}: bid ${whole.bid} > parts $partsAsk"
                    }
                }
                checked++
            }
            println("GE_HOUSE_LOOPS routes checked: $checked of ${GeHouseLoops.ROUTES.size}")
            assertTrue(checked > 100, "only $checked routes had a house price")
            assertTrue(gains.isEmpty(), gains.joinToString("\n"))
        } finally {
            library.close()
        }
    }
}
