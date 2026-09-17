package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.Voidwaker

/**
 * OSRS-IMPORT Voidwaker - Disrupt (rules in [Voidwaker]): a guaranteed magic-type hit of 50-150% of the melee max hit that
 * grants Magic experience - now through the shared special attack experience (SpecialAttackXp: the magic strategy's damage
 * experience, 0.2 Magic and 0.133 Hitpoints per damage point with the NPC multiplier, the same values this plugin added by hand).
 * Look: the imported OSRS sequence HUMAN_SPECIAL02_VOIDWAKER with FX_VOIDWAKER02_SPECIAL / FX_VOIDWAKER_IMPACT.
 */
SpecialAttacks.register(Voidwaker.SPECIAL_ENERGY, Items.VOIDWAKER) {
    val victim = target
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_SPECIAL02_VOIDWAKER)
    // OSRS FX_VOIDWAKER02_SPECIAL on the attacker and FX_VOIDWAKER_IMPACT on the target (fxpilot); heights ADAPTED (0).
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.VOIDWAKER_SPECIAL)
    victim.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.VOIDWAKER_IMPACT)
    val (minimum, maximum) = Voidwaker.disruptRange(MeleeCombatFormula.getMaxHit(player, victim))
    player.dealHit(
        target = victim,
        minHit = Voidwaker.minHitArgument(minimum),
        maxHit = maximum.toDouble(),
        landHit = true,
        delay = 1,
        hitType = HitType.MAGIC,
    )
}
