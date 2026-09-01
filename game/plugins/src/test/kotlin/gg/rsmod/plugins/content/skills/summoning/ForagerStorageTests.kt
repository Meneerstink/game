package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
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
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.lang.ref.WeakReference
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Forager storage, the second of the two carrying contracts: "A forager will find certain items
 * from time to time, and can carry up to 30. You are only able to 'Withdraw' items from these
 * familiars."
 *
 * All twenty-two foragers therefore own a real 30-slot store that refuses deposits, and the
 * "Take Beast of Burden items" button reaches them too, because it covers "a beast of burden or
 * a forager". What a forager finds on its own is not implemented: the knowledge base names the
 * forage of only a handful of them (the albino rat's cheese, evil turnip slices, cockatrice
 * eggs, strange fruit, oak logs), all of which come from scrolls, and gives no find table for
 * the rest.
 */
class ForagerStorageTests {
    @Test
    fun `every forager owns a withdraw-only thirty slot store`() {
        val foragers = SummoningCatalogue.inCategory(FamiliarCategory.FORAGER)
        assertEquals(22, foragers.size)
        foragers.forEach { entry ->
            val storage =
                requireNotNull(BeastOfBurden.storageFor(entry.pouch)) {
                    "${entry.pouch.name} has no forager store"
                }
            assertEquals(30, storage.key.capacity, "${entry.pouch.name} capacity")
            assertTrue(storage.withdrawOnly, "${entry.pouch.name} should be withdraw-only")
            assertFalse(storage.essenceOnly, "${entry.pouch.name} should not be essence-only")
            assertTrue(BeastOfBurden.isCarrierNpc(entry.pouch.npc), "${entry.pouch.name} should carry items")
            assertFalse(BeastOfBurden.isBobNpc(entry.pouch.npc), "${entry.pouch.name} is not a beast of burden")
        }
    }

    /** The albino rat "stores cheese after scroll use" - a forager, not a container special case. */
    @Test
    fun `the albino rat cheese store is its forager store`() {
        val storage = requireNotNull(BeastOfBurden.storageFor(SummoningPouchData.ALBINO_RAT))
        assertEquals(30, storage.key.capacity)
        assertTrue(storage.withdrawOnly)
    }

    @Test
    fun `a forager refuses deposits and keeps the item in the inventory`() {
        val player = newPlayer(SummoningPouchData.MACAW.npc)
        player.inventory[0] = Item(Items.COINS, 100)

        val moved = BeastOfBurden.deposit(player, Item(Items.COINS, 100))

        assertEquals(0, moved)
        assertEquals(100, player.inventory[0]?.amount)
        assertTrue(BeastOfBurden.contents(player).isEmpty())
    }

    @Test
    fun `deposit-all does nothing to a forager`() {
        val player = newPlayer(SummoningPouchData.MACAW.npc)
        player.inventory[0] = Item(Items.COINS, 100)

        assertEquals(0, BeastOfBurden.depositAll(player))
        assertEquals(100, player.inventory[0]?.amount)
    }

    /** What the familiar finds goes in through [BeastOfBurden.grant]; the player takes it out. */
    @Test
    fun `foraged items can be withdrawn`() {
        val player = newPlayer(SummoningPouchData.ALBINO_RAT.npc)

        // Cheese does not stack, so four cheeses occupy four of the thirty slots.
        assertEquals(4, BeastOfBurden.grant(player, Item(Items.CHEESE, 4)))
        assertEquals(4, BeastOfBurden.contents(player).size)

        assertEquals(4, BeastOfBurden.withdrawAll(player))
        assertEquals(4, (0 until player.inventory.capacity).count { player.inventory[it]?.id == Items.CHEESE })
        assertTrue(BeastOfBurden.contents(player).isEmpty())
    }

    private fun newPlayer(familiarNpcId: Int): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs

        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.containers } returns HashMap()

        val npc = Npc(familiarNpcId, Tile(0, 0, 0), world)
        npcs.add(npc)
        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadDefinitions() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
