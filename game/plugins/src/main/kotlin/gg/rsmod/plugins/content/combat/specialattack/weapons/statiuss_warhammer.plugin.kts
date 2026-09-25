package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS Wiki "Statius's warhammer" (fetched 2026-09-16): "Smash" costs 35% special attack energy and
 * "deals between 25% and 125% of the user's max hit, while lowering the target's current Defence
 * level by 75% on a successful hit" (that is the (bh) variant - audit I-04 uses the Deadman 30 %, see below); "Defence reductions stack across multiple successful hits, with
 * each subsequent reduction calculated from the temporarily lowered level"; the page states no
 * accuracy bonus, so accuracy uses the normal (unmultiplied) roll.
 *
 * Previously this rolled 0%-125% (a flat 1.25x max-hit multiplier through the standard 0..maxHit
 * roll, not a 25%..125% range), reduced Defence by 30% (the Dragon warhammer's percentage, not this
 * weapon's 75%), and boosted accuracy by an unsourced 1.25x. Fixed to match the wiki exactly.
 */
SpecialAttacks.register(35, Items.STATIUSS_WARHAMMER, Items.STATIUS_WARHAMMER_DEG) {
    val victim = target
    player.animate(Anims.STATIUSS_WARHAMMER_SPECIAL)
    player.graphic(Gfx.STATIUSS_WARHAMMER_SPECIAL, 0, 0)
    player.playSound(Sfx.SHATTER)

    val normalMax = MeleeCombatFormula.getMaxHit(player, victim)
    val minHit = normalMax * 0.25
    val maxHit = normalMax * 1.25
    val accuracy = MeleeCombatFormula.getAccuracy(player, victim)
    val landHit = accuracy >= world.randomDouble()
    val pawnHit = player.dealHit(target = victim, minHit = minHit, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }

    if (landHit && dealt > 0) {
        when (victim) {
            is Player -> {
                val level = victim.skills.getCurrentLevel(Skills.DEFENCE)
                victim.skills.setCurrentLevel(Skills.DEFENCE, level - smashDefenceReduction(level))
            }
            is Npc -> {
                val level = victim.stats.getCurrentLevel(NpcSkills.DEFENCE)
                victim.stats.setCurrentLevel(NpcSkills.DEFENCE, level - smashDefenceReduction(level))
            }
        }
    }
}

/**
 * Audit I-04 (owner: OSRS Deadman behaviour): the Deadman/LMS Statius's warhammer lowers the current Defence by 30 %, rounded down
 * (99 -> 70); the 75 % is the Bounty Hunter (bh) variant.
 */
val SMASH_DEFENCE_REDUCTION_PERCENT = 30

fun smashDefenceReduction(level: Int): Int = level * SMASH_DEFENCE_REDUCTION_PERCENT / 100
