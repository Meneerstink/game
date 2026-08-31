package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * Bandos Godsword ("Warstrike") - one of the variants armadyl_godsword.plugin.kts's own comment
 * flagged as not implemented that pass. Real mechanic (OSRS Wiki, verified): 50% energy, doubled
 * accuracy, +21% max hit, single hit; on a successful hit drains the real damage dealt 1:1 from
 * the target's stats in order Defence -> Strength -> Prayer(points) -> Attack -> Magic -> Ranged,
 * with any leftover once a stat hits 0 spilling into the next one in the list.
 *
 * NPC targets have no modeled Prayer stat in this codebase (NpcSkills has no PRAYER constant), so
 * the Npc drain order skips Prayer: Defence -> Strength -> Attack -> Magic -> Ranged. Disclosed
 * simplification, not a guess - NpcSkills was checked directly.
 *
 * Player Prayer points are stored internally at x10 the real 1-99 displayed scale (verified via
 * Player.getMaximumPrayerPoints()/decreasePrayerPoints() vs. real usage in Prayers.kt), so the
 * real-points drain amount is multiplied by 10 before calling decreasePrayerPoints. Lifepoints
 * (HP) are stored the same way (getMaximumLifepoints() = HP level * 10, confirmed in Player.kt),
 * and hitmark damage is in that same x10 scale (PawnExt.kt's dealHit multiplies by 10), so the
 * real per-level drain amount used below is the hitmark damage divided back down by 10.
 */
SpecialAttacks.register(50, Items.BANDOS_GODSWORD) {
    val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.21)
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
        var remaining = hit.hit.hitmarks.sumOf { it.damage } / 10
        if (target is Player) {
            val p = target as Player
            val order = intArrayOf(Skills.DEFENCE, Skills.STRENGTH, Skills.PRAYER, Skills.ATTACK, Skills.MAGIC, Skills.RANGED)
            for (skill in order) {
                if (remaining <= 0) break
                if (skill == Skills.PRAYER) {
                    // getCurrentPrayerPoints() is raw x10 scale; convert to real points to drain
                    // 1:1 against `remaining` (also real units), then convert back for the call.
                    val currentRealPoints = p.getCurrentPrayerPoints() / 10
                    if (currentRealPoints <= 0) continue
                    val drain = currentRealPoints.coerceAtMost(remaining)
                    p.decreasePrayerPoints(drain * 10)
                    remaining -= drain
                } else {
                    val currentLevel = p.skills.getCurrentLevel(skill)
                    if (currentLevel <= 0) continue
                    val drain = currentLevel.coerceAtMost(remaining)
                    p.skills.setCurrentLevel(skill, currentLevel - drain)
                    remaining -= drain
                }
            }
        } else {
            val npc = target as Npc
            val order = intArrayOf(NpcSkills.DEFENCE, NpcSkills.STRENGTH, NpcSkills.ATTACK, NpcSkills.MAGIC, NpcSkills.RANGED)
            for (skill in order) {
                if (remaining <= 0) break
                val currentLevel = npc.stats.getCurrentLevel(skill)
                if (currentLevel <= 0) continue
                val drain = currentLevel.coerceAtMost(remaining)
                npc.stats.setCurrentLevel(skill, currentLevel - drain)
                remaining -= drain
            }
        }
    }
}
