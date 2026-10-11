package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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

    /**
     * WORK_QUEUE Q3: cache clientscript 1710 draws the curse tab's Turmoil Attack / Strength / Defence as 15 / 23 / 15 % plus
     * clientscript 1714 of varbits 6844 / 6845 / 6846 (varp 1584 bits 5-9 / 10-14 / 15-19, cache varbit defs). They must carry
     * the snapshotted opponent bonus in levels and fall back to 0 when Turmoil stops.
     */
    @Test
    fun `Turmoil shows the opponent bonus in the curse tab varbits and clears them`() {
        val player = newPlayer()
        val varps = gg.rsmod.game.model.varp.VarpSet((0 until 2000).toSet())
        every { player.varps } returns varps
        val layout = mapOf(6844 to 5, 6845 to 10, 6846 to 15)
        val definitions = player.world.definitions
        every { definitions.get(VarbitDef::class.java, any()) } answers {
            val id = secondArg<Int>()
            VarbitDef(id).apply {
                layout[id]?.let { start ->
                    varp = 1584
                    startBit = start
                    endBit = start + 4
                }
            }
        }
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleTurmoil(player)
        val opponent = newPlayer(mapOf(Skills.ATTACK to 99, Skills.STRENGTH to 80, Skills.DEFENCE to 40))
        AncientCurses.onDamageDealt(player, opponent, damage = 1)
        assertEquals(14, varps.getBit(1584, 5, 9))
        assertEquals(8, varps.getBit(1584, 10, 14))
        assertEquals(6, varps.getBit(1584, 15, 19))
        AncientCurses.toggleTurmoil(player)
        assertFalse(AncientCurses.isTurmoilActive(player))
        assertEquals(0, varps.getBit(1584, 5, 19))
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

    private fun procAfterCooldown(attacker: Player, target: Player, style: CombatClass = CombatClass.MELEE) {
        val world = attacker.world
        val now = world.currentCycle
        every { world.currentCycle } returns now + AncientCurses.SAP_LEECH_COOLDOWN_TICKS
        AncientCurses.onDamageDealt(attacker, target, damage = 10, style = style)
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
        // Owner 2026-10-09 (Darkan): Sap Spirit and Leech Energy close each other; Sap Spirit closes Leech Special Attack, which
        // closes nothing (`conflictsWith` is asked of the curse going on).
        expect(AncientCurse.SAP_SPIRIT, AncientCurse.LEECH_ENERGY, true)
        assertTrue(AncientCurse.SAP_SPIRIT.conflictsWith(AncientCurse.LEECH_SPECIAL_ATTACK))
        assertFalse(AncientCurse.LEECH_SPECIAL_ATTACK.conflictsWith(AncientCurse.SAP_SPIRIT))
        listOf(AncientCurse.SAP_WARRIOR, AncientCurse.SAP_RANGER, AncientCurse.SAP_MAGE).forEach { expect(it, AncientCurse.LEECH_ENERGY, false) }
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
        // Owner curse table 2026-10-11: Leech Attack +15 % (was 5 %).
        assertEquals(1.15, AncientCurses.leechMultiplier(attacker, Skills.ATTACK), 1e-9)
        // Owner 2026-09-18: one proc per 45-second cooldown; the cap itself is unchanged.
        repeat(30) { procAfterCooldown(attacker, target) }
        assertEquals(85, target.skills.getCurrentLevel(Skills.ATTACK)) // 99 - 14 (extra 15 % of 99)
        assertEquals(104, attacker.skills.getCurrentLevel(Skills.ATTACK)) // 99 + 5 (owner 2026-10-09: at most +5 levels per Leech run)
        AncientCurses.toggleCurse(attacker, AncientCurse.LEECH_ATTACK)
        assertEquals(1.0, AncientCurses.leechMultiplier(attacker, Skills.ATTACK), 1e-9)
        assertEquals(1.0, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        assertEquals(104, attacker.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(85, target.skills.getCurrentLevel(Skills.ATTACK))
    }

    @Test
    fun `owner 2026-10-09 - a Leech lifts a potted stat one level per proc, at most five above the first proc`() {
        val attacker = newPlayer(mapOf(Skills.RANGED to 99))
        AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(attacker, AncientCurse.LEECH_RANGED)
        attacker.skills.setCurrentLevel(Skills.RANGED, 117)
        val target = newPlayer(mapOf(Skills.RANGED to 99))
        AncientCurses.onDamageDealt(attacker, target, damage = 10, style = CombatClass.RANGED)
        assertEquals(118, attacker.skills.getCurrentLevel(Skills.RANGED))
        repeat(10) { procAfterCooldown(attacker, target, CombatClass.RANGED) }
        assertEquals(117 + AncientCurses.LEECH_LEVEL_CAP, attacker.skills.getCurrentLevel(Skills.RANGED))
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

    /**
     * One test for the whole Leech set (owner 2026-10-06, bug 031309, OWNER_QUESTIONS Q5 answer A). Leech Attack, Strength,
     * Defence and Magic share one path: the base 5 % modifier while active plus a visible +1 level per proc up to +5 % of the
     * base level, and the target's drain (one level per proc, 10 % base modifier, 25 % cap). Leech Defence and Magic used to
     * carry a hidden +15 % / Augury modifier with no visible level (owner 2026-09-29) - that is gone. Leech Ranged keeps its
     * 2026-09-29 Rigour-style modifier (+20 % accuracy / +18 % damage, no levels). Leech Energy / Special Attack move 10 run
     * energy / 5 % special energy. Every Leech rolls Darkan's 20 % (LEECH_PROC_CHANCE_PERCENT).
     */
    @Test
    fun `the whole Leech set - stat leeches share one visible level path, ranged keeps its modifier, energy and special transfer`() {
        AncientCurse.values().filter { it.category == AncientCurse.Category.LEECH }.forEach {
            assertEquals(it.name, LEECH_PROC_CHANCE_PERCENT, it.activationChancePercent, 1e-9)
        }
        assertEquals(setOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC), AncientCurses.STAT_LEECHES_WITH_LEVEL_BOOST)
        val statLeeches =
            listOf(
                Triple(AncientCurse.LEECH_ATTACK, Skills.ATTACK, CombatClass.MELEE),
                Triple(AncientCurse.LEECH_STRENGTH, Skills.STRENGTH, CombatClass.MELEE),
                Triple(AncientCurse.LEECH_DEFENCE, Skills.DEFENCE, CombatClass.MELEE),
                Triple(AncientCurse.LEECH_MAGIC, Skills.MAGIC, CombatClass.MAGIC),
                Triple(AncientCurse.LEECH_RANGED, Skills.RANGED, CombatClass.RANGED),
            )
        statLeeches.forEach { (curse, skill, style) ->
            // Owner 2026-10-09: every stat Leech gives the visible +1 (Ranged too); Ranged keeps its Rigour-style modifier and
            // Magic has an Augury-style accuracy one, the others the shared 5 %.
            // Owner curse table 2026-10-11: Leech Attack +15 %, Leech Strength +18 %, Leech Defence +10 % (all were 5 %).
            val accuracy = when (skill) {
                Skills.RANGED -> AncientCurses.LEECH_RANGED_ACCURACY
                Skills.MAGIC -> AncientCurses.LEECH_MAGIC_ACCURACY
                Skills.ATTACK -> 1.15
                Skills.STRENGTH -> 1.18
                else -> 1.10
            }
            val damage = when (skill) {
                Skills.RANGED -> AncientCurses.LEECH_RANGED_DAMAGE
                Skills.MAGIC -> 1.0
                Skills.ATTACK -> 1.15
                Skills.STRENGTH -> 1.18
                else -> 1.10
            }
            val attacker = newPlayer(mapOf(skill to 99))
            AncientCurses.switchBook(attacker, AncientCurses.PrayerBook.ANCIENT)
            assertEquals(curse.name, 1.0, AncientCurses.leechMultiplier(attacker, skill), 1e-9)
            AncientCurses.toggleCurse(attacker, curse)
            val target = newPlayer(mapOf(skill to 99))
            AncientCurses.onDamageDealt(attacker, target, damage = 10, style = style)
            // The target: one real level and the 10 % base modifier on the first proc.
            assertEquals(curse.name, 98, target.skills.getCurrentLevel(skill))
            assertEquals(curse.name, 0.9, AncientCurses.drainMultiplier(target, skill), 1e-9)
            // The caster: a visible +1 and the curse's modifier.
            assertEquals(curse.name, 100, attacker.skills.getCurrentLevel(skill))
            assertEquals(curse.name, accuracy, AncientCurses.leechMultiplier(attacker, skill), 1e-9)
            assertEquals(curse.name, damage, AncientCurses.leechMultiplier(attacker, skill, damage = true), 1e-9)
            // One proc per cooldown (owner 2026-09-18, 15 s since 2026-10-09); the caps: drain 25 % (99 - 14), boost +5 levels.
            repeat(30) { procAfterCooldown(attacker, target, style) }
            assertEquals(curse.name, 85, target.skills.getCurrentLevel(skill))
            assertEquals(curse.name, 104, attacker.skills.getCurrentLevel(skill))
            AncientCurses.toggleCurse(attacker, curse)
            assertEquals(curse.name, 1.0, AncientCurses.leechMultiplier(attacker, skill), 1e-9)
            assertEquals(curse.name, 1.0, AncientCurses.drainMultiplier(target, skill), 1e-9)
            assertEquals(curse.name, 104, attacker.skills.getCurrentLevel(skill))
        }

        val runner = newPlayer()
        AncientCurses.switchBook(runner, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.toggleCurse(runner, AncientCurse.LEECH_ENERGY)
        val runTarget = newPlayer()
        every { runTarget.runEnergy } returns 50.0
        every { runner.runEnergy } returns 30.0
        AncientCurses.onDamageDealt(runner, runTarget, damage = 10)
        verify { runTarget.runEnergy = 40.0 }
        verify { runner.runEnergy = 40.0 }

        mockkObject(AttackTab)
        try {
            val specer = newPlayer()
            AncientCurses.switchBook(specer, AncientCurses.PrayerBook.ANCIENT)
            AncientCurses.toggleCurse(specer, AncientCurse.LEECH_SPECIAL_ATTACK)
            val specTarget = newPlayer()
            every { AttackTab.getEnergy(specTarget) } returns 100
            every { AttackTab.getEnergy(specer) } returns 50
            AncientCurses.onDamageDealt(specer, specTarget, damage = 10)
            verify { AttackTab.setEnergy(specTarget, 100 - AncientCurses.LEECH_SPECIAL_PCT) }
            verify { AttackTab.setEnergy(specer, 50 + AncientCurses.LEECH_SPECIAL_PCT) }
        } finally {
            unmockkObject(AttackTab)
        }
    }

    /** A player whose curse-tab varbits are real: 6857..6861 six bits each, the sapped / leeched flags one bit each. */
    private fun tabPlayer(level: Int = 99): Player {
        val player = newPlayer(listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC).associateWith { level })
        every { player.varps } returns gg.rsmod.game.model.varp.VarpSet((0 until 8000).toSet())
        val definitions = player.world.definitions
        every { definitions.get(VarbitDef::class.java, any()) } answers {
            val id = secondArg<Int>()
            VarbitDef(id).apply {
                varp = id
                startBit = 0
                endBit = if (id in 6857..6861) 5 else 0
            }
        }
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
        return player
    }

    private fun tab(player: Player) = (0..4).map { player.varps.getBit(6857 + it, 0, 5) - 30 }

    private fun flags(player: Player, base: Int) = (0..4).map { player.varps.getBit(base + it, 0, 0) }

    /**
     * Owner 2026-10-11 ("de show stat adjustment van alle curses moet volledig kloppen"): cs2 1710/1711 show only the player's
     * OWN stats. An enemy Sap / Leech sets the sapped (6847+) / leeched (6852+) flag, from which the client shows its initial
     * -10 % and floors the total at -20 % / -25 %; the server adds only the levels the escalation really took. Owner curse table
     * 2026-10-11: a Sap now boosts its caster, and the caster's own book shows that boost (Sap Warrior +10 % Attack / Strength).
     */
    @Test
    fun `an enemy Sap or Leech shows on the victim's curse tab, a Sap shows its caster's own boost`() {
        val caster = tabPlayer()
        val victim = tabPlayer()
        AncientCurses.toggleCurse(caster, AncientCurse.SAP_WARRIOR)
        AncientCurses.onDamageDealt(caster, victim, damage = 10)
        // Sap Warrior: Attack, Strength and Defence sapped, one real level each drained by the first proc.
        assertEquals(listOf(1, 1, 1, 0, 0), flags(victim, 6847))
        assertEquals(listOf(0, 0, 0, 0, 0), flags(victim, 6852))
        assertEquals(listOf(-1, -1, -1, 0, 0), tab(victim))
        // The caster: +10 % Attack and Strength (10 levels of 99), no Defence; never "sapped" itself.
        assertEquals(listOf(10, 10, 0, 0, 0), tab(caster))
        assertEquals(listOf(0, 0, 0, 0, 0), flags(caster, 6847))
        // The caster switches the Sap off: the initial 10 % is released at once, the drained levels regenerate as usual.
        AncientCurses.toggleCurse(caster, AncientCurse.SAP_WARRIOR)
        assertEquals(listOf(0, 0, 0, 0, 0), tab(caster))
        assertEquals(listOf(0, 0, 0, 0, 0), flags(victim, 6847))
        assertEquals(listOf(-1, -1, -1, 0, 0), tab(victim))
        victim.skills.setCurrentLevel(Skills.ATTACK, 99)
        AncientCurses.refreshStatAdjustments(victim)
        assertEquals(0, tab(victim)[0])

        // Leech Ranged: the victim's Ranged is leeched (floor -25 % in the client), the caster sees its own boost.
        val leecher = tabPlayer()
        val other = tabPlayer()
        AncientCurses.toggleCurse(leecher, AncientCurse.LEECH_RANGED)
        assertEquals(15, tab(leecher)[3])
        AncientCurses.onDamageDealt(leecher, other, damage = 10, style = CombatClass.RANGED)
        assertEquals(listOf(0, 0, 0, 1, 0), flags(other, 6852))
        assertEquals(listOf(0, 0, 0, 0, 0), flags(other, 6847))
        assertEquals(-1, tab(other)[3])
        assertEquals(16, tab(leecher)[3])
    }

    /**
     * Owner curse table 2026-10-11 (FINAL, dossier 4a-2): exactly the six changed curses, and the unchanged ones exactly as before.
     * The combat formulas take [AncientCurses.curseBoostMultiplier] for every stat; the curse tab (own book) shows the boost in
     * levels above the +5 % cs2 1713 adds for an active Leech.
     */
    @Test
    fun `the owner's 2026-10-11 curse table - six changed curses, the others unchanged`() {
        data class Row(val curse: AncientCurse, val skill: Int, val accuracy: Double, val damage: Double, val tab: Int)
        val rows = listOf(
            // Changed.
            Row(AncientCurse.SAP_WARRIOR, Skills.ATTACK, 1.10, 1.10, 10),
            Row(AncientCurse.SAP_WARRIOR, Skills.STRENGTH, 1.10, 1.10, 10),
            Row(AncientCurse.SAP_WARRIOR, Skills.DEFENCE, 1.0, 1.0, 0),
            Row(AncientCurse.SAP_RANGER, Skills.RANGED, 1.10, 1.10, 10),
            Row(AncientCurse.SAP_RANGER, Skills.DEFENCE, 1.0, 1.0, 0),
            Row(AncientCurse.SAP_MAGE, Skills.MAGIC, 1.10, 1.0, 10),
            Row(AncientCurse.SAP_MAGE, Skills.DEFENCE, 1.0, 1.0, 0),
            Row(AncientCurse.LEECH_ATTACK, Skills.ATTACK, 1.15, 1.15, 10),
            Row(AncientCurse.LEECH_STRENGTH, Skills.STRENGTH, 1.18, 1.18, 13),
            Row(AncientCurse.LEECH_DEFENCE, Skills.DEFENCE, 1.10, 1.10, 5),
            // Unchanged (regression targets).
            Row(AncientCurse.LEECH_RANGED, Skills.RANGED, 1.20, 1.13, 15),
            Row(AncientCurse.LEECH_MAGIC, Skills.MAGIC, 1.25, 1.0, 20),
            Row(AncientCurse.SAP_SPIRIT, Skills.ATTACK, 1.0, 1.0, 0),
        )
        for (row in rows) {
            val player = tabPlayer()
            val label = "${row.curse.name} skill ${row.skill}"
            assertEquals(label, 1.0, AncientCurses.curseBoostMultiplier(player, row.skill), 1e-9)
            AncientCurses.toggleCurse(player, row.curse)
            assertTrue(label, AncientCurses.isCurseActive(player, row.curse))
            assertEquals(label, row.accuracy, AncientCurses.curseBoostMultiplier(player, row.skill), 1e-9)
            assertEquals(label, row.damage, AncientCurses.curseBoostMultiplier(player, row.skill, damage = true), 1e-9)
            assertEquals(label, row.tab, tab(player)[listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC).indexOf(row.skill)])
            AncientCurses.toggleCurse(player, row.curse)
            assertEquals("$label off", 1.0, AncientCurses.curseBoostMultiplier(player, row.skill), 1e-9)
            assertEquals("$label off", 0, tab(player)[listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC).indexOf(row.skill)])
        }
        assertEquals(15, AncientCurses.LEECH_ATTACK_BOOST_PCT)
        assertEquals(18, AncientCurses.LEECH_STRENGTH_BOOST_PCT)
        assertEquals(10, AncientCurses.LEECH_DEFENCE_BOOST_PCT)
        assertEquals(10, AncientCurses.SAP_SELF_BOOST_PCT)
    }

    @Test
    fun `a Sap's drain on the target is unchanged and no curse boost stacks with Turmoil`() {
        // Drain: the first proc takes one real level and the 10 % base modifier, the escalation stops at 20 %.
        val caster = tabPlayer()
        val target = tabPlayer()
        AncientCurses.toggleCurse(caster, AncientCurse.SAP_WARRIOR)
        AncientCurses.onDamageDealt(caster, target, damage = 10)
        assertEquals(98, target.skills.getCurrentLevel(Skills.ATTACK))
        assertEquals(0.9, AncientCurses.drainMultiplier(target, Skills.ATTACK), 1e-9)
        repeat(30) { procAfterCooldown(caster, target) }
        assertEquals(90, target.skills.getCurrentLevel(Skills.ATTACK)) // 99 - 9 (the extra 10 % of 99 rounded down, like the Leech's 14)
        // The Sap gives the caster no real levels (only the modifier while on).
        assertEquals(99, caster.skills.getCurrentLevel(Skills.ATTACK))

        // Turmoil switches the Sap / Leech off and the other way round: never both boosts at once.
        AncientCurses.toggleTurmoil(caster)
        assertTrue(AncientCurses.isTurmoilActive(caster))
        assertFalse(AncientCurses.isCurseActive(caster, AncientCurse.SAP_WARRIOR))
        assertEquals(1.0, AncientCurses.curseBoostMultiplier(caster, Skills.ATTACK), 1e-9)
        AncientCurses.toggleCurse(caster, AncientCurse.LEECH_STRENGTH)
        assertFalse(AncientCurses.isTurmoilActive(caster))
        assertEquals(1.18, AncientCurses.curseBoostMultiplier(caster, Skills.STRENGTH), 1e-9)
    }
}
