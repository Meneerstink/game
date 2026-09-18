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

// Every dark bow variant (Bows.DARK_BOWS: Items.DARK_BOW, the 667 recolours and the OSRS painted bows).
private val DARK_BOWS = (setOf(Items.DARK_BOW) + gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows.DARK_BOWS).toIntArray()
private val DRAGON_ARROWS = intArrayOf(Items.DRAGON_ARROW, Items.DRAGON_ARROW_P, Items.DRAGON_ARROW_P_11229, Items.DRAGON_FIRE_ARROWS, Items.DRAGON_FIRE_ARROWS_11222)

/* Magic shortbow / Magic shortbow (i) - Snapshot (OSRS rules in MagicShortbowSnapshot): 55 % / 50 %, two arrows, accuracy ×10/7, own max hit. */
listOf(Items.MAGIC_SHORTBOW, Items.MAGIC_SHORTBOW_I).forEach { bow ->
    SpecialAttacks.register(gg.rsmod.plugins.content.items.osrs.MagicShortbowSnapshot.energy(bow)!!, bow) {
        val victim = target
        player.animate(1074)
        // Snapshot's own double-arrow drawback: graphic 256 (Novite PlayerCombat case 861 `Graphics(256, 0, 100)`,
        // Void `snapshot_special` = 256); 250 was the crystal bow drawback.
        player.graphic(256, 100)
        player.playSound(Sfx.SNAPSHOT)
        val snapshotMax = { ammoId: Int ->
            val ammoStrength = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, ammoId).bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id]
            gg.rsmod.plugins.content.items.osrs.MagicShortbowSnapshot.maxHit(player.skills.getCurrentLevel(Skills.RANGED), ammoStrength)
        }
        val accuracy = gg.rsmod.plugins.content.items.osrs.MagicShortbowSnapshot.ACCURACY
        if (rangedShot(player, victim, accuracy = accuracy, projectileGfx = 249, maxHitOverride = snapshotMax) == -1) return@register
        rangedShot(player, victim, accuracy = accuracy, projectileGfx = 249, projectileDelayOffset = 1, maxHitOverride = snapshotMax)
    }
}

/* Magic longbow / Magic composite bow - Powershot: 35%, a single arrow that always hits. */
SpecialAttacks.register(35, Items.MAGIC_LONGBOW, Items.MAGIC_COMPOSITE_BOW) {
    val victim = target
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(250, 96)
    player.playSound(Sfx.POWERSHOT)
    rangedShot(player, victim, forceLand = true, projectileGfx = 249)
}

/*
 * Dark bow - Descent of Darkness / Descent of Dragons (OSRS Wiki "Dark bow" + wiki DPS calculator, 2026-09-17): 55 %, "require more than
 * 1 arrow equipped"; each of the two arrows makes the regular accuracy check (no accuracy boost), max hit x13/10 (x15/10 with dragon arrows),
 * a minimum of 5 (8) damage per arrow - also on a failed accuracy roll ("have to pass regular accuracy check to be able to deal more damage
 * than their minimum guaranteed hit") - and "capped at 48 damage for each arrow" (Mod Ronan, 3 March 2016; the calculator caps both
 * variants). Previously: x1.15 accuracy, no cap and 0 damage on a miss.
 */
SpecialAttacks.register(55, *DARK_BOWS) {
    val victim = target
    val fired = gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.fired(player)
    if (fired == null || fired.item.amount < 2) {
        player.message("You need at least two arrows in your quiver to use this special attack.")
        return@register
    }
    val dragon = fired.item.id in DRAGON_ARROWS
    val projectile = if (dragon) 1099 else 1101
    val impact = if (dragon) 1100 else 1103
    val multiplier = if (dragon) 1.5 else 1.3
    val minimum = if (dragon) 8.0 else 5.0
    player.animate(426)
    val maxHit = RangedCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = multiplier).toInt()
    repeat(2) { arrow ->
        // Calculator: the normal hit distribution (0..max on an accurate roll, 0 on a miss), then limited to [minimum, 48].
        val lands = RangedCombatFormula.getAccuracy(player, victim, specialAttackMultiplier = 1.0) >= world.randomDouble()
        val rolled = if (lands) world.random(maxHit.coerceAtLeast(0)) else 0
        val damage = rolled.coerceIn(minimum.toInt(), 48).toDouble()
        rangedShot(
            player,
            victim,
            forceLand = true,
            minFraction = 1.0,
            projectileGfx = projectile,
            projectileDelayOffset = arrow,
            maxHitOverride = { damage },
        )
    }
    victim.graphic(impact, 96, 60)
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
    // Shared retrieval rule (Ava's devices recover thrownaxes too - OSRS Wiki "Ava's device").
    val outcome = gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.outcome(player, world.random(99))
    if (outcome != gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.AmmoOutcome.RECOVERED) player.equipment.remove(weapon.id, 1)
    if (outcome == gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.AmmoOutcome.DROPPED) {
        world.spawn(gg.rsmod.game.model.entity.GroundItem(weapon.id, 1, previous.tile, player))
    }
}
