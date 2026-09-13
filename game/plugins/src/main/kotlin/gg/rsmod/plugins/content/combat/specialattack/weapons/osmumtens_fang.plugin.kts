package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.OsmumtensFang

/**
 * OSRS-IMPORT Osmumten's fang - Eviscerate (OSRS Wiki raw wikitext + osrs-dps-calc, 2026-09-14): 25% energy, "a 50%
 * increase to accuracy for the next hit" (the fang's stab accuracy formula still applies), and the damage roll uses the
 * fang's true max hit (between 15% and 100%). The OSRS special animation/graphic are not in 667: the normal attack
 * animation is used (ADAPTED_TO_667).
 */
SpecialAttacks.register(OsmumtensFang.SPECIAL_ENERGY, Items.OSMUMTENS_FANG) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim)
    val (minimum, maximum) = OsmumtensFang.damageRange(maxHit, special = true)
    val landHit = MeleeCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = OsmumtensFang.SPECIAL_ACCURACY) >= world.randomDouble()
    player.dealHit(
        target = victim,
        minHit = OsmumtensFang.minHitArgument(minimum),
        maxHit = maximum.toDouble(),
        landHit = landHit,
        delay = 1,
        hitType = HitType.MELEE,
    )
}
