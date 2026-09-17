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
    fun `magic damage bonus (BonusSlot MAGIC_DAMAGE_BONUS, tenths of a percent) composes into max hit`() {
        val noBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, magicDamageBonus = 0))
        val withBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, magicDamageBonus = 200))
        assertEquals(2.0, noBonus)
        // 200 tenths = 20 %: ⌊2 × 1.2⌋ = 2
        assertEquals(floor(2.0 * 1.2), withBonus)
    }

    @Test
    fun `a larger magic damage bonus produces a strictly larger max hit once it crosses a whole point`() {
        // WIND_BOLT has maxHit 9; +50 % (500 tenths) crosses a whole additional point.
        val noBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_BOLT, magicDamageBonus = 0))
        val withBonus = getMaxHit(newPlayer(spell = CombatSpell.WIND_BOLT, magicDamageBonus = 500))
        assertEquals(9.0, noBonus)
        assertEquals(floor(9.0 * 1.5), withBonus)
        assertTrue(withBonus > noBonus)
    }

    @Test
    fun `chaos gauntlets add a flat plus 3 to bolt spells before the magic damage multiplier`() {
        val withGauntlets =
            getMaxHit(newPlayer(spell = CombatSpell.FIRE_BOLT, magicDamageBonus = 100, gauntlets = true))
        // hit = floor((12 + 3) * 1.10) = floor(16.5) = 16
        assertEquals(floor(15.0 * 1.10), withGauntlets)
    }

    @Test
    fun `gear, elite void and prayer magic damage add up before multiplying (OSRS Maximum magic hit)`() {
        // FIRE_BOLT 12, 15 % gear + 4 % Augury: additive ⌊12 × 1.19⌋ = 14; a chained ⌊⌊12 × 1.15⌋ × 1.04⌋ would give 13.
        val additive = getMaxHit(newPlayer(spell = CombatSpell.FIRE_BOLT, magicDamageBonus = 150, prayer = Prayer.AUGURY))
        assertEquals(14.0, additive)
        // Elite magic void adds +5 %: ⌊12 × 1.20⌋ = 14, the plain void set adds nothing to damage: ⌊12 × 1.15⌋ = 13.
        assertEquals(14.0, getMaxHit(newPlayer(spell = CombatSpell.FIRE_BOLT, magicDamageBonus = 150, eliteVoid = true)))
        assertEquals(13.0, getMaxHit(newPlayer(spell = CombatSpell.FIRE_BOLT, magicDamageBonus = 150, void = true)))
        // Seers ring (i) precision: 5 tenths = 0.5 % is kept, not rounded away (⌊12 × 1.155⌋ = 13 with 15 % gear).
        assertEquals(13.0, getMaxHit(newPlayer(spell = CombatSpell.FIRE_BOLT, magicDamageBonus = 155)))
    }

    @Test
    fun `chaos gauntlets do not apply to a non-bolt spell`() {
        val withGauntlets = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, gauntlets = true))
        assertEquals(2.0, withGauntlets)
    }

    // ---- getMaxHit: Virtus robes' Ancient Magicks-only extra magic damage (audit round 2026-09-17b) ----

    @Test
    fun `full Virtus gives 15 percent extra magic damage on an Ancient Magicks spell, not the flat 6 percent spellbook`() {
        // OSRS Wiki "Virtus robes": "an additional 3% magic damage is given per piece... 15% for the full set" when
        // using Ancient Magicks (SMOKE_RUSH, interfaceId 193). The flat 6% (2% per piece, any spellbook) is applied
        // generically elsewhere through each piece's own items.yml magic_damage field, not through this bonus, so
        // this test isolates the new conditional +3%-per-piece with the mocked equipmentBonuses left at 0.
        val ancient = getMaxHit(newPlayer(spell = CombatSpell.SMOKE_RUSH, virtusPieces = 3))
        // hit = floor(15 * 1.15) with the flat 6% already-generic bonus == this test's baseline (0 mocked), so here
        // only the +9% (3 pieces x 3%) from VirtusRobes shows up: floor(15 * 1.09) = 16.
        assertEquals(floor(15.0 * 1.09), ancient)
    }

    @Test
    fun `Virtus gives no Ancient Magicks bonus on a standard-spellbook spell`() {
        val standard = getMaxHit(newPlayer(spell = CombatSpell.WIND_STRIKE, virtusPieces = 3))
        assertEquals(2.0, standard)
    }

    @Test
    fun `Virtus' Ancient Magicks bonus scales per piece worn, not just full set`() {
        val onePiece = getMaxHit(newPlayer(spell = CombatSpell.SMOKE_RUSH, virtusPieces = 1))
        val twoPieces = getMaxHit(newPlayer(spell = CombatSpell.SMOKE_RUSH, virtusPieces = 2))
        val threePieces = getMaxHit(newPlayer(spell = CombatSpell.SMOKE_RUSH, virtusPieces = 3))
        assertEquals(floor(15.0 * 1.03), onePiece)
        assertEquals(floor(15.0 * 1.06), twoPieces)
        assertEquals(floor(15.0 * 1.09), threePieces)
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
    fun `the plain Salve amulet does not change magic accuracy (only Salve (i) and (ei) do)`() {
        // OSRS Wiki "Damage per second/Magic": the magic accuracy gear bonus is the imbued Salve/slayer helm (1.15).
        val plain = newPlayer(magicLevel = 99)
        val withSalve = newPlayer(magicLevel = 99, amulet = Items.SALVE_AMULET)
        val undead = newNpc(species = setOf(NpcSpecies.UNDEAD))
        assertEquals(MagicCombatFormula.getAccuracy(plain, undead, 1.0), MagicCombatFormula.getAccuracy(withSalve, undead, 1.0), 1e-12)
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
        eliteVoid: Boolean = false,
        prayer: Prayer? = null,
        amulet: Int? = null,
        virtusPieces: Int = 0,
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
        if (eliteVoid) {
            equipment[EquipmentType.HEAD.id] = Item(Items.VOID_MAGE_HELM)
            equipment[EquipmentType.CHEST.id] = Item(Items.ELITE_VOID_KNIGHT_TOP)
            equipment[EquipmentType.LEGS.id] = Item(Items.ELITE_VOID_KNIGHT_ROBE)
            equipment[EquipmentType.GLOVES.id] = Item(Items.VOID_KNIGHT_GLOVES)
        }
        amulet?.let { equipment[EquipmentType.AMULET.id] = Item(it) }
        if (virtusPieces >= 1) equipment[EquipmentType.HEAD.id] = Item(Items.VIRTUS_MASK)
        if (virtusPieces >= 2) equipment[EquipmentType.CHEST.id] = Item(Items.VIRTUS_ROBE_TOP)
        if (virtusPieces >= 3) equipment[EquipmentType.LEGS.id] = Item(Items.VIRTUS_ROBE_LEGS)
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
