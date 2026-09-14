package gg.rsmod.plugins.content.combat.specialattack.weapons


import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.adjacentTargets
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.currentLevel
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.drain
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.meleeHit
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * Bulk melee special-attack import (2026-09-10).
 *
 * Energy costs, accuracy/damage multipliers, animations and graphics are ported from the
 * 2009scape special handlers (Shatter, Rampage, Sweep, Shove, Powerstab, Sever, QuickSmash,
 * EnergyDrain, AncientMace, Weaken, Backstab, Impale) and the Matrix 718 PlayerCombat cases for
 * the post-2009 weapons (Korasi's sword, Barrelchest anchor, Abyssal vine whip, Brine sabre).
 * Effects follow the 2011 RuneScape Wiki descriptions.
 */

/* Dragon mace - Shatter: 25%, +25% accuracy, +50% damage. */
SpecialAttacks.register(25, Items.DRAGON_MACE) {
    val victim = target
    player.animate(1060)
    player.graphic(251, 96)
    player.playSound(Sfx.SHATTER)
    meleeHit(player, victim, accuracy = 1.25, damage = 1.5)
}

/* Dragon scimitar - Sever: 55%, +25% accuracy; a landed hit turns off the victim's protection prayers for 5 seconds. */
SpecialAttacks.register(55, Items.DRAGON_SCIMITAR, Items.DRAGON_SCIMITAR_OR) {
    val victim = target
    player.animate(1872)
    player.graphic(347, 96)
    player.playSound(Sfx.SEVER)
    meleeHit(player, victim, accuracy = 1.25) {
        if (victim is Player) {
            Prayers.deactivate(victim, Prayer.PROTECT_FROM_MAGIC)
            Prayers.deactivate(victim, Prayer.PROTECT_FROM_MISSILES)
            Prayers.deactivate(victim, Prayer.PROTECT_FROM_MELEE)
            AncientCurses.deactivateCurse(victim, gg.rsmod.plugins.content.mechanics.prayer.AncientCurse.DEFLECT_MAGIC)
            AncientCurses.deactivateCurse(victim, gg.rsmod.plugins.content.mechanics.prayer.AncientCurse.DEFLECT_MISSILES)
            AncientCurses.deactivateCurse(victim, gg.rsmod.plugins.content.mechanics.prayer.AncientCurse.DEFLECT_MELEE)
            Prayers.disableOverheads(victim, 8)
            victim.message("You have been injured!")
        }
    }
}

/* Dragon halberd - Sweep: 30%, +10% damage; a second hit lands on large (size >= 2) targets, and adjacent targets in multi. */
SpecialAttacks.register(30, Items.DRAGON_HALBERD) {
    val victim = target
    player.animate(1203)
    player.graphic(282, 96)
    player.playSound(Sfx.HALBERD_SWIPE)
    meleeHit(player, victim, damage = 1.1, delay = 1)
    if (victim.getSize() >= 2) {
        meleeHit(player, victim, accuracy = 0.75, damage = 1.1, delay = 2)
    }
    adjacentTargets(player, victim).forEach { other -> meleeHit(player, other, accuracy = 0.75, damage = 1.1, delay = 1) }
}

/* Dragon spear / Zamorakian spear - Shove: 25%, no damage; pushes the victim back one tile and stuns it for 3 seconds. */
SpecialAttacks.register(
    25,
    Items.DRAGON_SPEAR, Items.DRAGON_SPEAR_P, Items.DRAGON_SPEAR_KP, Items.DRAGON_SPEAR_P_5716, Items.DRAGON_SPEAR_P_5730,
    Items.ZAMORAKIAN_SPEAR,
) {
    val victim = target
    player.animate(1064)
    player.graphic(253, 96)
    player.playSound(Sfx.SHOVE)
    if (victim.getSize() > 1 || victim.timers.has(gg.rsmod.game.model.timer.STUN_TIMER)) {
        player.message("That creature is too large to knock back.")
        return@register
    }
    val dx = victim.tile.x - player.tile.x
    val dz = victim.tile.z - player.tile.z
    val dest = victim.tile.transform(Integer.signum(dx), Integer.signum(dz))
    if (world.collision.isBlocked(victim.tile, gg.rsmod.game.model.Direction.between(victim.tile, dest), false)) {
        victim.stun(5)
        return@register
    }
    victim.stun(5) {
        victim.animate(424)
        victim.graphic(80, 96)
        if (victim is Player) victim.message("You have been stunned!")
    }
    victim.moveTo(dest.x, dest.z, dest.height)
}

/* Dragon 2h sword - Powerstab: 60%, hits the victim and every adjacent enemy in multi-way areas. */
SpecialAttacks.register(60, Items.DRAGON_2H_SWORD) {
    val victim = target
    player.animate(3157)
    player.graphic(1225)
    player.playSound(Sfx.DRAGON_AXE_THUNDER)
    meleeHit(player, victim)
    adjacentTargets(player, victim).forEach { other -> meleeHit(player, other) }
}

/* Granite maul - Quick Smash: OSRS rules (60 % / 50 % ornate handle, instant, homing) live in GraniteMaul / granite_maul.plugin.kts. */

/* Abyssal whip - Energy Drain: 50%, +25% accuracy; steals 10 run energy from a player victim. */
SpecialAttacks.register(50, Items.ABYSSAL_WHIP) {
    val victim = target
    player.animate(1658)
    victim.graphic(341, 96)
    player.playSound(Sfx.ENERGYDRAIN)
    meleeHit(player, victim, accuracy = 1.25) {
        if (victim is Player) {
            val stolen = minOf(10.0, victim.runEnergy)
            victim.runEnergy -= stolen
            victim.sendRunEnergy(victim.runEnergy.toInt())
            player.runEnergy = minOf(100.0, player.runEnergy + stolen)
            player.sendRunEnergy(player.runEnergy.toInt())
        }
    }
}

/* Abyssal vine whip - Vine Call: 60%, +20% damage plus a vine that hits the victim for 10 damage every 2 ticks (up to 5 times). */
SpecialAttacks.register(60, Items.ABYSSAL_VINE_WHIP) {
    val victim = target
    player.animate(11971)
    player.graphic(2108)
    meleeHit(player, victim, damage = 1.2) {
        victim.graphic(2109)
        player.world.queue {
            repeat(5) {
                wait(2)
                if (victim.isDead() || player.isDead()) return@queue
                victim.hit(damage = 10, type = HitType.MELEE.id)
                victim.damageMap.add(player, 10)
            }
        }
    }
}

/* Ancient mace - Favour of the War God: 100%, +10% accuracy; restores prayer points equal to the damage dealt. */
SpecialAttacks.register(100, Items.ANCIENT_MACE) {
    val victim = target
    player.animate(6147)
    player.graphic(1052)
    player.playSound(Sfx.GOBLIN_MACE)
    meleeHit(player, victim, accuracy = 1.1) { dealt ->
        player.restorePrayer(dealt, capValue = dealt)
        if (victim is Player) {
            victim.setCurrentPrayerPoints((victim.getCurrentPrayerPoints() - dealt).coerceAtLeast(0))
        }
    }
}

/* Darklight - Weaken: 50%; drains the victim's Attack, Strength and Defence by 5% (10% for demons). */
SpecialAttacks.register(50, Items.DARKLIGHT) {
    val victim = target
    player.animate(2890)
    player.graphic(483)
    player.playSound(Sfx.DARKLIGHT_WEAKEN)
    meleeHit(player, victim) {
        val demon = victim is Npc && victim.def.name.contains("demon", ignoreCase = true)
        val percent = if (demon) 0.10 else 0.05
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE).forEach { skill ->
            drain(victim, skill, (currentLevel(victim, skill) * percent).toInt() + 1)
        }
    }
}

