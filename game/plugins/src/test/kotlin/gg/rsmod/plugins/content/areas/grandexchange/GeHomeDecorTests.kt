package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeHomeDecorTests {
    @Test
    fun `south gate is mirrored and keeps both approaches open`() {
        val placements = GeHomeDecor.SOUTH_GATE
        assertEquals(placements.size, placements.map { it.type to it.tile }.distinct().size)
        assertTrue(placements.none { it.tile in GeHomeDecor.SOUTH_APPROACH })

        val mirrored = placements.groupingBy { it.objectId to it.tile.z }.eachCount()
        assertTrue(mirrored.values.all { it % 2 == 0 })
        placements.forEach { placement ->
            assertTrue(
                placements.any {
                    it.objectId == placement.objectId &&
                        it.tile.z == placement.tile.z &&
                        it.tile.x == GeHomeDecor.CENTRE_X2 - placement.tile.x
                },
                "${placement.objectId} at ${placement.tile} has no mirror across the south entrance",
            )
        }
    }

    @Test
    fun `danger signs precede safe side landmarks`() {
        val danger = GeHomeDecor.SOUTH_GATE.filter { it.objectId == GeHomeDecor.DANGER_SIGN }
        val welcome = GeHomeDecor.SOUTH_GATE.filter { it.objectId == GeHomeDecor.VARROCK_STATUE }
        assertTrue(danger.all { it.tile.z < GeHomeDecor.SAFE_EDGE_Z })
        assertTrue(welcome.all { it.tile.z > GeHomeDecor.SAFE_EDGE_Z })
        assertTrue(danger.none { GuardedZones.contains(it.tile) })
        assertTrue(welcome.all { GuardedZones.contains(it.tile) })
        assertTrue(GeHomeDecor.SOUTH_APPROACH.all { GuardedZones.contains(it) })
    }
}
