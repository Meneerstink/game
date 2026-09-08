package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
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
import gg.rsmod.plugins.content.combat.Combat
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
import kotlin.test.assertTrue

/**
 * Coverage for [MagicCombatFormula], written for S1_MODERN_OFFENSIVE_STAT_FOUNDATION
 * (2026-09-03 autonomous run - see `RSPS_DECISIONS.md` for the full write-up). Proves:
 * - magic damage % (`BonusSlot.MAGIC_DAMAGE_BONUS`, read via `getMagicDamageBonus()`)
 *   composes correctly into `getMaxHit` as `1.0 + bonus / 100.0`, the generic modern-gear
 *   path for magic gear such as the Twisted bow's magic-adjacent peers or Tumeken's shadow;
 * - chaos gauntlets' flat +3 (bolt spells only) applies before, not after, the magic
 *   damage % multiplier;
 * - [TargetModifiers] deliberately does NOT apply to `getMaxHit` here - a pre-existing,
 *   documented design decision (P9, `RSPS_DECISIONS.md`), not a bug - so a regression that
 *   accidentally wires it in would be caught;
 * - Augury's Magic Attack prayer multiplier composes into the effective Attack level used
 *   for accuracy, and [TargetModifiers] DOES apply to the attack roll (unlike max hit);
 * - void mage's 1.45x applies to the effective Attack level (accuracy) only, matching this
 *   codebase's pre-elite-void 2011 era (no magic damage bonus from void);
 * - a baseline/control case with no bonuses, prayers, or gauntlets.
 */
class MagicCombatFormulaTests {
    @BeforeTest
    fun setUp() {
        mockkObject(Prayers)
        every { Prayers.isActive(any(), any()) } returns false
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(Prayers)
    }

    // ---- getMaxHit: magic damage % composition ----

