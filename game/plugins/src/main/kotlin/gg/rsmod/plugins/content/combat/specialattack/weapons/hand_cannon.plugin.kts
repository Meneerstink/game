package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.mechanics.weapons.HandCannon

/**
 * Hand cannon special attack (P8, 2026-09-02 - see `HandCannon.kt` for the full sourcing
 * note). Sourced mechanic (prior session's investigation, `OWNER_TASK_STATUS.md` R04.10):
 * 30%-200% of normal weapon damage, +75% accuracy, 50% special energy. Reuses the existing
 * `dealHit` core overload's `minHit`/`maxHit: Double` parameters directly against
 * `RangedCombatFormula.getMaxHit()`'s raw (un-multiplied-by-10) scale, so no custom
 * damage-rolling code is needed to express the 30%-200% range.
 *
 * BLOCKED, documented rather than guessed: no unique special-attack animation id exists
 * anywhere in this cache/codebase for the hand cannon (confirmed by a prior session and
 * re-confirmed this session - see `RSPS_DECISIONS.md`). This reuses the weapon's normal
 * attack animation (`CombatConfigs.getAttackAnimation`) as a functional placeholder instead
 * of inventing an id; replace with the real special animation the moment one is sourced.
 */
val ENERGY_REQUIRED = 50
val MIN_DAMAGE_MULTIPLIER = 0.30
val MAX_DAMAGE_MULTIPLIER = 2.00
val ACCURACY_MULTIPLIER = 1.75

SpecialAttacks.register(ENERGY_REQUIRED, Items.HAND_CANNON) {
    val ammo = player.getEquipment(EquipmentType.AMMO)
    if (ammo == null || ammo.id != Items.HAND_CANNON_SHOT) {
        player.message("You have no ammo left in your quiver.")
        return@register
    }

    if (HandCannon.rollExplodes(world, HandCannon.firemakingLevel(player), isSpecialAttack = true)) {
        player.equipment.remove(Items.HAND_CANNON_SHOT, amount = 1)
        HandCannon.explode(player)
        return@register
    }

    player.animate(CombatConfigs.getAttackAnimation(player))
    player.equipment.remove(Items.HAND_CANNON_SHOT, amount = 1)

    val baseMaxHit = RangedCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.0)
    val accuracy = RangedCombatFormula.getAccuracy(player, target, specialAttackMultiplier = ACCURACY_MULTIPLIER)
    val landHit = accuracy >= world.randomDouble()

    player.dealHit(
        target = target,
        minHit = baseMaxHit * MIN_DAMAGE_MULTIPLIER,
        maxHit = baseMaxHit * MAX_DAMAGE_MULTIPLIER,
        landHit = landHit,
        delay = 1,
        hitType = HitType.RANGE,
    )
}
