package gg.rsmod.plugins.content.combat.strategy.ranged

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chinchompa fuse accuracy, target caps and wiring against the OSRS Wiki "Black chinchompa" page and the DPS calculator. */
class ChinchompasTests {
    @Test
    fun `fuse accuracy table by distance to the closest tile`() {
        // Wiki table: Short 100/75/50, Medium 75/100/75, Long 50/75/100 for 0-3 / 4-6 / 7+ squares.
        val expected = mapOf(0 to listOf(4, 3, 2), 1 to listOf(3, 4, 3), 2 to listOf(2, 3, 4))
        expected.forEach { (style, numerators) ->
            assertEquals(numerators[0], Chinchompas.accuracyNumerator(style, 3), "style $style at 3")
            assertEquals(numerators[1], Chinchompas.accuracyNumerator(style, 4), "style $style at 4")
            assertEquals(numerators[1], Chinchompas.accuracyNumerator(style, 6), "style $style at 6")
            assertEquals(numerators[2], Chinchompas.accuracyNumerator(style, 7), "style $style at 7")
        }
        // Closest tile of a 3x3 npc standing on (100,100): from (98,101) the closest tile is 2 squares away.
        assertEquals(2, Chinchompas.distanceToClosestTile(Tile(98, 101), Tile(100, 100), 3))
        assertEquals(0, Chinchompas.distanceToClosestTile(Tile(101, 101), Tile(100, 100), 3))
        assertEquals(5, Chinchompas.distanceToClosestTile(Tile(107, 100), Tile(100, 100), 3))
    }

    @Test
    fun `target caps and every chinchompa uses the shared model`() {
        assertEquals(12, Chinchompas.maxTargets(Items.BLACK_CHINCHOMPA, pvp = false))
        assertEquals(10, Chinchompas.maxTargets(Items.BLACK_CHINCHOMPA, pvp = true))
        assertEquals(11, Chinchompas.maxTargets(Items.RED_CHINCHOMPA_10034, pvp = false))
        assertEquals(9, Chinchompas.maxTargets(Items.CHINCHOMPA_10033, pvp = true))
        listOf(Items.CHINCHOMPA_10033, Items.RED_CHINCHOMPA_10034, Items.BLACK_CHINCHOMPA).forEach { assertTrue(Chinchompas.isChinchompa(it), "$it") }
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/RangedCombatStrategy.kt").readText()
        assertTrue("formula.getAccuracy(pawn, target, fuseFactor)" in strategy)
        assertTrue("landHit = landHit, delay = hitDelay, hitType = HitType.RANGE" in strategy, "secondary targets follow the primary roll")
    }
}
