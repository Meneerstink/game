package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks

/**
 * OSRS Wiki "Vesta's longsword" (fetched 2026-09-16): "Feint" costs 25% special attack energy,
 * "deals between 20% and 120% of the user's max hit"; accuracy is "rolled against 25% of the
 * opponent's defence, using the attack bonus of the player's currently selected combat style
 * against the target's stab defence" - the target's defence roll is reduced to 25%, not a
 * multiplier on the attacker's own accuracy roll.
 *
 * Previously this rolled 0%-120% (a flat 1.2x max-hit multiplier through the standard 0..maxHit
 * roll, not a 20%..120% range) and boosted the attacker's own accuracy by an unsourced 1.75x
 * instead of reducing the target's defence. Fixed to match the wiki exactly.
 */
SpecialAttacks.register(25, Items.VESTAS_LONGSWORD, Items.VESTAS_LONGSWORD_DEG) {
    player.animate(Anims.VESTAS_LONGSWORD_SPECIAL)
    player.playSound(Sfx.STABSWORD_STAB)

    val normalMax = MeleeCombatFormula.getMaxHit(player, target)
    val minHit = normalMax * 0.2
    val maxHit = normalMax * 1.2
    val accuracy = MeleeCombatFormula.getAccuracyAgainstReducedDefence(player, target, defenceMultiplier = 0.25)
    val landHit = accuracy >= world.randomDouble()
    val delay = 1
    player.dealHit(target = target, minHit = minHit, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.MELEE)
}
