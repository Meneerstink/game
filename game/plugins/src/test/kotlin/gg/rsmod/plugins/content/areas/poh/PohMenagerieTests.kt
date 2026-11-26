package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.ownership.Possessions
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CONSTRUCTION-99 deel 6: the menagerie room and its pieces carry the OSRS numbers, a habitat fills its 66 floor tiles with its
 * own locs, and a pet stored in the pet house still counts as owned (duplicate pet rule, Perdu).
 */
class PohMenagerieTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>()
    private val attr = AttributeMap()
    private val containers = hashMapOf(INVENTORY_KEY to ItemContainer(definitions, INVENTORY_KEY))
    private val player = mockk<Player>().also { p ->
        every { p.world } returns world
        every { p.attr } returns attr
        every { p.containers } returns containers
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
    }

    @Test
    fun `menagerie is the OSRS room - level 37, 30,000 coins, outdoors on the ground floor, one per house`() {
        val m = PohRooms.RoomType.MENAGERIE
        assertEquals(37, m.level)
        assertEquals(30_000, m.cost)
        assertEquals(PohRooms.Kind.LAND, m.kind)
        assertTrue(m.buildableOn(PohRooms.GROUND_FLOOR))
        assertFalse(m.buildableOn(PohRooms.UPSTAIRS))
        assertEquals(PlayerHouse.Style.WHITEWASHED_STONE.chunk(7, 2), m.template(PlayerHouse.Style.WILDERNESS))
        assertTrue(PohRooms.onlyOne(player, m))
        PohRooms.addRoom(player, PohRooms.Built(m, 0, 0, 0, 0))
        assertFalse(PohRooms.onlyOne(player, m))
        assertTrue(PohRooms.onlyOne(player, PohRooms.RoomType.PARLOUR))
    }

    @Test
    fun `each pet house costs the previous house plus its OSRS upgrade row`() {
        val houses = listOf("OAK_PET_HOUSE", "TEAK_PET_HOUSE", "MAHOGANY_PET_HOUSE", "CONSECRATED_PET_HOUSE", "DESECRATED_PET_HOUSE", "NATURAL_PET_HOUSE")
        assertEquals(houses.toSet(), PohMenagerie.PET_HOUSES)
        assertEquals(listOf(37, 48, 59, 70, 81, 92), houses.map { PohFurniture.DECOS.getValue(it).level })
        houses.zipWithNext().forEach { (prev, next) ->
            val p = PohFurniture.DECOS.getValue(prev)
            val n = PohFurniture.DECOS.getValue(next)
            val up = PohMenagerie.UPGRADES.getValue(next)
            assertEquals(n.level, up.level, next)
            assertEquals(n.xp, p.xp + up.xp, next)
            val sum = (p.materials + up.materials).groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
            assertEquals(n.materials.toMap(), sum, next)
        }
        val hotspot = assertNotNull(PohFurniture.hotspotOf(44909))
        assertEquals(houses, hotspot.decos.map { it?.key })
    }

    @Test
    fun `a habitat gives every one of the 66 floor tiles its own loc`() {
        val h = assertNotNull(PohFurniture.hotspotOf(44908))
        assertEquals("HABITAT", h.type)
        assertEquals((44843..44908).toList(), h.ids.toList())
        assertEquals(setOf("GARDEN_HABITAT", "JUNGLE_HABITAT", "DESERT_HABITAT", "POLAR_HABITAT", "VOLCANIC_HABITAT"), PohFurniture.HABITAT_DECOS)
        h.decos.filterNotNull().forEach { assertEquals(66, it.objs.size, it.key) }
        val jungle = PohFurniture.DECOS.getValue("JUNGLE_HABITAT")
        assertEquals(44569, PohFurniture.habitatObj(h, jungle, 44848))
        assertEquals(44569, PohRooms.placedObj("JUNGLE_HABITAT#5", PlayerHouse.Style.BASIC_WOOD))
        assertEquals(44563, PohRooms.placedObj("GARDEN_HABITAT#65", PlayerHouse.Style.FANCY_STONE))
        assertEquals(44500, PohRooms.placedObj("GARDEN_HABITAT#0", PlayerHouse.Style.FANCY_STONE))
        assertEquals(44827, PohRooms.placedObj("VOLCANIC_HABITAT#65", PlayerHouse.Style.WILDERNESS))
        // OSRS: no tools for the desert, polar and volcanic habitats; a watering can for the planted two.
        assertEquals("", PohFurniture.DECOS.getValue("POLAR_HABITAT").tools)
        assertTrue(PohFurniture.DECOS.getValue("GARDEN_HABITAT").watering)
    }

    @Test
    fun `a pet in the pet house still counts as owned`() {
        val house = PohMenagerie.container(player)
        house.add(23940, 1)
        assertEquals(1, PohMenagerie.stored(player))
        assertEquals("your menagerie", Possessions.whereOwned(player, setOf(23940)))
    }
}
