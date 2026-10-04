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
    // Rev-667 Sever look: animation 12031 + graphic 2118 (Novite PlayerCombat case 4587); 1872/347 were pre-667 ids.
    player.animate(12031)
    player.graphic(2118) // the sequence carries its own vorbis frame sounds (cache), so no server cue
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
    // Graphic 253's sequence 2039 carries the Shove sound (cache: synth 5274, radius 20); the server cue doubled it.
    player.graphic(253, 96)
    if (victim.getSize() > 1 || !victim.canBeStunned()) {
        player.message("That creature is too large to knock back.")
        return@register
    }
    val dx = victim.tile.x - player.tile.x
    val dz = victim.tile.z - player.tile.z
    val dest = victim.tile.transform(Integer.signum(dx), Integer.signum(dz))
    // OSRS Wiki "Dragon spear": "the brief stun causes all incoming damage to be stalled until it expires, after which all damage
    // taken is applied at once" (PvP run 2026-10-03, conform combat "stun").
    if (world.collision.isBlocked(victim.tile, gg.rsmod.game.model.Direction.between(victim.tile, dest), false)) {
        victim.stun(5)
        victim.timers[gg.rsmod.game.model.timer.DAMAGE_STALL_TIMER] = 5
        return@register
    }
    victim.stun(5) {
        victim.animate(424)
        victim.graphic(80, 96)
        if (victim is Player) victim.message("You have been stunned!")
    }
    victim.timers[gg.rsmod.game.model.timer.DAMAGE_STALL_TIMER] = 5
    victim.moveTo(dest.x, dest.z, dest.height)
}

/* Dragon 2h sword - Powerstab: 60%, hits the victim and every adjacent enemy in multi-way areas. */
SpecialAttacks.register(60, Items.DRAGON_2H_SWORD) {
    val victim = target
    player.animate(3157)
    player.graphic(1225)
    // No server cue: sequence 3157 carries its own sound (cache: vorbis 6035); the extra cue doubled the special.
    meleeHit(player, victim)
    adjacentTargets(player, victim).forEach { other -> meleeHit(player, other) }
}

/*
 * Abyssal tentacle - Binding Tentacle (OSRS Wiki "Abyssal tentacle" + "Special attacks" table, re-read 2026-09-30): 50 % energy, attack
 * roll +25 %, no damage modifier. "Regardless of whether the hit is successful, the target will be frozen for 4.8 seconds (8 ticks), and
 * there will be approximately a 50% chance of applying poison, starting at 4 damage" (severity 20). Look and sound are the whip's
 * special: the wiki's sound line is "Abyssal whip special attack" = sound 2713 (Sfx.ENERGYDRAIN, owner: 2713), and RuneLite gameval has
 * no tentacle special sequence or spotanim, so the tentacle plays its whip attack sequence (SLAYER_ABYSSAL_WHIP_ATTACK 1658) with the
 * whip special spotanim SP_ATTACK_ABYSSAL_WHIP 341 on the target, exactly as our Energy Drain does.
 */
SpecialAttacks.register(50, Items.ABYSSAL_TENTACLE) {
    val victim = target
    player.animate(gg.rsmod.plugins.content.combat.CombatConfigs.getAttackAnimation(player))
    victim.graphic(341, 96)
    player.playSound(Sfx.ENERGYDRAIN)
    meleeHit(player, victim, accuracy = 1.25)
    // The special is an attack too: one charge, hit or miss (AbyssalTentacle).
    gg.rsmod.plugins.content.items.osrs.AbyssalTentacle.afterAttack(player)
    victim.freeze(8, player)
    // The special's own 1/2 poison roll, hit or miss (WeaponPoison skips its landed-hit roll for this special).
    if (world.random(gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.TENTACLE_SPECIAL_CHANCE - 1) == 0) {
        world.queue {
            wait(1)
            if (!victim.isDead()) {
                gg.rsmod.plugins.content.mechanics.poison.Poison.poisonSeverity(victim, gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.WEAPON_POISON)
            }
        }
    }
}

/* Granite maul - Quick Smash: OSRS rules (60 % / 50 % ornate handle, instant, homing) live in GraniteMaul / granite_maul.plugin.kts. */

