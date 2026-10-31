package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.attr.HAMSTRING
import gg.rsmod.game.model.timer.HAMSTRING_TIMER
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile


on_timer(HAMSTRING_TIMER) {
    player.attr.remove(HAMSTRING)
}

/**
 * Hamstring, Deadman Mode variant (owner 2026-10-01: adopt it). OSRS Wiki "Morrigan's throwing axe (Deadman
 * Mode)": "consumes 25% of the player's special attack energy", "deals between 20% and 120% of the user's max hit", a landed special
 * makes the attacker's next attack 1 tick faster (12 March 2025 update: "Successfully landing the attack now reduces the player's attack
 * delay by 1 tick for the next attack"), against players "For the next minute, your run energy will drain 6x faster".
 */
val HAMSTRING_ENERGY = 25
val HAMSTRING_MIN = 0.2
val HAMSTRING_MAX = 1.2

SpecialAttacks.register(HAMSTRING_ENERGY, Items.MORRIGANS_THROWING_AXE) {
    player.animate(Anims.MORRIGANS_THROWING_AXE_SPECIAL)
    player.graphic(Gfx.MORRIGANS_THROWING_AXE_SPECIAL, 0, 0)
    val proj = player.createProjectile(
        target.tile,
        RangedProjectile.MORRIGANS_THROWING_AXE.gfx,
        ProjectileType.MORRIGANS_THROWN.startHeight,
        ProjectileType.MORRIGANS_THROWN.endHeight,
        ProjectileType.MORRIGANS_THROWN.angle,
        ProjectileType.MORRIGANS_THROWN.steepness,
        34,
        ProjectileType.MORRIGANS_THROWN.calculateLife(player.tile.getDistance(target.tile))
    )
    player.world.spawn(proj)
    player.playSound(Sfx.THROWINGAXE)

    val baseMax = RangedCombatFormula.getMaxHit(player, target, specialAttackMultiplier = 1.0)
    val accuracy = RangedCombatFormula.getAccuracy(player, target, specialAttackMultiplier = 1.0)
    val landHit = accuracy >= world.randomDouble()
    val delay = RangedCombatStrategy.getHitDelay(player.tile, target.tile)
    player.dealHit(
        target = target, minHit = baseMax * HAMSTRING_MIN, maxHit = baseMax * HAMSTRING_MAX, landHit = landHit, delay = delay,
        hitType = HitType.RANGE,
    )
    if (landHit) player.attr[gg.rsmod.plugins.content.combat.Combat.NEXT_ATTACK_ONE_TICK_FASTER] = true

    if (target is Player) {
        target.attr[HAMSTRING] = true
        target.timers[HAMSTRING_TIMER] = 100
        // OSRS Wiki "Morrigan's throwing axe (Deadman Mode)": the target is told.
        (target as Player).message("You've been hamstrung! For the next minute, your run energy will drain 6x faster.")
    }
}
