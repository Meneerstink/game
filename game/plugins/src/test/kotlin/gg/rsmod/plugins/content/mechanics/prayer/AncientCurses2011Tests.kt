package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2011-era rules that are pure functions of the curse table or of Turmoil's opponent scaling. */
class AncientCurses2011Tests {
    private fun newPlayer(skillLevels: Map<Int, Int> = emptyMap()): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(VarbitDef::class.java, any()) } returns VarbitDef(0)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { world.percentChance(any()) } returns true
        every { player.world } returns world
        every { player.varcs } returns MutableList(2000) { 0 }
        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.PRAYER, 99)
        skillLevels.forEach { (skill, level) -> skills.setBaseLevel(skill, level) }
        every { player.skills } returns skills
        every { player.getCurrentPrayerPoints() } returns 100
        player.attr[AncientCurses.UNLOCKED_ATTR] = true
        return player
    }

    @Test
    fun `exclusion table matches the 2011 key`() {
        assertFalse(AncientCurse.SAP_WARRIOR.conflictsWith(AncientCurse.SAP_MAGE))
        assertFalse(AncientCurse.LEECH_ATTACK.conflictsWith(AncientCurse.LEECH_DEFENCE))
        assertTrue(AncientCurse.SAP_WARRIOR.conflictsWith(AncientCurse.LEECH_ATTACK))
        assertTrue(AncientCurse.DEFLECT_MELEE.conflictsWith(AncientCurse.DEFLECT_MAGIC))
        assertFalse(AncientCurse.DEFLECT_MELEE.conflictsWith(AncientCurse.DEFLECT_SUMMONING))
        assertTrue(AncientCurse.DEFLECT_SUMMONING.conflictsWith(AncientCurse.WRATH))
        assertTrue(AncientCurse.DEFLECT_MELEE.conflictsWith(AncientCurse.SOUL_SPLIT))
        assertTrue(AncientCurse.WRATH.conflictsWith(AncientCurse.SOUL_SPLIT))
        assertFalse(AncientCurse.BERSERKER.conflictsWith(AncientCurse.SOUL_SPLIT))
        assertFalse(AncientCurse.BERSERKER.conflictsWith(AncientCurse.LEECH_ATTACK))
        assertTrue(AncientCurse.SAP_SPIRIT.conflictsWithTurmoil)
        assertFalse(AncientCurse.WRATH.conflictsWithTurmoil)
    }

    @Test
    fun `book slots and varbits follow the cache-proven enum 862 order`() {
        assertEquals(18, AncientCurse.values.size)
        assertEquals((1..18).toList(), AncientCurse.values.map { it.slot })
        assertEquals((6821..6838).toList(), AncientCurse.values.map { it.varbit })
        assertEquals(1, AncientCurse.SAP_WARRIOR.slot)
        assertEquals(6, AncientCurse.DEFLECT_SUMMONING.slot)
        assertEquals(18, AncientCurse.SOUL_SPLIT.slot)
        assertEquals(6821, AncientCurse.SAP_WARRIOR.varbit)
        assertEquals(6838, AncientCurse.SOUL_SPLIT.varbit)
        assertEquals(6839, AncientCurse.TURMOIL_VARBIT)
        assertEquals(6820, AncientCurse.PROTECT_ITEM_VARBIT)
        assertEquals(AncientCurse.values.size, AncientCurse.values.map { it.slot }.distinct().size)
    }

    @Test
    fun `Soul Split deactivates active Deflects while Deflect Summoning stacks with Deflect Melee`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MELEE)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_SUMMONING)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_MELEE))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_SUMMONING))
        AncientCurses.toggleCurse(player, AncientCurse.SOUL_SPLIT)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_MELEE))
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_SUMMONING))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.SOUL_SPLIT))
    }

    @Test
    fun `Turmoil deactivates Saps and Leeches but keeps Berserker`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(player, AncientCurse.LEECH_ATTACK)
        AncientCurses.toggleCurse(player, AncientCurse.BERSERKER)
        AncientCurses.toggleTurmoil(player)
        assertTrue(AncientCurses.isTurmoilActive(player))
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.LEECH_ATTACK))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.BERSERKER))
    }

    @Test
    fun `Turmoil starts at its base boost and snapshots the opponent level after a melee proc`() {
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleTurmoil(player)
        assertEquals(1.15, AncientCurses.turmoilMultiplier(player, Skills.ATTACK, null), 1e-9)
        val opponent = newPlayer(mapOf(Skills.ATTACK to 99, Skills.STRENGTH to 80, Skills.DEFENCE to 40))
        AncientCurses.onDamageDealt(player, opponent, damage = 1)
        assertEquals(1.0 + (15.0 + 14) / 100.0, AncientCurses.turmoilMultiplier(player, Skills.ATTACK, opponent), 1e-9)
        assertEquals(1.0 + (23.0 + 8) / 100.0, AncientCurses.turmoilMultiplier(player, Skills.STRENGTH, opponent), 1e-9)
        assertEquals(1.0 + (15.0 + 6) / 100.0, AncientCurses.turmoilMultiplier(player, Skills.DEFENCE, opponent), 1e-9)
        assertEquals(1.0, AncientCurses.turmoilMultiplier(player, Skills.RANGED, opponent), 1e-9)
        val inactive = newPlayer()
        assertEquals(1.0, AncientCurses.turmoilMultiplier(inactive, Skills.ATTACK, opponent), 1e-9)
    }

    @Test
    fun `Sap Warrior escalates one point per activation up to 20 percent and never below the cap`() {
        val attacker = newPlayer()
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, AncientCurse.SAP_WARRIOR)
        val target = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        assertEquals(90, target.skills.getCurrentLevel(Skills.ATTACK))
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        assertEquals(89, target.skills.getCurrentLevel(Skills.ATTACK))
        repeat(20) { AncientCurses.onDamageDealt(attacker, target, damage = 10) }
        assertEquals(80, target.skills.getCurrentLevel(Skills.ATTACK))
    }

    @Test
    fun `Leech Attack escalates the drain to 25 percent and the self boost to 10 percent`() {
        val attacker = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, AncientCurse.LEECH_ATTACK)
        val target = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        assertEquals(90, target.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(103, attacker.skills.getCurrentLevel(Skills.ATTACK))
        repeat(30) { AncientCurses.onDamageDealt(attacker, target, damage = 10) }
        assertEquals(75, target.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(108, attacker.skills.getCurrentLevel(Skills.ATTACK))
    }

    @Test
    fun `Sap and Leech do nothing when the activation roll fails`() {
        val attacker = newPlayer()
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, AncientCurse.SAP_WARRIOR)
        every { attacker.world.percentChance(any()) } returns false
        val target = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        assertEquals(99, target.skills.getCurrentLevel(Skills.ATTACK))
    }

    @Test
    fun `Sap effects follow the incoming combat style and only one style curse procs`() {
        val attacker = newPlayer().also {
            AncientCurses.switchBook(it, AncientCurses.PrayerBook.ANCIENT)
            AncientCurses.toggleCurse(it, AncientCurse.SAP_WARRIOR)
            AncientCurses.toggleCurse(it, AncientCurse.SAP_RANGER)
        }
        val target = newPlayer(mapOf(Skills.ATTACK to 99, Skills.RANGED to 99))

        AncientCurses.onDamageDealt(attacker, target, damage = 10, style = CombatClass.RANGED)
        assertEquals(99, target.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(90, target.skills.getCurrentLevel(Skills.RANGED))

        AncientCurses.onDamageDealt(attacker, target, damage = 10, style = CombatClass.MELEE)
        assertEquals(90, target.skills.getCurrentLevel(Skills.ATTACK))
    }
}
