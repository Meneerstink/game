package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.adjacentTargets
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.currentLevel
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.drain
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.rangedShot
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * Bulk ranged special-attack import (2026-09-10).
 *
 * Energy costs, multipliers, graphics and projectiles ported from the 2009scape handlers
 * (Snapshot, Powershot, DescentOfDarkness, Seercull, Snipe, Chainhit) and Matrix 718 for Zanik's
 * crossbow; effects follow the 2011 RuneScape Wiki.
 */

private val DARK_BOWS = intArrayOf(Items.DARK_BOW, Items.DARK_BOW_15701, Items.DARK_BOW_15702, Items.DARK_BOW_15703, Items.DARK_BOW_15704)
private val DRAGON_ARROWS = intArrayOf(Items.DRAGON_ARROW, Items.DRAGON_ARROW_P, Items.DRAGON_ARROW_P_11229, Items.DRAGON_FIRE_ARROWS, Items.DRAGON_FIRE_ARROWS_11222)

/* Magic shortbow - Snapshot: 55%, fires two arrows in quick succession at +43% accuracy... wiki: two arrows, slightly reduced accuracy (x0.9). */
SpecialAttacks.register(55, Items.MAGIC_SHORTBOW) {
    val victim = target
    player.animate(1074)
    player.graphic(250, 96)
    player.playSound(Sfx.SNAPSHOT)
    if (rangedShot(player, victim, accuracy = 0.9, projectileGfx = 249) == -1) return@register
    rangedShot(player, victim, accuracy = 0.9, projectileGfx = 249, projectileDelayOffset = 1)
}

/* Magic longbow / Magic composite bow - Powershot: 35%, a single arrow that always hits. */
SpecialAttacks.register(35, Items.MAGIC_LONGBOW, Items.MAGIC_COMPOSITE_BOW) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(250, 96)
    player.playSound(Sfx.POWERSHOT)
    rangedShot(player, victim, forceLand = true, projectileGfx = 249)
}

/* Dark bow - Descent of Darkness: 55%, two arrows at x1.3 damage (min 5 each), x1.5 and min 8 with dragon arrows. */
SpecialAttacks.register(55, *DARK_BOWS) {
    val victim = target
    val ammo = player.getEquipment(EquipmentType.AMMO)
    val dragon = ammo != null && ammo.id in DRAGON_ARROWS
    val projectile = if (dragon) 1099 else 1101
    val impact = if (dragon) 1100 else 1103
    val multiplier = if (dragon) 1.5 else 1.3
    val minimum = if (dragon) 8.0 else 5.0
    player.animate(426)
    val maxHit = RangedCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = multiplier)
    val minFraction = (minimum / maxHit.coerceAtLeast(minimum)).coerceAtMost(1.0)
    if (rangedShot(player, victim, accuracy = 1.15, damage = multiplier, minFraction = minFraction, projectileGfx = projectile) == -1) return@register
    victim.graphic(impact, 96, 60)
    rangedShot(player, victim, accuracy = 1.15, damage = multiplier, minFraction = minFraction, projectileGfx = projectile, projectileDelayOffset = 1)
}

/* Seercull - Soulshot: 100%, an arrow that always hits and drains the victim's Magic by the damage dealt (in hitpoints). */
SpecialAttacks.register(100, Items.SEERCULL) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(472, 96)
    player.playSound(Sfx.SOULSHOT)
    rangedShot(player, victim, forceLand = true, projectileGfx = 473) { dealt ->
        victim.graphic(474)
        drain(victim, Skills.MAGIC, dealt)
    }
}

/* Dorgeshuun crossbow - Snipe: 75%, guaranteed hit when the victim isn't fighting you; lowers Defence by the damage dealt. */
SpecialAttacks.register(75, Items.DORGESHUUN_CBOW) {
    val victim = target
    player.animate(4230)
    player.playSound(Sfx.DTTD_BONE_CROSSBOW_SA)
    val unaware = victim.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() !== player
    rangedShot(player, victim, forceLand = unaware, projectileGfx = 698) { dealt -> drain(victim, Skills.DEFENCE, dealt) }
}

/* Zanik's crossbow - Defiance: 50%; the bolt deals 30-150 extra damage on top of a normal hit and is stronger against prayer users. */
SpecialAttacks.register(50, Items.ZANIKS_CROSSBOW) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(1714)
    val praying = victim is Player && (Prayers.isActive(victim, gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_FROM_MISSILES) || victim.prayerIcon != -1)
    val bonus = if (praying) 1.5 else 1.0
    rangedShot(player, victim, damage = bonus, minFraction = 0.2, projectileGfx = 2001)
}

/* Rune thrownaxe - Chainhit: 10%; the axe ricochets to adjacent targets in multi-way areas. */
SpecialAttacks.register(10, Items.RUNE_THROWNAXE) {
    val victim = target
    val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return@register
    player.animate(1068)
    player.graphic(257, 96)
    player.playSound(Sfx.CHAINSHOT)
    world.spawn(player.createProjectile(victim, 258, ProjectileType.THROWN))
    val maxHit = RangedCombatFormula.getMaxHit(player, victim)
    var landHit = RangedCombatFormula.getAccuracy(player, victim) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 2, hitType = HitType.RANGE)
    var previous = victim
    var delay = 3
    for (other in adjacentTargets(player, victim).take(3)) {
        world.spawn(previous.createProjectile(other, 258, ProjectileType.THROWN))
        landHit = RangedCombatFormula.getAccuracy(player, other) >= world.randomDouble()
        player.dealHit(target = other, maxHit = maxHit, landHit = landHit, delay = delay, hitType = HitType.RANGE)
        previous = other
        delay++
    }
    player.equipment.remove(weapon.id, 1)
    if (world.random(99) >= 20) {
        world.spawn(gg.rsmod.game.model.entity.GroundItem(weapon.id, 1, previous.tile, player))
    }
}
