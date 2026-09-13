package gg.rsmod.plugins.content.mechanics.objteleports

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sourced object-teleport table (converted from Void's teles data) against the real revision-667
 * object definitions: every entry that survives validation advertises its option in this cache, and
 * the Chaos Tunnels roster (all rifts, exits and the 109 portals) survives completely.
 */
class ObjectTeleportsTests {
    @Test
    fun `destination follows Void semantics for to, delta and near`() {
        val from = Tile(3100, 3500, 0)
        val fixed = ObjectTeleports.Entry(id = 1, option = "Enter", tile = point(0, 0), to = point(3293, 5480))
        assertEquals(Tile(3293, 5480, 0), ObjectTeleports.destination(fixed, from))

        val shifted = ObjectTeleports.Entry(id = 1, option = "Climb-down", tile = point(0, 0), delta = ObjectTeleports.Point(52, 6407, 0))
        assertEquals(Tile(3152, 9907, 0), ObjectTeleports.destination(shifted, from))

        val upstairs = ObjectTeleports.Entry(id = 1, option = "Climb-up", tile = point(0, 0), delta = ObjectTeleports.Point(0, 0, 1))
        assertEquals(Tile(3100, 3500, 1), ObjectTeleports.destination(upstairs, from))

        val near = ObjectTeleports.Entry(id = 1, option = "Enter", tile = point(0, 0), to = point(3090, 3510), near = ObjectTeleports.Area(3, 2))
        assertEquals(Tile(3092, 3510, 0), ObjectTeleports.destination(near, from))
    }

    @Test
    fun `an entry only matches its own id, tile and option`() {
        ObjectTeleports.load(listOf(ObjectTeleports.Entry(id = 28892, option = "Enter", tile = point(3165, 3561), to = point(3293, 5480))))
        val tile = Tile(3165, 3561, 0)
        assertNotNull(ObjectTeleports.find(tile, setOf(28892), "enter"))
        assertNull(ObjectTeleports.find(tile, setOf(28891), "Enter"))
        assertNull(ObjectTeleports.find(tile, setOf(28892), "Examine"))
        assertNull(ObjectTeleports.find(Tile(3165, 3561, 1), setOf(28892), "Enter"))
    }

    @Test
    fun `the whole table validates against the 667 cache and the Chaos Tunnels roster is complete`() {
        val path = Paths.get("..", "..", "data", "cfg", "object-teleports", "object-teleports.json")
        val loaded = ObjectTeleports.load(path)
        assertTrue(loaded > 1000, "table should hold the converted Void roster, was $loaded")

        val rejected = ObjectTeleports.retainValid { id -> definitions.getNullable(ObjectDef::class.java, id)?.options?.toList() }
        rejected.forEach { println("rejected: ${it.name} (${it.id}) ${it.option} at ${it.tile} from ${it.source}") }
        println("object teleports: ${ObjectTeleports.size} accepted, ${rejected.size} rejected")

        val chaosRejected = rejected.filter { it.source?.contains("chaos_tunnels") == true }
        assertTrue(chaosRejected.isEmpty(), "Chaos Tunnels entries rejected: $chaosRejected")
        val rifts = listOf(28891, 28892, 28893)
        // The five rifts actually placed in the revision-667 map (runObjectPlacementProbeTool "name rift").
        // 3130,3588 and 3176,3585 are not in Void's older data; the owner reported the level-9 rift dead.
        val riftTiles = listOf(Tile(3059, 3550), Tile(3119, 3571), Tile(3130, 3588), Tile(3165, 3561), Tile(3176, 3585))
        riftTiles.forEach { tile ->
            assertNotNull(ObjectTeleports.find(tile, rifts, "Enter"), "rift at $tile has no accepted entry")
        }
    }

    private fun point(
        x: Int,
        z: Int,
    ) = ObjectTeleports.Point(x, z, 0)

    companion object {
        private val definitions = DefinitionSet()

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            assertNotEquals(0, definitions.getCount(ObjectDef::class.java))
        }
    }
}
