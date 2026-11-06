package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.instance.InstancedMap
import io.mockk.*
import kotlin.test.*

class ToaRoomScopeTests {
    @Test fun `room clear cancels its work and removes only its owned actors once`() {
        val world = mockk<World>(relaxed = true)
        val map = mockk<InstancedMap>(relaxed = true)
        every { map.area } returns Area(6400, 0, 6848, 448)
        val raid = ToaRaid(1, listOf(PlayerUID(1)), ToaRaidSettings(), map, world)
        val run = raid.progress.challenge(ToaPath.HET, 0)!!
        val scope = raid.scope(run)!!
        val actor = mockk<Npc>(relaxed = true)
        every { actor.world } returns world
        every { actor.tile } returns Tile(6420, 160)
        every { world.spawn(actor) } returns true
        var cancelled = 0
        scope.onClose { cancelled++ }
        assertTrue(scope.spawn(actor))
        assertTrue(run.clear(1))
        scope.close()
        assertEquals(1, cancelled)
        assertFalse(scope.active)
        verify(exactly = 1) { world.remove(actor) }
        assertFailsWith<IllegalStateException> { scope.spawn(actor) }
        val next = raid.progress.boss(ToaPath.HET)!!
        assertTrue(raid.scope(next)!!.active)
        assertFalse(run.wipe(2))
        verify(exactly = 1) { world.remove(actor) }
    }
}
