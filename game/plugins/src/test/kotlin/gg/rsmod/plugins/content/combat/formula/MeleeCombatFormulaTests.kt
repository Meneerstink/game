package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
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
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [MeleeCombatFormula], written for S1_MODERN_OFFENSIVE_STAT_FOUNDATION
 * (2026-09-03 autonomous run - see `RSPS_DECISIONS.md` for the full write-up). Proves:
 * - the fixed effective-Strength style bonus (Accurate/Defensive +0, Controlled +1,
 *   Aggressive +3). Before the fix, `getEffectiveStrengthLevel`'s `else` branch returned
 *   1.0 instead of 0.0, silently giving Accurate (and any other unhandled style) the same
 *   +1 bonus as Controlled and inflating every melee max hit rolled under that style;
 * - melee strength equipment bonus (`BonusSlot.STRENGTH_BONUS`), the Strength prayer
 *   multiplier, and void melee all composing correctly into `getMaxHit`, proving the
 *   generic equipment-bonus architecture (`items.yml` -> `ItemDef.bonuses` ->
 *   `equipmentBonuses` -> `BonusSlot`) is wired correctly and reusable for future modern
 *   gear rather than a one-off;
 * - [TargetModifiers] composes multiplicatively on top of the base/equipment-derived hit
 *   instead of replacing it;
 * - a baseline/control case with no bonuses, prayers, styles, or target conditions.
 *
 * [CombatConfigs] (weapon-type/varp -> [WeaponStyle]/[StyleType] resolution) and [Prayers]
 * (varbit-backed prayer activation) are mocked directly so each test can drive a specific
 * attack style or active prayer without also faking a full equipped-weapon/varp/varbit
 * chain; that resolution is each object's own separate responsibility.
 */
