package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.isAttackedByOtherThan
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner A9 2026-10-11: a combat familiar may join its owner's fight against an npc in single-way combat; against a player only in
 * multi-way combat. Owner and familiar count as one attacker for the single-way rules, so the familiar never locks its own owner
 * out of his fight, and another player's fight stays protected.
 */
class FamiliarSingleCombatTests {
    private var nextIndex = 0
    private val singleTile = Tile(3200, 3200, 0)
    private val multiTile = Tile(3300, 3900, 0)

    private val world: World =
        mockk<World>(relaxed = true).also { world ->
            every { world.getMultiCombatRegions() } returns setOf(multiTile.regionId)
            every { world.getMultiCombatChunks() } returns emptySet()
            every { world.npcs } returns PawnList(arrayOfNulls<Npc>(16))
        }

    private fun player(tile: Tile): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.tile } returns tile
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.isOnline } returns true
        every { player.isDead() } returns false
        return player
    }

    private fun npc(tile: Tile, owner: Player? = null): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.world } returns world
        every { npc.tile } returns tile
        every { npc.attr } returns AttributeMap()
        every { npc.timers } returns TimerMap()
        every { npc.owner } returns owner
        every { npc.isDead() } returns false
        // PawnList keys on the index: every mock npc needs its own slot or only the last one counts as spawned.
        every { npc.index } returns ++nextIndex
        world.npcs.add(npc)
        return npc
    }

    private fun familiarOf(owner: Player): Npc =
        npc(owner.tile, owner).also { it.attr[DAMAGE_CREDIT_ATTR] = WeakReference(owner) }

    @Test
    fun `single-way - any npc nobody else fights is allowed, a player never`() {
        val owner = player(singleTile)
        val familiar = familiarOf(owner)
        // The owner's own report (2026-10-11 19:48:54): Attack from the panel on an npc he was not fighting yet.
        val goblin = npc(singleTile)
        assertTrue(FamiliarCombat.mayFightHere(owner, familiar, goblin, silent = true))
        // The npc the owner fights / that attacks him: allowed as well.
        owner.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(goblin)
        assertTrue(FamiliarCombat.mayFightHere(owner, familiar, goblin, silent = true))
        // A player is never a single-way familiar target, even when he is the owner's fight.
        val pker = player(singleTile)
        owner.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(pker)
        assertFalse(FamiliarCombat.mayFightHere(owner, familiar, pker, silent = false))
        verify { owner.write(match { it is gg.rsmod.game.message.impl.MessageGameMessage && it.message == "Your familiar cannot fight unless it is in a multi-way combat area." }) }
        // Another player's familiar is a player-owned target: multi-way only too.
        val otherFamiliar = familiarOf(pker)
        assertFalse(FamiliarCombat.mayFightHere(owner, familiar, otherFamiliar, silent = true))
    }

    @Test
    fun `single-way - another player's npc stays his, and the familiar never locks its own owner out`() {
        val owner = player(singleTile)
        val familiar = familiarOf(owner)
        val boss = npc(singleTile)
        owner.attr[COMBAT_TARGET_FOCUS_ATTR] = WeakReference(boss)
        // Someone else is already fighting it (PJ timer + last hit by another player).
        val stranger = player(singleTile)
        boss.timers[Combat.PJ_TIMER] = 10
        boss.attr[LAST_HIT_BY_ATTR] = WeakReference(stranger)
        assertFalse(FamiliarCombat.mayFightHere(owner, familiar, boss, silent = false))
        verify { owner.write(match { it is gg.rsmod.game.message.impl.MessageGameMessage && it.message == "Someone is already fighting this." }) }
        // The familiar's own hit: owner and familiar are one side - not "someone else" for either of them.
        boss.attr[LAST_HIT_BY_ATTR] = WeakReference(familiar)
        assertFalse(boss.isAttackedByOtherThan(owner), "the familiar's hit must not lock the owner out")
        assertTrue(boss.isAttackedByOtherThan(stranger), "another player is still kept off the npc")
        assertTrue(FamiliarCombat.mayFightHere(owner, familiar, boss, silent = true))
        assertTrue(Combat.sameSide(owner, familiar) && Combat.sameSide(familiar, owner))
        assertFalse(Combat.sameSide(stranger, familiar))
        assertFalse(Combat.sameSide(boss, npc(singleTile)), "two unowned npcs are never one side")
    }

    @Test
    fun `multi-way - players and any npc, as before`() {
        val owner = player(multiTile)
        val familiar = familiarOf(owner)
        assertTrue(FamiliarCombat.mayFightHere(owner, familiar, player(multiTile), silent = true))
        assertTrue(FamiliarCombat.mayFightHere(owner, familiar, npc(multiTile), silent = true))
        // Owner in multi, target just outside it: the second period message.
        val outside = player(singleTile)
        assertFalse(FamiliarCombat.mayFightHere(owner, familiar, outside, silent = false))
        verify { owner.write(match { it is gg.rsmod.game.message.impl.MessageGameMessage && it.message == "Your familiar cannot fight a target that is not in a multi-way combat area." }) }
    }

    @Test
    fun `A10 - a beast of burden never attacks, also now that single-way combat is open to familiars`() {
        val bobs = FamiliarCapabilityTable.all.filter { it.isBeastOfBurden }
        assertTrue(bobs.any { it.pouch == SummoningPouchData.WAR_TORTOISE } && bobs.any { it.pouch == SummoningPouchData.PACK_YAK })
        bobs.forEach { bob ->
            assertFalse(bob.canReceiveAttackCommand, "${bob.pouch}: no Attack command")
            assertFalse(bob.retaliates, "${bob.pouch}: no retaliation")
            assertFalse(bob.supports(FamiliarAction.ATTACK), "${bob.pouch}: no Attack on the orb")
            assertFalse(FamiliarAction.ATTACK in bob.actions, "${bob.pouch}: no Attack on the orb")
        }
        // A combat familiar that does not carry keeps all of it (A9 / A41).
        val wolf = FamiliarCapabilityTable.all.first { it.pouch == SummoningPouchData.SPIRIT_WOLF }
        assertTrue(wolf.canReceiveAttackCommand && wolf.retaliates && FamiliarAction.ATTACK in wolf.actions)
    }
}
