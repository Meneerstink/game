package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.SLAYER_ASSIGNMENT
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
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
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
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

/**
 * S2_GOLDEN_COMBAT_TEST_VECTORS (2026-09-03 autonomous run - see `RSPS_DECISIONS.md` for the
 * full write-up). Unlike [MeleeCombatFormulaTests]/[RangedCombatFormulaTests]/
 * [MagicCombatFormulaTests] (S1, one composition property isolated per test), these vectors
 * exercise several realistic gear/prayer/style/target-modifier terms of a single loadout
 * TOGETHER and pin the exact deterministic result, so a future modern-gear/prayer/target-
 * modifier change cannot silently regress the composed pipeline even if every individual S1
 * unit test still passes in isolation. Every expected value below is hand-derived from the
 * production formula's own documented arithmetic (CODE_VERIFIED), not computed by calling the
 * function under test and comparing it to itself.
 *
 * While building the Ranged/Magic accuracy vectors, a real, generic, pre-existing bug was
 * found and fixed (see `RangedCombatFormula.getDefenceRoll` / `MagicCombatFormula`'s private
 * `getDefenceRoll(pawn, target: Npc)`): both derived the defence roll's level component from
 * the ATTACKER instead of the TARGET, unlike Melee's already-correct equivalent. An existing
 * S1 Ranged accuracy test happened not to catch this because it left both pawns at the
 * default level-1 defence, making the wrong (attacker) and right (target) values coincidentally
 * equal - exactly the "test that passes without asserting the intended formula" pitfall this
 * task's adversarial review calls out. Vectors 7/8 below use deliberately asymmetric
 * attacker/target defence levels specifically to make that class of bug impossible to hide.
 */
