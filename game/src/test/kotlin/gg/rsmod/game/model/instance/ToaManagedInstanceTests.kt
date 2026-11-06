package gg.rsmod.game.model.instance

import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.entity.Npc
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

/** Real allocator/chunks/collision, synthetic source data: no cache writes or dependency on a moving local cache. */
class ToaManagedInstanceTests {
    private fun world(): World {
        val context = GameContext(false, "ToA isolation", 667, 600, 8, Tile(3200, 3200), 25, 10, false, 100, 100, false, false, 0)
        val world = World(context, DevContext(false, false, false, false, false, false, false))
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ObjectDef::class.java, any()) } answers { ObjectDef(secondArg()) }
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
        World::class.java.getDeclaredField("definitions").apply { isAccessible = true }.set(world, definitions)
        return world
    }

    private fun allocate(world: World, managed: Boolean): InstancedMap {
        val source = Tile(4096, 6656)
        val b = InstancedChunkSet.Builder()
        for (h in 0..3) b.set(0, 0, h, copy = source.transform(0, 0, h))
        val config = InstancedMapConfiguration.Builder().setExitTile(Tile(3359, 9114))
        if (managed) config.addAttribute(InstancedMapAttribute.MANAGED_LIFETIME)
        return world.instanceAllocator.allocate(world, b.build(), config.build())!!
    }

    @Test fun `empty managed raid survives scans until content explicitly releases it`() {
        val world = world()
        val raid = allocate(world, true)
        val normal = allocate(world, false)
        repeat(60) { world.instanceAllocator.cycle(world) }
        assertSame(raid, world.instanceAllocator.getMap(raid.area.bottomLeft))
        assertNull(world.instanceAllocator.getMap(normal.area.bottomLeft))
        world.instanceAllocator.release(world, raid)
        assertEquals(0, world.instanceAllocator.activeMapCount)
        world.instanceAllocator.release(world, raid) // duplicate completion/reward callback
        assertEquals(0, world.instanceAllocator.activeMapCount)
    }

    @Test fun `parallel groups share no scene objects or collision and release affects only its group`() {
        val world = world()
        val source = Tile(4098, 6658, 1)
        world.chunks.getOrCreate(source).addEntity(world, StaticObject(1, 10, 0, source), source)
        val a = allocate(world, true)
        val b = allocate(world, true)
        val at = a.area.bottomLeft.transform(2, 2, 1)
        val bt = b.area.bottomLeft.transform(2, 2, 1)
        val ao = world.chunks.getOrCreate(at).getEntities<StaticObject>(EntityType.STATIC_OBJECT).single()
        val bo = world.chunks.getOrCreate(bt).getEntities<StaticObject>(EntityType.STATIC_OBJECT).single()
        assertNotSame(ao, bo)
        assertEquals(at, ao.tile)
        assertEquals(bt, bo.tile)
        val before = world.chunks.getOrCreate(bt).getMatrix(1).get(2, 2)
        assertTrue(before != 0)
        val an = Npc(1, at.transform(1, 0), world)
        val bn = Npc(1, bt.transform(1, 0), world)
        world.npcs.add(an)
        world.npcs.add(bn)
        world.instanceAllocator.release(world, a)
        assertFalse(world.npcs.contains(an))
        assertTrue(world.npcs.contains(bn))
        assertNull(world.chunks.get(at, false))
        assertEquals(before, world.chunks.getOrCreate(bt).getMatrix(1).get(2, 2))
        assertSame(b, world.instanceAllocator.getMap(bt))
        assertSame(bo, world.chunks.getOrCreate(bt).getEntities<StaticObject>(EntityType.STATIC_OBJECT).single())
    }
}
