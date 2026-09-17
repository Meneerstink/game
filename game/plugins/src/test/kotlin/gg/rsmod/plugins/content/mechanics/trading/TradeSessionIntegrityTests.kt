package gg.rsmod.plugins.content.mechanics.trading

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.mechanics.trading.impl.TradeSession
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The trade works on a snapshot of each inventory and writes it back on completion. These tests
 * pin the guard that refuses to complete when a real inventory no longer matches its snapshot
 * (death loot removal, unequip, queued task), which previously duplicated or destroyed items.
 */
class TradeSessionIntegrityTests {
    @Test
    fun `unchanged inventories complete and swap the offers`() {
        val (a, b) = pair()
        a.inventory[0] = Item(WHIP, 1)
        b.inventory[0] = Item(COINS, 1000)
        val sa = TradeSession(a, b)
        val sb = TradeSession(b, a)
        a.attr[TRADE_SESSION_ATTR] = sa
        b.attr[TRADE_SESSION_ATTR] = sb

        sa.offer(0, 1)
        sb.offer(0, 1000)
        assertEquals(1, sa.container.getItemCount(WHIP), "whip offered")
        assertEquals(1000, sb.container.getItemCount(COINS), "coins offered")
        acceptBoth(a, b)
        assertNotNull(a.getTradeSession(), "session survives the first accept round")
        assertNotNull(b.getTradeSession(), "partner session survives the first accept round")
        acceptBoth(a, b)

        assertEquals(1000, a.inventory.getItemCount(COINS))
        assertEquals(0, a.inventory.getItemCount(WHIP))
        assertEquals(1, b.inventory.getItemCount(WHIP))
        assertEquals(0, b.inventory.getItemCount(COINS))
        assertNull(a.attr[TRADE_SESSION_ATTR])
        assertNull(b.attr[TRADE_SESSION_ATTR])
    }

    @Test
    fun `an offered item removed from the real inventory mid-trade cancels instead of duplicating`() {
        val (a, b) = pair()
        a.inventory[0] = Item(WHIP, 1)
        b.inventory[0] = Item(COINS, 1000)
        val sa = TradeSession(a, b)
        val sb = TradeSession(b, a)
        a.attr[TRADE_SESSION_ATTR] = sa
        b.attr[TRADE_SESSION_ATTR] = sb

        sa.offer(0, 1)
        sb.offer(0, 1000)
        acceptBoth(a, b)

        // Death (or anything else) takes the whip out of the real inventory while the trade is open.
        a.inventory[0] = null

        acceptBoth(a, b)

        assertEquals(0, a.inventory.getItemCount(WHIP), "the removed whip must not come back")
        assertEquals(0, a.inventory.getItemCount(COINS), "no coins may move on a cancelled trade")
        assertEquals(1000, b.inventory.getItemCount(COINS))
        assertEquals(0, b.inventory.getItemCount(WHIP))
        assertNull(a.attr[TRADE_SESSION_ATTR])
        assertNull(b.attr[TRADE_SESSION_ATTR])
    }

    @Test
    fun `an item added to the real inventory mid-trade cancels instead of being destroyed`() {
        val (a, b) = pair()
        a.inventory[0] = Item(WHIP, 1)
        val sa = TradeSession(a, b)
        val sb = TradeSession(b, a)
        a.attr[TRADE_SESSION_ATTR] = sa
        b.attr[TRADE_SESSION_ATTR] = sb

        sa.offer(0, 1)
        acceptBoth(a, b)
        // e.g. an unequip landing in the real inventory after the snapshot was taken
        a.inventory[5] = Item(DAGGER, 1)
        acceptBoth(a, b)

        assertEquals(1, a.inventory.getItemCount(DAGGER), "the unequipped dagger must survive")
        assertEquals(1, a.inventory.getItemCount(WHIP))
        assertEquals(0, b.inventory.getItemCount(WHIP))
    }

    @Test
    fun `negative offer amounts are ignored`() {
        val (a, b) = pair()
        a.inventory[0] = Item(COINS, 1000)
        val sa = TradeSession(a, b)
        a.attr[TRADE_SESSION_ATTR] = sa
        b.attr[TRADE_SESSION_ATTR] = TradeSession(b, a)

        sa.offer(0, -1)
        sa.offer(0, 0)

        assertEquals(0, sa.container.getItemCount(COINS))
        assertEquals(1000, sa.inventory.getItemCount(COINS))
    }

    private fun acceptBoth(
        a: Player,
        b: Player,
    ) {
        a.getTradeSession()?.progress()
        b.getTradeSession()?.progress()
    }

    private fun pair(): Pair<Player, Player> {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val a = newPlayer(world, "a")
        val b = newPlayer(world, "b")
        return a to b
    }

    private fun newPlayer(
        world: World,
        name: String,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.username } returns name
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(3200, 3200, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.varcs } returns MutableList(4096) { 0 }
        return player
    }

    companion object {
        private const val WHIP = 4151
        private const val COINS = 995
        private const val DAGGER = 1205

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
            // `tradeable` comes from items.yml via ItemMetadataService, which this fixture does not load.
            listOf(WHIP, COINS, DAGGER).forEach { DEFINITIONS.get(ItemDef::class.java, it).tradeable = true }
        }
    }
}
