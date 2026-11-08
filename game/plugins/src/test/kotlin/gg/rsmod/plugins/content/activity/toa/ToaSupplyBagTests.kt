package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import io.mockk.*
import kotlin.test.*

class ToaSupplyBagTests {
    @BeforeTest fun sourceMapping() {
        mockkObject(ToaIds, ToaMap)
        every { ToaIds.item(any()) } answers { firstArg<Int>() }
        every { ToaIds.itemSource(any()) } answers { firstArg<Int>() }
        every { ToaMap.source(any(), any()) } returns Tile(3551, 5161)
    }
    @AfterTest fun cleanup() { unmockkObject(ToaIds, ToaMap) }

    @Test fun `second checkpoint fills the same bag even with a full inventory and cannot be claimed twice`() {
        val f = Fixture()
        assertTrue(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        val item = f.inventory[0]!!
        val bag = f.supplies.bag(f.player, item)!!
        repeat(27) { f.inventory.add(1000 + it) }
        f.checkpoints.pathsCleared(4)
        assertTrue(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        assertSame(item, f.inventory[0])
        assertEquals(1, f.inventory.getItemCount(ToaSupplies.BAG_SOURCE))
        assertEquals(listOf(27343, 27343, 27339, 27343, 27343, 27339), bag.contents())
        assertFalse(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
    }

    @Test fun `over 28 items refuses the complete offer and freeing three slots permits the retained offer`() {
        val f = Fixture()
        assertTrue(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        val bag = f.supplies.bag(f.player, f.inventory[0]!!)!!
        assertTrue(bag.addAll(List(25) { 27315 }))
        f.checkpoints.pathsCleared(4)
        val before = bag.contents()
        assertFalse(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        assertEquals(before, bag.contents())
        assertTrue(f.checkpoints.offered(f.player.uid))
        repeat(3) { assertTrue(f.supplies.withdraw(f.player, 0, supplySlot = it)) }
        assertTrue(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        assertEquals(28, bag.contents().count { it != null })
        assertFalse(f.checkpoints.offered(f.player.uid))
    }

    @Test fun `partial doses return to the first empty slot and invalid items or other owners cannot enter the bag`() {
        val f = Fixture()
        assertTrue(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        val bagItem = f.inventory[0]!!
        val bag = f.supplies.bag(f.player, bagItem)!!
        assertTrue(f.supplies.withdraw(f.player, 0, supplySlot = 1))
        f.inventory[1] = gg.rsmod.game.model.item.Item(27345) // Smelling salts (1), not a refilled (2).
        assertTrue(f.supplies.returnItem(f.player, 1, 0))
        assertNull(f.inventory[1])
        assertEquals(27345, bag.contents()[1])
        assertFalse(f.supplies.returnItem(f.player, 0, 0))
        f.inventory.add(995)
        assertFalse(f.supplies.returnItem(f.player, 1, 0))
        val other = mockk<Player>()
        every { other.uid } returns PlayerUID("Other")
        assertNull(f.supplies.bag(other, bagItem))
        assertTrue(f.supplies.swap(f.player, 0, 1, 27))
        assertEquals(27345, bag.contents()[27])
        assertNull(bag.contents()[1])
        assertFalse(f.supplies.swap(f.player, 0, 27, 28))
    }

    @Test fun `last item drop destroys the bag and an overflowing batch cannot partially insert`() {
        val f = Fixture()
        assertTrue(f.supplies.claim(f.player, ToaSupplies.Choice.POWER))
        val bag = f.supplies.bag(f.player, f.inventory[0]!!)!!
        assertFalse(bag.addAll(List(26) { 27315 }))
        assertEquals(3, bag.contents().count { it != null })
        assertTrue(f.supplies.drop(f.player, 0, 0))
        assertTrue(f.supplies.drop(f.player, 0, 1))
        assertTrue(f.supplies.drop(f.player, 0, 2))
        assertNull(f.inventory[0])
        assertTrue(bag.empty)
        assertFalse(f.supplies.drop(f.player, 0, 2))
        verify(exactly = 3) { f.world.spawn(any<gg.rsmod.game.model.entity.GroundItem>()) }
    }

    private class Fixture {
        val world = mockk<World>(relaxed = true)
        private val definitions = mockk<DefinitionSet>()
        val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        val player = mockk<Player>(relaxed = true)
        val checkpoints = ToaCheckpoints().also { it.pathsCleared(2) }
        private val raid = mockk<ToaRaid>(relaxed = true)
        val supplies = ToaSupplies(raid)
        init {
            every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg<Int>()) }
            every { player.uid } returns PlayerUID("Owner")
            every { player.isDead() } returns false
            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.tile } returns Tile(6400, 256)
            every { raid.contains(player) } returns true
            every { raid.checkpoints } returns checkpoints
            every { raid.settings } returns ToaRaidSettings()
        }
    }
}
