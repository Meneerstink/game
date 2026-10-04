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
        assertTrue(noviteInsideBandos(GodWars.bandosDoorDestination(outside = true).x, 5333), "entering lands inside")
        assertFalse(noviteInsideBandos(GodWars.bandosDoorDestination(outside = false).x, 5333), "leaving lands outside")
    }

    /**
     * Owner 030006 (2026-10-04, "noclipped into the door then got stuck" at 2850,5334,2): the door is a west-facing wall at
     * 2851,5333; its crossing ([gg.rsmod.plugins.content.mechanics.doors.DoorCrossing]) runs between 2851,5333 and 2850,5333, and
     * both lie on open ground. Novite's 2850/2851,5334 are one-tile pockets beside the doorway on this map.
     */
    @Test
    fun `the Bandos door is crossed through its doorway onto open ground, never into the pockets beside it`() {
        val world = gg.rsmod.plugins.testutil.RealMapWorld.world
        val tile = gg.rsmod.game.model.Tile(2851, 5333, 2)
        val door =
            world.chunks.get(tile, true)!!.getEntities<gg.rsmod.game.model.entity.GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                .first { it.id == Objs.BIG_DOOR_26384 }
        val dc = gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
        for (outside in listOf(true, false)) {
            val far = GodWars.bandosDoorDestination(outside)
            val near = GodWars.bandosDoorDestination(!outside)
            assertEquals(far, dc.destination(near, door), "outside=$outside")
            val open = gg.rsmod.game.model.collision.Landing.OPEN_GROUND
            assertTrue(gg.rsmod.game.model.collision.Landing.pocket(world.collision, far).size >= open, "$far is open ground")
        }
        assertTrue(gg.rsmod.game.model.collision.Landing.pocket(world.collision, gg.rsmod.game.model.Tile(2850, 5334, 2)).size < 4, "Novite's tile is a pocket")
    }

    /**
     * Audit 2026-10-04 section 1: the chamber doors are crossed like Void `enterDoor` - through the doorway onto the far side -
     * instead of a teleport to a fixed chamber tile. For every god: the door is placed next to its chamber, the crossing from the
     * outside ends inside the chamber on open ground, and the way back from inside ends outside on open ground.
     */
    @Test
    fun `every chamber door is crossed through its doorway into the chamber, on open ground`() {
        val world = gg.rsmod.plugins.testutil.RealMapWorld.world
        val dc = gg.rsmod.plugins.content.mechanics.doors.DoorCrossing
        val landing = gg.rsmod.game.model.collision.Landing
        for (god in GodWars.God.values().filter { it.doorId != -1 }) {
            val c = god.chamberEntry
            val door =
                (-8..8).flatMap { dx -> (-8..8).map { dz -> gg.rsmod.game.model.Tile(c.x + dx, c.z + dz, c.height) } }.flatMap { t ->
                    world.chunks.get(t, true)!!.getEntities<gg.rsmod.game.model.entity.GameObject>(t, gg.rsmod.game.model.EntityType.STATIC_OBJECT)
                }.firstOrNull { it.id == god.doorId }
            assertTrue(door != null, "$god door ${god.doorId} near ${c.x},${c.z},${c.height}")
            val sides = listOf(door!!.tile, door.tile.step(dc.faceDirection(door.rot)))
            // Nex's door (Zaros) leads into the Ancient Prison, x >= 2900 (the door handler's own rule); the others into the chamber.
            val inChamber = { t: gg.rsmod.game.model.Tile -> if (god == GodWars.God.ZAROS) t.x >= 2900 else god.inChamber(t) }
            val inside = sides.single(inChamber)
            val outside = sides.single { !inChamber(it) }
            if (god != GodWars.God.ZAROS) assertEquals(god.chamberEntry, inside, "$god lands on its chamber entry")
            assertEquals(inside, dc.destination(outside, door), "$god in")
            assertEquals(outside, dc.destination(inside, door), "$god out")
            // The Ancient Prison's antechamber behind Nex's door is a closed room of 29 tiles (its old fixed entry 2900,5203 was a
            // blocked tile, pocket 1).
            val room = if (god == GodWars.God.ZAROS) 20 else landing.OPEN_GROUND
            sides.forEach { assertTrue(landing.pocket(world.collision, it).size >= room, "$god $it open ground") }
        }
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
