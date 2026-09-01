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
        assertEquals(9, BeastOfBurden.allKeys.size)
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
        BeastOfBurden.deposit(player, Item(TEST_STACKABLE, 3))
        check(player.containers.getValue(BeastOfBurden.WAR_TORTOISE_KEY).getItemCount(TEST_STACKABLE) == 3)

        val withdrawn = BeastOfBurden.withdrawAll(player)

        assertEquals(3, withdrawn)
        assertEquals(3, player.inventory.getItemCount(TEST_STACKABLE))
        assertEquals(true, player.containers.getValue(BeastOfBurden.WAR_TORTOISE_KEY).isEmpty)
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
