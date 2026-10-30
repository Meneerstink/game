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
    /** N224: the shards moved to "Buy devices" (Ava's Devices) - slot [SHARD_SLOT], after the attractor, accumulator and assembler. */
    private val shop get() = world.getShop("Ava's Devices") ?: fail("Ava_plugin did not register 'Ava's Devices'")

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
    fun `N224 - Buy devices sells the three devices and the shard row, the odds and ends no shards`() {
        val s = shop
        assertEquals(listOf(Items.AVAS_ATTRACTOR, Items.AVAS_ACCUMULATOR, Items.AVAS_ASSEMBLER), (0..2).map { s.items[it]!!.item })
        assertTrue(world.getShop("Ava's Odds and Ends")!!.items.none { it?.item == Items.CRYSTAL_SHARD }, "shards moved out of the odds and ends")
        val row = s.items[SHARD_SLOT] ?: fail("shard slot $SHARD_SLOT is empty in the loaded shop")
        val price = CrystalShardPrice.of(OsrsGuidePrices.book)
        println("N35-RUNTIME row6 item=${row.item} sellPrice=${row.sellPrice} buyPrice=${row.buyPrice} amount=${row.amount} seedPrice/150=$price")
        assertEquals(Items.CRYSTAL_SHARD, row.item)
        assertNotNull(price)
        assertEquals(price, row.sellPrice, "price = checked-in Enhanced crystal teleport seed / 150, never the cache 10,000")
        assertEquals(0, row.buyPrice)
        assertEquals(CrystalShardPrice.STOCK, row.amount)
        assertEquals(QuartermasterSupplies.resupplyAmount(CrystalShardPrice.STOCK), row.resupplyAmount)
        // N446 + N461 (owner 2026-10-30): the Locator orb and Sunfire splinters follow the shards in the same Buy device shop.
        assertEquals(gg.rsmod.plugins.content.items.osrs.LocatorOrb.ACTIVE, s.items[ORB_SLOT]!!.item)
        assertEquals(Items.SUNFIRE_SPLINTERS, s.items[SPLINTER_SLOT]!!.item)
        assertEquals(((SPLINTER_SLOT + 1) until s.items.size).toList().filter { s.items[it] != null }, emptyList(), "no stray rows after the splinters")
    }

    @Test
    fun `a buy through the loaded shop and its own currency charges the authored price exactly and never buys back`() {
        val s = shop
        val price = CrystalShardPrice.of(OsrsGuidePrices.book)!!
        val inv = inventory(coins = 600 * price)
        listOf(1, 5, 10).forEach { n -> s.currency.sellToPlayer(player(inv), s, slot = SHARD_SLOT, amt = n) }
        assertEquals(16, inv.getItemCount(Items.CRYSTAL_SHARD))
        assertEquals(600 * price - 16 * price, inv.getItemCount(Items.COINS_995))
        assertEquals(CrystalShardPrice.STOCK - 16, s.items[SHARD_SLOT]!!.currentAmount)

        val sellInv = ItemContainer(world.definitions, INVENTORY_KEY).also { it[0] = Item(Items.CRYSTAL_SHARD, 100) }
        s.currency.buyFromPlayer(player(sellInv), s, slot = 0, amt = 100)
        assertEquals(100, sellInv.getItemCount(Items.CRYSTAL_SHARD), "shards are refused when sold back")
        assertEquals(0, sellInv.getItemCount(Items.COINS_995))
    }

    @Test
    fun `N224 - Ava has no Trade option, Buy device is bound`() {
        val options = world.definitions.get(NpcDef::class.java, Npcs.AVA).options
        assertTrue(options.none { it.equals("trade", ignoreCase = true) }, "Ava's Trade option is gone from the cache: ${options.toList()}")
        val buy = options.indexOfFirst { it.equals("buy device", ignoreCase = true) } + 1
        assertTrue(buy > 0 && buy in world.plugins.boundNpcOptions(Npcs.AVA), "Buy device has a handler")
    }

    @Test
    fun `N224 - the assembler is Ava's own route - Dragon Slayer II, Vorkath's head, 75 mithril arrows and its coins`() {
        val s = shop
        val inv = inventory(coins = 1_000_000)
        inv.add(Items.VORKATHS_HEAD, 1)
        inv.add(Items.MITHRIL_ARROW, 75)
        val p = player(inv)
        val said = mutableListOf<String>()
        every { p.write(*anyVararg<gg.rsmod.game.message.Message>()) } answers {
            said.addAll((args[0] as Array<*>).filterIsInstance<gg.rsmod.game.message.impl.MessageGameMessage>().map { it.message })
        }
        val skills = mockk<gg.rsmod.game.model.skill.SkillSet>(relaxed = true)
        every { skills.getCurrentLevel(any()) } returns 99
        every { p.skills } returns skills
        s.currency.sellToPlayer(p, s, slot = 2, amt = 1)
        assertEquals(0, inv.getItemCount(Items.AVAS_ASSEMBLER), "no Dragon Slayer II: no assembler")
        p.attr[gg.rsmod.plugins.content.unlocks.UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] = true
        s.currency.sellToPlayer(p, s, slot = 2, amt = 5)
        assertEquals(1, inv.getItemCount(Items.AVAS_ASSEMBLER), "one at a time; said: $said")
        assertEquals(0, inv.getItemCount(Items.VORKATHS_HEAD))
        assertEquals(0, inv.getItemCount(Items.MITHRIL_ARROW))
        assertEquals(1_000_000 - s.items[2]!!.sellPrice!!, inv.getItemCount(Items.COINS_995))
    }

    @Test
    fun `N461 Ava's Buy device shop sells Sunfire splinters for 379 coins, any amount, never buys them back`() {
        val s = shop
        val row = s.items[SPLINTER_SLOT]!!
        assertEquals(Items.SUNFIRE_SPLINTERS, row.item)
        assertEquals(379, s.currency.priceOf(world, row))
        assertEquals(1_000_000, row.amount)
        val inv = inventory(379 * 150_000)
        val p = player(inv)
        listOf(1, 5, 10, 19_984, 130_000).forEach { s.currency.sellToPlayer(p, s, SPLINTER_SLOT, it) }
        assertEquals(150_000, inv.getItemCount(Items.SUNFIRE_SPLINTERS))
        assertEquals(0, inv.getItemCount(Items.COINS_995))
        assertEquals(1_000_000 - 150_000, row.currentAmount)
        s.currency.buyFromPlayer(p, s, 0, 150_000)
        assertEquals(150_000, inv.getItemCount(Items.SUNFIRE_SPLINTERS), "never bought back")
    }

    @Test
    fun `N461 full inventory buys into an existing splinter stack but never loses coins without room`() {
        val s = shop
        val inv = inventory(379 * 10)
        (1..27).forEach { inv[it] = Item(Items.LOGS) }
        s.currency.sellToPlayer(player(inv), s, SPLINTER_SLOT, 5)
        assertEquals(379 * 10, inv.getItemCount(Items.COINS_995))
        assertEquals(0, inv.getItemCount(Items.SUNFIRE_SPLINTERS))
        inv[27] = Item(Items.SUNFIRE_SPLINTERS, 100)
        s.currency.sellToPlayer(player(inv), s, SPLINTER_SLOT, 5)
        assertEquals(105, inv.getItemCount(Items.SUNFIRE_SPLINTERS))
        assertEquals(379 * 5, inv.getItemCount(Items.COINS_995))
    }

    companion object {
        const val SCRIPT = "gg.rsmod.plugins.content.areas.draynor.Ava_plugin"
        const val SHARD_SLOT = 3
        const val ORB_SLOT = 4
        const val SPLINTER_SLOT = 5
        const val SOURCE = "src/main/kotlin/gg/rsmod/plugins/content/areas/draynor/ava.plugin.kts"
        val CLASSES = File("scripts/build/classes/kotlin/main")

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.definitions.loadAll(CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: Paths.get("..", "..", "data", "cache").toFile().toString()))
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
