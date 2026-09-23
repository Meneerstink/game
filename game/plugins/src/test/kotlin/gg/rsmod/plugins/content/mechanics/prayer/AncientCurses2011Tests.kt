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
        // Owner 2026-09-18: Sap Warrior / Ranger / Mage are mutually exclusive (like the style leeches).
        assertTrue(AncientCurse.SAP_WARRIOR.conflictsWith(AncientCurse.SAP_MAGE))
        assertFalse(AncientCurse.SAP_WARRIOR.conflictsWith(AncientCurse.SAP_SPIRIT))
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

    private fun procAfterCooldown(attacker: Player, target: Player) {
        val world = attacker.world
        val now = world.currentCycle
        every { world.currentCycle } returns now + AncientCurses.SAP_LEECH_COOLDOWN_TICKS
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
    }

    @Test
    fun `Sap Warrior applies the base 10 percent as a modifier, drains one percent per proc up to 20 percent, and releases the base on deactivation`() {
        // Owner-supplied 2011 Knowledge Base: immediate 10 %, slow drain to 20 %, initial 10 % restored
        // immediately on deactivation, the rest regenerates as usual.
        val attacker = newPlayer()
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, AncientCurse.SAP_WARRIOR)
        val target = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        assertEquals(98, target.skills.getCurrentLevel(Skills.ATTACK)) // owner 2026-09-19: the first proc is a real level too
        assertEquals(0.9, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        assertEquals(98, target.skills.getCurrentLevel(Skills.ATTACK)) // owner 2026-09-18: inside the 45 s cooldown
        procAfterCooldown(attacker, target)
        assertEquals(97, target.skills.getCurrentLevel(Skills.ATTACK))
        repeat(20) { procAfterCooldown(attacker, target) }
        assertEquals(90, target.skills.getCurrentLevel(Skills.ATTACK)) // 99 - 9 (extra 10 % of 99)
        assertEquals(0.9, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        AncientCurses.toggleCurse(attacker, AncientCurse.SAP_WARRIOR)
        assertEquals(1.0, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        assertEquals(90, target.skills.getCurrentLevel(Skills.ATTACK))
    }

    @Test
    fun `a Sap and a Leech conflict only when they drain the same type`() {
        val offenders = mutableListOf<String>()
        fun expect(a: AncientCurse, b: AncientCurse, conflict: Boolean) {
            if (a.conflictsWith(b) != conflict || b.conflictsWith(a) != conflict) offenders += "$a vs $b expected conflict=$conflict"
        }
        expect(AncientCurse.SAP_RANGER, AncientCurse.LEECH_RANGED, true)
        expect(AncientCurse.SAP_RANGER, AncientCurse.LEECH_DEFENCE, true)
        expect(AncientCurse.SAP_RANGER, AncientCurse.LEECH_ATTACK, false)
        expect(AncientCurse.SAP_WARRIOR, AncientCurse.LEECH_ATTACK, true)
        expect(AncientCurse.SAP_WARRIOR, AncientCurse.LEECH_STRENGTH, true)
        expect(AncientCurse.SAP_WARRIOR, AncientCurse.LEECH_MAGIC, false)
        expect(AncientCurse.SAP_MAGE, AncientCurse.LEECH_MAGIC, true)
        expect(AncientCurse.SAP_MAGE, AncientCurse.LEECH_RANGED, false)
        expect(AncientCurse.SAP_SPIRIT, AncientCurse.LEECH_SPECIAL_ATTACK, true)
        expect(AncientCurse.SAP_SPIRIT, AncientCurse.LEECH_ENERGY, false)
        AncientCurse.values().filter { it.category == AncientCurse.Category.SAP }.forEach { expect(it, AncientCurse.LEECH_ENERGY, false) }
        listOf(AncientCurse.SAP_WARRIOR, AncientCurse.SAP_RANGER, AncientCurse.SAP_MAGE, AncientCurse.SAP_SPIRIT).forEach { a ->
            listOf(AncientCurse.SAP_WARRIOR, AncientCurse.SAP_RANGER, AncientCurse.SAP_MAGE, AncientCurse.SAP_SPIRIT).forEach { b -> if (a != b) expect(a, b, a in AncientCurse.STYLE_SAPS && b in AncientCurse.STYLE_SAPS) }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `owner rule - Leech Strength, Ranged and Magic are mutually exclusive, other leeches stack`() {
        val offenders = mutableListOf<String>()
        val style = listOf(AncientCurse.LEECH_STRENGTH, AncientCurse.LEECH_RANGED, AncientCurse.LEECH_MAGIC)
        val leeches = AncientCurse.values().filter { it.category == AncientCurse.Category.LEECH }
        leeches.forEach { a -> leeches.forEach { b ->
            if (a != b) {
                val expected = a in style && b in style
                if (a.conflictsWith(b) != expected) offenders += "$a vs $b expected conflict=$expected"
            }
        } }
        assertEquals(emptyList<String>(), offenders)
        // Activation path: turning on a second style leech switches the first off.
        val player = newPlayer()
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        style.forEach { AncientCurses.toggleCurse(player, it) }
        assertEquals(listOf(AncientCurse.LEECH_MAGIC), style.filter { AncientCurses.isCurseActive(player, it) })
        AncientCurses.toggleCurse(player, AncientCurse.LEECH_ATTACK)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.LEECH_MAGIC))
    }

    @Test
    fun `Leech Attack escalates the drain to 25 percent and the self boost to 10 percent`() {
        val attacker = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, AncientCurse.LEECH_ATTACK)
        val target = newPlayer(mapOf(Skills.ATTACK to 99))
        AncientCurses.onDamageDealt(attacker, target, damage = 10)
        // KB: base 10 % drain / 5 % boost are modifiers while the Leech is active; procs are real +-1 % level steps (drain to
        // 25 %, boost to 10 %) that regenerate as usual. Owner 2026-09-19: the FIRST proc is a real step as well (it used to
        // register only the invisible modifier, so the owner saw "the message but no drain").
        assertEquals(98, target.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(0.9, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        assertEquals(100, attacker.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(1.05, AncientCurses.leechMultiplier(attacker, Skills.ATTACK), 1e-9)
        // Owner 2026-09-18: one proc per 45-second cooldown; the cap itself is unchanged.
        repeat(30) { procAfterCooldown(attacker, target) }
        assertEquals(85, target.skills.getCurrentLevel(Skills.ATTACK)) // 99 - 14 (extra 15 % of 99)
        assertEquals(103, attacker.skills.getCurrentLevel(Skills.ATTACK)) // 99 + 4 (extra 5 % of 99)
        AncientCurses.toggleCurse(attacker, AncientCurse.LEECH_ATTACK)
        assertEquals(1.0, AncientCurses.leechMultiplier(attacker, Skills.ATTACK), 1e-9)
        assertEquals(1.0, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        assertEquals(103, attacker.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(85, target.skills.getCurrentLevel(Skills.ATTACK))
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
        // Owner 2026-09-18: turning Sap Ranger on switched Sap Warrior off.
        assertFalse(AncientCurses.isCurseActive(attacker, AncientCurse.SAP_WARRIOR))
        val target = newPlayer(mapOf(Skills.ATTACK to 99, Skills.RANGED to 99))

        AncientCurses.onDamageDealt(attacker, target, damage = 10, style = CombatClass.RANGED)
        assertEquals(1.0, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        assertEquals(0.9, AncientCurses.drainMultiplier(target, Skills.RANGED), 1e-9)
        assertEquals(98, target.skills.getCurrentLevel(Skills.RANGED)) // the first proc is a real one-level drain (owner 2026-09-19)

        // Sap Warrior is off, so a melee hit drains nothing.
        AncientCurses.onDamageDealt(attacker, target, damage = 10, style = CombatClass.MELEE)
        assertEquals(1.0, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
    }

    @Test
    /** Owner 2026-09-19: a potion-boosted stat loses at most 1 level per trigger (117 -> 116), never the whole boost. */
    fun `Sap and Leech drain a temporary combat potion boost by at most one level`() {
        val cases = listOf(
            Triple(AncientCurse.SAP_WARRIOR, CombatClass.MELEE, intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE)),
            Triple(AncientCurse.SAP_RANGER, CombatClass.RANGED, intArrayOf(Skills.RANGED, Skills.DEFENCE)),
            Triple(AncientCurse.SAP_MAGE, CombatClass.MAGIC, intArrayOf(Skills.MAGIC, Skills.DEFENCE)),
            Triple(AncientCurse.LEECH_ATTACK, CombatClass.MELEE, intArrayOf(Skills.ATTACK)),
            Triple(AncientCurse.LEECH_RANGED, CombatClass.RANGED, intArrayOf(Skills.RANGED)),
            Triple(AncientCurse.LEECH_MAGIC, CombatClass.MAGIC, intArrayOf(Skills.MAGIC)),
        )

        cases.forEach { (curse, style, skills) ->
            val attacker = newPlayer()
            AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
            AncientCurses.toggleCurse(attacker, curse)
            val target = newPlayer(skills.associateWith { 99 })
            skills.forEach { skill -> target.skills.setCurrentLevel(skill, 117) }

            AncientCurses.onDamageDealt(attacker, target, damage = 10, style = style)

            skills.forEach { skill ->
                val level = target.skills.getCurrentLevel(skill)
                assertTrue("$curse skill $skill: boosted 117 may drop by at most 1, was $level", level == 116 || level == 117)
                assertEquals(1.0, AncientCurses.drainMultiplier(target, skill), 1e-9)
            }
        }
    }
}
