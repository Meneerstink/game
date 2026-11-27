package gg.rsmod.plugins.content.skills.construction.mahogany

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C7): Mahogany Homes. The plank sack never makes or loses a plank (fill / empty / build from it), the
 * generated data is whole (every OSRS layout task has a spot with the right Remove / Build / Repair variants), and the carpenter's
 * outfit gives the OSRS bonus.
 */
class MahoganyHomesTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>()
    private val attr = AttributeMap()
    private val inv = ItemContainer(definitions, INVENTORY_KEY)
    private val equipment = ItemContainer(definitions, EQUIPMENT_KEY)
    private val player = mockk<Player>(relaxed = true).also { p ->
        every { p.world } returns world
        every { p.attr } returns attr
        every { p.inventory } returns inv
        every { p.equipment } returns equipment
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
    }

    private fun total(plank: Int) = MahoganyHomes.planks(player, plank)

    @Test
    fun `fill and empty move planks between inventory and sack without making or losing any`() {
        inv.add(MahoganyHomes.PLANK_SACK, 1)
        inv.add(Items.OAK_PLANK, 10)
        inv.add(Items.MAHOGANY_PLANK, 5)
        MahoganyHomes.fillSack(player)
        assertEquals(0, inv.getItemCount(Items.OAK_PLANK))
        assertEquals(0, inv.getItemCount(Items.MAHOGANY_PLANK))
        assertEquals(10, total(Items.OAK_PLANK))
        assertEquals(5, total(Items.MAHOGANY_PLANK))
        MahoganyHomes.emptySack(player)
        assertEquals(10, inv.getItemCount(Items.OAK_PLANK))
        assertEquals(5, inv.getItemCount(Items.MAHOGANY_PLANK))
        assertEquals(10, total(Items.OAK_PLANK))
    }

    @Test
    fun `the sack holds 28 planks - the rest stays in the inventory`() {
        inv.add(MahoganyHomes.PLANK_SACK, 1)
        inv.add(Items.PLANK, 27)
        MahoganyHomes.fillSack(player)
        inv.add(Items.TEAK_PLANK, 5)
        MahoganyHomes.fillSack(player)
        val sack = MahoganyHomes.sackContents(inv[inv.getItemIndex(MahoganyHomes.PLANK_SACK, skipAttrItems = false)])
        assertEquals(28, sack.sum())
        assertEquals(1, sack[2])
        assertEquals(4, inv.getItemCount(Items.TEAK_PLANK))
        assertEquals(27, total(Items.PLANK))
        assertEquals(5, total(Items.TEAK_PLANK))
    }

    @Test
    fun `building takes the inventory planks first, then the sack`() {
        inv.add(MahoganyHomes.PLANK_SACK, 1)
        inv.add(Items.TEAK_PLANK, 6)
        MahoganyHomes.fillSack(player)
        inv.add(Items.TEAK_PLANK, 2)
        MahoganyHomes.takePlanks(player, Items.TEAK_PLANK, 5)
        assertEquals(0, inv.getItemCount(Items.TEAK_PLANK))
        assertEquals(3, total(Items.TEAK_PLANK))
    }

    @Test
    fun `every OSRS layout task has a spot whose variants carry the option the task needs`() {
        assertEquals(9, MahoganyHomes.HOMES.size)
        assertEquals(listOf(0, 1, 2, 3), MahoganyHomes.LAYOUTS.map { it.tier }.distinct())
        MahoganyHomes.LAYOUTS.forEach { layout ->
            val home = MahoganyHomes.HOMES[layout.home]
            layout.entries.forEach { e ->
                val spot = home.spot(e.varbit)
                if (e.repair) {
                    assertTrue(spot.variants[1] in MahoganyHomesData.REPAIR_IDS, "${home.name} ${e.varbit} repair")
                } else {
                    assertTrue(spot.variants[3] in MahoganyHomesData.REMOVE_IDS, "${home.name} ${e.varbit} remove")
                    assertTrue(spot.variants[4] in MahoganyHomesData.BUILD_IDS, "${home.name} ${e.varbit} build")
                }
                assertTrue(e.count > 0)
            }
        }
    }

    @Test
    fun `carpenter's outfit - 0,4 0,8 0,6 0,2 percent per piece, 2,5 percent as a set, Construction only`() {
        assertEquals(1.0, MahoganyHomes.outfitBonus(player, Skills.CONSTRUCTION))
        equipment[0] = gg.rsmod.game.model.item.Item(MahoganyHomes.HELMET)
        assertEquals(1.004, MahoganyHomes.outfitBonus(player, Skills.CONSTRUCTION), 1e-9)
        equipment[1] = gg.rsmod.game.model.item.Item(MahoganyHomes.SHIRT)
        equipment[2] = gg.rsmod.game.model.item.Item(MahoganyHomes.TROUSERS)
        equipment[3] = gg.rsmod.game.model.item.Item(MahoganyHomes.BOOTS)
        assertEquals(1.025, MahoganyHomes.outfitBonus(player, Skills.CONSTRUCTION), 1e-9)
        assertEquals(1.0, MahoganyHomes.outfitBonus(player, Skills.WOODCUTTING))
    }

    @Test
    fun `saws - the saw, the crystal saw and Amy's saw (inventory or wielded) - only the crystal saw boosts`() {
        assertTrue(!MahoganyHomes.hasSaw(player))
        equipment[3] = gg.rsmod.game.model.item.Item(MahoganyHomes.AMYS_SAW)
        assertTrue(MahoganyHomes.hasSaw(player))
        assertEquals(0, MahoganyHomes.crystalSawBoost(player))
        inv.add(Items.CRYSTAL_SAW, 1)
        assertEquals(3, MahoganyHomes.crystalSawBoost(player))
    }
}