class GoldenCombatVectorsTests {
    @BeforeTest
    fun setUp() {
        mockkObject(CombatConfigs, Prayers)
        every { Prayers.isActive(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CombatConfigs, Prayers)
    }

    // ==== MELEE ====

    @Test
    fun `melee golden 1 - level 1 unequipped baseline, accuracy and max hit together`() {
        val attacker = newPlayer(attackLevel = 1, strengthLevel = 1, style = WeaponStyle.ACCURATE)
        val target = newPlayer(defenceLevel = 1, style = WeaponStyle.ACCURATE)

        // maxHit: effStr = floor(1*1.0) + 0(Accurate) + 8 = 9; ⌊0.5 + 9*(0+64)/640⌋ = ⌊1.4⌋ = 1
        assertEquals(1.0, MeleeCombatFormula.getMaxHit(attacker, target, 1.0, 1.0), 1e-9)

        // accuracy: attackRoll = (floor(1*1.0)+3+8)*(0+64) = 12*64 = 768
        //           defenceRoll = (floor(1*1.0)+0+8)*(0+64) = 9*64 = 576
        val expected = 1.0 - (576.0 + 2.0) / (2.0 * (768.0 + 1.0))
        assertEquals(expected, MeleeCombatFormula.getAccuracy(attacker, target, 1.0), 1e-9)
    }

    @Test
    fun `melee golden 2 - full Accurate loadout composes strength bonus, attack bonus and target defence together`() {
        val attacker =
            newPlayer(
                attackLevel = 90,
                strengthLevel = 85,
                style = WeaponStyle.ACCURATE,
                combatStyle = StyleType.SLASH,
                meleeStrengthBonus = 89,
                meleeAttackBonus = 67,
            )
        val target =
            newPlayer(defenceLevel = 70, style = WeaponStyle.DEFENSIVE, combatStyle = StyleType.SLASH, meleeDefenceBonus = 50)

        // effStr = floor(85*1.0) + 0(Accurate) + 8 = 93; ⌊0.5 + 93*(89+64)/640⌋ = ⌊22.73⌋ = 22
        assertEquals(22.0, MeleeCombatFormula.getMaxHit(attacker, target, 1.0, 1.0), 1e-9)

        // attackRoll = (floor(90*1.0)+3+8)*(67+64) = 101*131 = 13231
        // defenceRoll = (floor(70*1.0)+3(Defensive)+8)*(50+64) = 81*114 = 9234
        val expected = 1.0 - (9234.0 + 2.0) / (2.0 * (13231.0 + 1.0))
        assertEquals(expected, MeleeCombatFormula.getAccuracy(attacker, target, 1.0), 1e-9)
    }

    @Test
    fun `melee golden 3 - Piety, Aggressive style, gear and the Salve amulet compose over an undead npc target`() {
        val attacker =
            newPlayer(
                attackLevel = 99,
                strengthLevel = 99,
                style = WeaponStyle.AGGRESSIVE,
                combatStyle = StyleType.CRUSH,
                meleeStrengthBonus = 100,
                meleeAttackBonus = 80,
                prayer = Prayer.PIETY,
                amulet = Items.SALVE_AMULET,
            )
        val target =
            newNpc(defenceLevel = 50, species = setOf(NpcSpecies.UNDEAD), defenceBonusSlot = BonusSlot.DEFENCE_CRUSH, defenceBonusValue = 40)

        // effStr = floor(99*1.23[Piety]) + 3(Aggressive) + 8 = 121+3+8 = 132
        // base = ⌊0.5 + 132*(100+64)/640⌋ = ⌊34.325⌋ = 34; Salve amulet (undead target) ⌊34 × 7/6⌋ = ⌊39.67⌋ = 39
        assertEquals(39.0, MeleeCombatFormula.getMaxHit(attacker, target, 1.0, 1.0), 1e-9)

        // attackRoll = (floor(99*1.20[Piety])+0(Aggressive)+8)*(80+64) = 126*144 = 18144,
        // then the Salve amulet's 7/6 (undead target) also multiplies the attack roll
        // itself (not just max hit): ⌊18144*7/6⌋ = 21168
        // OSRS npc defence roll = (Defence + 9) × (bonus + 64) = (50+9)*(40+64) = 59*104 = 6136
        val expected = 1.0 - (6136.0 + 2.0) / (2.0 * (21168.0 + 1.0))
        assertEquals(expected, MeleeCombatFormula.getAccuracy(attacker, target, 1.0), 1e-9)
    }

    @Test
    fun `melee golden 4 - the S1 style-bonus fix holds inside a full Strength-prayer plus gear composition`() {
        // Levels chosen so the +1 of Controlled crosses a whole point after OSRS's floor (Piety removed: with the
        // floored max hit, 99 Str + Piety + 100 bonus gives 33 for both styles and would no longer guard the fix).
        val accurate = newPlayer(strengthLevel = 96, style = WeaponStyle.ACCURATE, meleeStrengthBonus = 0)
        val controlled = newPlayer(strengthLevel = 96, style = WeaponStyle.CONTROLLED, meleeStrengthBonus = 0)

        // effStr(Accurate)   = 96 + 0 + 8 = 104; ⌊0.5 + 104*64/640⌋ = ⌊10.9⌋ = 10
        // effStr(Controlled) = 96 + 1 + 8 = 105; ⌊0.5 + 105*64/640⌋ = ⌊11.0⌋ = 11
        // If the S1 fix regressed (else -> 1.0 again), Accurate would silently equal Controlled.
        val accurateHit = getMaxHit(accurate)
        val controlledHit = getMaxHit(controlled)
        assertEquals(10.0, accurateHit, 1e-9)
        assertEquals(11.0, controlledHit, 1e-9)
        assertNotEquals(accurateHit, controlledHit)
    }

    @Test
    fun `melee golden 5 - the base hit is floored before the Salve multiplier (OSRS Maximum melee hit)`() {
        // OSRS: Max hit = ⌊⌊Base Damage⌋ × Gear Bonus⌋. Inputs chosen so flooring first changes the answer.
        val attacker =
            newPlayer(strengthLevel = 88, style = WeaponStyle.ACCURATE, meleeStrengthBonus = 32, prayer = Prayer.PIETY, amulet = Items.SALVE_AMULET)
        val target = newNpc(species = setOf(NpcSpecies.UNDEAD))

        // effStr = floor(88*1.23) + 0 + 8 = 108 + 8 = 116; base = 0.5 + 116*96/640 = 17.9 -> ⌊17.9⌋ = 17
        val hit = MeleeCombatFormula.getMaxHit(attacker, target, 1.0, 1.0)
        // ⌊17 × 7/6⌋ = ⌊19.83⌋ = 19; the old unfloored pipeline gave 17.9 × 7/6 = 20.88.
        assertEquals(19.0, hit, 1e-9)
        assertNotEquals(floor(17.9 * 7.0 / 6.0), hit)
    }

    @Test
    fun `melee golden 6 (cross-style control) - ranged and magic bonuses never leak into melee's max hit`() {
        val contaminated =
            newPlayer(
                strengthLevel = 70,
                style = WeaponStyle.ACCURATE,
                meleeStrengthBonus = 0,
                rangedStrengthBonus = 90,
                magicDamageBonus = 90,
            )
        // effStr = floor(70*1.0)+0+8 = 78; ⌊0.5 + 78*(0+64)/640⌋ = 8 - identical to a totally
        // clean player, proving Ranged/Magic bonus slots are never read by Melee's max hit.
        assertEquals(8.0, getMaxHit(contaminated), 1e-9)
    }

    // ==== RANGED ====

    @Test
    fun `ranged golden 1 - the base hit is floored before a later multiplier applies (rounding boundary)`() {
        val attacker = newPlayer(rangedLevel = 98, style = WeaponStyle.RAPID, rangedStrengthBonus = 18, prayer = Prayer.RIGOUR)
        val target = newNpc()
        // Protect from Missiles' 0.6x is the later multiplier here rather than the Sling's 0.9x: the Sling triggers
        // `getRangedStrengthBonus()`'s weapon-definition branch, which needs a definition-backed world.
        every { target.prayerIcon } returns PrayerIcon.PROTECT_FROM_MISSILES.id

        // effLevel = floor(98*1.23[Rigour]) + 0(Rapid) + 8 = 120 + 8 = 128; base = 0.5 + 128*(18+64)/640 = 16.9
        // OSRS floors the base first: ⌊16.9⌋ = 16, then ⌊16 × 0.6⌋ = ⌊9.6⌋ = 9. Flooring only at the end would give
        // ⌊16.9 × 0.6⌋ = ⌊10.14⌋ = 10.
        val hit = RangedCombatFormula.getMaxHit(attacker, target, 1.0, 1.0)
        assertEquals(9.0, hit, 1e-9)
        assertNotEquals(10.0, hit)
    }

    @Test
    fun `ranged golden 2 (S2 fix regression) - accuracy uses the target Player's defence level, not the attacker's`() {
        val attacker = newPlayer(rangedLevel = 99, defenceLevel = 1, style = WeaponStyle.RAPID)
        val target = newPlayer(defenceLevel = 45, style = WeaponStyle.RAPID)

        // attackRoll = (floor(99*1.0)+0+8)*(0+64) = 107*64 = 6848
        // defenceRoll, FIXED (target's level 45): (floor(45*1.0)+0+8)*(0+64) = 53*64 = 3392
        // defenceRoll, pre-fix bug (attacker's level 1): (floor(1*1.0)+0+8)*64 = 9*64 = 576
        val fixed = 1.0 - (3392.0 + 2.0) / (2.0 * (6848.0 + 1.0))
        val preFixBug = 1.0 - (576.0 + 2.0) / (2.0 * (6848.0 + 1.0))
        val accuracy = RangedCombatFormula.getAccuracy(attacker, target, 1.0)
        assertEquals(fixed, accuracy, 1e-9)
        assertNotEquals(preFixBug, accuracy)
    }

    @Test
    fun `ranged golden 3 (S2 fix regression) - accuracy uses the target Npc's defence level, not the attacker's`() {
        val attacker = newPlayer(rangedLevel = 99, defenceLevel = 30, style = WeaponStyle.RAPID)
        val target = newNpc(defenceLevel = 10, defenceBonusSlot = BonusSlot.DEFENCE_RANGED, defenceBonusValue = 6)

        // attackRoll = (floor(99*1.0)+0+8)*(0+64) = 107*64 = 6848
        // defenceRoll, target npc's level 10, OSRS "(Defence + 9) × (bonus + 64)": (10+9)*(6+64) = 19*70 = 1330
        // defenceRoll, pre-fix bug (attacker's level 30): (floor(30*1.0)+0+8)*(6+64) = 38*70 = 2660
        val fixed = 1.0 - (1330.0 + 2.0) / (2.0 * (6848.0 + 1.0))
        val preFixBug = 1.0 - (2660.0 + 2.0) / (2.0 * (6848.0 + 1.0))
        val accuracy = RangedCombatFormula.getAccuracy(attacker, target, 1.0)
        assertEquals(fixed, accuracy, 1e-9)
        assertNotEquals(preFixBug, accuracy)
    }

    @Test
    fun `ranged golden 4 - the plain black mask never boosts ranged, on or off task (imbued mask only)`() {
        val onTask =
            newPlayer(
                rangedLevel = 99,
                style = WeaponStyle.RAPID,
                rangedStrengthBonus = 20,
                head = Items.BLACK_MASK,
                slayerAssignment = SlayerAssignment.BANSHEE,
            )
        val offTask =
            newPlayer(
                rangedLevel = 99,
                style = WeaponStyle.RAPID,
                rangedStrengthBonus = 20,
                head = Items.BLACK_MASK,
                slayerAssignment = SlayerAssignment.ZOMBIE,
            )
        val onTaskTarget = newNpc(assignment = SlayerAssignment.BANSHEE)
        val offTaskTarget = newNpc(assignment = SlayerAssignment.BANSHEE)

        // base = ⌊0.5 + 107*(20+64)/640⌋ = ⌊14.54375⌋ = 14 in both cases: OSRS "Maximum ranged hit" lists only
        // Black mask (i)/Slayer helmet (i) as ranged gear bonuses.
        assertEquals(14.0, RangedCombatFormula.getMaxHit(onTask, onTaskTarget, 1.0, 1.0), 1e-9)
        assertEquals(14.0, RangedCombatFormula.getMaxHit(offTask, offTaskTarget, 1.0, 1.0), 1e-9)
    }

    @Test
    fun `ranged golden 5 (cross-style control) - melee and magic bonuses never leak into ranged's max hit`() {
        val contaminated =
            newPlayer(
                rangedLevel = 70,
                style = WeaponStyle.RAPID,
                rangedStrengthBonus = 0,
                meleeStrengthBonus = 90,
                meleeAttackBonus = 90,
                magicAttackBonus = 90,
                magicDamageBonus = 90,
            )
        val target = newPlayer()
        // base = 0.5 + 78*(0+64)/640 = 8.3, then RangedCombatFormula.applyRangedSpecials
        // unconditionally floors after the (here-neutral) TargetModifiers multiplier - see
        // golden vector 4's rounding-boundary comment for the same unconditional-floor behavior.
        assertEquals(8.0, RangedCombatFormula.getMaxHit(contaminated, target, 1.0, 1.0), 1e-9)
    }

    // ==== MAGIC ====

    @Test
    fun `magic golden 1 - gauntlets, magic damage percent, Mystic Might and the S2 npc defence-roll fix compose together`() {
        val attacker =
            newPlayer(
                magicLevel = 94,
                defenceLevel = 40,
                style = WeaponStyle.DEFENSIVE,
                magicAttackBonus = 76,
                magicDamageBonus = 150,
                spell = CombatSpell.FIRE_BOLT,
                gauntlets = true,
                prayer = Prayer.MYSTIC_MIGHT,
            )
        val target = newNpc(defenceLevel = 25, magicLevel = 30, defenceBonusSlot = BonusSlot.DEFENCE_MAGIC, defenceBonusValue = 10)

        // "Maximum magic hit": ⌊(12[FIRE_BOLT] + 3[gauntlets]) × (1 + 0.15 gear + 0.02 Mystic Might)⌋ = ⌊17.55⌋ = 17
        assertEquals(17.0, MagicCombatFormula.getMaxHit(attacker, target, 1.0, 1.0), 1e-9)

        // effAtk = floor(94*1.15[Mystic Might]) + 9 = 108+9 = 117 (owner decision (b), wiki DPS calculator
        // getPlayerMaxMagicAttackRoll: +9, no stance bonus for a spell); attackRoll = 117*(76+64) = 16380
        // OSRS npc magic defence roll = (9 + Magic level) × (magic defence + 64) = (9+30)*74 = 2886 - the npc's
        // Defence level (25) plays no part.
        // defenceRoll, pre-fix bug would have used the attacker's own effective defence level
        // (floor(40*1.0)+3[Defensive]+8 = 51) instead: 51*(10+64) = 3774
        val fixed = 1.0 - (2886.0 + 2.0) / (2.0 * (16380.0 + 1.0))
        val preFixBug = 1.0 - (3774.0 + 2.0) / (2.0 * (16380.0 + 1.0))
        val accuracy = MagicCombatFormula.getAccuracy(attacker, target, 1.0)
        assertEquals(fixed, accuracy, 1e-9)
        assertNotEquals(preFixBug, accuracy)
    }

    @Test
    fun `magic golden 2 - void mage accuracy, Augury and the Salve amulet's zero effect on a Player target compose together`() {
        val attacker =
            newPlayer(magicLevel = 99, style = WeaponStyle.ACCURATE, void = VoidType.MAGE, prayer = Prayer.AUGURY, amulet = Items.SALVE_AMULET)
        // ROCK_SKIN only affects the target's own effective-Defence-level component of its
        // magic defence roll; no Mystic/Augury prayer is active on the target, so the roll's
        // separate magic-level component keeps its uncontested 1.0 multiplier (the target's
        // own magic-attack-prayer cross-application to that component is an open, deliberately
        // un-pinned SOURCE_CONFLICT - see RSPS_DECISIONS.md - so this vector avoids it entirely).
        val target = newPlayer(defenceLevel = 80, magicLevel = 60, style = WeaponStyle.ACCURATE, magicDefenceBonus = 20, prayer = Prayer.ROCK_SKIN)

        // effAtk = floor(99*1.25[Augury]) + 9 = 123+9 = 132; void mage after the +9: trunc(132*29/20) = 191 (owner decision
        // (b), wiki DPS calculator); Accurate gives no +2 here (that stance bonus belongs to powered staves)
        // TargetModifiers vs a Player target: isUndead requires an Npc, so Salve is neutral (1.0)
        // attackRoll = floor(191*(0+64)*1.0) = 12224
        val accuracy = MagicCombatFormula.getAccuracy(attacker, target, 1.0)

        // target defence roll (untouched Player-target path):
        //   effectiveLvl = floor(80*1.10[Rock Skin]) + 0(Accurate) + 8 = 88+8 = 96; *0.3 -> floor(28.8) = 28
        //   magicLvl = floor(60*1.0) = 60; *0.7 -> floor(42.0) = 42
        //   a = floor(28+42) = 70; defenceRoll = 70*(20+64) = 70*84 = 5880
        val expected = 1.0 - (5880.0 + 2.0) / (2.0 * (12224.0 + 1.0))
        assertEquals(expected, accuracy, 1e-9)
    }

    @Test
    fun `magic golden 3 (cross-style control) - melee and ranged bonuses never leak into magic's max hit`() {
        val contaminated =
            newPlayer(
                magicLevel = 70,
                spell = CombatSpell.WIND_STRIKE,
                magicDamageBonus = 0,
                meleeStrengthBonus = 90,
                rangedStrengthBonus = 90,
                rangedAttackBonus = 90,
            )
        val target = newPlayer()
        // hit = 8[WIND_STRIKE at Magic 70: OSRS 29 May 2024 strike tier scaling] * (1.0 + 0/100) = 8.0, unaffected by the melee/ranged bonuses.
        assertEquals(8.0, MagicCombatFormula.getMaxHit(contaminated, target, 1.0, 1.0), 1e-9)
    }

    // ==== helpers ====

    private fun getMaxHit(player: Player): Double {
        val target = newPlayer()
        return MeleeCombatFormula.getMaxHit(player, target, 1.0, 1.0)
    }

    private enum class VoidType { NONE, MELEE, RANGED, MAGE }

    private fun newPlayer(
        attackLevel: Int = 1,
        strengthLevel: Int = 1,
        rangedLevel: Int = 1,
        magicLevel: Int = 1,
        defenceLevel: Int = 1,
        style: WeaponStyle = WeaponStyle.ACCURATE,
        combatStyle: StyleType = StyleType.CRUSH,
        meleeStrengthBonus: Int = 0,
        meleeAttackBonus: Int = 0,
        meleeDefenceBonus: Int = 0,
        rangedStrengthBonus: Int = 0,
        rangedAttackBonus: Int = 0,
        magicAttackBonus: Int = 0,
        magicDamageBonus: Int = 0,
        magicDefenceBonus: Int = 0,
        prayer: Prayer? = null,
        void: VoidType = VoidType.NONE,
        amulet: Int? = null,
        head: Int? = null,
        gauntlets: Boolean = false,
        spell: CombatSpell? = null,
        slayerAssignment: SlayerAssignment? = null,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id

        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.ATTACK, attackLevel)
        skills.setBaseLevel(Skills.STRENGTH, strengthLevel)
        skills.setBaseLevel(Skills.RANGED, rangedLevel)
        skills.setBaseLevel(Skills.MAGIC, magicLevel)
        skills.setBaseLevel(Skills.DEFENCE, defenceLevel)
        every { player.skills } returns skills

        val bonuses = IntArray(18)
        val meleeAttackSlot =
            when (combatStyle) {
                StyleType.STAB -> BonusSlot.ATTACK_STAB
                StyleType.SLASH -> BonusSlot.ATTACK_SLASH
                else -> BonusSlot.ATTACK_CRUSH
            }
        val meleeDefenceSlot =
            when (combatStyle) {
                StyleType.STAB -> BonusSlot.DEFENCE_STAB
                StyleType.SLASH -> BonusSlot.DEFENCE_SLASH
                else -> BonusSlot.DEFENCE_CRUSH
            }
        bonuses[meleeAttackSlot.id] = meleeAttackBonus
        bonuses[meleeDefenceSlot.id] = meleeDefenceBonus
        bonuses[BonusSlot.STRENGTH_BONUS.id] = meleeStrengthBonus
        bonuses[BonusSlot.ATTACK_RANGED.id] = rangedAttackBonus
        bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] = rangedStrengthBonus
        bonuses[BonusSlot.ATTACK_MAGIC.id] = magicAttackBonus
        bonuses[BonusSlot.DEFENCE_MAGIC.id] = magicDefenceBonus
        bonuses[BonusSlot.MAGIC_DAMAGE_BONUS.id] = magicDamageBonus
        every { player.equipmentBonuses } returns bonuses

        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        when (void) {
            VoidType.MELEE -> {
                equipment[EquipmentType.HEAD.id] = Item(Items.VOID_MELEE_HELM)
                equipment[EquipmentType.CHEST.id] = Item(Items.VOID_KNIGHT_TOP)
                equipment[EquipmentType.LEGS.id] = Item(Items.VOID_KNIGHT_ROBE)
                equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
            }
            VoidType.RANGED -> {
                equipment[EquipmentType.HEAD.id] = Item(Items.VOID_RANGER_HELM)
                equipment[EquipmentType.CHEST.id] = Item(Items.VOID_KNIGHT_TOP)
                equipment[EquipmentType.LEGS.id] = Item(Items.VOID_KNIGHT_ROBE)
                equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
            }
            VoidType.MAGE -> {
                equipment[EquipmentType.HEAD.id] = Item(Items.VOID_MAGE_HELM)
                equipment[EquipmentType.CHEST.id] = Item(Items.VOID_KNIGHT_TOP)
                equipment[EquipmentType.LEGS.id] = Item(Items.VOID_KNIGHT_ROBE)
                equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
            }
            VoidType.NONE -> {}
        }
        if (gauntlets) {
            equipment[EquipmentType.GLOVES.id] = Item(Items.CHAOS_GAUNTLETS)
        }
        amulet?.let { equipment[EquipmentType.AMULET.id] = Item(it) }
        head?.let { equipment[EquipmentType.HEAD.id] = Item(it) }
        every { player.equipment } returns equipment

