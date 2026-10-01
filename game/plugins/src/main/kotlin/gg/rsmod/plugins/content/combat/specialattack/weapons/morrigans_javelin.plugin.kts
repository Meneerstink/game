package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.attr.PHANTOM_STRIKE_BLEED
import gg.rsmod.game.model.timer.PHANTOM_STRIKE_TIMER
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile

/**
 * Phantom Strike (OSRS Wiki "Morrigan's javelin"): "Every tick (0.6 seconds) after the special attack is performed, the opponent
 * will take an additional 10 hitpoints of damage until 75% of the damage dealt by the special attack has been delivered";
 * "This special attack has no effect on NPCs". Until 2026-10-01 it bled 50 per 3 ticks up to 100 % - an RS2011 value in the old
 * x10 life-point units, so after the 1:1 hitpoint migration the whole bleed landed in one hit - re-armed itself forever and,
 * on an npc target, ran this player-only handler.
 */
val PHANTOM_STRIKE_TICK_DAMAGE = 10

on_timer(PHANTOM_STRIKE_TIMER) {
    val bleedLeft = player.attr.getOrDefault(PHANTOM_STRIKE_BLEED, 0)
    if (bleedLeft <= 0 || player.isDead()) {
        player.attr.remove(PHANTOM_STRIKE_BLEED)
        return@on_timer
    }
    val damage = PHANTOM_STRIKE_TICK_DAMAGE.coerceAtMost(bleedLeft)
    player.hit(damage, HitType.REGULAR_HIT)
    val leftNow = bleedLeft - damage
    if (leftNow > 0) {
        player.attr[PHANTOM_STRIKE_BLEED] = leftNow
        player.timers[PHANTOM_STRIKE_TIMER] = 1
    } else {
        player.attr.remove(PHANTOM_STRIKE_BLEED)
    }
}


SpecialAttacks.register(50, *RangedProjectile.MORRIGANS_JAVELIN.items.toIntArray()) {
    player.animate(Anims.MORRIGANS_JAVELIN_SPECIAL)
    val proj = player.createProjectile(player.tile, target.tile, RangedProjectile.MORRIGANS_JAVELIN.gfx, ProjectileType
        .MORRIGANS_JAVELIN)
    player.world.spawn(proj)
    player.playSound(Sfx.JAVELIN)

    val maxHit = RangedCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.0)
    val accuracy = RangedCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 1.0)
    val landHit = accuracy >= world.randomDouble()
    val delay = 1
    val hit = player.dealHit(target = target, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType
        .RANGE)

    val damage = hit.hit.hitmarks.firstOrNull()?.damage ?: 0
    if (hit.landed && target is Player && damage > 0) {
        target.attr[PHANTOM_STRIKE_BLEED] = damage * 3 / 4
        // The bleed starts the tick after the special hit lands.
        target.timers[PHANTOM_STRIKE_TIMER] = delay + 1
    }
}
