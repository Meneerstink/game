package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.timer.VENOM_IMMUNITY
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.content.combat.isEnvenomed
import gg.rsmod.plugins.content.combat.isPoisoned
import gg.rsmod.plugins.content.combat.poison
import gg.rsmod.plugins.content.combat.venom
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for Venom (Priority 5 of the 2026-09-02 autonomous foundations pass), sourced
 * from the OSRS Wiki ("Venom") - see RSPS_DECISIONS.md for the full sourcing note. Written
 * this session but NOT run (Bash tool unavailable all session, see RSPS_WORK_STATUS.md) -
 * a resuming session must run `:game:plugins:test --tests "*VenomTests*"` before trusting
 * this beyond "written to match the surrounding compiled patterns by careful reading".
 */
class VenomTests {
    // ---- pure damage math ----

    @Test
    fun `damage escalates by 2 per tick starting at 6`() {
        assertEquals(6, Venom.damageForTick(0))
        assertEquals(8, Venom.damageForTick(1))
        assertEquals(10, Venom.damageForTick(2))
    }

    @Test
    fun `damage caps at 20`() {
        assertEquals(20, Venom.damageForTick(7))
        assertEquals(20, Venom.damageForTick(8))
        assertEquals(20, Venom.damageForTick(1000))
    }

    // ---- envenom ----

    @Test
    fun `envenom applies venom state and the isEnvenomed extension reflects it`() {
        val player = newPlayer()
        assertFalse(player.isEnvenomed())

        assertTrue(Venom.envenom(player))

        assertTrue(player.isEnvenomed())
        assertEquals(0, player.attr[VENOM_TICKS_ELAPSED_ATTR])
    }

    @Test
    fun `re-envenoming an already-envenomed pawn is a no-op`() {
        val player = newPlayer()
        Venom.envenom(player)
        player.attr[VENOM_TICKS_ELAPSED_ATTR] = 3

        assertFalse(Venom.envenom(player))

        // progression must not have been reset back to 0
        assertEquals(3, player.attr[VENOM_TICKS_ELAPSED_ATTR])
    }

    @Test
    fun `envenoming clears any active regular poison - mutually exclusive states`() {
        val player = newPlayer()
        player.poison(6)
        assertTrue(player.isPoisoned())

        Venom.envenom(player)

        assertFalse(player.isPoisoned())
        assertTrue(player.isEnvenomed())
    }

    @Test
    fun `poison keeps the stronger dose and refreshes an equal or stronger dose`() {
        val player = newPlayer()

        assertTrue(Poison.poison(player, 4))
        val firstTicks = player.attr[POISON_TICKS_LEFT_ATTR]
        // OSRS Wiki "Poison": severity = 5 x starting damage (4 -> severity 20), stored as severity - 1.
        assertEquals(19, firstTicks)
        assertEquals(30, player.timers[POISON_TIMER])

        player.attr[POISON_TICKS_LEFT_ATTR] = 10
        player.timers[POISON_TIMER] = 3
        assertFalse(Poison.poison(player, 2))
        assertEquals(10, player.attr[POISON_TICKS_LEFT_ATTR])
        assertEquals(3, player.timers[POISON_TIMER])

        assertTrue(Poison.poison(player, 4))
        assertEquals(firstTicks, player.attr[POISON_TICKS_LEFT_ATTR])
        assertEquals(30, player.timers[POISON_TIMER])
    }

    @Test
    fun `poison deals 5 hits per damage level and 105 in total from 6 - OSRS Wiki Poison`() {
        val player = newPlayer()
        assertTrue(Poison.poison(player, 6))
        // Replays the poison timer's rule (poison_plugin): hit getDamageForTicks(ticks), the hit at ticks 0 is the last one.
        val hits = mutableListOf<Int>()
        var ticks = player.attr[POISON_TICKS_LEFT_ATTR]!!
        while (ticks >= 0) {
            hits += Poison.getDamageForTicks(ticks)
            ticks--
        }
        assertEquals(List(5) { 6 } + List(5) { 5 } + List(5) { 4 } + List(5) { 3 } + List(5) { 2 } + List(5) { 1 }, hits)
        assertEquals(105, hits.sum(), "\"a total of 105 poison damage\" for severity 30")
    }

