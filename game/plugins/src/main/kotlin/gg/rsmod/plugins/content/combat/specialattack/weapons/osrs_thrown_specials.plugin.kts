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
 * OSRS-IMPORT ammo2 thrown-weapon specials (OSRS Wiki raw wikitext 2026-09-14). Looks: Duality plays the imported OSRS sequence
 * HUMAN_DRAGON_TKNIVES_SPEC (_POISON for poisoned knives) with the DRAGON_TKNIFE_TRAVEL_SPEC (_P) projectile and sound 2528
 * (Zenyte-lineage DUALITY_SOUND); the thrownaxe keeps the 667 thrown look (ADAPTED_TO_667). Thrown items follow the Rune thrownaxe Chainhit rule already in
 * this server for what lands on the floor (80 % dropped at the target, SOURCE_GAP for OSRS knife/thrownaxe retrieval odds).
 */

/** Game cycle of the last Momentum Throw ("Only one can be launched per game cycle"). */
val MOMENTUM_THROW_CYCLE = AttributeKey<Int>()

fun throwOne(
    player: Player,
    victim: Pawn,
    projectile: RangedProjectile,
    accuracy: Double,
    projectileGfx: Int = projectile.gfx,
    launchGraphic: Boolean = true,
    extraProjectileDelay: Int = 0,
): Boolean {
    val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return false
    if (launchGraphic) projectile.drawback?.let { player.graphic(it) }
    val type = projectile.type
    val lifespan = gg.rsmod.plugins.content.combat.Combat.getProjectileLifespan(player, victim.tile, type)
    world.spawn(
        player.createProjectile(
            victim, projectileGfx, type.startHeight, type.endHeight, type.angle, type.steepness,
            delay = type.delay + extraProjectileDelay, lifespan = type.delay + extraProjectileDelay + lifespan,
        ),
    )
    val delay = RangedCombatStrategy.getThrownHitDelay(player.getCentreTile(), victim.getCentreTile())
    val landHit = RangedCombatFormula.getAccuracy(player, victim, accuracy) >= world.randomDouble()
    player.dealHit(target = victim, maxHit = RangedCombatFormula.getMaxHit(player, victim), landHit = landHit, delay = delay, hitType = HitType.RANGE)
    // Shared retrieval rule: Ava's devices recover knives and thrownaxes too (OSRS Wiki "Ava's device").
    val outcome = gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.outcome(player, world.random(99))
    if (outcome != gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.AmmoOutcome.RECOVERED) player.equipment.remove(weapon.id, 1)
    if (outcome == gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.AmmoOutcome.DROPPED) {
        world.spawn(GroundItem(weapon.id, 1, victim.tile, player))
    }
    return true
}

/*
 * Dragon knife - Duality: 25 %; "throw two dragon knives at once, with each knife having its own accuracy and damage rolls".
 * Owner 2026-09-18 ("dragon knives have a weird bug when speccing"): the special replayed the NORMAL knife launch graphic twice and
 * sent both knives as the same projectile on the same client cycle, so they rendered as one flickering knife. The OSRS special
 * sequence already holds a knife in each hand (8291/8292 hand items, imported), so no normal launch graphic is sent, and the
 * second knife leaves a few client cycles after the first (offset ADAPTED - two separate flights, OSRS spotanim 699 / 1629).
 */
SpecialAttacks.register(25, *Knives.DRAGON_KNIVES.toIntArray()) {
    val victim = target
    val poisoned = player.getEquipment(EquipmentType.WEAPON)?.id != Items.DRAGON_KNIFE
    player.animate(if (poisoned) gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_DRAGON_TKNIVES_SPEC_POISON else gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_DRAGON_TKNIVES_SPEC)
    player.playSound(Sfx.CHAINSHOT)
    val travel = if (poisoned) gg.rsmod.plugins.content.items.osrs.OsrsGfx.DRAGON_TKNIFE_TRAVEL_SPEC_P else gg.rsmod.plugins.content.items.osrs.OsrsGfx.DRAGON_TKNIFE_TRAVEL_SPEC
    val projectile = if (poisoned) RangedProjectile.DRAGON_KNIFE_P else RangedProjectile.DRAGON_KNIFE
    repeat(2) { knife ->
        if (player.getEquipment(EquipmentType.WEAPON) != null) {
            throwOne(player, victim, projectile, 1.0, travel, launchGraphic = false, extraProjectileDelay = knife * 6)
        }
    }
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