class MeleeCombatFormulaTests {
    @BeforeTest
    fun setUp() {
        mockkObject(CombatConfigs, Prayers)
        every { Prayers.isActive(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CombatConfigs, Prayers)
    }

    // ---- getMaxHit: effective Strength style bonus (the S1 fix) ----

    @Test
    fun `Accurate style gives effective Strength no style bonus, same as Defensive`() {
        val accurate = getMaxHit(newPlayer(strengthLevel = 99, style = WeaponStyle.ACCURATE))
        val defensive = getMaxHit(newPlayer(strengthLevel = 99, style = WeaponStyle.DEFENSIVE))
        assertEquals(accurate, defensive, 1e-9)
        // effectiveLevel = floor(99 * 1.0) + 0 + 8 = 107
        assertEquals(0.5 + 107.0 * 64.0 / 640.0, accurate, 1e-9)
    }

    @Test
    fun `Controlled style gives effective Strength a plus 1 bonus`() {
        val hit = getMaxHit(newPlayer(strengthLevel = 99, style = WeaponStyle.CONTROLLED))
        // effectiveLevel = floor(99 * 1.0) + 1 + 8 = 108
        assertEquals(0.5 + 108.0 * 64.0 / 640.0, hit, 1e-9)
    }

    @Test
    fun `Aggressive style gives effective Strength the largest, plus 3, bonus`() {
        val aggressive = getMaxHit(newPlayer(strengthLevel = 99, style = WeaponStyle.AGGRESSIVE))
        // effectiveLevel = floor(99 * 1.0) + 3 + 8 = 110
        assertEquals(0.5 + 110.0 * 64.0 / 640.0, aggressive, 1e-9)
        assertTrue(aggressive > getMaxHit(newPlayer(strengthLevel = 99, style = WeaponStyle.CONTROLLED)))
        assertTrue(aggressive > getMaxHit(newPlayer(strengthLevel = 99, style = WeaponStyle.ACCURATE)))
    }

    // ---- getMaxHit: generic equipment-bonus / prayer / void composition ----

    @Test
    fun `melee strength equipment bonus (BonusSlot STRENGTH_BONUS) composes into max hit`() {
        val noBonus = getMaxHit(newPlayer(strengthLevel = 99, strengthBonus = 0))
        val withBonus = getMaxHit(newPlayer(strengthLevel = 99, strengthBonus = 80))
        // effectiveLevel is unchanged at 107; only the (bonus + 64) / 640 term differs -
        // this is the generic modern-gear path (item's melee_strength -> BonusSlot(14)).
        assertEquals(0.5 + 107.0 * 144.0 / 640.0, withBonus, 1e-9)
        assertTrue(withBonus > noBonus)
    }

    @Test
    fun `Piety's Strength prayer multiplier composes into effective Strength`() {
        val hit = getMaxHit(newPlayer(strengthLevel = 99, prayer = Prayer.PIETY))
        // effectiveLevel = floor(99 * 1.23) + 0 + 8 = floor(121.77) + 8 = 129
        assertEquals(0.5 + 129.0 * 64.0 / 640.0, hit, 1e-9)
    }

    @Test
    fun `void melee multiplies effective Strength by 1_10`() {
        val hit = getMaxHit(newPlayer(strengthLevel = 99, void = true))
        // effectiveLevel = (floor(99 * 1.0) + 0 + 8) * 1.10 = 107 * 1.10 = 117.7
        // (unlike Attack, Strength is not re-floored after the void multiplier)
        assertEquals(0.5 + 117.7 * 64.0 / 640.0, hit, 1e-9)
    }

    // ---- getMaxHit: TargetModifiers composition ----

    @Test
    fun `TargetModifiers' Salve amulet bonus multiplies the base hit rather than replacing it`() {
        val player = newPlayer(strengthLevel = 99, strengthBonus = 20, amulet = Items.SALVE_AMULET)
        val undead = newNpc(species = setOf(NpcSpecies.UNDEAD))
        val hit = MeleeCombatFormula.getMaxHit(player, undead, 1.0, 1.0)
        // effectiveLevel = 107; base = 0.5 + 107 * (20 + 64) / 640; Salve amulet = 7/6
        val base = 0.5 + 107.0 * 84.0 / 640.0
        assertEquals(base * (7.0 / 6.0), hit, 1e-9)
    }

    // ---- getMaxHit: baseline/control ----

    @Test
    fun `a level 1, unequipped, un-prayed attacker deals the minimum baseline hit`() {
        val hit = getMaxHit(newPlayer(strengthLevel = 1))
        // effectiveLevel = floor(1 * 1.0) + 0 + 8 = 9
        assertEquals(0.5 + 9.0 * 64.0 / 640.0, hit, 1e-9)
    }

    // ---- getAccuracy: regression coverage for the sibling effective-level functions ----

    @Test
    fun `Accurate style's plus 3 effective Attack bonus composes into the attack roll`() {
        val attacker = newPlayer(attackLevel = 99, style = WeaponStyle.ACCURATE, combatStyle = StyleType.CRUSH)
        val target = newPlayer(defenceLevel = 1, style = WeaponStyle.ACCURATE, combatStyle = StyleType.CRUSH)
        // attackRoll = (floor(99*1.0) + 3 + 8) * (0 + 64) = 110 * 64 = 7040
        // defenceRoll = (floor(1*1.0) + 0 + 8) * (0 + 64) = 9 * 64 = 576
        val expected = 1.0 - (576.0 + 2.0) / (2.0 * (7040.0 + 1.0))
        assertEquals(expected, MeleeCombatFormula.getAccuracy(attacker, target, 1.0), 1e-9)
    }

    @Test
    fun `protect from melee only zeroes accuracy against a non-player attacker`() {
        val npcAttacker = mockk<Npc>(relaxed = true)
        val target = newPlayer(defenceLevel = 1)
        every { target.prayerIcon } returns PrayerIcon.PROTECT_FROM_MELEE.id
        assertEquals(0.0, MeleeCombatFormula.getAccuracy(npcAttacker, target, 1.0))

        val playerAttacker = newPlayer(attackLevel = 99)
        assertTrue(MeleeCombatFormula.getAccuracy(playerAttacker, target, 1.0) > 0.0)
    }

    // ---- helpers ----

    private fun getMaxHit(player: Player): Double {
        val target = newPlayer()
        return MeleeCombatFormula.getMaxHit(player, target, 1.0, 1.0)
    }

    private fun newPlayer(
        strengthLevel: Int = 1,
        attackLevel: Int = 1,
        defenceLevel: Int = 1,
        strengthBonus: Int = 0,
        style: WeaponStyle = WeaponStyle.ACCURATE,
        combatStyle: StyleType = StyleType.CRUSH,
        prayer: Prayer? = null,
        void: Boolean = false,
        amulet: Int? = null,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id

        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.STRENGTH, strengthLevel)
        skills.setBaseLevel(Skills.ATTACK, attackLevel)
        skills.setBaseLevel(Skills.DEFENCE, defenceLevel)
        every { player.skills } returns skills

        val bonuses = IntArray(18)
        bonuses[BonusSlot.STRENGTH_BONUS.id] = strengthBonus
        every { player.equipmentBonuses } returns bonuses

        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (void) {
            equipment[EquipmentType.HEAD.id] = Item(Items.VOID_MELEE_HELM)
            equipment[EquipmentType.CHEST.id] = Item(Items.VOID_KNIGHT_TOP)
            equipment[EquipmentType.LEGS.id] = Item(Items.VOID_KNIGHT_ROBE)
            equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
        }
        amulet?.let { equipment[EquipmentType.AMULET.id] = Item(it) }
        every { player.equipment } returns equipment

        every { player.attr } returns AttributeMap()

        every { CombatConfigs.getAttackStyle(player) } returns style
        every { CombatConfigs.getCombatStyle(player) } returns combatStyle

        if (prayer != null) {
            every { Prayers.isActive(player, prayer) } returns true
        }
        return player
    }

    private fun newNpc(species: Set<Any> = emptySet()): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.prayerIcon } returns PrayerIcon.NONE.id
        every { npc.species } returns species
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(species = species)
        every { npc.attr } returns AttributeMap()
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
