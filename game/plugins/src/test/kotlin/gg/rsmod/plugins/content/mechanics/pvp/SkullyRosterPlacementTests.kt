package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the shared Skully placement rule.
 *
 * The rule changed on 2026-09-20. It used to be "an owner tile is used only if it is also reachable from the
 * customer side and pinned against a wall, otherwise snap to the nearest wall tile" - and that snapping is
 * exactly what put Skully inside a bank booth at Edgeville, Falador and Camelot, four tiles from where the owner
 * had asked for him. An owner-named coordinate now wins outright, so the tests below pin the coordinates
 * themselves rather than the heuristic that used to override them.
 */
class SkullyRosterPlacementTests {
    private fun site(label: String) = SkullyRoster.SITES.first { it.label == label }

    @Test
    fun `every owner-named tile is marked exact so nothing can snap it away`() {
        val ownerNamed =
            mapOf(
                // Owner 2026-09-23: Edgeville and Camelot moved onto the removed desks (task 1 / picture "l").
                "Edgeville bank" to Tile(3092, 3488, 0),
                "Falador west bank" to Tile(2943, 3371, 0),
                "Seers' Village bank" to Tile(2721, 3491, 0),
                "Ardougne south bank" to Tile(2653, 3280, 0),
            )
        ownerNamed.forEach { (label, tile) ->
            val site = site(label)
            assertEquals(tile, site.wanted, "$label is not on the tile the owner asked for")
            assertTrue(site.exact, "$label must be exact, or the wall heuristic will move him")
        }
    }

    @Test
    fun `the owner-named facing is kept`() {
        // Owner 2026-09-23: back to the south wall where the desk stood, facing into the bank (north).
        assertEquals(Direction.NORTH, site("Edgeville bank").face)
    }

    @Test
    fun `an exact site is placed verbatim, with no wall or reachability veto`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/SkullyRoster.kt").readText()
        val exactBranch = source.substringAfter("if (site.exact) {").substringBefore("return Placement(site, site.wanted")
        assertTrue(
            "isAgainstWall(world, site.wanted)" !in exactBranch,
            "an owner tile must not be vetoed by the wall heuristic",
        )
        assertTrue(
            "site.wanted in reachable" !in exactBranch,
            "an owner tile must not be vetoed by the reachability search",
        )
        assertTrue(
            "Placement(site, site.wanted" in source,
            "an exact site must place Skully on the owner's tile itself",
        )
    }

    @Test
    fun `the chest never lands inside an object`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/SkullyRoster.kt").readText()
        val exactBranch = source.substringAfter("if (site.exact) {").substringBefore("return Placement(site, site.wanted")
        assertTrue(
            "!world.collision.isClipped(it)" in exactBranch,
            "the chest beside an exact Skully must still be on an unclipped tile",
        )
    }

    @Test
    fun `sites without an owner tile still prefer a wall`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/SkullyRoster.kt").readText()
        assertTrue(
            "filter { isAgainstWall(world, it) }" in source,
            "the heuristic path must still pin un-named sites against a wall",
        )
    }

    @Test
    fun `every site has a distinct tile`() {
        val tiles = SkullyRoster.SITES.map { it.wanted }
        assertEquals(tiles.size, tiles.toSet().size, "two Skully sites share a tile: $tiles")
    }
}
