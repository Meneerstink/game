package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HomeDecorFileTests {
    private val design = HomeDecorFile.load(Paths.get("..", "..", "data", "cfg", "home_decor.txt"))

    @Test
    fun `the live home file parses and places each object once`() {
        assertTrue(design.placements.isNotEmpty())
        assertEquals(design.placements.size, design.placements.map { Triple(it.tile, it.type, it.id) }.distinct().size)
    }

    @Test
    fun `both south gate lanes stay walkable`() {
        val lanes = (3457..3469).flatMap { z -> listOf(3162, 3163, 3166, 3167).map { Tile(it, z, 0) } }
        val blocking = design.placements.filter { it.tile in lanes && (it.type in 0..3 || it.type == 10) }
        assertTrue(blocking.isEmpty(), "gate lanes blocked by $blocking")
    }

    @Test
    fun `parser reads every line form and rejects typos`() {
        val parsed =
            HomeDecorFile.parse(
                listOf("# c", "obj 1 10 20", "obj 2 10 20 1 4 3 # tail", "rect 3 0 0 2 2 0 10 0 2", "del 5 6", "del 5 6 0 10"),
            )
        assertEquals(HomeDecorFile.Placement(1, Tile(10, 20, 0)), parsed.placements[0])
        assertEquals(HomeDecorFile.Placement(2, Tile(10, 20, 1), 4, 3), parsed.placements[1])
        assertEquals(4, parsed.placements.count { it.id == 3 })
        assertEquals(listOf(HomeDecorFile.Removal(Tile(5, 6, 0)), HomeDecorFile.Removal(Tile(5, 6, 0), 10)), parsed.removals)
        assertFailsWith<IllegalStateException> { HomeDecorFile.parse(listOf("objj 1 2 3")) }
        val npcs = HomeDecorFile.parse(listOf("npc 7 3160 3480 e", "npc 8 3161 3480")).npcs
        assertEquals(listOf(HomeDecorFile.NpcPost(7, Tile(3160, 3480, 0), Direction.EAST), HomeDecorFile.NpcPost(8, Tile(3161, 3480, 0))), npcs)
        assertFailsWith<IllegalStateException> { HomeDecorFile.parse(listOf("npc 7 3160 3480 up")) }
    }
}
