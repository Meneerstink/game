package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS-IMPORT Dragon warhammer - Smash (OSRS Wiki "Dragon warhammer", raw wikitext 2026-09-14): 50% energy, "deals 50%
 * more damage" and "lowers the target's current Defence level by 30% on successful hit"; "the hit has to deal damage";
 * "fractional defence levels reduced by the special attack will be rounded down"; reductions stack, each from the
 * current level. No accuracy change is stated. The OSRS special animation/graphic are not in 667: the normal warhammer
 * attack animation is used (ADAPTED_TO_667). The page describes monsters; players are drained the same way (SOURCE_GAP).
 */
SpecialAttacks.register(50, Items.DRAGON_WARHAMMER) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.DRAGON_WARHAMMER_SPECIAL) // OSRS DRAGON_WARHAMMER_SA_SPOTANIM (fxpilot)
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = 1.5)
    val landHit = MeleeCombatFormula.getAccuracy(player, victim) >= world.randomDouble()
    val pawnHit = player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }
    if (landHit && dealt > 0) {
        pawnHit.hit.addAction {
            when (victim) {
                is Player -> {
                    val level = victim.skills.getCurrentLevel(Skills.DEFENCE)
                    victim.skills.setCurrentLevel(Skills.DEFENCE, level - smashReduction(level))
                }
                is Npc -> {
                    val level = victim.stats.getCurrentLevel(NpcSkills.DEFENCE)
                    victim.stats.setCurrentLevel(NpcSkills.DEFENCE, level - smashReduction(level))
                }
            }
        }
    }
}

/** 30% of the current Defence level, rounded down. */
fun smashReduction(level: Int): Int = level * 3 / 10
