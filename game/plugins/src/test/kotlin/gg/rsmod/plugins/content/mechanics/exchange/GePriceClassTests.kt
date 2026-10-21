package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import com.google.gson.JsonParser
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import java.io.File
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A14 (owner 2026-10-10: "all grand exchange prices need to be exactly osrs grand exchange prices why is karils top only 50k"): the
 * WHOLE class "an item is worth the OSRS hourly average of its OSRS item id", not only Karil's top. The expectation is computed here from
 * the raw `/1h` fallback file with its own copy of the formula (volume-weighted mean of the average high and low price), and the OSRS id of
 * each 667 item comes from the sourced id map (roster / same id and name / same id, inventory model and cache value;
 * `osrs-item-id-map-audit.tsv`). Runs against the real 667 cache.
 */
class GePriceClassTests {
    private var library: CacheLibrary? = null

    @AfterTest
    fun close() {
        library?.close()
    }

    private fun data(name: String) = Paths.get("..", "..", "data", "cfg", "ge", name).toFile()

    /** Independent truth: OSRS id -> hourly average, straight from the checked-in raw `/1h` answer. */
    private fun hourlyTruth(): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for ((id, v) in JsonParser().parse(data("osrs-hourly-fallback.json").readText()).asJsonObject.getAsJsonObject("data").entrySet()) {
            val e = v.asJsonObject

            fun num(n: String) = e.get(n)?.takeIf { !it.isJsonNull }?.asDouble
            val high = num("avgHighPrice")
            val low = num("avgLowPrice")
            val hv = num("highPriceVolume") ?: 0.0
            val lv = num("lowPriceVolume") ?: 0.0
            val price =
                when {
                    high != null && low != null -> if (hv + lv > 0) (high * hv + low * lv) / (hv + lv) else (high + low) / 2
                    high != null -> high
                    low != null -> low
                    else -> continue
                }
            out[id.toInt()] = Math.round(price).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }
        // items without a trade in the last hour: the mean of their latest high and low trade
        val latest = JsonParser().parse(data("osrs-hourly-fallback.json").readText()).asJsonObject.getAsJsonObject("latest")
        for ((id, v) in latest?.entrySet() ?: emptySet()) {
            val e = v.asJsonObject
            val high = e.get("high")?.takeIf { !it.isJsonNull }?.asDouble
            val low = e.get("low")?.takeIf { !it.isJsonNull }?.asDouble
            val price = if (high != null && low != null) (high + low) / 2 else high ?: low ?: continue
            out.putIfAbsent(id.toInt(), Math.round(price).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
        }
        return out
    }

    private fun idMap(): Map<Int, Int> =
        JsonParser().parse(data("osrs-item-id-map.json").readText()).asJsonObject.entrySet().associate { it.key.toInt() to it.value.asInt }

