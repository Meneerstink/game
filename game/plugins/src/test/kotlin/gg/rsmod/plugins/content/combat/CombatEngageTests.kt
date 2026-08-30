package gg.rsmod.plugins.content.combat

import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Package-3 Wilderness/PvP foundation verification: [Combat.canEngage] is the
 * single PvP gate `Combat.canAttack` already funnels through, so these tests
 * drive it directly rather than duplicating a parallel gating system. Covers
 * the two fundamentals the milestone asks for that were not yet covered by
 * [gg.rsmod.plugins.content.areas.home.BountyHunterHomeTests] (which only
 * exercises the tile-level `BountyHunterHome` predicates, not their use
 * inside actual combat gating): the central safe-home exclusion applied to a
 * real fight attempt, and the standard Wilderness combat-level-range
 * restriction. Also verifies `postAttack`'s existing combat-escape/logout
 * integration (`ACTIVE_COMBAT_TIMER`), so this file exercises Package 3's
 * "usable Wilderness PvP fundamentals" and "combat escape/logout
 * restrictions" items end-to-end against real production code.
 */
class CombatEngageTests {
    @Test
    fun `players cannot fight inside the safe home hub`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, combatLevel = 100, home = home)
        val target = newPlayer(tile = home.transform(1, 0), combatLevel = 100, home = home)

        assertFalse(Combat.canEngage(attacker, target))
    }

    @Test
    fun `players can fight in dangerous wilderness within the wilderness combat level range`() {
        val home = Tile(3140, 3640, 0)
        // (3528 - 3520) / 8 + 1 = wilderness level 2, far outside the safe hub.
        val attacker = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3040, 3530), combatLevel = 51, home = home)

        assertTrue(Combat.canEngage(attacker, target))
    }

    @Test
    fun `wilderness combat level range still blocks a fight when the level gap exceeds the wilderness level`() {
        val home = Tile(3140, 3640, 0)
        // Same wilderness level 2 as above, so the valid range is only +/-2.
        val attacker = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3040, 3530), combatLevel = 100, home = home)

        assertFalse(Combat.canEngage(attacker, target))
    }

    @Test
    fun `one player standing in the safe hub still blocks the fight even if the other is in the wilderness`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = home, combatLevel = 50, home = home)
        val target = newPlayer(tile = home.transform(6, 0), combatLevel = 50, home = home)

        assertFalse(Combat.canEngage(attacker, target))
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

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
