package gg.rsmod.plugins.content.combat.specialattack

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.mechanics.pvp.AreaState

/**
 * Shared helpers for the bulk special-attack import (2026-09-10). Mechanics are ported from the
 * 2009scape special handlers (content/global/handlers/item/equipment/special) with 2011 wiki
 * values; these helpers keep every weapon plugin down to its own numbers and visuals.
 */
object SpecialAttackSupport {
    /**
     * Rolls and deals one melee hit. [accuracy]/[damage] are the special multipliers; [minFraction]
     * sets a guaranteed floor as a fraction of the max hit. Returns the damage dealt in real
     * hitpoints.
     */
    fun meleeHit(
        player: Player,
        target: Pawn,
        accuracy: Double = 1.0,
        damage: Double = 1.0,
        delay: Int = 1,
        minFraction: Double = 0.0,
        forceLand: Boolean = false,
        /** Forced defence style of the accuracy roll (OSRS DPS calculator `getNPCDefenceRoll`); `null` = the current style. */
        defenceStyle: gg.rsmod.game.model.combat.StyleType? = null,
        onHit: (Int) -> Unit = {},
    ): Int {
        val world = player.world
        val maxHit = MeleeCombatFormula.getMaxHit(player, target, specialAttackMultiplier = damage)
        val acc =
            if (defenceStyle != null) {
                MeleeCombatFormula.getAccuracyAgainst(player, target, accuracy, defenceStyle)
            } else {
                MeleeCombatFormula.getAccuracy(player, target, specialAttackMultiplier = accuracy)
            }
        val landHit = forceLand || acc >= world.randomDouble()
        val pawnHit =
            player.dealHit(
                target = target,
                minHit = (maxHit * minFraction).coerceAtLeast(0.1),
                maxHit = maxHit.coerceAtLeast(0.2),
                landHit = landHit,
                delay = delay,
                hitType = HitType.MELEE,
            )
        val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }
        if (landHit && dealt > 0) {
            pawnHit.hit.addAction { onHit(dealt) }
        }
        return if (landHit) dealt else 0
    }

    /**
     * Fires the equipped ammunition at [target] as a special shot: spawns the ammo projectile,
     * consumes one piece of ammo (dropping it under the target with the normal 80% chance) and
     * deals a ranged hit. Returns -1 when no usable ammo is equipped.
     */
    fun rangedShot(
        player: Player,
        target: Pawn,
        accuracy: Double = 1.0,
        damage: Double = 1.0,
        delay: Int = -1,
        minFraction: Double = 0.0,
        forceLand: Boolean = false,
        projectileGfx: Int = -1,
        projectileDelayOffset: Int = 0,
        consumeAmmo: Boolean = true,
        /** Non-null for crossbow specials that interact with enchanted bolt effects (Armadyl Eye, Evoke). */
        boltSpecial: gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts.Special? = null,
        /** A special with its own max hit formula (Snapshot) replaces the formula's max hit; [damage] is then ignored. */
        maxHitOverride: ((ammoId: Int) -> Double)? = null,
        onHit: (Int) -> Unit = {},
    ): Int {
        val world = player.world
        // Ammo slot first, then a worn Dizana's quiver's stored ammo (RangedAmmo).
        val fired = gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.fired(player)
        val ammo = fired?.item
        val ammoProjectile = ammo?.let { a -> RangedProjectile.values.firstOrNull { a.id in it.items } }
        if (ammo == null || ammoProjectile == null) {
            player.message("You have no ammo left in your quiver.")
            return -1
        }
        val gfx = if (projectileGfx > -1) projectileGfx else ammoProjectile.gfx
        val projectile = player.createProjectile(target, gfx, ammoProjectile.type)
        ammoProjectile.drawback?.let { player.graphic(it) }
        world.spawn(projectile)
        val hitDelay = if (delay > -1) delay else 1 + Math.ceil(player.tile.getDistance(target.tile) * 0.3).toInt() + projectileDelayOffset

        if (consumeAmmo) {
            // Same retrieval rule as a normal shot (Ava's devices / upgraded quiver): specials used to ignore the devices.
            val outcome = gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.outcome(player, world.random(99))
            if (outcome != gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.AmmoOutcome.RECOVERED) {
                gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.consume(player, fired!!, 1)
            }
            if (outcome == gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.AmmoOutcome.DROPPED) {
                world.spawn(GroundItem(ammo.id, 1, target.tile, player))
            }
        }

        val maxHit = maxHitOverride?.invoke(ammo.id) ?: RangedCombatFormula.getMaxHit(player, target, specialAttackMultiplier = damage)
        val acc = RangedCombatFormula.getAccuracy(player, target, specialAttackMultiplier = accuracy)
        val rolledHit = forceLand || acc >= world.randomDouble()
        val shot =
            boltSpecial?.let {
                gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts
                    .resolve(player, target, ammo.id, rolledHit, maxHit, it, world.randomDouble())
            }
        val landHit = shot?.landHit ?: rolledHit
        val pawnHit =
            player.dealHit(
                target = target,
                minHit = if (shot?.bolt != null) shot.minHit else (maxHit * minFraction).coerceAtLeast(0.1),
                maxHit = if (shot?.bolt != null) shot.maxHit else maxHit.coerceAtLeast(0.2),
                landHit = landHit,
                delay = hitDelay,
                hitType = HitType.RANGE,
                bonusDamage = shot?.bonusDamage ?: 0,
            )
        val dealt = pawnHit.hit.hitmarks.sumOf { it.damage }
        if (gg.rsmod.plugins.content.items.osrs.DizanasQuiver.applies(player)) {
            gg.rsmod.plugins.content.items.osrs.DizanasQuiver.afterShot(player)
        }
        shot?.bolt?.let { bolt ->
            pawnHit.hit.addAction {
                gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts.afterHit(bolt, player, target, dealt)
            }
        }
        if (landHit && dealt > 0) {
            pawnHit.hit.addAction { onHit(dealt) }
        }
        return if (landHit) dealt else 0
    }

    /**
     * Every other attackable pawn within one tile of [target] that the caster may hit, for the
     * multi-way specials (Dragon 2h, Dragon halberd, Rune thrownaxe). Empty outside multi zones.
     */
    fun adjacentTargets(
        player: Player,
        target: Pawn,
        radius: Int = 1,
    ): List<Pawn> {
        val world = player.world
        if (!player.tile.isMulti(world) || !target.tile.isMulti(world)) return emptyList()
        val result = mutableListOf<Pawn>()
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val tile = target.tile.transform(x, z)
                val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
                val candidates: List<Pawn> =
                    if (target is Player) {
                        chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT)
                    } else {
                        chunk.getEntities<Npc>(tile, EntityType.NPC)
                    }
                candidates.forEach { other ->
                    if (other !== target && other !== player && other !in result && canSplash(player, other)) {
                        result.add(other)
                    }
                }
            }
        }
        return result
    }

    private fun canSplash(
        player: Player,
        other: Pawn,
    ): Boolean {
        if (other.isDead() || other.invisible || !other.tile.isMulti(player.world)) return false
        return when (other) {
            is Npc -> other.isSpawned() && other.def.isAttackable() && other.combatDef.lifepoints != -1
            is Player -> {
                // AreaState.canPlayersFight already enforces the shared +/-12 combat-level range
                // (Deadman PvP guards plan, 2026-09-16); no separate check needed here.
                if (!other.isOnline || !other.lock.canBeAttacked() || !AreaState.canPlayersFight(player, other)) return false
                true
            }
            else -> false
        }
    }

    /** Drains [amount] current levels of [skill] (player skill index) on any pawn, never below zero. */
    fun drain(
        target: Pawn,
        skill: Int,
        amount: Int,
    ) {
        if (amount <= 0) return
        when (target) {
            is Player -> {
                val cur = target.skills.getCurrentLevel(skill)
                target.skills.setCurrentLevel(skill, (cur - amount).coerceAtLeast(0))
            }
            is Npc -> {
                val index =
                    when (skill) {
                        Skills.ATTACK -> NpcSkills.ATTACK
                        Skills.STRENGTH -> NpcSkills.STRENGTH
                        Skills.DEFENCE -> NpcSkills.DEFENCE
                        Skills.MAGIC -> NpcSkills.MAGIC
                        Skills.RANGED -> NpcSkills.RANGED
                        else -> return
                    }
                val cur = target.stats.getCurrentLevel(index)
                target.stats.setCurrentLevel(index, (cur - amount).coerceAtLeast(0))
            }
        }
    }

    /** Base (unboosted, undrained) level of [skill] (player skill index) for any pawn. */
    fun baseLevel(
        target: Pawn,
        skill: Int,
    ): Int =
        when (target) {
            is Player -> target.skills.getMaxLevel(skill)
            is Npc ->
                when (skill) {
                    Skills.ATTACK -> target.stats.getMaxLevel(NpcSkills.ATTACK)
                    Skills.STRENGTH -> target.stats.getMaxLevel(NpcSkills.STRENGTH)
                    Skills.DEFENCE -> target.stats.getMaxLevel(NpcSkills.DEFENCE)
                    Skills.MAGIC -> target.stats.getMaxLevel(NpcSkills.MAGIC)
                    Skills.RANGED -> target.stats.getMaxLevel(NpcSkills.RANGED)
                    else -> 0
                }
            else -> 0
        }

    /** Current level of [skill] (player skill index) for any pawn. */
    fun currentLevel(
        target: Pawn,
        skill: Int,
    ): Int =
        when (target) {
            is Player -> target.skills.getCurrentLevel(skill)
            is Npc ->
                when (skill) {
                    Skills.ATTACK -> target.stats.getCurrentLevel(NpcSkills.ATTACK)
                    Skills.STRENGTH -> target.stats.getCurrentLevel(NpcSkills.STRENGTH)
                    Skills.DEFENCE -> target.stats.getCurrentLevel(NpcSkills.DEFENCE)
                    Skills.MAGIC -> target.stats.getCurrentLevel(NpcSkills.MAGIC)
                    Skills.RANGED -> target.stats.getCurrentLevel(NpcSkills.RANGED)
                    else -> 0
                }
            else -> 0
        }
}
