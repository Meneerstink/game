package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner curse table 2026-10-11 (FINAL, dossier 4a-2), measured through the real combat formulas with the owner's own setups
 * (bug report 20261011-200043: 99 combat stats, dark bow 11235 + dragon arrows 11212; melee: an abyssal whip, +82 strength).
 * Expected numbers are worked out by hand from the OSRS Wiki formula ⌊0.5 + (⌊level × prayer⌋ + style + 8) × (bonus + 64) / 640⌋.
 */
class CurseCombatFormulaTests {
    @AfterTest
    fun reset() = unmockkAll()

    private fun player(skill: Int, level: Int, bonusSlot: BonusSlot, bonus: Int, style: WeaponStyle, active: Set<AncientCurse>, turmoil: Boolean = false): Player {
        mockkObject(CombatConfigs, Prayers, AncientCurses)
        every { Prayers.isActive(any(), any()) } returns false
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id
        val skills = SkillSet(maxSkills = 7).apply { setBaseLevel(skill, 99); setCurrentLevel(skill, level) }
        every { player.skills } returns skills
        val bonuses = IntArray(18)
        bonuses[bonusSlot.id] = bonus
        every { player.equipmentBonuses } returns bonuses
        every { player.equipment } returns ItemContainer(DefinitionSet(), EQUIPMENT_KEY)
        every { player.attr } returns AttributeMap()
        every { CombatConfigs.getAttackStyle(player) } returns style
        every { AncientCurses.getBook(player) } returns AncientCurses.PrayerBook.ANCIENT
        every { AncientCurses.isCurseActive(player, any()) } answers { secondArg<AncientCurse>() in active }
        every { AncientCurses.isTurmoilActive(player) } returns turmoil
        return player
    }

    private fun target(): Player {
        val target = mockk<Player>(relaxed = true)
        every { target.prayerIcon } returns PrayerIcon.NONE.id
        every { target.attr } returns AttributeMap()
        return target
    }

    private fun ranged(level: Int, vararg active: AncientCurse) =
        RangedCombatFormula.getMaxHit(player(Skills.RANGED, level, BonusSlot.RANGED_STRENGTH_BONUS, DRAGON_ARROWS, WeaponStyle.RAPID, active.toSet()), target(), 1.0, 1.0).toInt()

    private fun melee(level: Int, vararg active: AncientCurse, turmoil: Boolean = false) =
        MeleeCombatFormula.getMaxHit(player(Skills.STRENGTH, level, BonusSlot.STRENGTH_BONUS, WHIP, WeaponStyle.AGGRESSIVE, active.toSet(), turmoil), target(), 1.0, 1.0).toInt()

    @Test
    fun `dark bow with dragon arrows - Sap Ranger and Leech Ranged max hits`() {
        assertEquals(21, ranged(99), "no curse")
        assertEquals(23, ranged(99, AncientCurse.SAP_RANGER), "Sap Ranger +15 % ranged damage (N496)")
        assertEquals(25, ranged(99, AncientCurse.LEECH_RANGED), "Leech Ranged x1.23 = Rigour (N496)")
        assertEquals(26, ranged(112, AncientCurse.SAP_RANGER), "Sap Ranger with a ranging potion (112)")
        assertEquals(28, ranged(112, AncientCurse.LEECH_RANGED), "Leech Ranged with a ranging potion (112)")
        // Sap Warrior / Sap Mage do nothing for ranged.
        assertEquals(21, ranged(99, AncientCurse.SAP_WARRIOR))
        assertEquals(21, ranged(99, AncientCurse.SAP_MAGE))
    }

    @Test
    fun `whip on aggressive - Sap Warrior and Leech Strength max hits, nothing stacks with Turmoil`() {
        assertEquals(25, melee(99), "no curse")
        assertEquals(28, melee(99, AncientCurse.SAP_WARRIOR), "Sap Warrior +15 % Strength (N496)")
        assertEquals(29, melee(99, AncientCurse.LEECH_STRENGTH), "Leech Strength +18 % (was +5 % = 26)")
        assertEquals(34, melee(118, AncientCurse.LEECH_STRENGTH), "Leech Strength with a super strength (118)")
        assertEquals(25, melee(99, AncientCurse.LEECH_ATTACK), "Leech Attack does not touch the max hit")
        assertEquals(25, melee(99, AncientCurse.SAP_RANGER))
        // Turmoil running: the formula takes Turmoil's own multiplier and ignores a leech boost (owner: no stacking).
        val turmoilOnly = melee(99, turmoil = true)
        assertEquals(turmoilOnly, melee(99, AncientCurse.LEECH_STRENGTH, turmoil = true), "Leech Strength + Turmoil = Turmoil alone")
        assertEquals(turmoilOnly, melee(99, AncientCurse.SAP_WARRIOR, turmoil = true), "Sap Warrior + Turmoil = Turmoil alone")
    }

    private companion object {
        /** items.yml ranged_strength of Dragon arrow (11212); the dark bow (11235) adds 0. */
        const val DRAGON_ARROWS = 60

        /** items.yml melee_strength of Abyssal whip (4151). */
        const val WHIP = 82
    }
}