    @Test
    fun `poison never lands on an envenomed pawn`() {
        val player = newPlayer()
        Venom.envenom(player)
        assertFalse(Poison.poison(player, 6))
        assertFalse(player.isPoisoned())
        assertTrue(player.isEnvenomed())
    }

    @Test
    fun `an immune pawn cannot be envenomed`() {
        val player = newPlayer()
        player.timers[VENOM_IMMUNITY] = 100

        assertFalse(Venom.envenom(player))
        assertFalse(player.isEnvenomed())
    }

    @Test
    fun `an npc flagged venom-immune cannot be envenomed`() {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.attr } returns AttributeMap()
        every { npc.timers } returns TimerMap()
        every { npc.combatDef } returns immuneCombatDef()

        assertFalse(Venom.envenom(npc))
    }

    // ---- venom() PawnExt wrapper ----

    @Test
    fun `venom extension invokes the callback only when newly envenomed`() {
        val player = newPlayer()
        var calls = 0

        player.venom { calls++ }
        assertEquals(1, calls)

        player.venom { calls++ } // already envenomed, must not fire again
        assertEquals(1, calls)
    }

    // ---- cure (anti-venom) ----

    @Test
    fun `cure removes venom entirely and grants immunity`() {
        val player = newPlayer()
        Venom.envenom(player)

        assertTrue(Venom.cure(player, immunityTicks = 90))

        assertFalse(player.isEnvenomed())
        assertTrue(player.timers.has(VENOM_IMMUNITY))
    }

    @Test
    fun `cure is a no-op on a pawn that was never envenomed`() {
        val player = newPlayer()
        assertFalse(Venom.cure(player, immunityTicks = 90))
        assertFalse(player.timers.has(VENOM_IMMUNITY))
    }

    // ---- downgradeToPoison (regular antipoison) ----

    @Test
    fun `downgrading mid-venom starts poison at the venom's current damage, not a fresh cure`() {
        val player = newPlayer()
        Venom.envenom(player)
        // simulate 2 venom ticks having already elapsed (damage would be 10 next)
        player.attr[VENOM_TICKS_ELAPSED_ATTR] = 2

        assertTrue(Venom.downgradeToPoison(player))

        assertFalse(player.isEnvenomed())
        assertTrue(player.isPoisoned())
        // NOT immune - a downgrade is not a cure (real rule: a 2nd dose is needed)
        assertFalse(player.timers.has(POISON_IMMUNITY))
    }

    @Test
    fun `downgrading a pawn that isn't envenomed does nothing`() {
        val player = newPlayer()
        assertFalse(Venom.downgradeToPoison(player))
        assertFalse(player.isPoisoned())
    }

    // ---- helpers ----

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.varps } returns VarpSet((0..8000).toSet())
        return player
    }

    private fun immuneCombatDef(): NpcCombatDef =
        NpcCombatDef(
            lifepoints = 1,
            stats = listOf(1, 1, 1, 1, 1),
            attackSpeed = 4,
            attackAnimation = -1,
            blockAnimation = -1,
            deathAnimation = listOf(-1),
            respawnDelay = 25,
            deathDelay = 0,
            aggressiveRadius = 0,
            aggroTargetDelay = 0,
            aggressiveTimer = 0,
            poisonDamage = 0,
            poisonImmunity = false,
            slayerReq = 1,
            slayerXp = 0.0,
            bonuses = emptyList(),
            species = emptySet(),
            spell = -1,
            xpMultiplier = 1.0,
            slayerAssignment = null,
            attackStyleType = StyleType.STAB,
            deathBlowLifepoints = -1,
            venomDamage = 0,
            venomImmunity = true,
        )
}
