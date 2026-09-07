package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.lang.ref.WeakReference
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R07.3: regression tests for [BeastOfBurden]'s deposit/withdraw-all container logic - the
 * only 2 real, non-graphical mechanics this batch builds on top of a summoned Beast of
 * Burden familiar (see [BeastOfBurden]'s KDoc for why no interface component is implemented).
 *
 * Uses a real cache-backed [DefinitionSet] (like [gg.rsmod.plugins.content.mechanics.death.DeathRecoveryServiceTests])
 * because [ItemContainer.add] needs a loaded [ItemDef] to decide stacking, and a real [Npc] +
 * [PawnList] (rather than mocking [World.npcs]) so [Familiar.current]'s real
 * `world.npcs.contains(npc)` check behaves exactly as it does in production.
 */
class BeastOfBurdenTests {
    @Test
    fun `all target-period beasts of burden are registered`() {
        assertEquals(true, BeastOfBurden.isBobNpc(Npcs.PACK_YAK))
        assertEquals(true, BeastOfBurden.isBobNpc(Npcs.WAR_TORTOISE))
        assertEquals(true, BeastOfBurden.isBobNpc(Npcs.SPIRIT_TERRORBIRD))
        assertTrue(BeastOfBurden.isBobNpc(Npcs.THORNY_SNAIL))
        assertTrue(BeastOfBurden.isBobNpc(Npcs.SPIRIT_KALPHITE))
        assertTrue(BeastOfBurden.isBobNpc(Npcs.BULL_ANT))
        assertTrue(BeastOfBurden.isBobNpc(Npcs.ABYSSAL_PARASITE))
        assertTrue(BeastOfBurden.isBobNpc(Npcs.ABYSSAL_LURKER))
        assertTrue(BeastOfBurden.isBobNpc(Npcs.ABYSSAL_TITAN))
        // Nine beasts of burden plus the twenty-two foragers, each with its own store.
        assertEquals(31, BeastOfBurden.allKeys.size)
        assertEquals(31, BeastOfBurden.allKeys.map { it.name }.distinct().size)
        assertFalse(BeastOfBurden.isBobNpc(Npcs.SPIRIT_WOLF))
    }

    @Test
    fun `deposit does nothing without an active BoB familiar`() {
        val player = newPlayer(familiarNpcId = null)
        player.inventory[0] = Item(TEST_STACKABLE, 5)

        val moved = BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 5))

        assertEquals(0, moved)
        assertEquals(5, player.inventory.getItemCount(TEST_STACKABLE), "nothing should leave the inventory")
    }

    @Test
    fun `deposit does nothing while a non-BoB familiar is out`() {
        val player = newPlayer(familiarNpcId = Npcs.SPIRIT_WOLF)
        player.inventory[0] = Item(TEST_STACKABLE, 5)

        val moved = BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 5))

        assertEquals(0, moved)
        assertNull(BeastOfBurden.activeKey(player), "Spirit Wolf has no BoB storage")
    }

    @Test
    fun `deposit moves items from inventory into the matching Pack Yak container`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        player.inventory[0] = Item(TEST_STACKABLE, 5)

        val moved = BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 5))

        assertEquals(5, moved)
        assertEquals(0, player.inventory.getItemCount(TEST_STACKABLE))
        val container = player.containers.getValue(BeastOfBurden.PACK_YAK_KEY)
        assertEquals(5, container.getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `withdrawAll empties the active BoB container back into the inventory`() {
        val player = newPlayer(familiarNpcId = Npcs.WAR_TORTOISE)
        player.inventory[0] = Item(TEST_STACKABLE, 3)
        BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 3))
        check(player.containers.getValue(BeastOfBurden.WAR_TORTOISE_KEY).getItemCount(TEST_STACKABLE) == 3)

        val withdrawn = BeastOfBurden.withdrawAll(player)

        assertEquals(3, withdrawn)
        assertEquals(3, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(true, player.containers.getValue(BeastOfBurden.WAR_TORTOISE_KEY).isEmpty)
    }

    /**
     * The mandatory "empty BoB" case: Take BoB on a beast of burden that is carrying nothing must
     * give informative feedback rather than doing nothing silently.
     *
     * `withdrawAll` returns 0 for an empty store **and** for a full inventory, so the count alone
     * cannot tell those apart - which is why the handler branches on [BeastOfBurden.storedCount]
     * first to produce "Your familiar isn't carrying anything." This pins the distinction the
     * message depends on, rather than the message string itself.
     */
    @Test
    fun `an empty beast of burden is distinguishable from one that simply cannot unload`() {
        val player = newPlayer(familiarNpcId = Npcs.WAR_TORTOISE)
        assertEquals(0, BeastOfBurden.storedCount(player), "an untouched BoB should report nothing stored")

        player.inventory[0] = Item(TEST_STACKABLE, 3)
        BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 3))

        assertEquals(3, BeastOfBurden.storedCount(player), "a loaded BoB must not report itself empty")

        BeastOfBurden.withdrawAll(player)
        assertEquals(0, BeastOfBurden.storedCount(player), "an unloaded BoB should report nothing stored again")
    }

    @Test
    fun `storedCount reports nothing when no familiar is out`() {
        assertEquals(0, BeastOfBurden.storedCount(newPlayer(familiarNpcId = null)))
    }

    @Test
    fun `withdrawAll without an active familiar is a no-op`() {
        val player = newPlayer(familiarNpcId = null)

        val withdrawn = BeastOfBurden.withdrawAll(player)

        assertEquals(0, withdrawn)
    }

    @Test
    fun `each BoB familiar keeps its own separate container`() {
        val yakPlayer = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        yakPlayer.inventory[0] = Item(TEST_STACKABLE, 2)
        BeastOfBurden.deposit(yakPlayer, Item(TEST_STACKABLE, 2))

        // War Tortoise's container is never even lazily created for this player - it was
        // never the active familiar, so there's nothing to look up yet.
        assertEquals(null, yakPlayer.containers[BeastOfBurden.WAR_TORTOISE_KEY])
        assertEquals(2, yakPlayer.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `essence beasts accept only unnoted rune or pure essence`() {
        val player = newPlayer(familiarNpcId = Npcs.ABYSSAL_TITAN)
        player.inventory[0] = Item(TEST_STACKABLE, 1)
        player.inventory[1] = Item(Items.PURE_ESSENCE, 5)

        assertEquals(0, BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 1)))
        assertEquals(5, BeastOfBurden.deposit(player, Item(Items.PURE_ESSENCE, 5)))
        assertEquals(5, player.containers.getValue(BeastOfBurden.ABYSSAL_TITAN_KEY).getItemCount(Items.PURE_ESSENCE))
    }

    @Test
    fun `a stale deposit cannot create cargo without inventory items`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        val staleItem = Item(TEST_STACKABLE, 5)
        player.inventory[0] = staleItem
        player.inventory[0] = null

        assertEquals(0, BeastOfBurden.deposit(player, staleItem))
        assertTrue(BeastOfBurden.contents(player).isEmpty())
        assertEquals(0, player.inventory.getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `an oversized deposit moves only the quantity still owned`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        player.inventory[0] = Item(TEST_STACKABLE, 3)

        assertEquals(3, BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 8)))
        assertEquals(0, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(3, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `partial deposit removes only cargo that fits`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        assertEquals(29, BeastOfBurden.grant(player, Item(Items.LOGS, 29)))
        player.inventory.add(Items.LOGS, 3, assureFullInsertion = true)

        assertEquals(1, BeastOfBurden.deposit(player, Item(Items.LOGS, 3)))
        assertEquals(2, player.inventory.getItemCount(Items.LOGS))
        assertEquals(30, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(Items.LOGS))
    }

    @Test
    fun `full cargo leaves inventory unchanged`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        assertEquals(30, BeastOfBurden.grant(player, Item(Items.LOGS, 30)))
        player.inventory[0] = Item(TEST_STACKABLE, 5)

        assertEquals(0, BeastOfBurden.deposit(player, player.inventory[0]!!))
        assertEquals(5, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(0, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `withdrawAll leaves untransferred items in cargo when inventory fills`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        assertEquals(3, BeastOfBurden.grant(player, Item(Items.LOGS, 3)))
        player.inventory.add(Items.LOGS, 27, assureFullInsertion = true)

        assertEquals(1, BeastOfBurden.withdrawAll(player))
        assertEquals(28, player.inventory.getItemCount(Items.LOGS))
        assertEquals(2, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(Items.LOGS))
        assertEquals(0, BeastOfBurden.withdrawAll(player))
    }

    @Test
    fun `withdraw preserves cargo beyond the inventory stack limit`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        assertEquals(5, BeastOfBurden.grant(player, Item(TEST_STACKABLE, 5)))
        player.inventory[0] = Item(TEST_STACKABLE, Int.MAX_VALUE - 2)

        assertEquals(2, BeastOfBurden.withdraw(player, 0, 5))
        assertEquals(Int.MAX_VALUE, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(3, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `invalid withdrawal amounts and slots leave both containers unchanged`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        assertEquals(5, BeastOfBurden.grant(player, Item(TEST_STACKABLE, 5)))
        player.inventory[0] = Item(TEST_STACKABLE, 10)

        assertEquals(0, BeastOfBurden.withdraw(player, 0, 0))
        assertEquals(0, BeastOfBurden.withdraw(player, 0, -1))
        assertEquals(0, BeastOfBurden.withdraw(player, -1, 1))
        assertEquals(0, BeastOfBurden.withdraw(player, 30, 1))
        assertEquals(10, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(5, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
    }

    @Test
    fun `invalid deposit amounts leave both containers unchanged`() {
        val player = newPlayer(familiarNpcId = Npcs.PACK_YAK)
        assertEquals(5, BeastOfBurden.grant(player, Item(TEST_STACKABLE, 5)))
        player.inventory[0] = Item(TEST_STACKABLE, 10)

        assertEquals(0, BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 0)))
        assertEquals(0, BeastOfBurden.deposit(player, Item(TEST_STACKABLE, -1)))
        assertEquals(10, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(5, player.containers.getValue(BeastOfBurden.PACK_YAK_KEY).getItemCount(TEST_STACKABLE))
    }

    private fun newPlayer(familiarNpcId: Int?): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs

        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.containers } returns HashMap()

        if (familiarNpcId != null) {
            val npc = Npc(familiarNpcId, Tile(0, 0, 0), world)
            npcs.add(npc)
            player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        }
        return player
    }

    companion object {
        // Arbitrary real cache item ids used only as stackable test fixtures - same "any real
        // id from the loaded cache is fine for container-mechanics tests" precedent as
        // DeathRecoveryServiceTests' own RECOVERED_ITEM constant.
        private const val TEST_STACKABLE = Items.COINS_995

        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
