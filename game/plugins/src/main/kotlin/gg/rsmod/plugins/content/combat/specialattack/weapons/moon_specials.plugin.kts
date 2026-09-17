package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.items.osrs.Burns
import gg.rsmod.plugins.content.items.osrs.MoonSets

/*
 * OSRS-IMPORT step 4 moon weapon specials (rules and sources in MoonSets). Each needs the full matching set; without it the energy drained
 * by this engine is returned (ADAPTED message). Looks: Eclipse plays the imported HUMAN_SPECIAL_ATLATL_01 with SPECIAL_ATLATL_CAST /
 * _IMPACT; Blood Infusion the macuahuitl attack with SPECIAL_DUAL_MACUAHUITL_SPOTANIM and varlamore_pm_macuahuitl_special_01; no
 * Jagex-named asset exists for Break Shackles (RuneLite gameval has none), it keeps the spear attack.
 */

fun refund(
    player: Player,
    energy: Int,
    message: String,
) {
    player.message(message)
    AttackTab.setEnergy(player, minOf(100, AttackTab.getEnergy(player) + energy))
}

/* Eclipse atlatl - Eclipse: 50 %, magic-based at melee distance, accuracy x1.5; consumes the target's burns (+max, +half min, cap 50). */
SpecialAttacks.register(MoonSets.ECLIPSE_ENERGY, Items.ECLIPSE_ATLATL) {
    val victim: Pawn = target
    if (!MoonSets.wearing(player, MoonSets.MoonSet.ECLIPSE)) {
        refund(player, MoonSets.ECLIPSE_ENERGY, "You need to wear the full eclipse moon armour set to do that.")
        return@register
    }
    if (player.tile.getDistance(victim.tile) > 1) {
        refund(player, MoonSets.ECLIPSE_ENERGY, "You need to be next to your target to do that.")
        return@register
    }
    player.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_SPECIAL_ATLATL_01)
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.SPECIAL_ATLATL_CAST)
    val (minHit, maxHit) = MoonSets.eclipseHit(RangedCombatFormula.getMaxHit(player, victim).toInt(), Burns.consumeRemaining(victim))
    val landHit = MagicCombatFormula.getAccuracy(player, victim, MoonSets.ECLIPSE_ACCURACY) >= world.randomDouble()
    player.dealHit(target = victim, minHit = minHit.toDouble(), maxHit = maxHit.toDouble(), landHit = landHit, delay = 1, hitType = HitType.MAGIC)
        .hit.addAction { victim.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.SPECIAL_ATLATL_IMPACT) }
}

/* Dual macuahuitl - Blood Infusion: 25 %, independent accuracy checks, max hit x1.25, costs 25 % of current Hitpoints, Bloodrager on a hit. */
SpecialAttacks.register(MoonSets.BLOOD_INFUSION_ENERGY, Items.DUAL_MACUAHUITL) {
    val victim: Pawn = target
    if (!MoonSets.wearing(player, MoonSets.MoonSet.BLOOD)) {
        refund(player, MoonSets.BLOOD_INFUSION_ENERGY, "You need to wear the full blood moon armour set to do that.")
        return@register
    }
    player.animate(CombatConfigs.getAttackAnimation(player))
    player.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.SPECIAL_DUAL_MACUAHUITL)
    player.playSound(gg.rsmod.plugins.content.items.osrs.OsrsSfx.MACUAHUITL_SPECIAL)
    val selfDamage = MoonSets.bloodInfusionSelfDamage(player.getCurrentLifepoints())
    if (selfDamage > 0) player.hit(damage = selfDamage)
    val boosted = kotlin.math.floor(MeleeCombatFormula.getMaxHit(player, victim) * MoonSets.BLOOD_INFUSION_DAMAGE).toInt()
    val (firstMax, secondMax) = MoonSets.macuahuitlSplit(boosted)
    val firstLand = MeleeCombatFormula.getAccuracy(player, victim) >= world.randomDouble()
    val secondLand = MeleeCombatFormula.getAccuracy(player, victim) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = firstMax.toDouble(), landHit = firstLand, delay = 1, hitType = HitType.MELEE)
    player.dealHit(target = victim, maxHit = secondMax.toDouble(), landHit = secondLand, delay = 2, hitType = HitType.MELEE)
    if (firstLand || secondLand) player.attr[MoonSets.BLOODRAGER] = true
}

/* Blue moon spear - Break Shackles: 50 %, removes the target's bind; +1.5 % accuracy and damage per tick removed (damage cap +112.5 %). */
SpecialAttacks.register(MoonSets.BREAK_SHACKLES_ENERGY, Items.BLUE_MOON_SPEAR) {
    val victim: Pawn = target
    if (!MoonSets.wearing(player, MoonSets.MoonSet.BLUE)) {
        refund(player, MoonSets.BREAK_SHACKLES_ENERGY, "You need to wear the full blue moon armour set to do that.")
        return@register
    }
    val ticks = if (victim.timers.has(FROZEN_TIMER)) victim.timers[FROZEN_TIMER] else 0
    victim.timers.remove(FROZEN_TIMER)
    player.animate(CombatConfigs.getAttackAnimation(player))
    val maxHit = MeleeCombatFormula.getMaxHit(player, victim, specialAttackMultiplier = MoonSets.shacklesDamage(ticks))
    val landHit = MeleeCombatFormula.getAccuracy(player, victim, MoonSets.shacklesAccuracy(ticks)) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = maxHit, landHit = landHit, delay = 1, hitType = HitType.MELEE)
}
