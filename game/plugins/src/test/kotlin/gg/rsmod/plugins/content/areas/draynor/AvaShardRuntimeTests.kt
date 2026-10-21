package gg.rsmod.plugins.content.areas.draynor

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.areas.home.QuartermasterSupplies
import gg.rsmod.plugins.content.mechanics.exchange.CrystalShardPrice
import gg.rsmod.plugins.content.mechanics.exchange.OsrsGuidePrices
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * N35, loaded-runtime proof (the second Bouwer lane has no test server): the REAL compiled `ava.plugin.kts` (Ava_plugin, the
 * class the server loads at boot) is constructed against a real [World] with the real 667 cache, and the shop it registers is read
 * back from the world (`world.getShop`, what Player.openShop opens). Nothing in this class builds the shard row itself.
 */
class AvaShardRuntimeTests {
    private val shop get() = world.getShop("Ava's Odds and Ends") ?: fail("Ava_plugin did not register 'Ava's Odds and Ends'")

    private fun player(inventory: ItemContainer): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        every { player.bank } returns ItemContainer(world.definitions, 800, ContainerStackType.STACK)
        return player
    }

    private fun inventory(coins: Int) =
        ItemContainer(world.definitions, INVENTORY_KEY).also { it[0] = Item(Items.COINS_995, coins) }

    @Test
    fun `build identity - the loaded Ava_plugin class is newer than its source and the HEAD commit is printed`() {
        val compiled = File(CLASSES, SCRIPT.replace('.', '/') + ".class")
        println("N35-BUILD script=${compiled.path} classTime=${java.util.Date(compiled.lastModified())} sourceTime=${java.util.Date(File(SOURCE).lastModified())}")
        println("N35-BUILD head=" + ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim())
        assertTrue(compiled.lastModified() >= File(SOURCE).lastModified())
    }

    @Test
    fun `the loaded script registers Ava's Odds and Ends with the five arrow rows and the shard row in slot 6`() {
        val s = shop
        val expected = listOf(Items.FEATHER to 1000, Items.IRON_ARROW to 40, Items.STEEL_ARROW to 10, Items.IRON_ARROWTIPS to 30, Items.STEEL_ARROWTIPS to 20)
        expected.forEachIndexed { i, (item, amount) ->
            assertEquals(item, s.items[i]!!.item, "row ${i + 1}")
            assertEquals(amount, s.items[i]!!.amount, "row ${i + 1} stock")
        }
        val row = s.items[5] ?: fail("row 6 (slot 5) is empty in the loaded shop")
        val price = CrystalShardPrice.of(OsrsGuidePrices.book)
        println("N35-RUNTIME row6 item=${row.item} sellPrice=${row.sellPrice} buyPrice=${row.buyPrice} amount=${row.amount} seedPrice/150=$price")
        assertEquals(Items.CRYSTAL_SHARD, row.item)
        assertNotNull(price)
        assertEquals(price, row.sellPrice, "price = checked-in Enhanced crystal teleport seed / 150, never the cache 10,000")
        assertEquals(0, row.buyPrice)
        assertEquals(CrystalShardPrice.STOCK, row.amount)
        assertEquals(QuartermasterSupplies.resupplyAmount(CrystalShardPrice.STOCK), row.resupplyAmount)
        assertEquals((6..39).toList().filter { s.items[it] != null }, emptyList(), "no stray rows past the sixth")
    }

    @Test
    fun `a buy through the loaded shop and its own currency charges the authored price exactly and never buys back`() {
        val s = shop
        val price = CrystalShardPrice.of(OsrsGuidePrices.book)!!
        val inv = inventory(coins = 600 * price)
        listOf(1, 5, 10).forEach { n -> s.currency.sellToPlayer(player(inv), s, slot = 5, amt = n) }
        assertEquals(16, inv.getItemCount(Items.CRYSTAL_SHARD))
        assertEquals(600 * price - 16 * price, inv.getItemCount(Items.COINS_995))
        assertEquals(CrystalShardPrice.STOCK - 16, s.items[5]!!.currentAmount)

        val sellInv = ItemContainer(world.definitions, INVENTORY_KEY).also { it[0] = Item(Items.CRYSTAL_SHARD, 100) }
        s.currency.buyFromPlayer(player(sellInv), s, slot = 0, amt = 100)
        assertEquals(100, sellInv.getItemCount(Items.CRYSTAL_SHARD), "shards are refused when sold back")
        assertEquals(0, sellInv.getItemCount(Items.COINS_995))
    }

    @Test
    fun `Ava's Trade option is bound by the loaded script`() {
        val trade = world.definitions.get(NpcDef::class.java, Npcs.AVA).options.indexOfFirst { it.equals("trade", ignoreCase = true) } + 1
        assertTrue(trade > 0, "Ava has a Trade option in the cache")
        assertTrue(trade in world.plugins.boundNpcOptions(Npcs.AVA), "Ava's Trade option $trade has a handler (requirement check then openShop)")
    }

    companion object {
        const val SCRIPT = "gg.rsmod.plugins.content.areas.draynor.Ava_plugin"
        const val SOURCE = "src/main/kotlin/gg/rsmod/plugins/content/areas/draynor/ava.plugin.kts"
        val CLASSES = File("scripts/build/classes/kotlin/main")

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.definitions.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            val compiled = File(CLASSES, SCRIPT.replace('.', '/') + ".class")
            if (!compiled.exists() || compiled.lastModified() < File(SOURCE).lastModified()) {
                fail("${compiled.path} is missing or older than $SOURCE: the scripts compile did not run first")
            }
            URLClassLoader(arrayOf(CLASSES.toURI().toURL()), AvaShardRuntimeTests::class.java.classLoader)
                .loadClass(SCRIPT)
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            world
        }

        @BeforeClass
        @JvmStatic
        fun boot() {
            world
        }
    }
}
