package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.Skills
import io.mockk.*
import kotlin.test.*

class TreasureTrailStashTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    private val plugins = mockk<PluginRepository>(relaxed = true)
    private val catalogue get() = TreasureTrailStashCatalogue.current
    private val unit get() = catalogue.units.single { it.step.key.endsWith("Dance at the crossroads north of Draynor") }
    private val ids get() = unit.equipment.map { it.first().existingLocal!! }

    @BeforeTest fun setup() {
        every { world.definitions } returns definitions
        every { world.plugins } returns plugins
        every { plugins.canUnequipSlot(any(), any()) } returns true
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks = false } }
    }

    private fun player() = mockk<Player>(relaxed = true).also { p ->
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        every { p.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 99
    }

    private fun built(p: Player) { p.attr[unit.saved] = "[]" }
    private fun set(p: Player, charged: Boolean = false) = ids.forEachIndexed { slot, id ->
        p.inventory[slot] = Item(id).also { if (charged) it.putAttr(ItemAttribute.CHARGES, slot + 3) }
    }

    @Test fun `all selected source stashes have the right tier recipe and only wearable gear`() {
        assertEquals(17, catalogue.units.size)
        assertEquals(listOf(27, 42, 55, 77, 88), ClueScrollTier.values().map { tier -> catalogue.units.first { it.step.tier == tier }.recipe.level })
        assertEquals(listOf(150, 250, 400, 600, 1500), ClueScrollTier.values().map { tier -> catalogue.units.first { it.step.tier == tier }.recipe.experience })
        assertTrue(catalogue.units.all { it.recipe.ticks == 3 })
        val hut = catalogue.units.single { it.step.key.endsWith("Clap in the magic axe hut") }
        assertEquals(listOf(10394), hut.equipment.map { it.single().existingLocal })
        catalogue.units.forEach { stash ->
            val nails = stash.recipe.materials.single { it.amount == 10 }
            assertEquals(7, nails.alternatives.size)
            assertEquals(setOf(4819, 4820, 1539, 4821, 4822, 4823, 4824), nails.alternatives.toSet())
        }
    }

    @Test fun `partial sets spend nothing and do not create saved items`() {
        val p = player(); built(p)
        p.inventory[0] = Item(ids[0])
        assertEquals(TreasureTrailStashes.Result.INCOMPLETE_SET, TreasureTrailStashes.search(p, unit))
        assertEquals(1, p.inventory.getItemCount(ids[0]))
        assertTrue(TreasureTrailStashes.contents(p, unit)!!.isEmpty())
        assertEquals(TreasureTrailStashes.Result.NOT_BUILT, TreasureTrailStashes.search(player(), unit))
    }

    @Test fun `full inventory refuses the entire withdrawal without losing or duplicating the set`() {
        val p = player(); built(p); set(p)
        assertEquals(TreasureTrailStashes.Result.STORED, TreasureTrailStashes.search(p, unit))
        repeat(28) { p.inventory[it] = Item(995) }
        val saved = p.attr[unit.saved]
        assertEquals(TreasureTrailStashes.Result.NO_SPACE, TreasureTrailStashes.search(p, unit))
        assertEquals(saved, p.attr[unit.saved])
        p.inventory[0] = null; p.inventory[1] = null
        assertEquals(TreasureTrailStashes.Result.NO_SPACE, TreasureTrailStashes.search(p, unit))
        assertTrue(ids.all { !p.inventory.contains(it) })
        p.inventory[2] = null
        assertEquals(TreasureTrailStashes.Result.WITHDRAWN, TreasureTrailStashes.search(p, unit))
        assertTrue(ids.all { p.inventory.getItemCount(it) == 1 })
        assertTrue(TreasureTrailStashes.contents(p, unit)!!.isEmpty())
    }

    @Test fun `inventory has priority over equipment and worn gear needs the explicit setting`() {
        val p = player(); built(p)
        ids.forEachIndexed { slot, id -> p.equipment[slot] = Item(id) }
        assertEquals(TreasureTrailStashes.Result.INCOMPLETE_SET, TreasureTrailStashes.search(p, unit))
        assertTrue(ids.all { p.equipment.getItemCount(it) == 1 })
        p.attr[TreasureTrailStashes.TAKE_EQUIPPED] = true
        p.inventory[0] = Item(ids[0]).putAttr(ItemAttribute.CHARGES, 7)
        assertEquals(TreasureTrailStashes.Result.STORED, TreasureTrailStashes.search(p, unit))
        assertEquals(7, TreasureTrailStashes.contents(p, unit)!![0].getAttr(ItemAttribute.CHARGES))
        assertEquals(1, p.equipment.getItemCount(ids[0]))
        assertTrue(ids.drop(1).all { !p.equipment.contains(it) })
        verify(exactly = 0) { plugins.executeUnequipItem(p, ids[0]) }
        ids.drop(1).forEach { id -> verify(exactly = 1) { plugins.executeUnequipItem(p, id) } }
    }

    @Test fun `unequip denial rolls back earlier inventory selections too`() {
        val p = player(); built(p)
        p.attr[TreasureTrailStashes.TAKE_EQUIPPED] = true
        p.inventory[0] = Item(ids[0])
        p.equipment[1] = Item(ids[1]); p.equipment[2] = Item(ids[2])
        every { plugins.canUnequipSlot(p, 2) } returns false
        assertEquals(TreasureTrailStashes.Result.UNEQUIP_BLOCKED, TreasureTrailStashes.search(p, unit))
        assertEquals(ids[0], p.inventory[0]!!.id)
        assertEquals(ids[1], p.equipment[1]!!.id)
        assertEquals(ids[2], p.equipment[2]!!.id)
        assertEquals("[]", p.attr[unit.saved])
    }

    @Test fun `item charges survive a fresh account load and another account cannot retrieve the set`() {
        val first = player(); built(first); set(first, charged = true)
        assertEquals(TreasureTrailStashes.Result.STORED, TreasureTrailStashes.search(first, unit))
        val snapshot = first.attr.toPersistentMap()
        val reloaded = player()
        reloaded.attr[unit.saved] = snapshot.getValue(unit.saved.persistenceKey!!).toString()
        assertEquals(TreasureTrailStashes.Result.WITHDRAWN, TreasureTrailStashes.search(reloaded, unit))
        ids.forEachIndexed { slot, id -> assertEquals(slot + 3, reloaded.inventory.firstOrNull { it?.id == id }!!.getAttr(ItemAttribute.CHARGES)) }
        val other = player(); built(other)
        assertEquals(TreasureTrailStashes.Result.INCOMPLETE_SET, TreasureTrailStashes.search(other, unit))
        assertTrue(other.inventory.isEmpty)
    }

    @Test fun `corrupt storage is retained and cannot grant an arbitrary item`() {
        val p = player(); p.attr[unit.saved] = "[{\"id\":995,\"amount\":1000000,\"attr\":{}}]"
        val saved = p.attr[unit.saved]
        assertEquals(TreasureTrailStashes.Result.CORRUPT, TreasureTrailStashes.search(p, unit))
        assertEquals(saved, p.attr[unit.saved])
        assertTrue(p.inventory.isEmpty)
    }

    @Test fun `construction rejects mixed nails rechecks boosts and spends exact materials once`() {
        val p = player()
        unit.recipe.tools.forEachIndexed { index, group -> p.inventory[index] = Item(group.first().existingLocal!!) }
        p.inventory[2] = Item(960, 2); p.inventory[3] = Item(4819, 5); p.inventory[4] = Item(1539, 5)
        assertEquals(TreasureTrailStashes.Result.MATERIALS, TreasureTrailStashes.build(p, unit))
        assertEquals(2, p.inventory.getItemCount(960))
        p.inventory[4] = Item(1539, 10)
        every { p.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 26
        assertEquals(TreasureTrailStashes.Result.LEVEL, TreasureTrailStashes.build(p, unit))
        every { p.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 27
        assertEquals(TreasureTrailStashes.Result.BUILT, TreasureTrailStashes.build(p, unit))
        assertEquals(TreasureTrailStashes.Result.ALREADY_BUILT, TreasureTrailStashes.build(p, unit))
        assertEquals(0, p.inventory.getItemCount(960)); assertEquals(0, p.inventory.getItemCount(1539))
        assertEquals(5, p.inventory.getItemCount(4819))
        assertTrue(unit.recipe.tools.all { p.inventory.contains(it.first().existingLocal!!) })
    }

    @Test fun `crystal saw stacks with visible boosts without spending charges or accepting its seed`() {
        val p = player()
        every { p.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 24
        p.inventory[0] = Item(2347); p.inventory[1] = Item(9625).putAttr(ItemAttribute.CHARGES, 9)
        p.inventory[2] = Item(960, 2); p.inventory[3] = Item(1539, 10)
        assertEquals(TreasureTrailStashes.Result.BUILT, TreasureTrailStashes.build(p, unit))
        assertEquals(9, p.inventory[1]!!.getAttr(ItemAttribute.CHARGES))
        val other = player()
        every { other.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 24
        other.inventory[0] = Item(2347); other.inventory[1] = Item(9626)
        assertEquals(TreasureTrailStashes.Result.LEVEL, TreasureTrailStashes.build(other, unit))
    }
}
