package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy

/**
 * WildyWyrm (npc 3334).
 *
 * Novite Wildywyrm.java: every attack hits every player within 15 tiles. 2/3 of the time it fires
 * a ranged volley (anim 12791, projectile 2313, 5-40 damage, 10% chance to poison for 6); otherwise a
 * magic blast (anim 12795, projectile 2315, max 17 from the Novite combat definition, impact gfx 2315)
 * that has a 1/11 chance to freeze the victim for 5 ticks with gfx 369 instead.
 */
object WildyWyrmCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.WILDYWYRM)

    private const val ANIM_RANGED = 12791
    private const val ANIM_MAGIC = 12795
    private const val PROJ_RANGED = 2313
    private const val PROJ_MAGIC = 2315
    private const val GFX_MAGIC_IMPACT = 2315
    private const val GFX_FREEZE = 369
    private const val RANGED_MIN = 5.0
    private const val RANGED_MAX = 40.0
    private const val MAGIC_MAX = 17.0
    private const val POISON_DAMAGE = 60
    private const val FREEZE_TICKS = 5
    private const val TARGET_RADIUS = 15

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) {
                val victims = playersNear(npc)
                if (victims.isEmpty()) break
                if (world.random(2) != 0) {
                    ranged(npc, victims)
                } else {
                    magic(npc, victims)
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /**
     * Every player within [TARGET_RADIUS] of the wyrm's body. N160 (2026-10-22): read from the world's player list - the old scan of
     * 31x31 chunk tiles found nobody before the chunk task had registered the player (the wyrm then left its loop at once and
     * never attacked in the real-map harness).
     */
    private fun playersNear(npc: Npc): List<Player> {
        val size = npc.getSize()
        val result = mutableListOf<Player>()
        npc.world.players.forEach { p ->
            if (p.tile.height != npc.tile.height) return@forEach
            val dx = p.tile.x - npc.tile.x
            val dz = p.tile.z - npc.tile.z
            if (dx < -TARGET_RADIUS || dx > TARGET_RADIUS + size || dz < -TARGET_RADIUS || dz > TARGET_RADIUS + size) return@forEach
            if (p.isOnline && !p.isDead() && p.lock.canBeAttacked()) result.add(p)
        }
        return result
    }

    private fun ranged(
        npc: Npc,
        victims: List<Player>,
    ) {
        val world = npc.world
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(ANIM_RANGED)
        victims.forEach { victim ->
            world.spawn(npc.createProjectile(victim, PROJ_RANGED, ProjectileType.ARROW))
            val delay = RangedCombatStrategy.getHitDelay(npc.getCentreTile(), victim.getCentreTile())
            val landHit = RangedCombatFormula.getAccuracy(npc, victim) >= world.randomDouble()
            npc.dealHit(
                target = victim,
                minHit = RANGED_MIN,
                maxHit = RANGED_MAX,
                landHit = landHit,
                delay = delay,
                hitType = HitType.RANGE,
                onHit = {
                    if (world.random(9) == 0) {
                        victim.poison(POISON_DAMAGE)
                    }
                },
            )
        }
    }

    private fun magic(
        npc: Npc,
        victims: List<Player>,
    ) {
        val world = npc.world
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        victims.forEach { victim ->
            world.spawn(npc.createProjectile(victim, PROJ_MAGIC, ProjectileType.MAGIC))
            val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(victim), victim.getCentreTile())
            val landHit = MagicCombatFormula.getAccuracy(npc, victim) >= world.randomDouble()
            npc.dealHit(
                target = victim,
                maxHit = MAGIC_MAX,
                landHit = landHit,
                delay = delay,
                hitType = HitType.MAGIC,
                onHit = { hit ->
                    val frozen = world.random(10) == 0 && victim.freeze(FREEZE_TICKS) { victim.graphic(GFX_FREEZE) }
                    if (!frozen && hit.hit.hitmarks.sumOf { mark -> mark.damage } > 0) {
                        victim.graphic(GFX_MAGIC_IMPACT)
                    }
                },
            )
        }
    }
}
