package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.combat.strategy.magic.SpellEffect
import gg.rsmod.plugins.content.items.armor.MoonArmour

/**
 * OSRS-IMPORT step 4 moon equipment (OSRS Wiki raw wikitext 2026-09-14: "Moon equipment", "Eclipse/Blue/Blood moon armour", "Eclipse
 * atlatl", "Blue moon spear", "Dual macuahuitl"; wiki DPS calculator `PlayerVsNPCCalc.ts`):
 * - Set effects need helm, chestplate and tassets of one set; "It is possible for the set effect to activate even if one or more of the
 *   armour pieces are broken" (degraded and broken ids count).
 * - Eclipse atlatl: a bow using atlatl darts; "Its max hit is determined by the player's melee strength bonus"; the calculator uses the
 *   Strength level + boosts with ranged prayers, the Accurate +3, +8 and ranged void, the melee strength bonus, and the melee salve / black
 *   mask factors; "During player versus player combat, the Atlatl has an attack speed of 3". Eclipse (set): "successful attacks with the
 *   eclipse atlatl a 20% chance to inflict a Burn". Special Eclipse: 50 %, full set, "magic-based and performed at melee distance, with a
 *   50% accuracy bonus"; consumes the remaining burn damage, "increasing the player's max hit by that number and their minimum hit by half of
 *   that number, capped at 50"; darts not needed.
 * - Dual macuahuitl: two hitsplats "spaced 1 game tick apart", "max hit halved" (first rounded down, second up); "the second check will only
 *   proceed if the first succeeds". Bloodrager (set): "a 33% chance upon a successful hit to attack one tick earlier than usual ... Both hits
 *   count towards this independently". Blood Infusion: 25 %, full set, "removes the sequential accuracy check and raises the player's
 *   minimum and maximum hit by 25%", "taking damage equal to 25% of their current Hitpoints, rounded down (dealing no damage when less than
 *   4 health)", Bloodrager "guaranteed to trigger if the attack hits".
 * - Blue moon spear: Frostweaver (set) - "20% chance to occur after successfully casting a Bind spell from the Standard spellbook or an Ice
 *   spell from the Ancient spellbook, even if it does not bind a target" to melee attack instantly. Break Shackles: 50 %, full set, "removes
 *   all active binding effects on a target and has a 1.5% increase in accuracy and damage for every tick of binding removed. The damage
 *   boost ... is capped at a 112.5% increase".
 * SOURCE_CONFLICT: atlatl attack range - infobox 5, CombatStyles template 6 (infobox used). SOURCE_GAP: Blood Infusion's minimum hit (no
 * base stated; only the maximum is raised), Grasp spells (Arceuus absent), Frostweaver from a pending special, Strength experience of the
 * spear's melee hits, repair NPCs and costs.
 */
object MoonSets {
    enum class MoonSet(
        val pieces: List<MoonArmour>,
    ) {
        ECLIPSE(listOf(MoonArmour.ECLIPSE_MOON_HELM, MoonArmour.ECLIPSE_MOON_CHESTPLATE, MoonArmour.ECLIPSE_MOON_TASSETS)),
        BLUE(listOf(MoonArmour.BLUE_MOON_HELM, MoonArmour.BLUE_MOON_CHESTPLATE, MoonArmour.BLUE_MOON_TASSETS)),
        BLOOD(listOf(MoonArmour.BLOOD_MOON_HELM, MoonArmour.BLOOD_MOON_CHESTPLATE, MoonArmour.BLOOD_MOON_TASSETS)),
    }

    const val ATLATL_RANGE = 5

    /** OSRS Wiki "Eclipse atlatl": "During player versus player combat, its base attack speed is 5, unless the full
     * Eclipse armour set is worn" - "the Atlatl has an attack speed of 3" only then (audit round 2026-09-17b: the
     * old code applied speed 3 to every PvP attack regardless of the armour set, an unearned buff without it). */
    const val ATLATL_PVP_SPEED_NO_SET = 5
    const val ATLATL_PVP_SPEED_FULL_SET = 3
    const val BURN_CHANCE = 0.20
    const val ECLIPSE_ENERGY = 50
    const val ECLIPSE_ACCURACY = 1.5
    const val ECLIPSE_BURN_CAP = 50
    const val BLOODRAGER_CHANCE = 0.33
    const val BLOOD_INFUSION_ENERGY = 25
    const val BLOOD_INFUSION_DAMAGE = 1.25
    const val BLOOD_INFUSION_MIN_HITPOINTS = 4
    const val FROSTWEAVER_CHANCE = 0.20
    const val BREAK_SHACKLES_ENERGY = 50
    const val SHACKLES_PER_TICK = 0.015
    const val SHACKLES_DAMAGE_CAP = 1.125

