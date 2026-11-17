package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.impl.IfSetEventsMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import io.mockk.*
import kotlin.test.*

class TreasureTrailMapsTests {
    @AfterTest fun cleanup() = unmockkAll()

    @Test fun `all twenty six source illustrations resolve only with their verified import`() {
        val maps = TreasureTrailStepCatalogue.current.rows.filter { it.kind == "MAP" }
        assertEquals(26, maps.size)
        assertEquals(maps.map { it.key }.toSet(), TreasureTrailMaps.sources.map { it.key }.toSet())
        for (row in maps) {
            assertNull(TreasureTrailMaps.interfaceOf(row) { null })
            val expected = TreasureTrailMaps.sources.single { it.key == row.key }
            assertEquals(expected.group + 1000, TreasureTrailMaps.interfaceOf(row) { it + 1000 })
        }
        assertNull(TreasureTrailMaps.interfaceOf(TreasureTrailStepCatalogue.current.rows.first { it.kind != "MAP" }) { it })
    }

    @Test fun `reading mounts the imported drawing and enables its real Close without showing the solution`() {
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        mockkObject(ClueInterfaces)
        val player = mockk<Player>(relaxed=true)
        every { player.openInterface(any<Int>(), any<InterfaceDestination>(), any<Boolean>()) } just Runs
        for (row in TreasureTrailStepCatalogue.current.rows.filter { it.kind == "MAP" }) {
            val map = TreasureTrailMaps.sources.single { it.key == row.key }
            val local = map.group+1000
            every { ClueInterfaces.of(map.group) } returns local
            assertTrue(TreasureTrailMaps.read(player, TreasureTrailSourceStep(row)))
            verify { player.openInterface(local, InterfaceDestination.MAIN_SCREEN, false) }
            verify { player.write(IfSetEventsMessage((local shl 16) or map.close, -1, -1, 0x2)) }
        }
        verify(exactly=0) { ClueInterfaces.text(any(), any()) }
        val missing = mockk<Player>(relaxed=true)
        every { ClueInterfaces.of(any()) } returns null
        val row = TreasureTrailStepCatalogue.current.rows.first { it.kind == "MAP" }
        assertTrue(TreasureTrailMaps.read(missing, TreasureTrailSourceStep(row)))
        verify(exactly=0) { missing.openInterface(any<Int>(), any<InterfaceDestination>(), any<Boolean>()) }
        verify(exactly=0) { ClueInterfaces.text(any(), any()) }
    }

    @Test fun `each dig map needs its exact plane location and held clue before advancing`() {
        mockkObject(ClueScrollManager)
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        for (row in TreasureTrailStepCatalogue.current.rows.filter { it.kind == "MAP" && it.action == TreasureTrailStepCatalogue.Action.DIG }) {
            val world = mockk<World>(relaxed=true)
            every { world.definitions } returns definitions
            val player = mockk<Player>(relaxed=true)
            val inventory = ItemContainer(definitions, 28, ContainerStackType.NORMAL)
            every { player.world } returns world
            every { player.attr } returns AttributeMap()
            every { player.inventory } returns inventory
            val step = TreasureTrailSourceStep(row)
            val scroll = Item(row.tier.scrollId).putAttr(ItemAttribute.CLUE_STEP_KEY, row.id)
            inventory[0] = scroll
            every { ClueScrollManager.activeStep(player, any()) } returns null
            every { ClueScrollManager.activeStep(player, row.tier) } returns step
            every { ClueScrollManager.progress(player, row.tier) } returns 0
            every { ClueScrollManager.advance(player, row.tier, scroll, 0, emptyList()) } returns true
            val tile = row.points.single()
            every { player.tile } returns Tile(tile.x+1, tile.z, tile.height)
            assertFalse(TreasureTrailBasicSteps.dig(player))
            every { player.tile } returns Tile(tile.x, tile.z, (tile.height+1)%4)
            assertFalse(TreasureTrailBasicSteps.dig(player))
            every { player.tile } returns tile
            inventory[0] = null // A banked/dropped scroll cannot satisfy the captured step.
            assertFalse(TreasureTrailBasicSteps.dig(player))
            inventory[0] = scroll
            assertTrue(TreasureTrailBasicSteps.dig(player))
            verify(exactly=1) { ClueScrollManager.advance(player, row.tier, scroll, 0, emptyList()) }
        }
    }
}
