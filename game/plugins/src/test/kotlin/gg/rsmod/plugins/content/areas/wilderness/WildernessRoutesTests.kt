package gg.rsmod.plugins.content.areas.wilderness

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaves
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import gg.rsmod.game.model.Direction

/** Read-only production collision decoding; no server, accounts or cache writes. */
class WildernessRoutesTests {
    companion object {
        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            val w = World(ctx, mockk<DevContext>(relaxed = true))
            w.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toString())
            w.definitions.loadAll(w.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), w, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(w) as MutableList<Service>
            services += xteas
            w
        }
    }

    @Test fun `cave landings and both sides of every pillar are open ground`() {
        val tiles = RevenantCaves.ENTRANCES.values + RevenantCaves.EXITS.values +
            RevenantCaves.PILLARS.flatMap { listOf(it.sideA, it.sideB) } + Tile(3187, 10127) + Tile(3151, 3635)
        val bad = tiles.filter { world.collision.isClipped(it) || Landing.pocket(world.collision, it).size < Landing.OPEN_GROUND }
        bad.forEach { println("BAD landing $it clipped=${world.collision.isClipped(it)} pocket=${Landing.pocket(world.collision,it).size} resolved=${Landing.resolve(world.collision,it)}") }
        assertTrue(bad.isEmpty(), "blocked or enclosed landing(s): $bad")
    }

    @Test fun `all entrances and pillar shores connect to an exit through the real collision map`() {
        val stairs = listOf(Tile(3218, 10058), Tile(3244, 10215))
        val starts = stairs.flatMap { t -> Direction.NESW.map { t.step(it) } }.filterNot { world.collision.isClipped(it) }
        val seen = starts.toMutableSet()
        val queue = ArrayDeque(starts)
        while (queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            val adjacent = Direction.NESW.filter { world.collision.canStep(tile, it) }.map { tile.step(it) } +
                RevenantCaves.PILLARS.mapNotNull { RevenantCaves.landing(it, tile) }
            adjacent.filter { gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaveFee.inCaves(it) }
                .forEach { if (seen.add(it)) queue.add(it) }
        }
        val arrivals = RevenantCaves.ENTRANCES.values + RevenantCaves.PILLARS.flatMap { listOf(it.sideA, it.sideB) }
        assertTrue(arrivals.all { it in seen }, "cannot escape: ${arrivals.filterNot { it in seen }}")
    }

    @Test fun `N528 enclosed report tile and obsolete entrances recover to open connected ground`() {
        listOf(Tile(3207, 10132), Tile(3196, 10056), Tile(3241, 10234)).forEach { old ->
            val corrected = Landing.resolve(world.collision, old)
            assertFalse(world.collision.isClipped(corrected), "$old -> $corrected")
            assertTrue(Landing.pocket(world.collision, corrected).size >= Landing.OPEN_GROUND, "$old -> $corrected")
        }
    }

    @Test fun `all retired regions are evacuated but current caves and ordinary Wilderness are retained`() {
        RetiredRevenantDungeon.REGIONS.forEach { id ->
            for (level in 0..3) assertTrue(RetiredRevenantDungeon.contains(Tile((id shr 8) * 64 + 20, (id and 255) * 64 + 20, level)))
        }
        RevenantCaves.ENTRANCES.values.forEach { assertFalse(RetiredRevenantDungeon.contains(it)) }
        assertFalse(RetiredRevenantDungeon.contains(Tile(3039, 3765)))
        assertTrue(Landing.pocket(world.collision, RetiredRevenantDungeon.SAFE_EXIT).size >= Landing.OPEN_GROUND)
    }
}