    private fun definitions(): DefinitionSet {
        val lib = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()).also { library = it }
        val definitions = DefinitionSet()
        definitions.load(lib, ItemDef::class.java)
        // The server applies `tradeable` from items.yml at boot (same loader as GrandExchangeInterfaceTests).
        var id = -1
        Paths.get("..", "..", "data", "cfg", "items.yml").toFile().forEachLine { line ->
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                line.startsWith("  tradeable: ") && id >= 0 ->
                    definitions.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
            }
        }
        return definitions
    }

    @Test
    fun `Karils top, Ahrims robe top and the other renamed classic items are worth their OSRS hourly price`() {
        val definitions = definitions()
        val truth = hourlyTruth()
        // 667 id (= OSRS id for these classic items) and the name 667 gives it; the OSRS names are Karil's leathertop, Ahrim's robetop, ...
        val examples =
            mapOf(
                4736 to "Karil's top",
                4738 to "Karil's skirt",
                4712 to "Ahrim's robe top",
                4714 to "Ahrim's robe skirt",
                1033 to "Zamorak robe",
                1035 to "Zamorak robe",
                1683 to "Dragonstone ammy",
                3000 to "Clean snapdragon",
                4151 to "Abyssal whip",
            )
        examples.forEach { (id, name) ->
            val def = definitions.get(ItemDef::class.java, id)
            assertEquals(name, def.name, "the 667 name of $id")
            val expected = truth[id] ?: error("no OSRS hourly price for $id in the fixture")
            assertEquals(expected, OsrsGuidePrices.seed(def), "$id $name must be worth the OSRS hourly average, not the cache value ${def.cost}")
        }
        // The owner's item: ~50k cache value against ~800k on the GE.
        assertTrue(OsrsGuidePrices.seed(definitions.get(ItemDef::class.java, 4736)) > 500_000, "Karil's top")
    }

    @Test
    fun `imported OSRS items are priced through their upstream OSRS id`() {
        val definitions = definitions()
        val truth = hourlyTruth()
        // OSRS_IMPORT_MASTER.yml roster: local id -> OSRS id
        mapOf(22326 to 20997, 22929 to 21006).forEach { (local, osrs) ->
            val def = definitions.get(ItemDef::class.java, local)
            assertEquals(truth[osrs], OsrsGuidePrices.seed(def), "${def.id} ${def.name} = OSRS $osrs")
        }
        // Berserker ring (i) is untradeable in OSRS (11773 has no GE price): no OSRS price -> cache value (owner rule).
        val imbued = definitions.get(ItemDef::class.java, 15220)
        assertEquals(null, truth[11773], "OSRS has no price for the untradeable Berserker ring (i)")
        assertEquals(imbued.cost, OsrsGuidePrices.seed(imbued), "Berserker ring (i) keeps its cache value")
    }

    @Test
    fun `a noted item is worth its unnoted item and a 667-only item keeps its cache value`() {
        val definitions = definitions()
        val karils = definitions.get(ItemDef::class.java, 4736)
        assertTrue(karils.noteLinkId > 0)
        val unit = PriceChecker.value(definitions, Item(4736, 1))
        assertTrue(unit > 500_000, "one Karil's top is worth its OSRS price on the price checker")
        assertEquals(unit * 2, PriceChecker.value(definitions, Item(karils.noteLinkId, 2)), "two noted Karil's tops")
        @Suppress("UNCHECKED_CAST")
        val all = (definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>).values
        val pouch = all.first { it.name == "Steel titan pouch" && !it.noted }
        assertEquals(pouch.cost, OsrsGuidePrices.seed(pouch), "a summoning pouch is not an OSRS item: cache value")
    }

    @Test
    fun `every exchangeable item with an OSRS id is worth the OSRS hourly average of that id`() {
        val definitions = definitions()
        val truth = hourlyTruth()
        val map = idMap()

        @Suppress("UNCHECKED_CAST")
        val items = (definitions.getAll(ItemDef::class.java) as Map<Int, ItemDef>).values.filter { GrandExchangeInterface.exchangeable(it) }
        val wrong = mutableListOf<String>()
        var checked = 0
        items.sortedBy { it.id }.forEach { def ->
            val expected = map[def.id]?.let { truth[it] } ?: return@forEach
            checked++
            val got = OsrsGuidePrices.seed(def)
            if (got != expected) wrong += "${def.id} ${def.name}: got $got, OSRS ${map[def.id]} = $expected (cache value ${def.cost})"
        }
        File("build").mkdirs()
        File("build/a14-wrong-prices.txt").writeText(wrong.joinToString("\n"))
        println("A14_GE_PRICES exchangeable=${items.size} idMapped+priced=$checked wrong=${wrong.size}")
        assertTrue(checked > 2500, "the id map must cover thousands of exchangeable items (checked $checked)")
        assertTrue(wrong.isEmpty(), "${wrong.size} items are not worth their OSRS price, e.g. ${wrong.take(8)} (build/a14-wrong-prices.txt)")
    }
}
