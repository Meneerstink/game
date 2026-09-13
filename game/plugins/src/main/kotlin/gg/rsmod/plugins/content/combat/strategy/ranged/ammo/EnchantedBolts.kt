package gg.rsmod.plugins.content.combat.strategy.ranged.ammo

import gg.rsmod.game.model.attr.DRAGONFIRE_IMMUNITY_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.api.ext.isSpecies
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.content.combat.DEFAULT_MIN_HIT
import gg.rsmod.plugins.content.mechanics.poison.Poison
import kotlin.math.floor

/**
 * Enchanted dragon bolt special effects, one shared model for the whole roster (OSRS Wiki item pages of the ten
 * "<gem> dragon bolts (e)", fetched 2026-09-13; see `C:\RSPS\OSRS_IMPORT_STATUS.md`). Gfx 749-758 and sounds 2910-2920
 * are the 667 cache ids (Void donor `bolts.gfx.toml` / `bolt_special.sounds.toml`, cache-verified). The Kandarin hard
 * diary does not exist here, so base chances apply. The Armadyl crossbow special doubles the base chance; the Zaryte
 * crossbow special guarantees the effect on a successful hit and raises the ruby, diamond, onyx and dragonstone values.
 *
 * SOURCE_GAP, deliberately not implemented: Earth's Fury against players (the Agility resistance roll is only given as
 * "-16 % at level 1, 110 % at level 99" without saying which way it applies) - jade bolts do nothing against players.
 */
object EnchantedBolts {
    enum class Effect(
        val gfx: Int,
        val sound: Int,
    ) {
        LUCKY_LIGHTNING(749, 2918),
        SEA_CURSE(750, 2920),
        CLEAR_MIND(751, 2912),
        MAGICAL_POISON(752, 2919),
        LIFE_LEECH(753, 2917),
        BLOOD_FORFEIT(754, 2911),
        EARTHS_FURY(755, 2916),
        DRAGONS_BREATH(756, 2915),
        DOWN_TO_EARTH(757, 2914),
        ARMOUR_PIERCING(758, 2910),
    }

    /**
     * @param needsSuccessfulHit true when the item page says the effect only activates on a successful hit; false when
     * it "rolls its chance of activation regardless of whether or not it passes an accuracy roll".
     */
    data class Bolt(
        val itemId: Int,
        val effect: Effect,
        val monsterChance: Double,
        val playerChance: Double,
        val needsSuccessfulHit: Boolean,
    )

    val ROSTER: Map<Int, Bolt> =
        listOf(
            Bolt(Items.OPAL_DRAGON_BOLTS_E, Effect.LUCKY_LIGHTNING, 0.05, 0.05, needsSuccessfulHit = false),
            Bolt(Items.JADE_DRAGON_BOLTS_E, Effect.EARTHS_FURY, 0.06, 0.06, needsSuccessfulHit = false),
            Bolt(Items.PEARL_DRAGON_BOLTS_E, Effect.SEA_CURSE, 0.06, 0.06, needsSuccessfulHit = false),
            Bolt(Items.TOPAZ_DRAGON_BOLTS_E, Effect.DOWN_TO_EARTH, 0.0, 0.04, needsSuccessfulHit = false),
            Bolt(Items.SAPPHIRE_DRAGON_BOLTS_E, Effect.CLEAR_MIND, 0.25, 0.05, needsSuccessfulHit = true),
            Bolt(Items.EMERALD_DRAGON_BOLTS_E, Effect.MAGICAL_POISON, 0.55, 0.54, needsSuccessfulHit = true),
            Bolt(Items.RUBY_DRAGON_BOLTS_E, Effect.BLOOD_FORFEIT, 0.06, 0.11, needsSuccessfulHit = false),
            Bolt(Items.DIAMOND_DRAGON_BOLTS_E, Effect.ARMOUR_PIERCING, 0.10, 0.05, needsSuccessfulHit = false),
            Bolt(Items.DRAGONSTONE_DRAGON_BOLTS_E, Effect.DRAGONS_BREATH, 0.06, 0.06, needsSuccessfulHit = true),
            Bolt(Items.ONYX_DRAGON_BOLTS_E, Effect.LIFE_LEECH, 0.11, 0.10, needsSuccessfulHit = true),
        ).associateBy { it.itemId }

    /** Crossbow special modes that change bolt effects. */
    enum class Special { NONE, ARMADYL_EYE, ZARYTE_EVOKE }

