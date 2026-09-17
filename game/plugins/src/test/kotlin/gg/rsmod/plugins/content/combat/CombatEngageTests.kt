package gg.rsmod.plugins.content.combat

import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Package-3 Wilderness/PvP foundation verification: [Combat.canEngage] is the
 * single PvP gate `Combat.canAttack` already funnels through, so these tests
 * drive it directly rather than duplicating a parallel gating system. Covers
 * the two fundamentals the milestone asks for that were not yet covered by
 * [gg.rsmod.plugins.content.areas.home.BountyHunterHomeTests] (which only
 * exercises the tile-level `BountyHunterHome` predicates, not their use
 * inside actual combat gating): the bank-only Ferox exclusion applied to a
 * real fight attempt, and the standard Wilderness combat-level-range
 * restriction. Also verifies `postAttack`'s existing combat-escape/logout
 * integration (`ACTIVE_COMBAT_TIMER`), so this file exercises Package 3's
 * "usable Wilderness PvP fundamentals" and "combat escape/logout
 * restrictions" items end-to-end against real production code.
 */
class CombatEngageTests {
    @Test
    fun `an opponent can attack a public familiar in multi combat even without an Attack cache option`() {
        val home = Tile(3140, 3640, 0)
        val owner = newPlayer(tile = home, combatLevel = 100, home = home)
        val attacker = newPlayer(tile = home, combatLevel = 100, home = home)
        val world = attacker.world
        every { world.getMultiCombatChunks() } returns setOf(home.chunkCoords.hashCode())
        val familiar = publicFamiliar(owner, world, home)

        assertTrue(Combat.canEngage(attacker, familiar))
    }

    @Test
    fun `an owner cannot attack their own familiar and an opponent cannot attack one in single combat`() {
        val home = Tile(3140, 3640, 0)
        val owner = newPlayer(tile = home, combatLevel = 100, home = home)
        val attacker = newPlayer(tile = home, combatLevel = 100, home = home)
        val world = attacker.world
        every { world.getMultiCombatChunks() } returns emptySet()
        val familiar = publicFamiliar(owner, world, home)

        assertFalse(Combat.canEngage(owner, familiar))
        assertFalse(Combat.canEngage(attacker, familiar))
    }

