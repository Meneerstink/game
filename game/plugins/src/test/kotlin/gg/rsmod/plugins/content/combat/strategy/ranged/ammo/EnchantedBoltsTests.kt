package gg.rsmod.plugins.content.combat.strategy.ranged.ammo

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import gg.rsmod.plugins.content.combat.rollDamage
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts.Effect
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts.Special
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EnchantedBolts] against the ten OSRS Wiki "<gem> dragon bolts (e)" pages (2026-09-13), the Armadyl/Zaryte crossbow
 * pages and the 667 proc assets, across the whole roster.
 */
class EnchantedBoltsTests {
    @Test
    fun `the roster carries every sourced chance, hit requirement, gfx and sound`() {
        data class Row(val chanceMonster: Double, val chancePlayer: Double, val needsHit: Boolean, val gfx: Int, val sound: Int)
        val expected =
            mapOf(
                Items.OPAL_DRAGON_BOLTS_E to Row(0.05, 0.05, false, 749, 2918),
                Items.JADE_DRAGON_BOLTS_E to Row(0.06, 0.06, false, 755, 2916),
                Items.PEARL_DRAGON_BOLTS_E to Row(0.06, 0.06, false, 750, 2920),
                Items.TOPAZ_DRAGON_BOLTS_E to Row(0.0, 0.04, false, 757, 2914),
                Items.SAPPHIRE_DRAGON_BOLTS_E to Row(0.25, 0.05, true, 751, 2912),
                Items.EMERALD_DRAGON_BOLTS_E to Row(0.55, 0.54, true, 752, 2919),
                Items.RUBY_DRAGON_BOLTS_E to Row(0.06, 0.11, false, 754, 2911),
                Items.DIAMOND_DRAGON_BOLTS_E to Row(0.10, 0.05, false, 758, 2910),
                Items.DRAGONSTONE_DRAGON_BOLTS_E to Row(0.06, 0.06, true, 756, 2915),
                Items.ONYX_DRAGON_BOLTS_E to Row(0.11, 0.10, true, 753, 2917),
            )
        assertEquals(expected.keys, EnchantedBolts.ROSTER.keys, "every dragon bolt (e) is in the roster, nothing else")
        expected.forEach { (id, row) ->
            val bolt = EnchantedBolts.ROSTER.getValue(id)
            assertEquals(row, Row(bolt.monsterChance, bolt.playerChance, bolt.needsSuccessfulHit, bolt.effect.gfx, bolt.effect.sound), "bolt $id")
        }
        assertNull(EnchantedBolts.boltFor(Items.OSRS_DRAGON_BOLTS), "plain dragon bolts have no effect")
        assertNull(EnchantedBolts.boltFor(Items.DIAMOND_DRAGON_BOLTS), "gem-tipped but unenchanted bolts have no effect")
    }

    @Test
    fun `Armadyl Eye doubles the base chance, Evoke guarantees the effect only on a successful hit`() {
        val attacker = player()
        val npc = npc()
        val diamond = EnchantedBolts.ROSTER.getValue(Items.DIAMOND_DRAGON_BOLTS_E)
        assertEquals(0.20, EnchantedBolts.chance(diamond, npc, Special.ARMADYL_EYE), 1e-12)
        assertTrue(EnchantedBolts.activates(diamond, attacker, npc, landedHit = true, special = Special.ZARYTE_EVOKE, roll = 0.99))
        assertFalse(EnchantedBolts.activates(diamond, attacker, npc, landedHit = false, special = Special.ZARYTE_EVOKE, roll = 0.0))
        val onyx = EnchantedBolts.ROSTER.getValue(Items.ONYX_DRAGON_BOLTS_E)
        assertFalse(EnchantedBolts.activates(onyx, attacker, npc, landedHit = false, special = Special.NONE, roll = 0.0), "onyx needs a hit")
        assertTrue(EnchantedBolts.activates(diamond, attacker, npc, landedHit = false, special = Special.NONE, roll = 0.0), "diamond ignores accuracy")
    }

