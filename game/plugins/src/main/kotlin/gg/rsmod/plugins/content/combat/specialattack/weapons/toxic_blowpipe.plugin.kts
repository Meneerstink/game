package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.Blowpipe
import gg.rsmod.plugins.content.items.osrs.BlowpipeCombat

/**
 * OSRS-IMPORT Toxic blowpipe - Toxic Siphon (OSRS Wiki "Toxic blowpipe", 2026-09-14): 50% energy, accuracy +100% and
 * damage +50%, heals half the damage dealt (rounded down); hit delay 2 ticks at distance 4 or 5 ("Hit delay"). The OSRS
 * special graphics are not in 667: the dart throw animation and dart projectile are used (ADAPTED_TO_667).
 */
SpecialAttacks.register(Blowpipe.SPECIAL_ENERGY, Items.TOXIC_BLOWPIPE) {
    val pipe = player.getEquipment(EquipmentType.WEAPON) ?: return@register
    if (!Blowpipe.canFire(pipe)) {
        player.message(if (Blowpipe.scales(pipe) <= 0) Blowpipe.NO_SCALES_MESSAGE else Blowpipe.NO_DARTS_MESSAGE)
        return@register
    }
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.TOXIC_BLOWPIPE_SPECIALATTACK) // OSRS TOXIC_BLOWPIPE_SPECIALATTACK (fxpilot)
    if (!BlowpipeCombat.fire(player, victim)) return@register
    val delay = BlowpipeCombat.hitDelay(player.tile.getDistance(victim.tile), special = true)
    val maxHit = RangedCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = Blowpipe.SIPHON_DAMAGE)
    val landHit = RangedCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = Blowpipe.SIPHON_ACCURACY) >= world.randomDouble()
    val pawnHit = player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.RANGE)
    val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }
    pawnHit.hit.addAction {
        val heal = Blowpipe.siphonHeal(dealt)
        if (heal > 0) player.heal(heal)
        BlowpipeCombat.rollVenom(player, victim)
    }
}
