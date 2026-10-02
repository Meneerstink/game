package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.skills.summoning.SummoningTestCache
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ROOT_CAUSES #139: `respawns = false` written on a fresh npc before `World.spawn` was overwritten from the combat def, so one-off
 * spawns (Pest Control monsters, Fight Caves, Barrows, Nex ...) respawned after death. A pre-spawn `false` now sticks; `true`
 * (the world `spawn_npc` spawns) still follows the combat def, and a spawned npc's flag is left to the spawn logic.
 */
class NpcRespawnFlagTests {
    private fun world(): World {
        val world = mockk<World>(relaxed = true)
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.definitions } returns SummoningTestCache.definitions
        return world
    }

    @Test
    fun `an explicit false before spawn becomes the respawn override`() {
        val npc = Npc(3742, Tile(2650, 2600, 0), world())
        npc.respawns = false
        assertEquals(false, npc.respawnOverride)
    }

    @Test
    fun `true before spawn still follows the combat def`() {
        val npc = Npc(3742, Tile(2650, 2600, 0), world())
        npc.respawns = true
        assertNull(npc.respawnOverride)
    }

    @Test
    fun `a spawned npc's flag does not pin an override`() {
        val npc = Npc(3742, Tile(2650, 2600, 0), world())
        npc.index = 7
        npc.respawns = false
        assertNull(npc.respawnOverride)
    }
}