    @Test
    fun `immunities and player or monster restrictions`() {
        val attacker = player(hp = 99)
        val topaz = EnchantedBolts.ROSTER.getValue(Items.TOPAZ_DRAGON_BOLTS_E)
        assertFalse(EnchantedBolts.applicable(topaz, attacker, npc()), "topaz only works on players")
        assertTrue(EnchantedBolts.applicable(topaz, attacker, player()))
        val jade = EnchantedBolts.ROSTER.getValue(Items.JADE_DRAGON_BOLTS_E)
        assertTrue(EnchantedBolts.applicable(jade, attacker, npc()))
        assertFalse(EnchantedBolts.applicable(jade, attacker, player()), "jade vs players is a recorded SOURCE_GAP")
        val onyx = EnchantedBolts.ROSTER.getValue(Items.ONYX_DRAGON_BOLTS_E)
        assertFalse(EnchantedBolts.applicable(onyx, attacker, npc(species = setOf(NpcSpecies.UNDEAD))))
        val dragonstone = EnchantedBolts.ROSTER.getValue(Items.DRAGONSTONE_DRAGON_BOLTS_E)
        assertFalse(EnchantedBolts.applicable(dragonstone, attacker, npc(species = setOf(NpcSpecies.DRAGON))))
        assertFalse(EnchantedBolts.applicable(dragonstone, attacker, player(prayerIcon = PrayerIcon.PROTECT_FROM_MAGIC)))
        val pearl = EnchantedBolts.ROSTER.getValue(Items.PEARL_DRAGON_BOLTS_E)
        listOf(Items.STAFF_OF_WATER, Items.WATER_BATTLESTAFF, Items.MYSTIC_WATER_STAFF).forEach { staff ->
            assertFalse(EnchantedBolts.applicable(pearl, attacker, player(weapon = staff)), "water staff $staff negates Sea Curse")
        }
        val ruby = EnchantedBolts.ROSTER.getValue(Items.RUBY_DRAGON_BOLTS_E)
        assertFalse(EnchantedBolts.applicable(ruby, player(hp = 9), npc()), "cannot spare hitpoints")
        assertTrue(EnchantedBolts.applicable(ruby, player(hp = 50), npc()))
    }

    @Test
    fun `shot changes follow the item pages, including the Zaryte crossbow values`() {
        val attacker = player(ranged = 99, hp = 99)
        val target = npc(hp = 700)
        fun change(id: Int, special: Special = Special.NONE, t: gg.rsmod.game.model.entity.Pawn = target) =
            EnchantedBolts.shotChange(EnchantedBolts.ROSTER.getValue(id), attacker, t, special)
        assertEquals(9, change(Items.OPAL_DRAGON_BOLTS_E).bonusDamage) // ⌊99 × 10 %⌋
        assertEquals(19, change(Items.DRAGONSTONE_DRAGON_BOLTS_E).bonusDamage) // ⌊99 × 20 %⌋
        assertEquals(21, change(Items.DRAGONSTONE_DRAGON_BOLTS_E, Special.ZARYTE_EVOKE).bonusDamage) // ⌊99 × 22 %⌋
        assertEquals(4, change(Items.PEARL_DRAGON_BOLTS_E).bonusDamage) // 99 / 20
        assertEquals(6, change(Items.PEARL_DRAGON_BOLTS_E, t = npc(species = setOf(NpcSpecies.FIERY))).bonusDamage) // 99 / 15
        assertEquals(1.15, change(Items.DIAMOND_DRAGON_BOLTS_E).maxHitMultiplier)
        assertEquals(1.26, change(Items.DIAMOND_DRAGON_BOLTS_E, Special.ZARYTE_EVOKE).maxHitMultiplier)
        assertEquals(1.20, change(Items.ONYX_DRAGON_BOLTS_E).maxHitMultiplier)
        assertEquals(1.32, change(Items.ONYX_DRAGON_BOLTS_E, Special.ZARYTE_EVOKE).maxHitMultiplier)
        assertEquals(100, change(Items.RUBY_DRAGON_BOLTS_E).overrideDamage, "20 % of 700 capped at 100")
        assertEquals(110, change(Items.RUBY_DRAGON_BOLTS_E, Special.ZARYTE_EVOKE).overrideDamage, "22 % of 700 capped at 110")
        assertEquals(40, change(Items.RUBY_DRAGON_BOLTS_E, t = npc(hp = 200)).overrideDamage, "⌊20 % of 200⌋")
    }

    @Test
    fun `resolve turns activations into the dealt shot`() {
        val attacker = player(ranged = 99, hp = 99)
        val target = npc(hp = 200)
        // Opal on a missed roll: lands with 0 base damage plus the lightning bonus.
        val opalMiss = EnchantedBolts.resolve(attacker, target, Items.OPAL_DRAGON_BOLTS_E, landHit = false, maxHit = 30.0, special = Special.NONE, roll = 0.0)
        assertTrue(opalMiss.landHit)
        assertEquals(0.0, opalMiss.maxHit)
        assertEquals(9, opalMiss.bonusDamage)
        assertEquals(9, rollDamage(opalMiss.minHit, opalMiss.maxHit) + opalMiss.bonusDamage)
        // Diamond on a missed roll becomes a normal hit with ⌊30 × 1.15⌋ = 34.
        val diamond = EnchantedBolts.resolve(attacker, target, Items.DIAMOND_DRAGON_BOLTS_E, landHit = false, maxHit = 30.0, special = Special.NONE, roll = 0.0)
        assertTrue(diamond.landHit)
        assertEquals(34.0, diamond.maxHit)
        // Ruby overrides the damage with an exact 40.
        val ruby = EnchantedBolts.resolve(attacker, target, Items.RUBY_DRAGON_BOLTS_E, landHit = false, maxHit = 30.0, special = Special.NONE, roll = 0.0)
        assertEquals(setOf(40), (0 until 200).map { rollDamage(ruby.minHit, ruby.maxHit, Random(it)) }.toSet())
        // No activation: the shot is untouched.
        val none = EnchantedBolts.resolve(attacker, target, Items.ONYX_DRAGON_BOLTS_E, landHit = false, maxHit = 30.0, special = Special.NONE, roll = 0.0)
        assertNull(none.bolt)
        assertFalse(none.landHit)
        assertEquals(DEFAULT_MIN_HIT, none.minHit)
        assertEquals(Effect.LIFE_LEECH, EnchantedBolts.ROSTER.getValue(Items.ONYX_DRAGON_BOLTS_E).effect)
    }

