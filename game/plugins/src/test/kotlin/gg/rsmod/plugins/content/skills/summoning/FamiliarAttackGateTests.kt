package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.GameContext
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N516: exercise the real owner gate before any attack command can set a target. */
class FamiliarAttackGateTests {
    private val danger = Tile(3300, 3900)
    private val guarded = Tile(3165, 3487)
    private val world = mockk<World>(relaxed = true)
    private var index = 0

    init {
        val context = mockk<GameContext>(relaxed = true)
        every { context.home } returns Tile(3140, 3640)
        every { world.gameContext } returns context
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(32))
        every { world.getMultiCombatRegions() } returns setOf(danger.regionId, guarded.regionId)
        every { world.getMultiCombatChunks() } returns emptySet()
        val plugins = world.plugins
        every { plugins.canAttack(any(), any(), any()) } returns true
    }

    private fun player(tile: Tile, level: Int = 138): Player = mockk<Player>(relaxed = true).also {
        every { it.world } returns world
        every { it.tile } returns tile
        every { it.attr } returns AttributeMap()
        every { it.timers } returns TimerMap()
        every { it.entityType } returns EntityType.PLAYER
        every { it.isOnline } returns true
        every { it.isDead() } returns false
        every { it.isAlive() } returns true
        every { it.invisible } returns false
        every { it.lock } returns LockState.NONE
        every { it.combatLevel } returns level
    }

    private fun npc(id: Int, tile: Tile, owner: Player? = null): Npc = mockk<Npc>(relaxed = true).also { n ->
        every { n.world } returns world
        every { n.id } returns id
        every { n.tile } returns tile
        every { n.attr } returns AttributeMap()
        every { n.timers } returns TimerMap()
        every { n.owner } returns owner
        every { n.entityType } returns EntityType.NPC
        every { n.isAlive() } returns true
        every { n.isSpawned() } returns true
        every { n.isDead() } returns false
        every { n.invisible } returns false
        every { n.def } returns NpcDef(id).also { it.options[0] = "Attack" }
        every { n.combatDef } returns NpcCombatDef.DEFAULT
        every { n.index } returns ++index
        world.npcs.add(n)
        every { n.attack(any(), any()) } answers {
            n.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(firstArg<Pawn>())
        }
    }

    private fun familiar(owner: Player): Npc = npc(SummoningPouchData.SPIRIT_WOLF.npc, owner.tile, owner).also {
        it.attr[DAMAGE_CREDIT_ATTR] = WeakReference(owner)
        owner.attr[FAMILIAR_ATTR] = WeakReference(it)
    }

    @Test
    fun `guard command is refused before targeting unless owner is skulled inside the guarded area`() {
        for (tile in listOf(guarded, danger)) for (skulled in listOf(false, true)) {
            val owner = player(tile)
            if (skulled) owner.timers[SKULL_ICON_DURATION_TIMER] = 100
            val familiar = familiar(owner)
            val guard = npc(CityGuards.MELEE_GUARD_ID, tile)
            val allowed = tile == guarded && skulled
            assertTrue(Combat.canEngage(familiar, guard) == allowed, "tile=$tile skull=$skulled")
            assertTrue(FamiliarCombat.commandAttack(owner, guard) == allowed)
            verify(exactly = if (allowed) 1 else 0) { familiar.attack(guard, any()) }
            if (!allowed) assertFalse(familiar.attr[COMBAT_TARGET_FOCUS_ATTR]?.get() === guard)
        }
    }

    @Test
    fun `player command checks owner bracket and safe zone before targeting`() {
        for (tile in listOf(danger, guarded)) for (level in listOf(84, 123, 124, 138)) {
            val owner = player(tile)
            val familiar = familiar(owner)
            val target = player(tile.transform(1, 0), level)
            val allowed = tile == danger && level >= 124
            assertTrue(Combat.canEngage(familiar, target) == allowed, "tile=$tile level=$level")
            assertTrue(FamiliarCombat.commandAttack(owner, target) == allowed)
            verify(exactly = if (allowed) 1 else 0) { familiar.attack(target, any()) }
        }
    }
}
