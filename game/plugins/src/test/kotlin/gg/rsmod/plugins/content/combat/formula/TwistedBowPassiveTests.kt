package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.math.floor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Coverage for S3_TWISTED_BOW_PASSIVE (2026-09-03 autonomous run - see `RSPS_DECISIONS.md` and
 * `RSPS_IMPORT_MANIFEST.yml` for the full write-up).
 *
 * SOURCE (see the KDoc on [TargetModifiers.rangedAccuracyMultiplier]/[TargetModifiers.rangedDamageMultiplier]
 * for the full citation): OSRS Wiki "Twisted bow" Passive effect section, quoting Jagex
 * developer Mod Kieren's own formula, cross-checked against two of the wiki's own worked
 * table entries (Magic 99 -> accuracy 93.45%; Magic 350 inside Chambers of Xeric -> damage
 * 248.11%), both of which this suite's hand-derived expected percentages below reproduce
 * exactly using the same arithmetic:
 *
 *   accuracy%(M) = clamp(140 + (3M-10)/100 - ((3M/10-100)^2)/100, 0, 140)
 *   damage%(M)   = clamp(250 + (3M-14)/100 - ((3M/10-140)^2)/100, 0, 250)
 *
 * Every expected multiplier below is a literal `Double` computed by hand from that formula
 * (shown worked in each test's comment), not obtained by calling [TargetModifiers]' own
 * (`private`) percent functions - so a transcription bug in the production formula cannot
 * silently pass by "agreeing with itself".
 */
class TwistedBowPassiveTests {
    @BeforeTest
    fun setUp() {
        mockkObject(CombatConfigs, Prayers)
        every { CombatConfigs.getAttackStyle(any()) } returns WeaponStyle.RAPID
        every { Prayers.isActive(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CombatConfigs, Prayers)
    }

    // ---- 1: unequipped control ----

    @Test
    fun `no Twisted bow equipped - passive has no effect regardless of target Magic`() {
        val player = newPlayer(weapon = null)
        val lowMagicTarget = newTargetPlayer(magicLevel = 1)
        val highMagicTarget = newTargetPlayer(magicLevel = 99)

        assertEquals(1.0, TargetModifiers.rangedAccuracyMultiplier(player, lowMagicTarget), 1e-9)
        assertEquals(1.0, TargetModifiers.rangedDamageMultiplier(player, lowMagicTarget), 1e-9)
        assertEquals(1.0, TargetModifiers.rangedAccuracyMultiplier(player, highMagicTarget), 1e-9)
        assertEquals(1.0, TargetModifiers.rangedDamageMultiplier(player, highMagicTarget), 1e-9)
    }

    // ---- 2 / 14: wrong weapon control / legacy ranged control ----

    @Test
    fun `a different bow (Magic shortbow) never receives the passive against a high-Magic target`() {
        val player = newPlayer(weapon = Items.MAGIC_SHORTBOW)
        val target = newTargetPlayer(magicLevel = 99)

        assertEquals(1.0, TargetModifiers.rangedAccuracyMultiplier(player, target), 1e-9)
        assertEquals(1.0, TargetModifiers.rangedDamageMultiplier(player, target), 1e-9)
    }

    @Test
    fun `legacy ranged control - getMaxHit for a non-Twisted-bow weapon is invariant to target Magic`() {
        val player = newPlayer(weapon = Items.MAGIC_SHORTBOW, rangedLevel = 99, rangedStrengthBonus = 0)
        val lowMagicTarget = newTargetPlayer(magicLevel = 1)
        val highMagicTarget = newTargetPlayer(magicLevel = 99)

        val hitVsLowMagic = RangedCombatFormula.getMaxHit(player, lowMagicTarget, 1.0, 1.0)
        val hitVsHighMagic = RangedCombatFormula.getMaxHit(player, highMagicTarget, 1.0, 1.0)
        assertEquals(hitVsLowMagic, hitVsHighMagic, 1e-9)
    }

    // ---- 3 / 4 / 5: low / medium / high Magic targets ----

    @Test
    fun `low-Magic target (level 1) - exact hand-derived accuracy and damage percentages`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 1)

        // scaled = 3*1/10 = 0.3
        // accuracy = 140 + (3-10)/100 - (0.3-100)^2/100 = 140 - 0.07 - 99.4009 = 40.5291
        // damage   = 250 + (3-14)/100 - (0.3-140)^2/100 = 250 - 0.11 - 195.1609 = 54.7291
        assertEquals(40.5291 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, target), 1e-6)
        assertEquals(54.7291 / 100.0, TargetModifiers.rangedDamageMultiplier(player, target), 1e-6)
    }

    @Test
    fun `medium-Magic target (level 70) - exact hand-derived accuracy and damage percentages`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 70)

        // scaled = 21
        // accuracy = 140 + (210-10)/100 - (21-100)^2/100 = 140 + 2.0 - 62.41 = 79.59
        // damage   = 250 + (210-14)/100 - (21-140)^2/100 = 250 + 1.96 - 141.61 = 110.35
        assertEquals(79.59 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, target), 1e-6)
        assertEquals(110.35 / 100.0, TargetModifiers.rangedDamageMultiplier(player, target), 1e-6)
    }

    @Test
    fun `high-Magic target (level 99) - matches the wiki's own published 93_45 percent accuracy figure`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 99)

        // scaled = 29.7
        // accuracy = 140 + (297-10)/100 - (29.7-100)^2/100 = 140 + 2.87 - 49.4209 = 93.4491
        //   (the OSRS Wiki's own worked table independently states 93.45% at Magic 99, which
        //   this hand-derivation reproduces to the hundredths place: 93.4491 rounds to 93.45.)
        // damage   = 250 + (297-14)/100 - (29.7-140)^2/100 = 250 + 2.83 - 121.6609 = 131.1691
        assertEquals(93.4491 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, target), 1e-6)
        assertEquals(131.1691 / 100.0, TargetModifiers.rangedDamageMultiplier(player, target), 1e-6)
    }

    // ---- 6: cap boundary ----

    @Test
    fun `accuracy caps at exactly 140 percent, first reached at Magic 244 (not 243)`() {
        // 243/244 exceed SkillSet.MAX_LVL (99), so this uses the target's Magic attack bonus -
        // exactly like the "whichever is higher" input path - to reach that range, matching how
        // it's actually reached in-game (Magic accuracy bonus, not a raised Magic skill level).
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val justBelowCap = newTargetPlayer(magicLevel = 1, magicAccuracyBonus = 243)
        val atCap = newTargetPlayer(magicLevel = 1, magicAccuracyBonus = 244)

        // Magic 243: scaled = 72.9; 140 + (729-10)/100 - (72.9-100)^2/100 = 140+7.19-7.3441 = 139.8459
        assertEquals(139.8459 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, justBelowCap), 1e-6)

        // Magic 244: scaled = 73.2; raw = 140 + (732-10)/100 - (73.2-100)^2/100 = 140+7.22-7.1824
        //          = 140.0376, which is ABOVE the sourced 140 cap and must be clamped down to
        //          exactly 1.4 (140.0%), not left at the raw 140.0376/100 = 1.400376 value.
        assertEquals(140.0 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, atCap), 1e-9)
    }

    // ---- 7: above-cap target ----

    @Test
    fun `increasing target Magic accuracy bonus past 250 does not increase the damage percentage further`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val atInputCap = newTargetPlayer(magicLevel = 1, magicAccuracyBonus = 250)
        val pastInputCap = newTargetPlayer(magicLevel = 1, magicAccuracyBonus = 300)

        // Magic input clamps at 250 in this codebase (no Chambers of Xeric content - see the
        // KDoc source note on TargetModifiers), so 250 and 300 must produce the IDENTICAL
        // damage percentage: scaled = 75; 250 + (750-14)/100 - (75-140)^2/100
        //                            = 250 + 7.36 - 42.25 = 215.11, still below the 250 cap.
        val atCapMultiplier = TargetModifiers.rangedDamageMultiplier(player, atInputCap)
        val pastCapMultiplier = TargetModifiers.rangedDamageMultiplier(player, pastInputCap)
        assertEquals(215.11 / 100.0, atCapMultiplier, 1e-6)
        assertEquals(atCapMultiplier, pastCapMultiplier, 1e-9)
        assertTrue(pastCapMultiplier < 250.0 / 100.0)
    }

    // ---- "whichever is higher" (Magic level vs. Magic attack bonus) ----

    @Test
    fun `uses the target's Magic attack bonus when it is higher than their Magic level`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 10, magicAccuracyBonus = 99)

        // Bonus (99) beats level (10), so this must match the level-99 vector above (93.4491%).
        assertEquals(93.4491 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, target), 1e-6)
    }

    @Test
    fun `uses the target's Magic level when it is higher than their Magic attack bonus`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 99, magicAccuracyBonus = 10)

        assertEquals(93.4491 / 100.0, TargetModifiers.rangedAccuracyMultiplier(player, target), 1e-6)
    }

    // ---- 8: accuracy stage integration (end-to-end through RangedCombatFormula.getAccuracy) ----

    @Test
    fun `getAccuracy applies the passive at the attack-roll stage, before the accuracy formula divides`() {
        val attacker = newPlayer(weapon = Items.TWISTED_BOW, rangedLevel = 99, attackRangedBonus = 70)
        val target = newTargetPlayer(magicLevel = 99, defenceLevel = 1)

        // attackRoll (pre-passive) = (floor(99*1.0)+0+8) * (70+64) = 107 * 134 = 14338
        // Magic-99 accuracy multiplier = 93.4491% -> 0.934491 (see the high-Magic vector above)
        // production truncates the attack roll to Int AFTER the multiply (RangedCombatFormula's
        // own `maxRoll.toInt()`), so the expected value replicates that same truncation here.
        val attackRoll = (14338.0 * 0.934491).toInt()
        val defenceRoll = 576 // (floor(1*1.0)+0+8) * (0+64) = 9*64
        val expected = 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0))

        assertEquals(expected, RangedCombatFormula.getAccuracy(attacker, target, 1.0), 1e-9)

        // Regression guard: the un-passived accuracy roll (pure 14338) must give a DIFFERENT
        // result, proving the passive is really composing into this stage and not a no-op.
        val unpassivedAttackRoll = 14338
        val unpassived = 1.0 - (defenceRoll + 2.0) / (2.0 * (unpassivedAttackRoll + 1.0))
        assertNotEquals(unpassived, RangedCombatFormula.getAccuracy(attacker, target, 1.0))
    }

    // ---- 9 / 10: damage stage integration + base-stats preservation ----

    @Test
    fun `getMaxHit applies the passive at the damage stage while still composing the ranged strength bonus`() {
        val target = newTargetPlayer(magicLevel = 99)
        val noStrengthBonus = newPlayer(weapon = Items.TWISTED_BOW, rangedLevel = 99, rangedStrengthBonus = 0)
        val withStrengthBonus = newPlayer(weapon = Items.TWISTED_BOW, rangedLevel = 99, rangedStrengthBonus = 20)

        // OSRS "Maximum ranged hit": ⌊⌊0.5 + eff × (bonus + 64) / 640⌋ × Gear Bonus⌋ - the base is floored first.
        // base(bonus=0)  = ⌊0.5 + 107*(0+64)/640⌋  = ⌊11.2⌋ = 11
        // base(bonus=20) = ⌊0.5 + 107*(20+64)/640⌋ = ⌊14.54375⌋ = 14
        // Magic-99 damage multiplier = 131.1691% -> 1.311691 (see the high-Magic vector above)
        val expectedNoBonus = floor(11.0 * 1.311691) // 14
        val expectedWithBonus = floor(14.0 * 1.311691) // 18 (flooring only at the end would give 19)

        val hitNoBonus = RangedCombatFormula.getMaxHit(noStrengthBonus, target, 1.0, 1.0)
        val hitWithBonus = RangedCombatFormula.getMaxHit(withStrengthBonus, target, 1.0, 1.0)

        assertEquals(expectedNoBonus, hitNoBonus, 1e-9)
        assertEquals(expectedWithBonus, hitWithBonus, 1e-9)
        // 10: proves the +20 ranged strength equipment bonus still moves the result even with
        // the Twisted bow passive active - the passive augments the composition, it does not
        // replace the base equipment-bonus term.
        assertTrue(hitWithBonus > hitNoBonus)
    }

    // ---- 11: no double application ----

    @Test
    fun `the passive multiplier is applied exactly once, not squared`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 99)

        val actual = TargetModifiers.rangedDamageMultiplier(player, target)
        val singleApplication = 131.1691 / 100.0
        val doubleApplication = singleApplication * singleApplication

        assertEquals(singleApplication, actual, 1e-6)
        assertNotEquals(doubleApplication, actual, 1e-3)
    }

    // ---- 12: target change ----

    @Test
    fun `changing the target's Magic level deterministically changes the output`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val low = TargetModifiers.rangedDamageMultiplier(player, newTargetPlayer(magicLevel = 1))
        val medium = TargetModifiers.rangedDamageMultiplier(player, newTargetPlayer(magicLevel = 70))
        val high = TargetModifiers.rangedDamageMultiplier(player, newTargetPlayer(magicLevel = 99))
        // Magic level is capped at 99 (SkillSet.MAX_LVL), so the passive's higher, 250-capped
        // input range is reached via the target's Magic attack bonus instead - see the
        // "whichever is higher" vectors above.
        val capped = TargetModifiers.rangedDamageMultiplier(player, newTargetPlayer(magicLevel = 1, magicAccuracyBonus = 250))

        assertTrue(low < medium)
        assertTrue(medium < high)
        assertTrue(high < capped)
    }

    // ---- 13: cross-style control ----

    @Test
    fun `equipmentMultiplier - the function Melee and Magic call - is never affected by the Twisted bow`() {
        // MeleeCombatFormula and MagicCombatFormula both call TargetModifiers.equipmentMultiplier
        // directly (never rangedAccuracyMultiplier/rangedDamageMultiplier), so wielding a
        // Twisted bow - itself only meaningful for a ranged weapon slot - must never change
        // what that shared, cross-style function returns.
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val target = newTargetPlayer(magicLevel = 99)

        assertEquals(1.0, TargetModifiers.equipmentMultiplier(player, target), 1e-9)
    }

    // ---- Npc target coverage (PvM; the source states no Player/Npc distinction in the formula) ----

    @Test
    fun `applies identically against an Npc target as a Player target with the same Magic level`() {
        val player = newPlayer(weapon = Items.TWISTED_BOW)
        val playerTarget = newTargetPlayer(magicLevel = 70)
        val npcTarget = newTargetNpc(magicLevel = 70)

        assertEquals(
            TargetModifiers.rangedDamageMultiplier(player, playerTarget),
            TargetModifiers.rangedDamageMultiplier(player, npcTarget),
            1e-9,
        )
    }

    // ---- helpers ----

    private fun newPlayer(
        weapon: Int?,
        rangedLevel: Int = 1,
        rangedStrengthBonus: Int = 0,
        attackRangedBonus: Int = 0,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id

        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.RANGED, rangedLevel)
        skills.setBaseLevel(Skills.DEFENCE, 1)
        every { player.skills } returns skills

        val bonuses = IntArray(18)
        bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] = rangedStrengthBonus
        bonuses[BonusSlot.ATTACK_RANGED.id] = attackRangedBonus
        every { player.equipmentBonuses } returns bonuses

        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        weapon?.let { equipment[EquipmentType.WEAPON.id] = Item(it) }
        every { player.equipment } returns equipment

        every { player.attr } returns AttributeMap()
        return player
    }

    private fun newTargetPlayer(
        magicLevel: Int,
        magicAccuracyBonus: Int = 0,
        defenceLevel: Int = 1,
    ): Player {
        val target = mockk<Player>(relaxed = true)
        every { target.prayerIcon } returns PrayerIcon.NONE.id

        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.MAGIC, magicLevel)
        skills.setBaseLevel(Skills.DEFENCE, defenceLevel)
        every { target.skills } returns skills

        val bonuses = IntArray(18)
        bonuses[BonusSlot.ATTACK_MAGIC.id] = magicAccuracyBonus
        every { target.equipmentBonuses } returns bonuses

        every { target.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { target.attr } returns AttributeMap()
        return target
    }

    private fun newTargetNpc(
        magicLevel: Int,
        magicAccuracyBonus: Int = 0,
    ): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.prayerIcon } returns PrayerIcon.NONE.id
        every { npc.species } returns emptySet()
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(species = emptySet(), slayerAssignment = null)
        every { npc.attr } returns AttributeMap()

        val stats =
            Npc.Stats(5).apply {
                setMaxLevel(NpcSkills.MAGIC, magicLevel)
                setCurrentLevel(NpcSkills.MAGIC, magicLevel)
            }
        every { npc.stats } returns stats

        val bonuses = IntArray(18)
        bonuses[BonusSlot.ATTACK_MAGIC.id] = magicAccuracyBonus
        every { npc.equipmentBonuses } returns bonuses
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