    private fun player(
        ranged: Int = 1,
        hp: Int = 10,
        weapon: Int? = null,
        shield: Int? = null,
        cape: Int? = null,
        prayerIcon: PrayerIcon = PrayerIcon.NONE,
        timers: TimerMap = TimerMap(),
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val skills = SkillSet(maxSkills = 25)
        skills.setBaseLevel(Skills.RANGED, ranged)
        every { player.skills } returns skills
        every { player.getCurrentLifepoints() } returns hp
        every { player.prayerIcon } returns prayerIcon.id
        every { player.attr } returns AttributeMap()
        every { player.timers } returns timers
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        weapon?.let { equipment[EquipmentType.WEAPON.id] = Item(it) }
        shield?.let { equipment[EquipmentType.SHIELD.id] = Item(it) }
        cape?.let { equipment[EquipmentType.CAPE.id] = Item(it) }
        every { player.equipment } returns equipment
        return player
    }

    /**
     * OSRS-IMPORT audit round 2026-09-17b: OSRS Wiki "Fire cape" - "When worn, players are granted the fiery
     * attribute and will therefore receive additional damage in PvP from the special effects of Pearl bolts (e) and
     * Pearl dragon bolts (e)." Sea Curse's bonus damage was 1/15 of Ranged level against a fiery target instead of
     * 1/20 - the old check only ever matched NPCs with the FIERY species tag, so a Fire-cape-wearing player target
     * always got the weaker 1/20 rate.
     */
    @Test
    fun `Sea Curse deals the fiery 1 in 15 bonus against a player wearing a Fire cape, not the plain 1 in 20`() {
        val attacker = player(ranged = 99, hp = 99)
        val pearl = EnchantedBolts.ROSTER.getValue(Items.PEARL_DRAGON_BOLTS_E)
        val plainPlayer = player()
        assertEquals(4, EnchantedBolts.shotChange(pearl, attacker, plainPlayer, Special.NONE).bonusDamage, "99 / 20, not fiery")
        val fireCapePlayer = player(cape = Items.FIRE_CAPE)
        assertEquals(6, EnchantedBolts.shotChange(pearl, attacker, fireCapePlayer, Special.NONE).bonusDamage, "99 / 15, fiery via Fire cape")
        // The Infernal cape explicitly does NOT grant the fiery attribute (the wiki's own point of distinction).
        val infernalCapePlayer = player(cape = Items.INFERNAL_CAPE)
        assertEquals(4, EnchantedBolts.shotChange(pearl, attacker, infernalCapePlayer, Special.NONE).bonusDamage, "99 / 20, Infernal cape is not fiery")
    }

    /**
     * OSRS-IMPORT audit round 2026-09-17b: OSRS Wiki "Dragonstone dragon bolts (e)" - Dragon's Breath does not
     * activate against a dragonfire-immune target; this server models regular-antifire-potion immunity as needing
     * one of the anti-dragon-tier shields. Guards the fix: Dragonfire ward and Ancient wyvern shield (both sourced
     * as equivalent-tier protection in `DragonfireFormula`) now qualify, where before only three older shields did.
     */
    @Test
    fun `Dragon's Breath is negated by Dragonfire ward and Ancient wyvern shield with a regular antifire potion`() {
        val attacker = player(ranged = 99, hp = 99)
        val dragonstone = EnchantedBolts.ROSTER.getValue(Items.DRAGONSTONE_DRAGON_BOLTS_E)
        listOf(Items.DRAGONFIRE_WARD, Items.DRAGONFIRE_WARD_UNCHARGED, Items.ANCIENT_WYVERN_SHIELD, Items.ANCIENT_WYVERN_SHIELD_UNCHARGED).forEach { shield ->
            val timers = TimerMap().also { it[gg.rsmod.game.model.timer.ANTIFIRE_TIMER] = 10 }
            val protected = player(shield = shield, timers = timers)
            assertFalse(EnchantedBolts.applicable(dragonstone, attacker, protected), "shield $shield + regular antifire must block Dragon's Breath")
        }
        // Without any antifire potion timer, even a qualifying shield must not grant immunity.
        val noPotion = player(shield = Items.DRAGONFIRE_WARD)
        assertTrue(EnchantedBolts.applicable(dragonstone, attacker, noPotion), "shield alone, no potion, must not block Dragon's Breath")
    }

    private fun npc(
        species: Set<Any> = emptySet(),
        hp: Int = 100,
    ): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.species } returns species
        every { npc.combatDef } returns NpcCombatDef.DEFAULT
        every { npc.getCurrentLifepoints() } returns hp
        every { npc.prayerIcon } returns PrayerIcon.NONE.id
        every { npc.attr } returns AttributeMap()
        return npc
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
    }
}
