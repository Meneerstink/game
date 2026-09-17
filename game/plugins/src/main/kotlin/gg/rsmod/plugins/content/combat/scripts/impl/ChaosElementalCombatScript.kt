package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * Chaos Elemental (npc 3200).
 *
 * Ported from the 2009scape ChaosElementalNPC handler (same-era ids): cast animation 3148;
 * Discord attack gfx 556 / projectile 557 (damage, up to 28); Confusion gfx 553 / projectile 554
 * teleports the target up to 10 tiles away; Madness gfx 550 / projectile 551 unequips a random
 * worn item into the inventory. Attack pick 3/5 discord, 1/5 confusion, 1/5 madness.
 */
object ChaosElementalCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.CHAOS_ELEMENTAL)

    private const val MAX_HIT = 28.0

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) {
                when (world.random(4)) {
                    0, 1, 2 -> discord(npc, target)
                    3 -> confusion(npc, target)
                    else -> madness(npc, target)
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }

        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun cast(
        npc: Npc,
        target: Pawn,
        gfx: Int,
        projectile: Int,
    ): Int {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(3148)
        npc.graphic(gfx)
        npc.world.spawn(npc.createProjectile(target, projectile, ProjectileType.MAGIC))
        return MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
    }

    private fun discord(
        npc: Npc,
        target: Pawn,
    ) {
        val delay = cast(npc, target, 556, 557)
        val landHit = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        if (target is Player) target.playSound(Sfx.CHAOS_ELEMENTAL_HIT, delay = delay * 30)
        npc.dealHit(target = target, maxHit = MAX_HIT, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
    }

    private fun confusion(
        npc: Npc,
        target: Pawn,
    ) {
        val delay = cast(npc, target, 553, 554)
        val world = npc.world
        world.queue {
            wait(delay)
            if (target.isDead() || (target is Player && !target.isOnline) || !target.tile.isWithinRadius(npc.tile, 16)) return@queue
            val dest = world.findRandomTileAround(target.tile, radius = 10) ?: return@queue
            if (target is Player) {
                target.stopMovement()
                target.moveTo(dest.x, dest.z, dest.height)
                target.message("The Chaos Elemental's magic hurls you across the ground.")
            }
        }
    }

    private fun madness(
        npc: Npc,
        target: Pawn,
    ) {
        val delay = cast(npc, target, 550, 551)
        val world = npc.world
        if (target !is Player) return
        target.playSound(Sfx.CHAOS_ELEMENTAL_MADNESS_IMPACT, delay = delay * 30)
        world.queue {
            wait(delay)
            if (target.isDead() || !target.isOnline || target.inventory.freeSlotCount == 0) return@queue
            val wornSlots = (0 until target.equipment.capacity).filter { target.equipment[it] != null }
            if (wornSlots.isEmpty()) return@queue
            val slot = wornSlots.random()
            if (EquipAction.unequip(target, slot) == EquipAction.Result.SUCCESS) {
                target.message("The Chaos Elemental's madness forces you to remove an item.")
            }
        }
    }
}
