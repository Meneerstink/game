package gg.rsmod.plugins.content.inter.equipmentstats

import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentStatsTextLayoutTests {
    @Test
    fun `all item-stat columns keep exactly the same line count`() {
        val layout = EquipmentStatsTextLayout()
        layout.title("Attack bonus")
        layout.row("Stab", "+0")
        layout.row("Slash", "+0")
        layout.title("Defence bonus")
        layout.row("Stab", "+1")
        layout.title("Other bonuses")
        layout.row("Magic Dmg.", "+0.0%")

        val columns = layout.columns()
        val lineCounts =
            listOf(columns.titles, columns.names, columns.values).map { column ->
                "<br>".toRegex().findAll(column).count()
            }

        assertEquals(listOf(7, 7, 7), lineCounts)
        assertEquals(
            "Attack bonus<br><br><br>Defence bonus<br><br>Other bonuses<br><br>",
            columns.titles,
        )
        assertEquals(
            "<br>Stab:<br>Slash:<br><br>Stab:<br><br>Magic Dmg.:<br>",
            columns.names,
        )
    }
}
