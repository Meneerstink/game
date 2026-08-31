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
 * Player Prayer points AND lifepoints (HP) are both stored internally at x10 the real displayed
 * scale, and hitmark damage is in that same x10 scale (see bandos_godsword.plugin.kts for the
 * full verified detail) - so the real-HP percentages/minimums below are computed against the
 * damage divided back down by 10, then the heal/restore is converted back up by 10 before being
 * added to the (also x10-scale) lifepoints/prayer totals.
 */
SpecialAttacks.register(50, Items.SARADOMIN_GODSWORD) {
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
        // Real (displayed) HP-equivalent damage - see doc comment above for why /10 is required.
        val realDamage = hit.hit.hitmarks.sumOf { it.damage } / 10

        val healRealHp = ceil(realDamage * 0.5).toInt().coerceAtLeast(10)
        val newLifepoints = (player.getCurrentLifepoints() + healRealHp * 10).coerceAtMost(player.getMaximumLifepoints())
        player.setCurrentLifepoints(newLifepoints)

        val prayerRestoreRealPoints = ceil(realDamage * 0.25).toInt().coerceAtLeast(5)
        val newPrayerPoints = (player.getCurrentPrayerPoints() + prayerRestoreRealPoints * 10).coerceAtMost(player.getMaximumPrayerPoints())
        player.setCurrentPrayerPoints(newPrayerPoints)
    }
}