        val attr = AttributeMap()
        if (spell != null) {
            attr[gg.rsmod.plugins.content.combat.Combat.CASTING_SPELL] = spell
        }
        if (slayerAssignment != null) {
            attr[SLAYER_ASSIGNMENT] = slayerAssignment.identifier
        }
        every { player.attr } returns attr

        every { CombatConfigs.getAttackStyle(player) } returns style
        every { CombatConfigs.getCombatStyle(player) } returns combatStyle

        if (prayer != null) {
            every { Prayers.isActive(player, prayer) } returns true
        }
        return player
    }

    private fun newNpc(
        defenceLevel: Int = 1,
        magicLevel: Int = 1,
        species: Set<Any> = emptySet(),
        assignment: SlayerAssignment? = null,
        defenceBonusSlot: BonusSlot? = null,
        defenceBonusValue: Int = 0,
    ): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.prayerIcon } returns PrayerIcon.NONE.id
        every { npc.species } returns species
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(species = species, slayerAssignment = assignment)
        every { npc.attr } returns AttributeMap()

        val stats =
            Npc.Stats(5).apply {
                setMaxLevel(NpcSkills.DEFENCE, defenceLevel)
                setCurrentLevel(NpcSkills.DEFENCE, defenceLevel)
                setMaxLevel(NpcSkills.MAGIC, magicLevel)
                setCurrentLevel(NpcSkills.MAGIC, magicLevel)
            }
        every { npc.stats } returns stats

        val bonuses = IntArray(18)
        defenceBonusSlot?.let { bonuses[it.id] = defenceBonusValue }
        every { npc.equipmentBonuses } returns bonuses

        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