    /** Set by a Bloodrager trigger; [gg.rsmod.plugins.content.combat.Combat.postAttack] then shortens the attack delay by one tick. */
    val BLOODRAGER = AttributeKey<Boolean>()

    fun wearing(
        player: Player,
        set: MoonSet,
    ): Boolean {
        val worn = EquipmentType.values().mapNotNull { player.getEquipment(it)?.id }.toSet()
        return set.pieces.all { piece -> piece.newId in worn || piece.degradedId in worn || piece.brokenId in worn }
    }

    fun wieldingAtlatl(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id == Items.ECLIPSE_ATLATL

    fun eclipseBurnActive(player: Player): Boolean = wieldingAtlatl(player) && wearing(player, MoonSet.ECLIPSE)

    /** The atlatl's PvP attack speed: 5 normally, 3 only with the full Eclipse set worn. */
    fun atlatlPvpSpeed(player: Player): Int = if (wearing(player, MoonSet.ECLIPSE)) ATLATL_PVP_SPEED_FULL_SET else ATLATL_PVP_SPEED_NO_SET

    /** Max hit and minimum hit of Eclipse after consuming [burnDamage]: +burn (capped at 50) and +half of it. */
    fun eclipseHit(
        maxHit: Int,
        burnDamage: Int,
    ): Pair<Int, Int> {
        val bonus = burnDamage.coerceIn(0, ECLIPSE_BURN_CAP)
        return (bonus / 2) to (maxHit + bonus)
    }

    /** The dual macuahuitl's two max hits: the first rounded down, the second rounded up. */
    fun macuahuitlSplit(maxHit: Int): Pair<Int, Int> = (maxHit / 2) to (maxHit - maxHit / 2)

    fun bloodInfusionSelfDamage(currentHitpoints: Int): Int = if (currentHitpoints < BLOOD_INFUSION_MIN_HITPOINTS) 0 else currentHitpoints / 4

    /** Bloodrager: each successful hit rolls its own 33 % chance while the full blood moon set is worn. */
    fun rollBloodrager(
        player: Player,
        successfulHits: List<Boolean>,
        roll: () -> Double,
    ) {
        if (!wearing(player, MoonSet.BLOOD)) return
        if (successfulHits.filter { it }.any { roll() < BLOODRAGER_CHANCE }) player.attr[BLOODRAGER] = true
    }

    fun shacklesAccuracy(ticks: Int): Double = 1.0 + SHACKLES_PER_TICK * ticks

    fun shacklesDamage(ticks: Int): Double = 1.0 + minOf(SHACKLES_PER_TICK * ticks, SHACKLES_DAMAGE_CAP)

    /** Standard-spellbook bind spells and Ancient ice spells (the freezing spells of this server). */
    fun freezesForFrostweaver(spell: CombatSpell): Boolean = spell.effect is SpellEffect.Freeze && (spell.interfaceId == 192 || spell.interfaceId == 193)

    /** Frostweaver: after a qualifying cast, a 20 % chance for an instant melee attack with the spear (ADAPTED: the target must be adjacent). */
    fun frostweaver(
        pawn: Pawn,
        target: Pawn,
        spell: CombatSpell,
    ) {
        if (pawn !is Player || pawn.getEquipment(EquipmentType.WEAPON)?.id != Items.BLUE_MOON_SPEAR) return
        if (!freezesForFrostweaver(spell) || !wearing(pawn, MoonSet.BLUE)) return
        if (pawn.world.randomDouble() >= FROSTWEAVER_CHANCE) return
        if (pawn.tile.getDistance(target.tile) > 1) return
        gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy.attack(pawn, target)
    }
}
