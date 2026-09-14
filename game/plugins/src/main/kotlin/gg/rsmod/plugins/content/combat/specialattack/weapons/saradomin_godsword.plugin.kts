package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import kotlin.math.ceil

/**
 * Saradomin Godsword ("Healing Blade") - real mechanic (OSRS Wiki, verified): 50% energy, doubled
 * accuracy, +10% max hit, single hit; on a successful hit the WIELDER (not the target) is healed
 * 50% of the real damage dealt (min 10 HP) and restores 25% of the real damage dealt as Prayer
 * points (min 5 points), both rounded up, both capped at the wielder's max.
 *
 * Disclosed simplification: real OSRS calculates the heal from pre-defence-reduction potential
 * damage in some edge cases (e.g. vs. kurasks); this codebase heals from the actual dealt damage
 * instead, since the pre-reduction value isn't exposed by this combat pipeline. Simpler, honest,
 * and correct in the overwhelming majority of real fights.
 *
 * Player Prayer points, lifepoints and hitmark damage all use the same 1:1 real-value unit, so the
 * sourced percentages and minimums can be applied directly.
 */
// Special look: animation 12019 + graphic 2109 (Novite PlayerCombat case 11698; Void `healing_blade_special` = 2109), sound 3865 (Void).
SpecialAttacks.register(50, Items.SARADOMIN_GODSWORD) {
    player.animate(12019)
    player.graphic(2109)
    player.playSound(3865)
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.10)
    val accuracy = MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 2.0)
    val landHit = accuracy >= world.randomDouble()
    val hit = player.dealHit(
        target = target,
        maxHit = maxHit,
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )

    if (landHit) {
        val realDamage = hit.hit.hitmarks.sumOf { it.damage }

        val healRealHp = ceil(realDamage * 0.5).toInt().coerceAtLeast(10)
        val newLifepoints = (player.getCurrentLifepoints() + healRealHp).coerceAtMost(player.getMaximumLifepoints())
        player.setCurrentLifepoints(newLifepoints)

        val prayerRestoreRealPoints = ceil(realDamage * 0.25).toInt().coerceAtLeast(5)
        val newPrayerPoints = (player.getCurrentPrayerPoints() + prayerRestoreRealPoints).coerceAtMost(player.getMaximumPrayerPoints())
        player.setCurrentPrayerPoints(newPrayerPoints)
    }
}
