package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner "deadmanmode vervijning" 2026-09-17: the combat bracket is +/-14 everywhere (Wilderness
 * included), the HUD shows exactly that bracket, and the danger signs are grouped per opening.
 */
class DeadmanBracketTests {
    @Test
    fun `the bracket is 14 levels either side, clamped to real combat levels`() {
        assertEquals(14, AreaState.MAX_COMBAT_LEVEL_DIFFERENCE)
        assertEquals(74..102, AreaState.bracketOf(88))
        assertEquals(3..17, AreaState.bracketOf(3))
        assertEquals(124..138, AreaState.bracketOf(138))
        assertEquals("74-102", DeadmanHud.bracketText(88))
        assertEquals("3-17", DeadmanHud.bracketText(3))
    }

    @Test
    fun `the HUD zone text carries the label and the bracket`() {
        assertEquals("Guarded", DeadmanHud.TEXT_GUARDED)
        assertEquals("Deadman", DeadmanHud.TEXT_DANGEROUS)
        assertEquals('|', DeadmanHud.FIELD_SEPARATOR)
        assertEquals("Warning: You are entering a dangerous zone.", DeadmanHud.DANGER_WARNING)
        // Two fields in single-way combat, a third "M" field (the crossed swords) in a multicombat area.
        assertEquals("Deadman|74-102", DeadmanHud.zoneText(DeadmanHud.TEXT_DANGEROUS, 88, multi = false))
        assertEquals("Deadman|74-102|M", DeadmanHud.zoneText(DeadmanHud.TEXT_DANGEROUS, 88, multi = true))
        assertEquals("Level: 3|3-17|M", DeadmanHud.zoneText("Level: 3", 3, multi = true))
    }

    @Test
    fun `the skull HUD time rounds up to the next half minute`() {
        assertEquals("5:00", DeadmanHud.formatHalfMinutes(PvpSkull.SKULL_DURATION_CYCLES))
        assertEquals("4:30", DeadmanHud.formatHalfMinutes(449))
        assertEquals("0:30", DeadmanHud.formatHalfMinutes(1))
        assertEquals("0:00", DeadmanHud.formatHalfMinutes(0))
    }

    @Test
    fun `the safe-zone predicate is the same one for the HUD, the guards and the PvP gate`() {
        val varrockSquare = Tile(3212, 3428, 0)
        val edgeville = Tile(3094, 3491, 0)
        assertFalse(AreaState.isDangerous(varrockSquare))
        assertTrue(AreaState.isDangerous(edgeville))
        assertEquals(CityGuards.isGuardedZone(varrockSquare), !AreaState.isDangerous(varrockSquare))
        assertEquals(AreaState.isPvpAllowed(edgeville, varrockSquare), AreaState.isDangerous(edgeville))
    }

    @Test
    fun `danger-sign crossings are grouped per opening and the sign faces outward`() {
        val gate = listOf(Tile(3000, 3400, 0), Tile(3001, 3400, 0), Tile(3002, 3400, 0)).map { DangerSigns.Crossing(it, Direction.NORTH) }
        val road = listOf(Tile(3050, 3400, 0), Tile(3051, 3400, 0)).map { DangerSigns.Crossing(it, Direction.NORTH) }
        val groups = DangerSigns.group(gate + road)
        assertEquals(2, groups.size)
        assertEquals(setOf(3, 2), groups.map { it.size }.toSet())
        assertEquals(2, DangerSigns.rotationFacing(Direction.NORTH))
        assertEquals(0, DangerSigns.rotationFacing(Direction.SOUTH))
    }
}