    @Test
    fun `an ordinary non attackable npc remains blocked for a player`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, combatLevel = 100, home = home)
        val npc = mockk<Npc>(relaxed = true)
        every { npc.world } returns attacker.world
        every { npc.tile } returns home
        every { npc.entityType } returns EntityType.NPC
        every { npc.isDead() } returns false
        every { npc.isSpawned() } returns true
        every { npc.invisible } returns false
        every { npc.def } returns NpcDef(99998)
        every { npc.combatDef } returns NpcCombatDef.DEFAULT

        assertFalse(Combat.canEngage(attacker, npc))
    }

    @Test
    fun `players can fight in Ferox outside the bank`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, combatLevel = 100, home = home)
        val target = newPlayer(tile = home.transform(1, 0), combatLevel = 100, home = home)

        assertTrue(Combat.canEngage(attacker, target))
    }

    @Test
    fun `players can fight in dangerous wilderness within the flat +-14 combat level range`() {
        val home = Tile(3140, 3640, 0)
        // (3528 - 3520) / 8 + 1 = wilderness level 2, far outside the safe hub. The flat
        // +/-14 range (owner "deadmanmode vervijning" 2026-09-17, "also in the wilderness") allows
        // a 13-level gap here even though the old Wilderness-level-scaled formula (+/-2 at level
        // 2) would have blocked it - the regression check that the scaled formula was replaced.
        val attacker = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3040, 3530), combatLevel = 63, home = home)

        assertTrue(Combat.canEngage(attacker, target))
    }

    @Test
    fun `flat +-14 combat level range blocks a fight when the gap exceeds 14`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3040, 3530), combatLevel = 50 + AreaState.MAX_COMBAT_LEVEL_DIFFERENCE + 1, home = home)

        assertFalse(Combat.canEngage(attacker, target))
    }

    @Test
    fun `flat +-14 combat level range applies at exactly the boundary`() {
        val home = Tile(3140, 3640, 0)
        assertEquals(14, AreaState.MAX_COMBAT_LEVEL_DIFFERENCE)
        val attackerAt14 = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val targetAt14 = newPlayer(tile = Tile(3040, 3530), combatLevel = 64, home = home)
        assertTrue(Combat.canEngage(attackerAt14, targetAt14), "exactly 14 levels apart must be allowed")

        val attackerAt15 = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val targetAt15 = newPlayer(tile = Tile(3040, 3530), combatLevel = 65, home = home)
        assertFalse(Combat.canEngage(attackerAt15, targetAt15), "15 levels apart must be blocked")
    }

    @Test
    fun `flat +-14 combat level range also applies outside the Wilderness since PvP is dangerous everywhere`() {
        val home = Tile(3140, 3640, 0)
        // Ordinary overworld tile, not a Wilderness region and not the Ferox bank - dangerous
        // under R03.1 (PvP allowed everywhere outside explicit bank safe zones), so the range
        // gate must still apply here, not only inside the Wilderness regions.
        val attacker = newPlayer(tile = Tile(3200, 3200, 0), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3200, 3201, 0), combatLevel = 100, home = home)

        assertFalse(Combat.canEngage(attacker, target))
    }

    @Test
    fun `a player protected by the post-kill grace period cannot be attacked`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = Tile(3200, 3200, 0), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3200, 3201, 0), combatLevel = 50, home = home)
        val realTimers = TimerMap()
        every { target.timers } returns realTimers

        gg.rsmod.plugins.content.mechanics.pvp.KillGrace.grant(target)

        assertFalse(Combat.canEngage(attacker, target))
    }

    @Test
    fun `Ferox outside the bank can fight the Wilderness side of a barrier`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, combatLevel = 50, home = home)
        // One tile beyond the south Ferox boundary is real Wilderness and remains within
        // the normal combat view distance of the non-bank Ferox tile.
        val target = newPlayer(tile = Tile(3140, 3647, 0), combatLevel = 50, home = home)

        assertTrue(Combat.canEngage(attacker, target))
    }

    @Test
    fun `postAttack arms the combat-escape logout lock on the target`() {
        val pawn = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = Tile(3140, 3640, 0))
        val target = newPlayer(tile = Tile(3040, 3529), combatLevel = 50, home = Tile(3140, 3640, 0))
        val realTimers = TimerMap()
        every { target.timers } returns realTimers

        assertFalse(realTimers.has(ACTIVE_COMBAT_TIMER), "sanity check: timer must start unset")
        Combat.postAttack(pawn, target)

        assertTrue(
            realTimers.has(ACTIVE_COMBAT_TIMER),
            "logout_tab.plugin.kts's combat-logout lock reads exactly this timer key",
        )
    }

    private fun newPlayer(
        tile: Tile,
        combatLevel: Int,
        home: Tile,
    ): Player {
        val gameContext = mockk<GameContext>(relaxed = true)
        every { gameContext.home } returns home

        val world = mockk<World>(relaxed = true)
        every { world.gameContext } returns gameContext

        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.tile } returns tile
        every { player.entityType } returns EntityType.PLAYER
        every { player.isOnline } returns true
        every { player.invisible } returns false
        every { player.lock } returns LockState.NONE
        every { player.combatLevel } returns combatLevel
        // CombatConfigs.getAttackDelay (invoked by postAttack) reads real
        // equipment slot state; an empty real container keeps that path on
        // its early "no weapon equipped" branch instead of touching a
        // relaxed-mocked ItemContainer.
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        return player
    }

    private fun publicFamiliar(owner: Player, world: World, tile: Tile): Npc {
        val def = NpcDef(99999)
        val familiar = mockk<Npc>(relaxed = true)
        every { familiar.world } returns world
        every { familiar.tile } returns tile
        every { familiar.entityType } returns EntityType.NPC
        every { familiar.owner } returns owner
        every { familiar.publicOwner } returns true
        every { familiar.isDead() } returns false
        every { familiar.isSpawned() } returns true
        every { familiar.invisible } returns false
        every { familiar.def } returns def
        every { familiar.combatDef } returns NpcCombatDef.DEFAULT
        return familiar
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
