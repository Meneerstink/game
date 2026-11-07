package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.*
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.plugins.content.activity.toa.*
import io.mockk.*
import kotlin.test.*

/** Use real World.spawn/remove, chunks and collision: a flat entity mock cannot reproduce restored-copy leaks. */
class ToaPuzzleScopeTests {
    @Test fun `clear and wipe remove every owned loc state and restore only the original map loc`() {
        for (withOriginal in listOf(false, true)) for (clear in listOf(false, true)) {
            val context = mockk<GameContext>(relaxed = true).also { every { it.playerLimit } returns 2 }
            val world = World(context, mockk<DevContext>(relaxed = true))
            val definitions = mockk<DefinitionSet>(relaxed = true)
            //World accesses its own backing fields directly. Install only synthetic definitions; keep the actual world/containers.
            //The mocked createRegion is a no-op: this test never opens a cache, config, service or network port.
            World::class.java.getDeclaredField("definitions").apply { isAccessible = true }.set(world, definitions)
            val chunks = world.chunks
            val collision = world.collision
            val tile = Tile(6420, 20)
            every { definitions.get(ObjectDef::class.java, any()) } answers {
                ObjectDef(secondArg()).also { it.blockwalk = if (secondArg<Int>() == 100) 0 else 2 }
            }
            if (withOriginal) world.spawn(StaticObject(100, 10, 0, tile))
            val map = mockk<InstancedMap>(relaxed = true)
            every { map.area } returns Area(6400, 0, 6848, 448)
            val registration = ToaRooms.replaceForTest(ToaRoomKey(ToaPath.SCABARAS, false)) { object : ToaRoom {} }
            val raid = ToaRaid(1, listOf(PlayerUID(1)), ToaRaidSettings(), map, world)
            try {
                val run = assertNotNull(raid.progress.challenge(ToaPath.SCABARAS, 0))
                val scope = assertNotNull(raid.scope(run))
                //Repeated barrier/pressure-plate/cloud states occupy one tile and one client loc layer.
                scope.spawn(DynamicObject(101, 10, 0, tile))
                scope.spawn(DynamicObject(102, 10, 0, tile))
                scope.spawn(DynamicObject(103, 10, 0, tile))
                val entities = { chunks.getOrCreate(tile).getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT) }
                assertEquals(listOf(103), entities().map { it.id })
                assertTrue(collision.isBlocked(tile, Direction.EAST, projectile = false))
                if (clear) assertTrue(run.clear(1))
                else { assertTrue(run.wipe(1)); raid.progress.cycle(2) }
                assertEquals(if (withOriginal) listOf(100) else emptyList(), entities().map { it.id },
                    "owned intermediate states must never be copied back into the world")
                assertFalse(collision.isBlocked(tile, Direction.EAST, projectile = false), "no invisible collision after cleanup")
                assertFalse(scope.active)
            } finally { raid.close(); registration.close() }
        }
    }
}