    /** The water staves that negate Sea Curse (Pearl page: "a water staff"; combination staves unsourced, not included). */
    private val WATER_STAVES = intArrayOf(Items.STAFF_OF_WATER, Items.WATER_BATTLESTAFF, Items.MYSTIC_WATER_STAFF)

    fun boltFor(ammoId: Int?): Bolt? = ammoId?.let { ROSTER[it] }

    fun chance(
        bolt: Bolt,
        target: Pawn,
        special: Special,
    ): Double {
        val base = if (target is Player) bolt.playerChance else bolt.monsterChance
        return if (special == Special.ARMADYL_EYE) base * 2 else base
    }

    /** Whether the effect can apply to [target] at all (immunities and the sourced player/monster restrictions). */
    fun applicable(
        bolt: Bolt,
        attacker: Player,
        target: Pawn,
    ): Boolean =
        when (bolt.effect) {
            Effect.DOWN_TO_EARTH -> target is Player
            Effect.EARTHS_FURY -> target is Npc
            Effect.SEA_CURSE -> !(target is Player && target.hasEquipped(EquipmentType.WEAPON, *WATER_STAVES))
            Effect.LIFE_LEECH -> !(target is Npc && target.isSpecies(NpcSpecies.UNDEAD))
            Effect.DRAGONS_BREATH -> !immuneToDragonfire(target)
            Effect.MAGICAL_POISON -> !Poison.isImmune(target)
            Effect.BLOOD_FORFEIT -> attacker.getCurrentLifepoints() - bloodForfeitCost(attacker) >= 1 && bloodForfeitCost(attacker) >= 1
            else -> true
        }

    /**
     * Rolls the effect for one shot. [landedHit] is the accuracy result; the Zaryte crossbow special guarantees the
     * effect only when the hit landed.
     */
    fun activates(
        bolt: Bolt,
        attacker: Player,
        target: Pawn,
        landedHit: Boolean,
        special: Special,
        roll: Double,
    ): Boolean {
        if (!applicable(bolt, attacker, target)) return false
        if (special == Special.ZARYTE_EVOKE) return landedHit
        if (bolt.needsSuccessfulHit && !landedHit) return false
        return roll < chance(bolt, target, special)
    }

    /**
     * How an activated effect changes the shot before the damage roll. [forceHit] turns a missed roll into a real,
     * normally rolled hit (Armour Piercing "becoming a successful hit"); other effects that activate on a miss only deal
     * their own bonus or override damage.
     */
    data class ShotChange(
        val forceHit: Boolean = false,
        val maxHitMultiplier: Double = 1.0,
        val bonusDamage: Int = 0,
        val overrideDamage: Int? = null,
    )

    /** The shot as it should be dealt: [bolt] is null when no effect activated. */
    data class Resolution(
        val bolt: Bolt?,
        val landHit: Boolean,
        val minHit: Double,
        val maxHit: Double,
        val bonusDamage: Int,
    )

    /**
     * Rolls and applies the enchanted-bolt effect for one shot of [ammoId]. When an effect that ignores accuracy activates
     * on a missed roll, the shot lands with 0 base damage plus the effect's bonus (Opal, Pearl) or override (Ruby), or as a
     * full normal hit for Armour Piercing. Max-hit multipliers are floored ("Maximum ranged hit": ⌊Base × Special⌋).
     */
    fun resolve(
        attacker: Player,
        target: Pawn,
        ammoId: Int?,
        landHit: Boolean,
        maxHit: Double,
        special: Special,
        roll: Double,
    ): Resolution {
        val none = Resolution(null, landHit, DEFAULT_MIN_HIT, maxHit, 0)
        val bolt = boltFor(ammoId) ?: return none
        if (!activates(bolt, attacker, target, landHit, special, roll)) return none
        val change = shotChange(bolt, attacker, target, special)
        change.overrideDamage?.let { exact -> return Resolution(bolt, true, exact - 0.1, exact.toDouble(), 0) }
        val normalHit = landHit || change.forceHit
        val base = if (normalHit) floor(maxHit * change.maxHitMultiplier) else 0.0
        return Resolution(bolt, true, DEFAULT_MIN_HIT, base, change.bonusDamage)
    }

