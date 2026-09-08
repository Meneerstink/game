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
import kotlin.math.floor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [RangedCombatFormula], written for S1_MODERN_OFFENSIVE_STAT_FOUNDATION
 * (2026-09-03 autonomous run - see `RSPS_DECISIONS.md` for the full write-up). No bug was
 * found here (unlike [MeleeCombatFormula]'s effective-Strength style bonus) - this suite
 * exists to give the ranged side of the generic offensive-stat architecture the same
 * regression coverage, proving:
 * - ranged strength (`BonusSlot.RANGED_STRENGTH_BONUS`, read via `getRangedStrengthBonus()`)
 *   composes correctly into `getMaxHit`, including its extra `floor()` after the
 *   [TargetModifiers] multiplier (ranged-specific, absent from melee's equivalent path);
 * - Rigour's ranged-strength/accuracy prayer multipliers compose correctly;
 * - void ranged composes correctly (re-floored after its 1.10x, like melee's Attack but
 *   unlike melee's Strength);
 * - [TargetModifiers] composes multiplicatively rather than replacing the base hit;
 * - a baseline/control case with no bonuses, prayers, or target conditions.
 */
class RangedCombatFormulaTests {
    @BeforeTest
    fun setUp() {
        mockkObject(CombatConfigs, Prayers)
        every { Prayers.isActive(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CombatConfigs, Prayers)
    }

    // ---- getMaxHit: generic equipment-bonus / prayer / void composition ----

    @Test
    fun `ranged strength equipment bonus (BonusSlot RANGED_STRENGTH_BONUS) composes into max hit`() {
        val noBonus = getMaxHit(newPlayer(rangedLevel = 99, rangedStrengthBonus = 0))
        val withBonus = getMaxHit(newPlayer(rangedLevel = 99, rangedStrengthBonus = 80))
        assertTrue(withBonus > noBonus)
        // effectiveLevel = floor(99*1.0) + 0 + 8 = 107 (Accurate gives Ranged's max hit no style
        // bonus - only the attack roll's effective level gets Accurate's +3, per source).
        assertEquals(floor(0.5 + 107.0 * 144.0 / 640.0), withBonus, 1e-9)
    }

    @Test
    fun `Rigour's ranged strength prayer multiplier composes into effective Ranged`() {
        val hit = getMaxHit(newPlayer(rangedLevel = 99, prayer = Prayer.RIGOUR))
        // effectiveLevel = floor(99 * 1.23) + 0 + 8 = floor(121.77) + 8 = 129
        assertEquals(floor(0.5 + 129.0 * 64.0 / 640.0), hit, 1e-9)
    }

    @Test
    fun `void ranged multiplies effective Ranged by 1_10, then re-floors (unlike melee Strength)`() {
        val hit = getMaxHit(newPlayer(rangedLevel = 99, void = true))
        // effectiveLevel = floor((floor(99*1.0) + 0 + 8) * 1.10) = floor(107 * 1.10) = floor(117.7) = 117
        assertEquals(floor(0.5 + 117.0 * 64.0 / 640.0), hit, 1e-9)
    }

    // ---- getMaxHit: TargetModifiers composition ----

    @Test
    fun `TargetModifiers' black mask bonus multiplies the base hit rather than replacing it`() {
        // (Ranged shares the black mask's on-task multiplier with melee per `TargetModifiers`;
        // this proves the shared architecture composes identically for the ranged formula.)
        val player = newPlayer(rangedLevel = 99, rangedStrengthBonus = 20, head = Items.BLACK_MASK)
        val onTask = newNpc(assignment = gg.rsmod.game.model.combat.SlayerAssignment.BANSHEE)
        every { player.attr } returns
            AttributeMap().also {
                it[gg.rsmod.game.model.attr.SLAYER_ASSIGNMENT] =
                    gg.rsmod.game.model.combat.SlayerAssignment.BANSHEE.identifier
            }
        val hit = RangedCombatFormula.getMaxHit(player, onTask, 1.0, 1.0)
        // effectiveLevel = 107; base = floor(0.5 + 107 * (20 + 64) / 640) after the black mask's
        // 7/6 multiplier is applied and the result is floored (ranged floors post-TargetModifiers).
        val base = 0.5 + 107.0 * 84.0 / 640.0
        assertEquals(floor(base * (7.0 / 6.0)), hit, 1e-9)
    }

    // ---- getMaxHit: baseline/control ----

    @Test
    fun `a level 1, unequipped, un-prayed attacker deals the minimum baseline hit`() {
        val hit = getMaxHit(newPlayer(rangedLevel = 1))
        // effectiveLevel = floor(1 * 1.0) + 0 + 8 = 9
        assertEquals(floor(0.5 + 9.0 * 64.0 / 640.0), hit, 1e-9)
    }

    // ---- getAccuracy: regression coverage ----

    @Test
    fun `Accurate style's plus 3 effective Ranged Attack bonus composes into the attack roll`() {
        val attacker = newPlayer(rangedLevel = 99, style = WeaponStyle.ACCURATE)
        val target = newPlayer(defenceLevel = 1, style = WeaponStyle.ACCURATE)
        // attackRoll = (floor(99*1.0) + 3 + 8) * (0 + 64) = 110 * 64 = 7040
        // defenceRoll = (floor(1*1.0) + 0 + 8) * (0 + 64) = 9 * 64 = 576
        val expected = 1.0 - (576.0 + 2.0) / (2.0 * (7040.0 + 1.0))
        assertEquals(expected, RangedCombatFormula.getAccuracy(attacker, target, 1.0), 1e-9)
    }

    @Test
    fun `protect from missiles only zeroes accuracy against a non-player attacker`() {
        val npcAttacker = mockk<Npc>(relaxed = true)
        val target = newPlayer(defenceLevel = 1)
        every { target.prayerIcon } returns PrayerIcon.PROTECT_FROM_MISSILES.id
        assertEquals(0.0, RangedCombatFormula.getAccuracy(npcAttacker, target, 1.0))

        val playerAttacker = newPlayer(rangedLevel = 99)
        assertTrue(RangedCombatFormula.getAccuracy(playerAttacker, target, 1.0) > 0.0)
    }

    // ---- helpers ----

    private fun getMaxHit(player: Player): Double {
        val target = newPlayer()
        return RangedCombatFormula.getMaxHit(player, target, 1.0, 1.0)
    }

    private fun newPlayer(
        rangedLevel: Int = 1,
        defenceLevel: Int = 1,
        rangedStrengthBonus: Int = 0,
        // RAPID (not ACCURATE) is the default here: unlike melee, Ranged's Accurate style adds
        // +3 to the *same* effective-Ranged-level used for both the attack roll and the max hit
        // (there's no separate Ranged "strength" stat to split it from), so defaulting to
        // ACCURATE would fold a style bonus into every generic-composition test below. RAPID
        // keeps those tests isolated to the bonus/prayer/void term under test; Accurate's own
        // +3 is covered explicitly by the getAccuracy style-bonus test further down.
        style: WeaponStyle = WeaponStyle.RAPID,
        prayer: Prayer? = null,
        void: Boolean = false,
        head: Int? = null,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id

        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.RANGED, rangedLevel)
        skills.setBaseLevel(Skills.DEFENCE, defenceLevel)
        every { player.skills } returns skills

        val bonuses = IntArray(18)
        bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] = rangedStrengthBonus
        every { player.equipmentBonuses } returns bonuses

        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (void) {
            equipment[EquipmentType.HEAD.id] = Item(Items.VOID_RANGER_HELM)
            equipment[EquipmentType.CHEST.id] = Item(Items.VOID_KNIGHT_TOP)
            equipment[EquipmentType.LEGS.id] = Item(Items.VOID_KNIGHT_ROBE)
            equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
        }
        head?.let { equipment[EquipmentType.HEAD.id] = Item(it) }
        every { player.equipment } returns equipment

        every { player.attr } returns AttributeMap()

        every { CombatConfigs.getAttackStyle(player) } returns style

        if (prayer != null) {
            every { Prayers.isActive(player, prayer) } returns true
        }
        return player
    }

    private fun newNpc(
        species: Set<Any> = emptySet(),
        assignment: gg.rsmod.game.model.combat.SlayerAssignment? = null,
    ): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.prayerIcon } returns PrayerIcon.NONE.id
        every { npc.species } returns species
        every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(species = species, slayerAssignment = assignment)
        every { npc.attr } returns AttributeMap()
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
