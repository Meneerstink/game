package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * BATCH 2 mechanical coverage for the 19-curse Ancient Curses book (Turmoil's own pre-existing
 * behaviour is untouched and not retested here). Deliberately NOT covered, matching the disclosed
 * scope cuts in NIGHT_SERVER_STATUS.md:
 * - [AncientCurses.wrathExplosion]'s world-dispatch (iterating real [Npc]/[Player] pawn lists and
 *   calling the real damage-application pipeline) - that needs either fragile extension-function
 *   mocking or a full Hit-queue simulation for no extra regression coverage beyond what the boot
 *   test already exercises; the damage-radius/self-exclusion logic it reuses
 *   ([gg.rsmod.game.model.Tile.isWithinRadius]) already has its own coverage.
 * - Berserker's boost-duration extension: still not implemented (disclosed gap - this codebase has
 *   no boost-duration-timer system at all to extend), so there is nothing to test.
 *
 * Deflect's own "reflects a portion of the blocked damage back" bonus IS now implemented
 * ([AncientCurses.onIncomingHit]) and is covered below.
 */
class AncientCursesTests {
    private fun newPlayer(
        skillLevels: Map<Int, Int> = emptyMap(),
        prayerLevel: Int = 99,
        prayerPoints: Int = 100,
        unlocked: Boolean = true,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        // Prayers.isActive/deactivateAll (called by switchBook) route through the real
        // Player.getVarbit extension, which does `world.definitions.get(VarbitDef::class.java, id)`.
        // mockk's relaxed auto-answer for that generic method returns an unrelated Definition
        // subclass and blows up with a ClassCastException, so the VarbitDef lookup is stubbed
        // explicitly with a real (all-zero, inert) VarbitDef instead.
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(VarbitDef::class.java, any()) } returns VarbitDef(0)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { player.world } returns world
        every { world.percentChance(any()) } returns true
        // Prayers.deactivateAll also does p.setVarc(...), whose real body indexes into
        // Player.varcs (normally sized off the real VarbitDef count). The relaxed mock
        // otherwise hands back an empty list, so a real, generously-sized one is stubbed in.
        every { player.varcs } returns MutableList(2000) { 0 }
        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.PRAYER, prayerLevel)
        skillLevels.forEach { (skill, level) -> skills.setBaseLevel(skill, level) }
        every { player.skills } returns skills
        every { player.getCurrentPrayerPoints() } returns prayerPoints
        if (unlocked) player.attr[AncientCurses.UNLOCKED_ATTR] = true
        return player
    }

    // ---- gating ----

    @Test
    fun `toggleCurse is blocked before the unlock ritual`() {
        val player = newPlayer(unlocked = false)
        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
    }

    @Test
    fun `toggleCurse is blocked while on the normal prayer book`() {
        val player = newPlayer()
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(player))
        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
    }

    @Test
    fun `toggleCurse is blocked below the curse's Prayer level requirement`() {
        val player = newPlayer(prayerLevel = 70)
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MELEE) // requires 71
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_MELEE))
    }

    @Test
    fun `toggleCurse is blocked with zero prayer points left`() {
        val player = newPlayer(prayerPoints = 0)
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
    }

    @Test
    fun `toggleCurse activates and re-toggling deactivates it`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)

        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))

        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
    }

    // ---- mutual exclusion ----

    @Test
    fun `Saps stack with Saps but a Leech deactivates every active Sap`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        AncientCurses.toggleCurse(player, AncientCurse.SAP_RANGER)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.SAP_RANGER))
        AncientCurses.toggleCurse(player, AncientCurse.LEECH_ATTACK)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_RANGER))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.LEECH_ATTACK))
    }

    @Test
    fun `activating a second DEFLECT curse deactivates the first`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MAGIC)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MELEE)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_MAGIC))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_MELEE))
    }

    @Test
    fun `NONE-group curses freely combine with each other`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.BERSERKER)
        AncientCurses.toggleCurse(player, AncientCurse.WRATH)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.BERSERKER))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.WRATH))
    }

    @Test
    fun `activating an OFFENSIVE curse deactivates Turmoil`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        // Re-arm Turmoil after the book switch (which already clears it) to isolate
        // toggleCurse's own OFFENSIVE-vs-Turmoil cross-exclusion from switchBook's blanket clear.
        AncientCurses.toggleTurmoil(player)
        assertTrue(AncientCurses.isTurmoilActive(player))

        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)

        assertFalse(AncientCurses.isTurmoilActive(player))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
    }

    @Test
    fun `switchBook clears active curses and Turmoil from the outgoing book`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.SAP_WARRIOR)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))

        AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)

        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(player))
    }

    @Test
    fun `activating a Deflect curse refreshes the overhead icon block`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MAGIC)
        verify { player.addBlock(UpdateBlockType.APPEARANCE) }
    }

    // ---- combat hook: Sap / Leech / Soul Split (dealHit -> AncientCurses.onDamageDealt) ----

    private fun activate(
        attacker: Player,
        curse: AncientCurse,
    ) {
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, curse)
    }

    @Test
    fun `Sap Warrior drains the target Player's combat triad by 10 percent on the first activation`() {
        val attacker = newPlayer().also { activate(it, AncientCurse.SAP_WARRIOR) }
        val target = newPlayer(skillLevels = mapOf(Skills.ATTACK to 50, Skills.STRENGTH to 50, Skills.DEFENCE to 50))

        AncientCurses.onDamageDealt(attacker, target, damage = 30)

        assertEquals(45, target.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(45, target.skills.getCurrentLevel(Skills.STRENGTH))
        assertEquals(45, target.skills.getCurrentLevel(Skills.DEFENCE))
    }

    @Test
    fun `Sap Warrior drains an Npc target through its Stats block, floored at 1`() {
        val attacker = newPlayer().also { activate(it, AncientCurse.SAP_WARRIOR) }
        val npc = mockk<Npc>(relaxed = true)
        every { npc.attr } returns AttributeMap()
        val stats =
            Npc.Stats(5).apply {
                setMaxLevel(NpcSkills.ATTACK, 4)
                setCurrentLevel(NpcSkills.ATTACK, 4)
            }
        every { npc.stats } returns stats

        // 4 * 10% = 0.4 -> floored to 0, coerced up to the documented floor of 1.
        AncientCurses.onDamageDealt(attacker, npc, damage = 30)

        assertEquals(3, stats.getCurrentLevel(NpcSkills.ATTACK))
    }

    @Test
    fun `Leech Attack drains the target's Attack and boosts the attacker's own Attack`() {
        val attacker = newPlayer(skillLevels = mapOf(Skills.ATTACK to 60)).also { activate(it, AncientCurse.LEECH_ATTACK) }
        val target = newPlayer(skillLevels = mapOf(Skills.ATTACK to 50))

        AncientCurses.onDamageDealt(attacker, target, damage = 20)

        assertEquals(45, target.skills.getCurrentLevel(Skills.ATTACK)) // -5%
        assertEquals(63, attacker.skills.getCurrentLevel(Skills.ATTACK)) // +5%, capped 3 levels above max
    }

    @Test
    fun `Soul Split heals the attacker and drains the target's prayer, both 20 percent of the hit`() {
        val attacker = newPlayer().also { activate(it, AncientCurse.SOUL_SPLIT) }
        val target = newPlayer()

        AncientCurses.onDamageDealt(attacker, target, damage = 100)

        verify { attacker.alterLifepoints(20, 0) }
        verify { target.decreasePrayerPoints(200) }
    }

    @Test
    fun `Leech Energy transfers 10 run energy from the target to the attacker`() {
        val attacker = newPlayer().also { activate(it, AncientCurse.LEECH_ENERGY) }
        val target = newPlayer()
        every { target.runEnergy } returns 50.0
        every { attacker.runEnergy } returns 30.0

        AncientCurses.onDamageDealt(attacker, target, damage = 10)

        verify { target.runEnergy = 40.0 }
        verify { attacker.runEnergy = 40.0 }
    }

    @Test
    fun `Sap Spirit drains 10 special attack energy from the target`() {
        mockkObject(AttackTab)
        try {
            val attacker = newPlayer().also { activate(it, AncientCurse.SAP_SPIRIT) }
            val target = newPlayer()
            every { AttackTab.getEnergy(target) } returns 100

            AncientCurses.onDamageDealt(attacker, target, damage = 10)

            verify { AttackTab.setEnergy(target, 90) }
        } finally {
            unmockkObject(AttackTab)
        }
    }

    // ---- Deflect reflect bonus (dealHit -> AncientCurses.onIncomingHit) ----

    @Test
    fun `Deflect Melee reflects 10 percent of the damage back to the attacker on a successful roll`() {
        val target = newPlayer().also { activate(it, AncientCurse.DEFLECT_MELEE) }
        every { target.world.percentChance(63.0) } returns true
        val attacker = mockk<Player>(relaxed = true)

        AncientCurses.onIncomingHit(attacker, target, CombatClass.MELEE, damage = 100)

        val hitSlot = slot<gg.rsmod.game.model.Hit>()
        verify { attacker.addHit(capture(hitSlot)) }
        assertEquals(10, hitSlot.captured.hitmarks.sumOf { it.damage })
    }

    @Test
    fun `Deflect reflect does not trigger when the chance roll fails`() {
        val target = newPlayer().also { activate(it, AncientCurse.DEFLECT_MAGIC) }
        every { target.world.percentChance(63.0) } returns false
        val attacker = mockk<Player>(relaxed = true)

        AncientCurses.onIncomingHit(attacker, target, CombatClass.MAGIC, damage = 100)

        verify(exactly = 0) { attacker.addHit(any()) }
    }

    @Test
    fun `Deflect reflect is skipped when 10 percent of the damage would be under 10`() {
        val target = newPlayer().also { activate(it, AncientCurse.DEFLECT_MISSILES) }
        every { target.world.percentChance(63.0) } returns true
        val attacker = mockk<Player>(relaxed = true)

        AncientCurses.onIncomingHit(attacker, target, CombatClass.RANGED, damage = 50) // 10% = 5, under 10

        verify(exactly = 0) { attacker.addHit(any()) }
    }

    @Test
    fun `Deflect reflect requires the matching Deflect curse to be active for that combat style`() {
        val target = newPlayer().also { activate(it, AncientCurse.DEFLECT_MELEE) }
        every { target.world.percentChance(63.0) } returns true
        val attacker = mockk<Player>(relaxed = true)

        // Deflect Melee is active, but the incoming hit is Magic - no matching curse.
        AncientCurses.onIncomingHit(attacker, target, CombatClass.MAGIC, damage = 100)

        verify(exactly = 0) { attacker.addHit(any()) }
    }

    @Test
    fun `onDamageDealt ignores Npc attackers and zero or negative damage`() {
        val npcAttacker = mockk<Npc>(relaxed = true)
        val target = newPlayer(skillLevels = mapOf(Skills.ATTACK to 50))
        // Would sap the target's Attack if it were dispatched - asserts the early-return guards.
        AncientCurses.onDamageDealt(npcAttacker, target, damage = 30)
        assertEquals(50, target.skills.getCurrentLevel(Skills.ATTACK))

        val attacker = newPlayer().also { activate(it, AncientCurse.SAP_WARRIOR) }
        AncientCurses.onDamageDealt(attacker, target, damage = 0)
        assertEquals(50, target.skills.getCurrentLevel(Skills.ATTACK))
    }
}