    fun shotChange(
        bolt: Bolt,
        attacker: Player,
        target: Pawn,
        special: Special,
    ): ShotChange {
        val zaryte = special == Special.ZARYTE_EVOKE
        val ranged = attacker.skills.getCurrentLevel(Skills.RANGED)
        return when (bolt.effect) {
            // "+10 % of visible Ranged level" / "+20 %" (22 % under the Zaryte crossbow).
            Effect.LUCKY_LIGHTNING -> ShotChange(bonusDamage = floor(ranged * 0.10).toInt())
            Effect.DRAGONS_BREATH -> ShotChange(bonusDamage = floor(ranged * if (zaryte) 0.22 else 0.20).toInt())
            // "1/20 of the user's Ranged level" (1/15 against fiery targets), activates even on a miss.
            Effect.SEA_CURSE -> ShotChange(bonusDamage = ranged / if (isFiery(target)) 15 else 20)
            // Hits regardless of accuracy with +15 % max hit (+26 % under the Zaryte crossbow).
            Effect.ARMOUR_PIERCING -> ShotChange(forceHit = true, maxHitMultiplier = if (zaryte) 1.26 else 1.15)
            Effect.LIFE_LEECH -> ShotChange(maxHitMultiplier = if (zaryte) 1.32 else 1.20)
            // Overrides bolt damage: 20 % of the target's current hitpoints, capped at 100 (22 % / 110 under Zaryte).
            Effect.BLOOD_FORFEIT -> {
                val damage = floor(target.getCurrentLifepoints() * if (zaryte) 0.22 else 0.20).toInt()
                ShotChange(overrideDamage = minOf(damage, if (zaryte) 110 else 100))
            }
            else -> ShotChange()
        }
    }

    /** Effects applied once the hit is resolved: visuals, audio, heals, drains, poison and binds. */
    fun afterHit(
        bolt: Bolt,
        attacker: Player,
        target: Pawn,
        damageDealt: Int,
    ) {
        target.graphic(bolt.effect.gfx)
        attacker.playSound(bolt.effect.sound)
        when (bolt.effect) {
            // "heal the attacker's Hitpoints by 25% of the total damage dealt".
            Effect.LIFE_LEECH -> if (damageDealt >= 4) attacker.heal(damageDealt / 4)
            Effect.MAGICAL_POISON -> Poison.poison(target, 5)
            Effect.EARTHS_FURY -> target.freeze(8)
            Effect.DOWN_TO_EARTH -> (target as? Player)?.skills?.decrementCurrentLevel(Skills.MAGIC, 1, capped = false)
            Effect.CLEAR_MIND ->
                if (target is Player) {
                    // Players: drain 1/20 of the attacker's Ranged level, the attacker gains half of it.
                    val drain = attacker.skills.getCurrentLevel(Skills.RANGED) / 20
                    target.decreasePrayerPoints(drain)
                    attacker.restorePrayer(drain / 2)
                } else if (target is Npc) {
                    // Monsters: 1/10 of the target's Ranged level, the attacker restores 1/8 of that.
                    val drain = target.stats.getCurrentLevel(NpcSkills.RANGED) / 10
                    attacker.restorePrayer(drain / 8)
                }
            Effect.BLOOD_FORFEIT -> attacker.hit(damage = bloodForfeitCost(attacker), type = HitType.REGULAR_HIT)
            else -> {}
        }
    }

    /** "Costs 10% of the player's current Hitpoints" (rounded down as the Void donor does). */
    fun bloodForfeitCost(attacker: Player): Int = attacker.getCurrentLifepoints() / 10

    private fun isFiery(target: Pawn): Boolean = target is Npc && target.isSpecies(NpcSpecies.FIERY)

    /** Dragonstone page: not against dragonfire-immune targets or players praying Protect from Magic. */
    private fun immuneToDragonfire(target: Pawn): Boolean =
        when (target) {
            is Npc -> target.isSpecies(NpcSpecies.DRAGON)
            is Player ->
                target.isProtectedFrom(CombatClass.MAGIC) ||
                    target.attr[DRAGONFIRE_IMMUNITY_ATTR] == true ||
                    target.timers.has(SUPER_ANTIFIRE_TIMER) ||
                    (target.timers.has(ANTIFIRE_TIMER) && target.hasEquipped(EquipmentType.SHIELD, Items.ANTIDRAGON_SHIELD, Items.DRAGONFIRE_SHIELD, Items.DRAGONFIRE_SHIELD_11284))
            else -> false
        }
}
