package gg.rsmod.plugins.content.combat

import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
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
    /** COMBATENGAGE-FLAKY: multi comes from mocked multi.yml chunks here; a class that loaded the plugins (multiway_combat) leaves the real surface map in this JVM. */
    @kotlin.test.BeforeTest
    fun unloadMultiZones() = gg.rsmod.plugins.content.mechanics.multi.MultiCombatZones.unload()

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

    /** DMM-ARMAGEDDON D8 (owner 20-11-2026): only players of the same combat bracket fight (3-50, 51-70, 71-90, 91-110, 111-138). */
    @Test
    fun `players of the same bracket fight in the wilderness, however far apart inside it`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = Tile(3040, 3528), combatLevel = 52, home = home)
        val target = newPlayer(tile = Tile(3040, 3530), combatLevel = 70, home = home)

        assertTrue(Combat.canEngage(attacker, target))
    }

    @Test
    fun `a player of another bracket cannot be attacked, even one level apart`() {
        val home = Tile(3140, 3640, 0)
        val attacker = newPlayer(tile = Tile(3040, 3528), combatLevel = 50, home = home)
        val target = newPlayer(tile = Tile(3040, 3530), combatLevel = 51, home = home)

        assertFalse(Combat.canEngage(attacker, target))
    }

    @Test
    fun `bracket boundaries - 70 and 71, 110 and 111 never fight, 111 and 138 do`() {
        val home = Tile(3140, 3640, 0)
        fun fight(a: Int, b: Int) = Combat.canEngage(newPlayer(Tile(3040, 3528), a, home), newPlayer(Tile(3040, 3530), b, home))
        assertFalse(fight(70, 71))
        assertFalse(fight(110, 111))
        assertTrue(fight(111, 138))
        assertTrue(fight(3, 50))
    }

    @Test
    fun `the bracket rule also applies outside the Wilderness since PvP is dangerous everywhere`() {
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
        // A real attribute map: a relaxed mock answers attr[JAILED_UNTIL] (Punishments.isJailed, read by
        // AreaState.canPlayersFight) with a bare Object, which threw ClassCastException before any PvP rule ran.
        every { player.attr } returns AttributeMap()
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