/* Barrelchest anchor - Sunder: 50%, +10% damage; lowers the victim's Defence/Attack/Ranged/Magic by 10% of the damage. */
SpecialAttacks.register(50, Items.BARRELCHEST_ANCHOR) {
    val victim = target
    player.animate(5870)
    player.graphic(1027)
    meleeHit(player, victim, damage = 1.1) { dealt ->
        val amount = dealt / 10
        listOf(Skills.DEFENCE, Skills.ATTACK, Skills.RANGED, Skills.MAGIC).forEach { drain(victim, it, amount) }
    }
}

/* Bone dagger - Backstab: 75%; guaranteed to hit when the victim isn't fighting you, lowers Defence by the damage dealt. */
SpecialAttacks.register(75, Items.BONE_DAGGER, Items.BONE_DAGGER_P, Items.BONE_DAGGER_P_8876, Items.BONE_DAGGER_P_8878) {
    val victim = target
    player.animate(4198)
    player.graphic(704)
    player.playSound(Sfx.DTTD_BONE_DAGGER_STAB)
    val unaware = victim.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() !== player
    meleeHit(player, victim, forceLand = unaware) { dealt -> drain(victim, Skills.DEFENCE, dealt) }
}

/* Rune claws - Impale: 25%, +10% accuracy and damage. */
SpecialAttacks.register(25, Items.RUNE_CLAWS) {
    val victim = target
    player.animate(923)
    player.graphic(274, 96)
    player.playSound(Sfx.IMPALE)
    meleeHit(player, victim, accuracy = 1.1, damage = 1.1)
}

/* Korasi's sword - Disrupt: 60%; a magic-based hit of 50%-150% of the melee max, hits adjacent enemies in multi. */
SpecialAttacks.register(60, Items.KORASIS_SWORD_19780, Items.KORASIS_SWORD_19784) {
    val victim = target
    player.animate(14788)
    player.graphic(1729)
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim)
    val accuracy = MagicCombatFormula.getAccuracy(player, victim)
    val landHit = accuracy >= world.randomDouble()
    player.dealHit(target = victim, minHit = maxHit * 0.5, maxHit = maxHit * 1.5, landHit = landHit, delay = 1, hitType = HitType.MAGIC)
    adjacentTargets(player, victim).forEach { other ->
        val lands = MagicCombatFormula.getAccuracy(player, other) >= world.randomDouble()
        player.dealHit(target = other, minHit = maxHit * 0.5, maxHit = maxHit * 1.5, landHit = lands, delay = 1, hitType = HitType.MAGIC)
    }
}

/* Brine sabre - Liquefy: 75%, +25% accuracy and damage; on a hit boosts Attack, Strength and Defence by 25% of the damage. */
SpecialAttacks.register(75, Items.BRINE_SABRE) {
    val victim = target
    player.animate(6118)
    player.graphic(1048)
    meleeHit(player, victim, accuracy = 1.25, damage = 1.25) { dealt ->
        val boost = dealt / 4
        if (boost > 0) {
            listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE).forEach { skill ->
                player.skills.alterCurrentLevel(skill, boost, capValue = boost)
            }
        }
    }
}

/* Staff of light: OSRS Power of Death, registered with the staff of the dead family in items/osrs/staff_of_the_dead.plugin.kts. */
