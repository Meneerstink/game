package gg.rsmod.plugins.content.mechanics.exchange

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.content.mechanics.trading.impl.TradeItems
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A14: a worn Barrows state is a separate, unpriced item, not an identity alias of its intact form. */
class WornBarrowsGePriceClassTests {
    @Test
    fun `all 96 worn Barrows states use their own cache cost on every consumer even with a saved trade index`() {
        val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toString())
        try {
            val defs = DefinitionSet().also { it.load(cache, ItemDef::class.java) }
            val pattern = Regex("^(Ahrim|Dharok|Guthan|Karil|Torag|Verac)'s .+ (100|75|50|25)$")
            val worn = defs.getAllKeys(ItemDef::class.java).map { defs.get(ItemDef::class.java, it) }
                .filter { !it.noted && pattern.matches(it.name) }
            assertEquals(96, worn.size, "six sets, four pieces, four worn states, independently selected from cache names")
            val names = OsrsPriceBook.readNameMap(OsrsGuidePrices.dataFile("osrs-item-mapping.json"))
            val actual = OsrsGuidePrices.book
            val dir = Files.createTempDirectory("a14-worn-barrows").toFile()
            File(dir, "guide_index.json").writeText(worn.joinToString(",", "{", "}") {
                "\"${it.id}\":{\"price\":900000,\"samples\":[]}"
            })
            val exchange = GrandExchangeService(File(dir, "offers.json")).also { it.useDefinitions(defs); it.load() }
            val world = mockk<World>()
            every { world.definitions } returns defs
            every { world.getService(GrandExchangeService::class.java) } returns exchange
            val player = mockk<Player>()
            every { player.world } returns world
            val risk = GuidePriceValueProvider(world)
            val wrong = mutableListOf<String>()
            for (def in worn) {
                assertTrue(def.id !in names, "fixture confirms worn id ${def.id} has no OSRS exchange listing")
                val item = Item(def.id, 2)
                val expected = def.cost.toLong() * 2
                val checks = mapOf(
                    "seed" to (OsrsGuidePrices.seed(def).toLong() to def.cost.toLong()),
                    "GE" to (GePrice.unit(world, def.id).toLong() to def.cost.toLong()),
                    "checker" to (PriceChecker.value(player, item).toLong() to expected.coerceAtMost(Int.MAX_VALUE.toLong())),
                    "trade" to (TradeItems.value(item) { GePrice.unit(world, it) } to expected),
                    "death/risk" to (risk.getValue(def.id) to def.cost.toLong()),
                    "loot key" to (LootKeys.value(world, listOf(item)) to expected),
                )
                if (def.id in actual.idMap || OsrsGuidePrices.osrsPrice(def) != null) wrong += "${def.id} ${def.name}: incorrectly mapped/priced as OSRS ${actual.osrsIdOf(def)}"
                checks.forEach { (surface, values) ->
                    if (values.first != values.second) wrong += "${def.id} ${def.name} $surface: ${values.first}, cache-based ${values.second}"
                }
            }
            println("A14_WORN_BARROWS states=${worn.size} mismatches=${wrong.size}")
            assertTrue(wrong.isEmpty(), "${wrong.size} wrong valuations: ${wrong.take(12)}")
        } finally {
            cache.close()
        }
    }
}
