package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.tools.importer.ObjectPlacementProbeTool
import gg.rsmod.plugins.api.cfg.Objs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RCV-011 Q-043-b: GWD access gates and altars against Void/Novite and the real 667 map. */
class GodWarsGatesTests {
    /** Novite `GodWars.inBandosPrepare`: the Bandos stronghold side of the big door. */
    private fun noviteInsideBandos(x: Int, z: Int) = x in 2823..2850 && z in 5313..5432

    @Test
    fun `bandos big door asks for strength and hammer from outside only, on the real map placement`() {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer))
        val xtea = File("../../data").walkTopDown().maxDepth(3).first { it.isFile && it.name.contains("xtea", ignoreCase = true) }.path
        try {
            ObjectPlacementProbeTool.main(arrayOf(File("../../data/cache").path, xtea, "name", "Big door"))
        } finally {
            System.setOut(original)
        }
        val doors =
            Regex("""PLACEMENT id=(\d+) .*?tile=(\d+),(\d+),(\d+)""").findAll(buffer.toString())
                .filter { it.groupValues[1].toInt() == Objs.BIG_DOOR_26384 }
                .map { Triple(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt()) }
                .toList()
        println("BANDOS_BIG_DOOR placements $doors")
        assertTrue(doors.isNotEmpty(), "Big door 26384 must be placed on the 667 map")
        doors.forEach { (doorX, doorZ, _) ->
            for (x in doorX - 20..doorX + 20) {
                if (x == doorX) continue
                assertEquals(!noviteInsideBandos(x, doorZ), GodWars.outsideBandosStronghold(x, doorX), "x=$x door=$doorX")
            }
            assertTrue(kotlin.math.abs(doorZ - 5334) <= 1, "door row next to the Novite walk tiles (z=$doorZ)")
        }
        assertTrue(noviteInsideBandos(GodWars.bandosDoorDestination(outside = true).x, 5334), "entering lands inside")
        assertFalse(noviteInsideBandos(GodWars.bandosDoorDestination(outside = false).x, 5334), "leaving lands outside")
    }

    @Test
    fun `altar refuses in Void's order with the sourced texts`() {
        assertEquals(GodWars.MSG_ALTAR_FULL, GodWars.altarRefusal(prayerFull = true, recharging = true, underAttack = true))
        assertEquals(GodWars.MSG_ALTAR_WAIT, GodWars.altarRefusal(prayerFull = false, recharging = true, underAttack = true))
        assertEquals(GodWars.MSG_ALTAR_COMBAT, GodWars.altarRefusal(prayerFull = false, recharging = false, underAttack = true))
        assertNull(GodWars.altarRefusal(prayerFull = false, recharging = false, underAttack = false))
    }

    @Test
    fun `altar clock is persisted, ticks offline and lasts ten minutes`() {
        assertEquals("gwd_altar_recharge", GodWars.ALTAR_RECHARGE_TIMER.persistenceKey)
        assertTrue(GodWars.ALTAR_RECHARGE_TIMER.tickOffline)
        assertTrue(GodWars.ALTAR_RECHARGE_TIMER.removeOnZero)
        assertEquals(10 * 60 * 1000 / 600, GodWars.ALTAR_RECHARGE_TICKS)
    }

    @Test
    fun `altar bonus counts worn items of the altar's god for every god`() {
        GodWars.God.values().filter { it.altarId != -1 }.forEach { god ->
            val aligned = god.keywords.map { "$it test item" }.filter { name -> god.excludedKeywords.none { name.contains(it) } }
            assertEquals(aligned.size, GodWars.altarBonus(god, aligned + "abyssal whip"), god.name)
            assertEquals(0, GodWars.altarBonus(god, listOf("abyssal whip", "rune platebody")), god.name)
        }
        assertEquals(0, GodWars.altarBonus(GodWars.God.ZAMORAK, listOf("zamorak brew(4)")), "brews are not god items")
    }

    @Test
    fun `zamorak river needs 70 current life points both ways`() {
        assertFalse(GodWars.canCrossZamorakRiver(69))
        assertTrue(GodWars.canCrossZamorakRiver(70))
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/areas/godwars/godwars_dungeon.plugin.kts").readText()
        assertTrue(plugin.contains("GodWars.canCrossZamorakRiver(player.getCurrentLifepoints())"))
        assertFalse(plugin.contains("Agility level of 70 to cross"), "the copied Novite Agility gate is gone")
        assertTrue(plugin.contains("GodWars.outsideBandosStronghold(") && plugin.contains("GodWars.ALTAR_RECHARGE_TIMER"))
    }
}