/** Pure math on [AncientCurse] itself - no [Player]/[Npc] fixture needed. */
class AncientCurseTests {
    @Test
    fun `drain effects convert to the documented seconds per Prayer point`() {
        // 2026-09-06: curses use the same drain-counter units as [Prayer.drainEffect]. At zero
        // prayer bonus the counter advances by `drainEffect` per tick and spends a tenth of a
        // point every 60, so seconds per whole point is `360 / drainEffect`.
        fun secondsPerPoint(curse: AncientCurse) = 360.0 / curse.drainEffect
        assertEquals(2.4, secondsPerPoint(AncientCurse.SAP_WARRIOR), 0.001)
        assertEquals(3.6, secondsPerPoint(AncientCurse.LEECH_ATTACK), 0.001)
        assertEquals(2.0, secondsPerPoint(AncientCurse.SOUL_SPLIT), 0.001)
        assertEquals(12.0, secondsPerPoint(AncientCurse.WRATH), 0.001)
        assertEquals(18.0, secondsPerPoint(AncientCurse.BERSERKER), 0.001)
        // Every Deflect costs exactly what a Protect prayer costs - the cross-check that
        // calibrates the whole table against an independently-sourced value.
        assertEquals(Prayer.PROTECT_FROM_MELEE.drainEffect, AncientCurse.DEFLECT_MELEE.drainEffect)
        assertEquals(Prayer.PROTECT_FROM_MELEE.drainEffect, AncientCurse.DEFLECT_MAGIC.drainEffect)
        assertEquals(Prayer.PROTECT_FROM_MELEE.drainEffect, AncientCurse.DEFLECT_MISSILES.drainEffect)
        assertEquals(Prayer.PROTECT_FROM_MELEE.drainEffect, AncientCurse.DEFLECT_SUMMONING.drainEffect)
        assertEquals(3.0, secondsPerPoint(AncientCurse.DEFLECT_MELEE), 0.001)
    }

    @Test
    fun `byCommand matches both the enum name and the display name, case-insensitively`() {
        assertEquals(AncientCurse.DEFLECT_MELEE, AncientCurse.byCommand("deflect melee"))
        assertEquals(AncientCurse.DEFLECT_MELEE, AncientCurse.byCommand("DEFLECT_MELEE"))
        assertNull(AncientCurse.byCommand("not a curse"))
    }
}
