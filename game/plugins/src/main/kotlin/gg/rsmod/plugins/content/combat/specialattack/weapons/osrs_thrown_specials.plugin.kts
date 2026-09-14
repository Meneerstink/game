package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Knives

/*
 * OSRS-IMPORT ammo2 thrown-weapon specials (OSRS Wiki raw wikitext 2026-09-14). Looks: the 667 thrown attack animation and the
 * 667 rune knife / rune thrownaxe projectiles (ADAPTED_TO_667). Thrown items follow the Rune thrownaxe Chainhit rule already in
 * this server for what lands on the floor (80 % dropped at the target, SOURCE_GAP for OSRS knife/thrownaxe retrieval odds).
 */

/** Game cycle of the last Momentum Throw ("Only one can be launched per game cycle"). */
val MOMENTUM_THROW_CYCLE = AttributeKey<Int>()

fun throwOne(
    player: Player,
    victim: Pawn,
    projectile: RangedProjectile,
    accuracy: Double,
): Boolean {
    val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return false
    projectile.drawback?.let { player.graphic(it) }
    val flight = player.createProjectile(victim, projectile.gfx, projectile.type)
    world.spawn(flight)
    val delay = RangedCombatStrategy.getHitDelay(player.getCentreTile(), victim.getCentreTile())
    val landHit = RangedCombatFormula.getAccuracy(player, victim, accuracy) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = RangedCombatFormula.getMaxHit(player, victim), landHit = landHit, delay = delay, hitType = HitType.RANGE)
    player.equipment.remove(weapon.id, 1)
    if (world.random(99) >= 20) world.spawn(GroundItem(weapon.id, 1, victim.tile, player))
    return true
}

/* Dragon knife - Duality: 25 %; "throw two dragon knives at once, with each knife having its own accuracy and damage rolls". */
SpecialAttacks.register(25, *Knives.DRAGON_KNIVES.toIntArray()) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.playSound(Sfx.THROWN)
    repeat(2) { if (player.getEquipment(EquipmentType.WEAPON) != null) throwOne(player, victim, RangedProjectile.DRAGON_KNIFE, 1.0) }
}

/*
 * Dragon thrownaxe - Momentum Throw: 25 %; "improves the player's accuracy by 25% along with guaranteeing that the player will
 * attack on the next game tick"; "using the special attack counts as a normal attack, resetting the player's attack delay to 5
 * ticks, or 4 using rapid"; "Only one can be launched per game cycle." Instant special: it needs a current combat target in range
 * (without one no energy is used - ADAPTED).
 */
SpecialAttacks.registerInstant(25, Items.DRAGON_THROWNAXE) { p ->
    val victim = p.attr[COMBAT_TARGET_FOCUS_ATTR]?.get() ?: return@registerInstant false
    if (p.attr[MOMENTUM_THROW_CYCLE] == world.currentCycle) return@registerInstant false
    if (!Combat.canAttack(p, victim, RangedCombatStrategy)) return@registerInstant false
    p.attr[MOMENTUM_THROW_CYCLE] = world.currentCycle
    p.animate(CombatConfigs.getAttackAnimation(p))
    p.playSound(Sfx.THROWN)
    if (!throwOne(p, victim, RangedProjectile.DRAGON_THROWNAXE, 1.25)) return@registerInstant false
    Combat.postAttack(p, victim)
    true
}
