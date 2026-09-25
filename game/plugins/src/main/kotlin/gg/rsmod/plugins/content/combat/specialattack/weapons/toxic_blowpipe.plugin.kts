package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.Blowpipe
import gg.rsmod.plugins.content.items.osrs.BlowpipeCombat

/**
 * OSRS-IMPORT Toxic blowpipe - Toxic Siphon (OSRS Wiki "Toxic blowpipe", 2026-09-14): 50% energy, accuracy +100% and
 * damage +50%, heals half the damage dealt (rounded down); hit delay 2 ticks at distance 4 or 5 ("Hit delay"). Look: the
 * imported OSRS blowpipe sequences (OsrsWeaponLooks), TOXIC_BLOWPIPE_SPECIALATTACK and the Zenyte-lineage siphon sounds 2696 + 800.
 */
/* Rosewood blowpipe - Rapid Burst: 25 %, "shoot two darts in rapid succession" (normal accuracy and damage since 22 July 2026). */
SpecialAttacks.register(Blowpipe.RAPID_BURST_ENERGY, Items.ROSEWOOD_BLOWPIPE) {
    val victim = target
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.ROSEWOOD_BLOWPIPE_SPECIAL_ATTACK)
    val delay = BlowpipeCombat.hitDelay(player.tile.getDistance(victim.tile), special = false)
    repeat(2) { index ->
        val pipe = player.getEquipment(EquipmentType.WEAPON) ?: return@register
        if (!Blowpipe.canFire(pipe)) {
            player.message(Blowpipe.noChargesMessage(pipe))
            // Audit C-15: an empty pipe before the first dart costs nothing; after the first dart the special did happen.
            if (index == 0) specialFailed()
            return@register
        }
        if (!BlowpipeCombat.fire(player, victim)) {
            if (index == 0) specialFailed()
            return@register
        }
        val landHit = RangedCombatFormula.getAccuracy(player, victim) >= world.randomDouble()
        // ADAPTED: the second dart lands one tick after the first (spacing unsourced).
        player.dealHit(target = victim, maxHit = RangedCombatFormula.getMaxHit(player, victim), landHit = landHit, delay = delay + index, hitType = HitType.RANGE)
    }
}

SpecialAttacks.register(Blowpipe.SPECIAL_ENERGY, Items.TOXIC_BLOWPIPE, Items.BLAZING_BLOWPIPE) {
    val pipe = player.getEquipment(EquipmentType.WEAPON) ?: return@register
    if (!Blowpipe.canFire(pipe)) {
        player.message(Blowpipe.noChargesMessage(pipe))
        specialFailed() // Audit C-15: an empty blowpipe costs no energy and starts no attack delay.
        return@register
    }
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.TOXIC_BLOWPIPE_SPECIALATTACK) // OSRS TOXIC_BLOWPIPE_SPECIALATTACK (fxpilot)
    player.playSound(Sfx.DART)
    player.playSound(Sfx.SNAKE_HIT, delay = 32)
    if (!BlowpipeCombat.fire(player, victim)) {
        specialFailed()
        return@register
    }
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
