package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.areas.wilderness.bosses.*
import gg.rsmod.plugins.content.combat.NpcLeash
import io.mockk.*
import kotlin.test.*

class WildernessBossArrivalTests {
    @Test fun `the compiled boss and minion definitions opt into full death playback and Calvarion uses its imported parries`() {
        val f = WildernessBossTestSupport.Fixture()
        val defs = f.repo.allNpcCombatDefs()
        val ids = listOf(15052,15053,15055,15056,15057,15069,15070,15071,15072,15073,15074,15075,
            15076,15077,15078,15079,15080,15082,15083,15084,15085,15086,15087)
        ids.forEach { assertTrue(defs.getValue(it).waitFullDeathAnimation, "npc $it must not use the old four-tick subtraction") }
        assertEquals(16253, defs.getValue(15084).blockAnimation)
        assertEquals(16262, defs.getValue(15085).blockAnimation)
    }
    @Test fun `the compiled aggro plugin acquires both measured arrival tiles through every refusal check`() {
        val f = WildernessBossTestSupport.Fixture()
        val modes = gg.rsmod.plugins.content.mechanics.aggro.NpcHuntModes
        mockkObject(modes)
        try {
            every { modes.table } returns gg.rsmod.plugins.content.mechanics.aggro.NpcHuntModes.Table()
            val script = WildernessBossTestSupport.script("gg.rsmod.plugins.content.mechanics.aggro.Npc_aggro_plugin", f.repo, f.world)
            f.players.entries[1] = f.player
            every { f.player.entityType } returns gg.rsmod.game.model.EntityType.PLAYER
            every { f.player.lock } returns gg.rsmod.game.model.LockState.NONE
            for ((arena, arrival) in listOf(VenenatisCombat.VENENATIS_ARENA to Tile(3424,10212,2), VenenatisCombat.SPINDEL_ARENA to Tile(1632,11556,2))) {
                val npc = f.npc(arena.npc, arena.spawn)
                every { npc.combatDef } returns f.repo.allNpcCombatDefs().getValue(arena.npc)
                every { npc.def } returns WildernessBossTestSupport.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, arena.npc)
                every { npc.entityType } returns gg.rsmod.game.model.EntityType.NPC
                every { npc.owner } returns null
                every { npc.spawnTile } returns arena.spawn
                every { npc.aggroCheck } returns null
                every { f.player.tile } returns arrival
                val refusal = script.javaClass.getMethod("refusal", gg.rsmod.game.model.entity.Npc::class.java, Player::class.java, Int::class.javaPrimitiveType)
                    .invoke(script, npc, f.player, 1)
                assertNull(refusal, "measured arrival must pass every aggro rule")
                assertEquals(true, script.javaClass.getMethod("checkRadius", gg.rsmod.game.model.entity.Npc::class.java).invoke(script, npc))
                verify(exactly = 1) { npc.attack(f.player) }
                f.player.attr.remove(gg.rsmod.game.model.attr.AGGRESSOR)
            }
        } finally { unmockkObject(modes) }
    }
    @Test fun `both spider arrival tiles pass the real pursuit gate instead of the old seven plus one leash`() {
        val f = WildernessBossTestSupport.Fixture()
        for ((arena, arrival) in listOf(VenenatisCombat.VENENATIS_ARENA to Tile(3424,10212,2), VenenatisCombat.SPINDEL_ARENA to Tile(1632,11556,2))) {
            val npc = f.npc(arena.npc, arena.spawn)
            every { npc.owner } returns null
            every { npc.spawnTile } returns arena.spawn
            every { f.player.tile } returns arrival
            assertFalse(NpcLeash.withinAggro(arena.spawn, arrival, 7, 1), "reproduces the measured refusal")
            assertTrue(NpcLeash.mayPursue(npc, f.player, 1))
            every { f.player.tile } returns Tile(arrival.x, arrival.z, 0)
            assertFalse(NpcLeash.mayPursue(npc, f.player, 1))
        }
    }

    @Test fun `each lair boss and its minions can pursue throughout its own arena but not beyond its exit`() {
        val groups = listOf(
            "Callisto's Den" to listOf(15076), "Vet'ion's Rest" to listOf(15077,15078,15079,15080),
            "Hunter's End" to listOf(15083), "Skeletal Tomb" to listOf(15084,15085,15086,15087))
        for ((name, ids) in groups) {
            val lair = WildernessLairs.LAIRS.first { it.name == name }
            for (id in ids) {
                val territory = WildernessBossTerritory.territory(id)!!
                assertTrue(territory.contains(lair.arrival))
                assertTrue(territory.contains(Tile(lair.x.first, lair.z.last, lair.height)))
                assertFalse(territory.contains(Tile(lair.x.first-1, lair.z.last, lair.height)))
            }
        }
    }

    @Test fun `spiderling cleanup removes the entire owned brood as soon as its boss dies`() {
        val f = WildernessBossTestSupport.Fixture()
        val boss = f.npc(VenenatisCombat.NPC)
        val minion = f.npc(VenenatisCombat.SPIDERLING)
        minion.attr[VenenatisCombat.OWNER] = boss
        VenenatisCombat.spiderlings(boss) += minion
        // Ownership list is authoritative even if the summoned index lost an entry.
        VenenatisCombat.onDeath(boss)
        verify(exactly = 1) { f.world.remove(minion) }
        assertTrue(VenenatisCombat.spiderlings(boss).isEmpty())
    }
}