/* Abyssal whip - Energy Drain: 50%, +25% accuracy; steals 10 run energy from a player victim. */
// The four 667 coloured whips (15441-15444) share Energy Drain; they had no special attack (combat run 2026-09-29).
SpecialAttacks.register(
    50, Items.ABYSSAL_WHIP, Items.FROZEN_ABYSSAL_WHIP, Items.VOLCANIC_ABYSSAL_WHIP,
    Items.ABYSSAL_WHIP_15441, Items.ABYSSAL_WHIP_15442, Items.ABYSSAL_WHIP_15443, Items.ABYSSAL_WHIP_15444,
) {
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

/*
 * Abyssal vine whip - Vine Call (released 12 July 2011, RuneScape Wiki update history). Darkan's pre-EoC wiki: "uses 60% of the special
 * attack bar", "summons a vine that strikes the target 10 times (after the initial hit, which functions the same as the rest of the vine
 * hits - so 11 hits total)", each with "125% of the user's accuracy and 33% of their base strength"; in multicombat a maximum of three
 * are damaged by it; "Since you no longer wield a vine whip, the vine ignores your call." Novite 667 (PlayerCombat case 21371): the vine
 * hits once per tick (0.33 max-hit rolls). Until 2026-10-01 this was a Matrix-718 shape - one 120 % hit plus 5 x 10 flat damage every 2
 * ticks, an x10 life-point value (ROOT_CAUSES #72) that dealt 50 extra hitpoints.
 */
val VINE_HITS = 10
val VINE_ACCURACY = 1.25
val VINE_DAMAGE = 0.33
val VINE_MAX_TARGETS = 3

fun vineStrike(
    attacker: Player,
    victim: gg.rsmod.game.model.entity.Pawn,
) {
    meleeHit(attacker, victim, accuracy = VINE_ACCURACY, damage = VINE_DAMAGE)
    adjacentTargets(attacker, victim).take(VINE_MAX_TARGETS - 1).forEach { other ->
        meleeHit(attacker, other, accuracy = VINE_ACCURACY, damage = VINE_DAMAGE)
    }
}

SpecialAttacks.register(60, Items.ABYSSAL_VINE_WHIP) {
    val attacker = player
    val victim = target
    player.animate(11971)
    player.graphic(2108)
    vineStrike(attacker, victim)
    victim.graphic(2109)
    attacker.world.queue {
        repeat(VINE_HITS) {
            wait(1)
            if (victim.isDead() || attacker.isDead() || (victim is Player && !victim.isOnline) || !attacker.isOnline) return@queue
            if (attacker.getEquipment(EquipmentType.WEAPON)?.id != Items.ABYSSAL_VINE_WHIP) {
                attacker.message("Since you no longer wield a vine whip, the vine ignores your call.")
                return@queue
            }
            // Every vine hit is an attack: the victim may have walked into a guarded zone or out of reach, or teleported - the call
            // then ends (the same gate as any attack: zones, Deadman bracket, guards, view distance).
            if (!gg.rsmod.plugins.content.combat.Combat.canEngage(attacker, victim)) return@queue
            vineStrike(attacker, victim)
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

/*
 * Weaken (Darklight, Arclight, Arclight (inactive), Emberlight): 50%; accuracy against stab defence; drains Attack, Strength and Defence
 * by 5% (10% for demons, 15% with Emberlight) of the base level + 1 (OSRS Wiki item pages, 2026-09-17; rules in items/osrs/Demonbane.kt).
 * Demons are the DEMON species attribute, the same one the demonbane passive uses. Look: DARK_SPEC_PLAYER / DARK_SPEC_SPOT (2890 / 483, the
 * same sequence in 667 and OSRS) for Darklight and Arclight; Emberlight plays the imported HUMAN_WEAPON_EMBERLIGHT_01_SPEC / VFX_EMBERLIGHT_SPEC_02.
 */
SpecialAttacks.register(gg.rsmod.plugins.content.items.osrs.Weaken.ENERGY, Items.DARKLIGHT, Items.ARCLIGHT, Items.ARCLIGHT_INACTIVE, Items.EMBERLIGHT) {
    val victim = target
    val weaponId = player.getEquipment(EquipmentType.WEAPON)?.id ?: Items.DARKLIGHT
    if (weaponId == Items.EMBERLIGHT) {
        player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_WEAPON_EMBERLIGHT_01_SPEC)
        player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.EMBERLIGHT_SPEC)
    } else {
        player.animate(2890)
        player.graphic(483)
    }
    player.playSound(Sfx.DARKLIGHT_WEAKEN)
    meleeHit(player, victim, defenceStyle = gg.rsmod.game.model.combat.StyleType.STAB) {
        val demon = gg.rsmod.plugins.content.items.osrs.Demonbane.isDemon(victim)
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE).forEach { skill ->
            val base = gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport.baseLevel(victim, skill)
            drain(victim, skill, gg.rsmod.plugins.content.items.osrs.Weaken.drainAmount(base, weaponId, demon))
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
