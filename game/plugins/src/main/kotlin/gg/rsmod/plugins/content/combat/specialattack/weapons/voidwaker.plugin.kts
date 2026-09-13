package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.Voidwaker

/**
 * OSRS-IMPORT Voidwaker - Disrupt (rules in [Voidwaker]): a guaranteed magic-type hit of 50-150% of the melee max hit that
 * grants Magic experience. Special attacks get no experience from the special framework, so it is added here in the
 * units of this server's magic strategy (0.2 Magic and 0.133 Hitpoints per damage point, NPC multiplier), as the
 * Saradomin sword special already does for its magic part. The OSRS special animation/graphic are not in 667: the normal
 * attack animation is used (ADAPTED_TO_667).
 */
SpecialAttacks.register(Voidwaker.SPECIAL_ENERGY, Items.VOIDWAKER) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    val (minimum, maximum) = Voidwaker.disruptRange(MeleeCombatFormula.getMaxHit(player, victim))
    val modDamageCap = if (victim is Npc) victim.getCurrentLifepoints() else Int.MAX_VALUE
    val pawnHit =
        player.dealHit(
            target = victim,
            minHit = Voidwaker.minHitArgument(minimum),
            maxHit = maximum.toDouble(),
            landHit = true,
            delay = 1,
            hitType = HitType.MAGIC,
        )
    val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }
    if (dealt > 0) {
        val counted = minOf(dealt, modDamageCap)
        val multiplier = if (victim is Npc) Combat.getNpcXpMultiplier(victim) else 1.0
        val bonusRate = player.addXp(Skills.MAGIC, counted * 0.2 * multiplier, checkBrawlingGloves = true)
        player.addXp(Skills.CONSTITUTION, counted * 0.133 * multiplier * bonusRate)
    }
}
