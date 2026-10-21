package gg.rsmod.plugins.api

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * N87 (side finding N25 round 2: after a Nex test the tiles 2923,5201-5205 - the edge of the "Contain this!" ring - stayed
 * blocked, "I can't reach that!"). World.spawnTemporaryObject remembered the object it was given but spawned a COPY
 * (`spawn(DynamicObject(obj))`); when the timer ran out World.cycle removed the original, which was never in the chunk, so the
 * copy and its collision stayed on the server for good (the client got the LOC_DEL and showed the tile empty).
 * Darkan World.spawnObjectTemporary (spawnObject(obj) + a task that removes THAT object) and Void (GameObjects.add with
 * ticks -> remove of the same GameObject) remove exactly the loc they spawned.
 *
 * Class test: every shape of call in the game (11 plugin call sites) - a temporary loc on an empty tile (Nex ice-prison
 * stalagmite 57263, Nex shadow trap 57261, a Mithril-seeds flower 2980: Nex, flower_seeds, ::tempobj) and a temporary loc that
 * replaces a loc and puts it back (web 733 -> slashed web 734: web, Draynor coffin, Living Rock deposits, obelisks, Void
 * objects). When the timer runs out the tile holds exactly what it held before, objects AND collision.
 */
class TemporaryObjectRemovalTests {
    @Test
    fun `a temporary loc on an empty tile leaves no loc and no collision behind`() {
        listOf(Objs.STALAGMITE_57263, Objs.SHADOW_57261, FLOWER).forEachIndexed { i, id ->
            val tile = Tile(2921 + i, 5203)
            val before = world.collision.isClipped(tile)
            world.spawnTemporaryObject(DynamicObject(id, 10, 0, tile), 4)
            assertTrue("$id: spawned on $tile", id in locs(tile))
            cycle(5)
            assertTrue("$id: the temporary loc is gone from the server's chunk after its 4 ticks: ${locs(tile)}", id !in locs(tile))
            assertEquals("$id: the collision of $tile is back to before (clipped=$before)", before, world.collision.isClipped(tile))
        }
    }

    @Test
    fun `a temporary loc that replaces a loc gives exactly the old loc back`() {
        val tile = Tile(2920, 5205)
        val old = DynamicObject(Objs.WEB, 0, 0, tile)
        world.spawn(old)
        val clippedWithOld = world.collision.isClipped(tile)
        world.spawnTemporaryObject(DynamicObject(Objs.SLASHED_WEB, 0, 0, tile), 3, old)
        assertTrue("slashed web replaced the web: ${locs(tile)}", Objs.SLASHED_WEB in locs(tile) && Objs.WEB !in locs(tile))
        cycle(4)
        assertTrue("the web is back on $tile and the slashed copy is gone: ${locs(tile)}", Objs.WEB in locs(tile) && Objs.SLASHED_WEB !in locs(tile))
        assertEquals("the web's own collision", clippedWithOld, world.collision.isClipped(tile))
        world.remove(world.chunks.getOrCreate(tile).getEntities<GameObject>(tile, EntityType.DYNAMIC_OBJECT).first())
    }

    private fun locs(tile: Tile): List<Int> =
        world.chunks.getOrCreate(tile).getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).map { it.id }

    private fun cycle(n: Int) = repeat(n) { worldCycle.invoke(world) }

    companion object {
        /** flower_seeds.plugin.kts Flower: the first Mithril-seeds flower loc. */
        const val FLOWER = 2980

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            world.collision.isClipped(Tile(2923, 5203)) // loads the Nex arena region
            world
        }

        private val worldCycle: java.lang.reflect.Method by lazy {
            World::class.java.declaredMethods.first { it.name.startsWith("cycle") && it.parameterCount == 0 }.apply { isAccessible = true }
        }
    }
}
