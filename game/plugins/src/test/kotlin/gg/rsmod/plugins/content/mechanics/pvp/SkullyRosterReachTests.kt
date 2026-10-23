package gg.rsmod.plugins.content.mechanics.pvp

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.util.ServerProperties
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N40 (LC16 10-16): the Falador east bank Skully and his chest stood on the grass outside the bank's west wall - 3008 is
 * outside, the wall line is that tile's east edge - so a customer could only reach him by leaving the bank. Every Skully stands
 * on his bank's customer side: a short walk (no detour through a door and around the building) from the site's anchor.
 */
class SkullyRosterReachTests {
    @Test
    fun `every Skully faces open floor on his bank's customer side, on the real map`() {
        val problems =
            SkullyRoster.SITES.mapNotNull { site ->
                WORLD.chunks.getOrCreate(site.anchor)
                WORLD.chunks.getOrCreate(site.wanted)
                site.clear.forEach { SkullyRoster.clearScenery(WORLD, it) } // the desks removed for him at world start
                val face = site.face ?: return@mapNotNull null
                val front = site.wanted.step(face)
                when {
                    !WORLD.collision.canStep(site.wanted, face) -> "${site.label}: ${site.wanted} faces $face into a wall or an object"
                    !reachable(site.anchor, front, MAX_STEPS) -> "${site.label}: the floor in front of him ($front) is not the customers' side of ${site.anchor}"
                    else -> null
                }
            }
        assertEquals(emptyList(), problems)
    }

    /** [to] is within [limit] steps of [from] (breadth-first over the server's own step rule). */
    private fun reachable(
        from: Tile,
        to: Tile,
        limit: Int,
    ): Boolean {
        var frontier = listOf(from)
        val seen = hashSetOf(from)
        repeat(limit + 1) {
            if (to in frontier) return true
            frontier =
                frontier.flatMap { t ->
                    Direction.RS_ORDER.mapNotNull { d -> t.step(d).takeIf { n -> WORLD.collision.canStep(t, d) && seen.add(n) } }
                }
        }
        return false
    }

    companion object {
        private const val MAX_STEPS = 12
        private lateinit var WORLD: World

        @BeforeClass
        @JvmStatic
        fun load() {
            val w = World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true))
            w.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            w.definitions.loadAll(w.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), w, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(w) as MutableList<Service>
            services += xteas
            WORLD = w
        }

        internal fun world(): World = WORLD

        @AfterClass
        @JvmStatic
        fun close() {
            WORLD.filestore.close()
        }
    }
}
