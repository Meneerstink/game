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
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import gg.rsmod.plugins.content.combat.rollDamage
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-exact combat audit (owner decision 2026-09-13: "alle effecten alles osrs"). Each assertion names its OSRS Wiki
 * source; the shared damage roll, void sets and effect conditions are checked across their whole rosters.
 */
class OsrsCombatExactnessTests {
    @BeforeTest
    fun setUp() {
        mockkObject(CombatConfigs, Prayers)
        every { Prayers.isActive(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(CombatConfigs, Prayers)
    }

    @Test
    fun `damage roll is a whole number from 0 to max inclusive and a successful 0 becomes 1`() {
        // "Damage per second/Melee|Ranged|Magic": average = Max hit/2 + 1/(Max hit+1), i.e. 0..max with 0 -> 1.
        val random = Random(1234)
        val seen = (0 until 20_000).map { rollDamage(DEFAULT_MIN_HIT, 10.0, random) }.toSet()
        assertEquals((1..10).toSet(), seen, "every value 1..10 appears, 0 never does, 11 never does")
        assertEquals(0, rollDamage(DEFAULT_MIN_HIT, 0.9, random), "a max hit below 1 deals 0")
        // A caller-set minimum is kept as the lowest whole damage; an exact hit stays exact.
        val withMinimum = (0 until 5_000).map { rollDamage(5.0, 10.0, random) }.toSet()
        assertEquals((5..10).toSet(), withMinimum)
        assertEquals(setOf(37), (0 until 200).map { rollDamage(36.9, 37.0, random) }.toSet())
    }

    @Test
    fun `npc melee and ranged defence rolls use Defence plus 9, magic uses Magic plus 9`() {
        val attacker = newPlayer(attack = 1, ranged = 1, magic = 1)
        val npc = newNpc(defence = 40, magic = 70, stab = 20, ranged = 30, magicDef = 10)
        every { CombatConfigs.getCombatStyle(attacker) } returns StyleType.STAB
        // attack rolls: melee (1+0+8)*(0+64)=576 (Accurate +3 -> 12*64=768); ranged 12*64=768; magic (1+9)*64=640 (owner
        // decision (b): the wiki DPS calculator adds 9 to the magic level)
        assertEquals(hitChance(768, (40 + 9) * (20 + 64)), MeleeCombatFormula.getAccuracy(attacker, npc, 1.0), 1e-12)
        assertEquals(hitChance(768, (40 + 9) * (30 + 64)), RangedCombatFormula.getAccuracy(attacker, npc, 1.0), 1e-12)
        assertEquals(hitChance(640, (70 + 9) * (10 + 64)), MagicCombatFormula.getAccuracy(attacker, npc, 1.0), 1e-12)
    }

    @Test
    fun `elite ranged void raises ranged strength to 12_5 percent, plain void 10 percent`() {
        // Void Knight equipment: ranged void +10 % damage, elite +2.5 % more; ⌊(eff) × modifier⌋.
        val plain = newPlayer(ranged = 99, voidHelm = Items.VOID_RANGER_HELM)
        val elite = newPlayer(ranged = 99, voidHelm = Items.VOID_RANGER_HELM, elite = true)
        val target = newNpc()
        // "Maximum ranged hit": ⌊(⌊Ranged × Prayer⌋ + Atk Style + 8) × Void⌋ - the Accurate style's +3 is part of the
        // strength level too, so the fixture's Accurate player has 99 + 3 + 8 = 110: plain ⌊110 × 1.1⌋ = 121,
        // elite ⌊110 × 1.125⌋ = 123. Without bonus both give 12 (⌊12.6⌋, ⌊12.8⌋).
        val plainBonus = newPlayer(ranged = 99, voidHelm = Items.VOID_RANGER_HELM, rangedStrength = 60)
        val eliteBonus = newPlayer(ranged = 99, voidHelm = Items.VOID_RANGER_HELM, elite = true, rangedStrength = 60)
        assertEquals(12.0, RangedCombatFormula.getMaxHit(plain, target, 1.0, 1.0))
        assertEquals(12.0, RangedCombatFormula.getMaxHit(elite, target, 1.0, 1.0))
        // +60: plain ⌊0.5 + 121*124/640⌋ = ⌊23.94⌋ = 23; elite ⌊0.5 + 123*124/640⌋ = ⌊24.33⌋ = 24
        assertEquals(23.0, RangedCombatFormula.getMaxHit(plainBonus, target, 1.0, 1.0))
        assertEquals(24.0, RangedCombatFormula.getMaxHit(eliteBonus, target, 1.0, 1.0))
        val plainHigh = newPlayer(ranged = 99, voidHelm = Items.VOID_RANGER_HELM, rangedStrength = 100)
        val eliteHigh = newPlayer(ranged = 99, voidHelm = Items.VOID_RANGER_HELM, elite = true, rangedStrength = 100)
        // +100: plain ⌊0.5 + 121*164/640⌋ = ⌊31.51⌋ = 31; elite ⌊0.5 + 123*164/640⌋ = ⌊32.02⌋ = 32
        assertEquals(31.0, RangedCombatFormula.getMaxHit(plainHigh, target, 1.0, 1.0))
        assertEquals(32.0, RangedCombatFormula.getMaxHit(eliteHigh, target, 1.0, 1.0))
    }

    @Test
    fun `the berserker necklace boosts only obsidian melee weapons`() {
        // Berserker necklace: +20 % with the wiki DPS calculator's isWearingTzhaarWeapon roster (Tzhaar-ket-om (t) absent).
        val target = newNpc()
        listOf(Items.TOKTZXILAK, Items.TZHAARKETOM, Items.TZHAARKETEM, Items.TOKTZXILEK, Items.TOKTZMEJTAL).forEach { weapon ->
            val player = newPlayer(strength = 99, strengthBonus = 100, weapon = weapon, amulet = Items.BERSERKER_NECKLACE)
            // base ⌊0.5 + 107*164/640⌋ = ⌊27.92⌋ = 27; ⌊27 × 1.2⌋ = 32
            assertEquals(32.0, MeleeCombatFormula.getMaxHit(player, target, 1.0, 1.0), "weapon $weapon")
        }
        val whip = newPlayer(strength = 99, strengthBonus = 100, weapon = Items.ABYSSAL_WHIP, amulet = Items.BERSERKER_NECKLACE)
        assertEquals(27.0, MeleeCombatFormula.getMaxHit(whip, target, 1.0, 1.0))
    }

    @Test
    fun `Silverlight and Darklight add 60 percent accuracy and max hit against demons only`() {
        // OSRS Wiki "Silverlight"/"Darklight" 60 %; wiki DPS calculator trackAddFactor: x + trunc(x × 60 / 100).
        val demon = newNpc(species = setOf(gg.rsmod.plugins.api.NpcSpecies.DEMON))
        val other = newNpc()
        listOf(Items.SILVERLIGHT, Items.DARKLIGHT).forEach { weapon ->
            val player = newPlayer(strength = 99, strengthBonus = 100, weapon = weapon)
            // base 27 -> 27 + ⌊16.2⌋ = 43; attack roll (1 + 3 + 8) × 64 = 768 -> 768 + 460 = 1228; defence (1 + 9) × 64
            assertEquals(43.0, MeleeCombatFormula.getMaxHit(player, demon, 1.0, 1.0), "weapon $weapon")
            assertEquals(27.0, MeleeCombatFormula.getMaxHit(player, other, 1.0, 1.0), "weapon $weapon vs non-demon")
            assertEquals(hitChance(1228, 640), MeleeCombatFormula.getAccuracy(player, demon, 1.0), 1e-12)
            assertEquals(hitChance(768, 640), MeleeCombatFormula.getAccuracy(player, other, 1.0), 1e-12)
        }
        val whip = newPlayer(strength = 99, strengthBonus = 100, weapon = Items.ABYSSAL_WHIP)
        assertEquals(27.0, MeleeCombatFormula.getMaxHit(whip, demon, 1.0, 1.0))
    }

    @Test
    fun `every void helm needs top robe and gloves, elite pieces count as the normal set`() {
        listOf(VoidKnight.MELEE_HELMS, VoidKnight.RANGER_HELMS, VoidKnight.MAGE_HELMS).forEach { helms ->
            helms.forEach { helm ->
                assertTrue(VoidKnight.wearing(newPlayer(voidHelm = helm), helms), "normal set with $helm")
                assertTrue(VoidKnight.wearing(newPlayer(voidHelm = helm, elite = true), helms), "elite pieces with $helm")
                assertTrue(VoidKnight.wearingElite(newPlayer(voidHelm = helm, elite = true), helms), "elite set with $helm")
                assertTrue(!VoidKnight.wearingElite(newPlayer(voidHelm = helm), helms), "plain set is not elite with $helm")
                assertTrue(!VoidKnight.wearing(newPlayer(voidHelm = helm, gloves = false), helms), "no gloves, no set with $helm")
            }
        }
    }

    private fun hitChance(
        attack: Int,
        defence: Int,
    ): Double = if (attack > defence) 1.0 - (defence + 2.0) / (2.0 * (attack + 1.0)) else attack / (2.0 * (defence + 1))

    @Suppress("LongParameterList")
    private fun newPlayer(
        attack: Int = 1,
        strength: Int = 1,
        ranged: Int = 1,
        magic: Int = 1,
        strengthBonus: Int = 0,
        rangedStrength: Int = 0,
        voidHelm: Int? = null,
        elite: Boolean = false,
        gloves: Boolean = true,
        weapon: Int? = null,
        amulet: Int? = null,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id
        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.ATTACK, attack)
        skills.setBaseLevel(Skills.STRENGTH, strength)
        skills.setBaseLevel(Skills.RANGED, ranged)
        skills.setBaseLevel(Skills.MAGIC, magic)
        every { player.skills } returns skills
        val bonuses = IntArray(18)
        bonuses[BonusSlot.STRENGTH_BONUS.id] = strengthBonus
        bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] = rangedStrength
        every { player.equipmentBonuses } returns bonuses
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (voidHelm != null) {
            equipment[EquipmentType.HEAD.id] = Item(voidHelm)
            equipment[EquipmentType.CHEST.id] = Item(if (elite) Items.ELITE_VOID_KNIGHT_TOP else Items.VOID_KNIGHT_TOP)
            equipment[EquipmentType.LEGS.id] = Item(if (elite) Items.ELITE_VOID_KNIGHT_ROBE else Items.VOID_KNIGHT_ROBE)
            if (gloves) equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
        }
        weapon?.let { equipment[EquipmentType.WEAPON.id] = Item(it) }
        amulet?.let { equipment[EquipmentType.AMULET.id] = Item(it) }
        every { player.equipment } returns equipment
        every { player.attr } returns AttributeMap()
        every { CombatConfigs.getAttackStyle(player) } returns WeaponStyle.ACCURATE
        every { CombatConfigs.getCombatStyle(player) } returns StyleType.CRUSH
        return player
    }

    private fun newNpc(
        defence: Int = 1,
        magic: Int = 1,
        stab: Int = 0,
        ranged: Int = 0,
        magicDef: Int = 0,
        species: Set<Any> = emptySet(),
    ): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.prayerIcon } returns PrayerIcon.NONE.id
        every { npc.species } returns species
        every { npc.combatDef } returns NpcCombatDef.DEFAULT
        every { npc.attr } returns AttributeMap()
        val stats =
            Npc.Stats(5).apply {
                setMaxLevel(gg.rsmod.plugins.api.NpcSkills.DEFENCE, defence)
                setCurrentLevel(gg.rsmod.plugins.api.NpcSkills.DEFENCE, defence)
                setMaxLevel(gg.rsmod.plugins.api.NpcSkills.MAGIC, magic)
                setCurrentLevel(gg.rsmod.plugins.api.NpcSkills.MAGIC, magic)
            }
        every { npc.stats } returns stats
        val bonuses = IntArray(18)
        bonuses[BonusSlot.DEFENCE_STAB.id] = stab
        bonuses[BonusSlot.DEFENCE_RANGED.id] = ranged
        bonuses[BonusSlot.DEFENCE_MAGIC.id] = magicDef
        every { npc.equipmentBonuses } returns bonuses
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
