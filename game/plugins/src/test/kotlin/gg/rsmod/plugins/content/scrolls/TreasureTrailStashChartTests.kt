package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class TreasureTrailStashChartTests {
    @Test fun `chart reads actual personal built and filled states without modifying storage`() {
        fun player() = mockk<Player>(relaxed = true).also { every { it.attr } returns AttributeMap() }
        val first = player(); val second = player()
        val unit = TreasureTrailStashCatalogue.current.units.first { u -> u.equipment.all { g -> g.first().existingLocal != null } }
        assertTrue(TreasureTrailStashChart.checkmarks(first).values.none { it })
        first.attr[unit.saved] = "[]"
        val marks = TreasureTrailStashChart.marks.filter { it.source == unit.sourceId }
        assertEquals(2, marks.size)
        assertTrue(TreasureTrailStashChart.checkmarks(first).getValue(marks.single { !it.filled }.component))
        assertFalse(TreasureTrailStashChart.checkmarks(first).getValue(marks.single { it.filled }.component))
        val json = Gson().toJson(unit.equipment.map { Item(it.first().existingLocal!!) })
        first.attr[unit.saved] = json
        assertTrue(marks.all { TreasureTrailStashChart.checkmarks(first).getValue(it.component) })
        assertTrue(TreasureTrailStashChart.checkmarks(second).values.none { it })
        assertEquals(json, first.attr[unit.saved])
        first.attr[unit.saved] = "invalid-storage"
        assertFalse(TreasureTrailStashChart.checkmarks(first).getValue(marks.single { it.filled }.component))
        assertEquals("invalid-storage", first.attr[unit.saved])
    }

    @Test fun `chart mapping and tabs cannot expose the excluded beginner tier or an unapplied import`() {
        assertEquals(listOf("easy", "medium", "hard", "elite", "master"), TreasureTrailStashChart.tiers.map { it.name })
        assertEquals("78", TreasureTrailStashChart.tiers.last().text)
        val text = "# STASH chart fixture; applied=true\ninterface\t493\t1300\ncontrol\t493\t2410\nscript\t1483\t6100\nsprite\t1210\t9000\n"
        assertEquals(1300, TreasureTrailStashChart.parseMapping(text).group)
        assertFailsWith<IllegalArgumentException> { TreasureTrailStashChart.parseMapping(text.replace("applied=true", "applied=false")) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailStashChart.parseMapping(text + "sprite\t1210\t9001\n") }
    }
}
