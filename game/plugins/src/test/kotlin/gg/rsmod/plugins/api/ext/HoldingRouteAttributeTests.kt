package gg.rsmod.plugins.api.ext

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.inter.bank.Bank
import gg.rsmod.plugins.content.inter.pricecheck.PriceChecker
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bug search 2026-10-02 (ROOT_CAUSES class 52/6): every screen or bag that holds a player's items moves them with their
 * own attributes. The price checker, the looting bag and the duel stake (DuelArenaInterfacesTests) rebuilt items as
 * `add(item.id, count)`: a degraded Chaotic/Nex piece came back fully charged ("absent = full") and a trident lost its
 * charges ("absent = 0"). Real cache definitions, so stacking and the creation redirect behave as in production.
 */
class HoldingRouteAttributeTests {
    private fun player(tile: Tile): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { world.getService(GrandExchangeService::class.java, any()) } returns null
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.username } returns "holder"
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.containers } returns HashMap<ContainerKey, ItemContainer>()
        every { player.tile } returns tile
        every { player.varcs } returns MutableList(2000) { 0 }
        return player
    }

    private fun charged(charges: Int) = Item(Items.ABYSSAL_WHIP).apply { attr[ItemAttribute.CHARGES] = charges }

    private fun chargesOf(container: ItemContainer): List<Int?> =
        container.rawItems.filterNotNull().filter { it.id == Items.ABYSSAL_WHIP }.map { it.attr[ItemAttribute.CHARGES] }.sortedBy { it ?: -1 }

    @Test
    fun `moveEach keeps every exemplar's own attributes and its id`() {
        val from = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val to = ItemContainer(DEFINITIONS, 28, ContainerStackType.NORMAL)
        from[0] = charged(10)
        from[3] = charged(20)
        val result = from.moveEach(to, Items.ABYSSAL_WHIP, 2, startSlot = 3)
        assertEquals(2, result.moved)
        assertEquals(listOf(3, 0), result.emptied)
        assertEquals(listOf<Int?>(10, 20), chargesOf(to))
        assertEquals(0, from.getItemCount(Items.ABYSSAL_WHIP))
    }

    @Test
    fun `the price checker gives checked items back exactly as they went in`() {
        val p = player(Tile(3212, 3428, 0))
        p.attr[PriceChecker.CONTAINER_ATTR] = ItemContainer(DEFINITIONS, PriceChecker.CAPACITY, ContainerStackType.NORMAL)
        p.inventory[0] = charged(1234)
        p.inventory[5] = charged(5)

        PriceChecker.check(p, 0, 2)
        assertEquals(listOf<Int?>(5, 1234), chargesOf(PriceChecker.container(p)!!))
        assertEquals(0, p.inventory.getItemCount(Items.ABYSSAL_WHIP))

        PriceChecker.uncheck(p, 0, 2)
        assertEquals(listOf<Int?>(5, 1234), chargesOf(p.inventory), "Remove keeps the charges")

        PriceChecker.check(p, p.inventory.rawItems.indexOfFirst { it != null }, 2)
        PriceChecker.close(p)
        assertEquals(listOf<Int?>(5, 1234), chargesOf(p.inventory), "closing the screen keeps the charges")
    }

    @Test
    fun `the looting bag stores and empties items with their attributes`() {
        val p = player(Tile(3100, 3600, 0)) // Wilderness: the bag is usable here
        assertTrue(DEFINITIONS.get(ItemDef::class.java, Items.ABYSSAL_WHIP).tradeable, "items.yml marks the whip tradeable")
        p.inventory[2] = charged(77)
        assertEquals(1, LootingBag.store(p, Items.ABYSSAL_WHIP, 1))
        assertEquals(listOf<Int?>(77), chargesOf(LootingBag.container(p)))

        every { p.interfaces.isVisible(Bank.BANK_INTERFACE_ID) } returns true
        assertEquals(1, LootingBag.withdrawAtBank(p))
        assertEquals(listOf<Int?>(77), chargesOf(p.inventory))
    }

    /**
     * Census over the routes that hold or hand back a player's existing items: none rebuilds one from its id (which drops
     * the attributes and applies the creation redirect). New routes belong in this list.
     */
    @Test
    fun `no holding route rebuilds an existing item from its id`() {
        val content = java.io.File("src/main/kotlin/gg/rsmod/plugins/content")
        val routes =
            listOf(
                "inter/pricecheck/PriceChecker.kt",
                "mechanics/pvp/LootingBag.kt",
                "activity/duel_arena/DuelArenaInterfaces.kt",
                "activity/duel_arena/DuelArenaRules.kt",
                "activity/duel_arena/duel_arena.plugin.kts",
                "mechanics/trouver/Trouver.kt",
                "mechanics/shops/ItemCurrency.kt",
                "skills/summoning/SummoningSpecialMoves.kt",
            )
        val rebuild = Regex("""(\.add\(|GroundItem\()(item|existing|stored|held|lockedItem|selected|it)\.id,""")
        val offenders =
            routes.flatMap { path ->
                java.io.File(content, path).readLines().mapIndexedNotNull { index, line ->
                    // A rebuild is only allowed for exemplars taken by removeStateless, marked "stateless" on the line.
                    if (rebuild.containsMatchIn(line) && !line.contains("stateless")) "$path:${index + 1}: ${line.trim()}" else null
                }
            }
        assertTrue(offenders.isEmpty(), "existing items rebuilt from their id: $offenders")
        // Routes that turn an item into its bare id (offer book, shop stock) take only exemplars without value state.
        listOf("mechanics/exchange/GrandExchangeInterface.kt", "mechanics/exchange/grand_exchange.plugin.kts", "mechanics/shops/ItemCurrency.kt").forEach { path ->
            assertTrue(java.io.File(content, path).readText().contains("removeStateless("), "$path sells stateless exemplars only")
        }
        val yak = java.io.File(content, "skills/summoning/SummoningSpecialMoves.kt").readText()
        assertTrue(yak.contains("Bank.deposit(player, player.inventory, slot, 1)"), "Winter Storage banks through the real bank rules")
    }

    @Test
    fun `exchanges that keep bare ids take only exemplars without value state`() {
        val inv = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        inv[0] = charged(12) // a used, partly degraded piece
        inv[1] = Item(Items.ABYSSAL_WHIP)
        inv[2] = Item(Items.ABYSSAL_WHIP).apply { attr[ItemAttribute.BANK_TAB] = 2 } // bookkeeping, not value
        assertTrue(inv[0]!!.carriesState())
        assertEquals(2, inv.statelessCount(Items.ABYSSAL_WHIP))
        assertEquals(2, inv.removeStateless(Items.ABYSSAL_WHIP, 3), "never the charged exemplar")
        assertEquals(listOf<Int?>(12), chargesOf(inv), "the charged one stays")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            // ItemDef.tradeable comes from data/cfg/items.yml at boot (ItemMetadataService), as in DuelArenaInterfacesTests.
            var id = -1
            java.io.File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
                when {
                    line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                    line.startsWith("  tradeable: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
                }
            }
        }
    }
}