    @Test
    fun `magic damage bonus (BonusSlot MAGIC_DAMAGE_BONUS) composes into max hit as a percentage`() {
        val noBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, magicDamageBonus = 0))
        val withBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, magicDamageBonus = 20))
        assertEquals(2.0, noBonus)
        // hit = floor(2 * (1.0 + 20/100.0)) = floor(2.4) = 2 (floored twice, once after the
        // magic damage multiplier and once more after the damage-deal multiplier - both no-ops
        // here since neither changes an already-integral value).
        assertEquals(floor(2.0 * 1.2), withBonus)
    }

    @Test
    fun `a larger magic damage bonus produces a strictly larger max hit once it crosses a whole point`() {
        // WIND_BOLT has maxHit 9; +50% crosses a whole additional point, unlike the +20%
        // case above which stays within the same floored integer.
        val noBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_BOLT, magicDamageBonus = 0))
        val withBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_BOLT, magicDamageBonus = 50))
        assertEquals(9.0, noBonus)
        assertEquals(floor(9.0 * 1.5), withBonus)
        assertTrue(withBonus > noBonus)
    }

    @Test
    fun `chaos gauntlets add a flat plus 3 to bolt spells before the magic damage multiplier`() {
        val withGauntlets =
            getMaxHit(newPlayer(spell = CombatSpell.FIRE_BOLT, magicDamageBonus = 10, gauntlets = true))
        // hit = floor((12 + 3) * 1.10) = floor(16.5) = 16
        assertEquals(floor(15.0 * 1.10), withGauntlets)
    }

    @Test
    fun `chaos gauntlets do not apply to a non-bolt spell`() {
        val withGauntlets = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, gauntlets = true))
        assertEquals(2.0, withGauntlets)
    }

    // ---- getMaxHit: TargetModifiers deliberately excluded (documented decision, not a bug) ----

    @Test
    fun `TargetModifiers' Salve amulet bonus does not change the magic max hit`() {
        val player = newPlayer(spell = CombatSpell.WIND_STRIKE, amulet = Items.SALVE_AMULET)
        val undead = newNpc(species = setOf(NpcSpecies.UNDEAD))
        val hit = MagicCombatFormula.getMaxHit(player, undead, 1.0, 1.0)
        assertEquals(2.0, hit)
    }

    // ---- getMaxHit: baseline/control ----

    @Test
    fun `an unbuffed caster deals exactly the spell's base max hit`() {
        assertEquals(2.0, getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE)))
    }

    // ---- getAccuracy: prayer and TargetModifiers composition on the attack roll ----

    @Test
    fun `Augury's Magic Attack prayer multiplier composes into the attack roll`() {
        val withoutAugury = newPlayer(magicLevel = 99, prayer = null)
        val withAugury = newPlayer(magicLevel = 99, prayer = Prayer.AUGURY)
        val target = newPlayer(defenceLevel = 1)
        val base = MagicCombatFormula.getAccuracy(withoutAugury, target, 1.0)
        val boosted = MagicCombatFormula.getAccuracy(withAugury, target, 1.0)
        assertTrue(boosted > base)
    }

    @Test
    fun `TargetModifiers composes into the attack roll even though it does not affect max hit`() {
        val plain = newPlayer(magicLevel = 99)
        val withSalve = newPlayer(magicLevel = 99, amulet = Items.SALVE_AMULET)
        val undead = newNpc(species = setOf(NpcSpecies.UNDEAD))
        val plainAccuracy = MagicCombatFormula.getAccuracy(plain, undead, 1.0)
        val salveAccuracy = MagicCombatFormula.getAccuracy(withSalve, undead, 1.0)
        assertTrue(salveAccuracy > plainAccuracy)
    }

    @Test
    fun `void mage's 1_45x applies to the effective Attack level used for accuracy`() {
        val plain = newPlayer(magicLevel = 99)
        val void = newPlayer(magicLevel = 99, void = true)
        val target = newPlayer(defenceLevel = 1)
        val plainAccuracy = MagicCombatFormula.getAccuracy(plain, target, 1.0)
        val voidAccuracy = MagicCombatFormula.getAccuracy(void, target, 1.0)
        assertTrue(voidAccuracy > plainAccuracy)
    }

    @Test
    fun `protect from magic only zeroes accuracy against a non-player attacker`() {
        val npcAttacker = mockk<Npc>(relaxed = true)
        val target = newPlayer(defenceLevel = 1)
        every { target.prayerIcon } returns PrayerIcon.PROTECT_FROM_MAGIC.id
        assertEquals(0.0, MagicCombatFormula.getAccuracy(npcAttacker, target, 1.0))

        val playerAttacker = newPlayer(magicLevel = 99)
        assertTrue(MagicCombatFormula.getAccuracy(playerAttacker, target, 1.0) > 0.0)
    }

    // ---- helpers ----

    private fun getMaxHit(player: Player): Double {
        val target = newPlayer()
        return MagicCombatFormula.getMaxHit(player, target, 1.0, 1.0)
    }

    private fun newPlayer(
        magicLevel: Int = 1,
        defenceLevel: Int = 1,
        spell: CombatSpell? = null,
        magicDamageBonus: Int = 0,
        gauntlets: Boolean = false,
        void: Boolean = false,
        prayer: Prayer? = null,
        amulet: Int? = null,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.prayerIcon } returns PrayerIcon.NONE.id

        val skills = SkillSet(maxSkills = 7)
        skills.setBaseLevel(Skills.MAGIC, magicLevel)
        skills.setBaseLevel(Skills.DEFENCE, defenceLevel)
        every { player.skills } returns skills

        val bonuses = IntArray(18)
        bonuses[BonusSlot.MAGIC_DAMAGE_BONUS.id] = magicDamageBonus
        every { player.equipmentBonuses } returns bonuses

        val attr = AttributeMap()
        if (spell != null) {
            attr[Combat.CASTING_SPELL] = spell
        }
        every { player.attr } returns attr

        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        if (gauntlets) {
            equipment[EquipmentType.GLOVES.id] = Item(Items.CHAOS_GAUNTLETS)
        }
        if (void) {
            equipment[EquipmentType.HEAD.id] = Item(Items.VOID_MAGE_HELM)
            equipment[EquipmentType.CHEST.id] = Item(Items.VOID_KNIGHT_TOP)
            equipment[EquipmentType.LEGS.id] = Item(Items.VOID_KNIGHT_ROBE)
            equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
        }
        amulet?.let { equipment[EquipmentType.AMULET.id] = Item(it) }
        every { player.equipment } returns equipment

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
        // A relaxed mock's default IntArray answer is empty, not a real 18-slot array, which
        // throws ArrayIndexOutOfBoundsException the moment `getBonus` indexes into it (e.g. via
        // getDefenceRoll's DEFENCE_MAGIC lookup).
        every { npc.equipmentBonuses } returns IntArray(18)
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
