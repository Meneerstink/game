package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy

/**
 * Spinolyps (2892 / 2894 / 2896, Waterbirth Island dungeon).
 *
 * Void waterbirth_island.combat.toml: a ranged attack that looks like a Water Strike (projectile 2703,
 * impact gfx 2708, attack anim 2868, range 10), max hit 10, and every landed hit poisons the target for
 * 2 (impact_poison = 20). Novite Spinolyp.java agrees on the "magic-looking projectile with a 2-tick
 * hit delay" shape but uses the pre-2010 projectile 94 and a 30 max hit that does not match a level-76
 * npc with 100 lifepoints, so Void's numbers are used. The attack rolls against Ranged defence, so
 * Protect from Missiles blocks it.
 */
object SpinolypCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.SPINOLYP, Npcs.SPINOLYP_2894, Npcs.SPINOLYP_2896)

    private const val MAX_HIT = 10.0
    private const val ATTACK_ANIM = 2868
    private const val POISON_DAMAGE = 20
    private const val RANGE = 10

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = RANGE, projectile = true)) {
                shoot(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun shoot(
        npc: Npc,
        target: Pawn,
    ) {
        val world = npc.world
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(ATTACK_ANIM)
        world.spawn(npc.createProjectile(target, Gfx.WATER_STRIKE_PROJ, ProjectileType.MAGIC))
        val delay = RangedCombatStrategy.getHitDelay(npc.getCentreTile(), target.getCentreTile())
        val landHit = RangedCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        npc.dealHit(
            target = target,
            maxHit = MAX_HIT,
            landHit = landHit,
            delay = delay,
            hitType = HitType.RANGE,
            onHit = { hit ->
                target.graphic(Gfx.WATER_STRIKE_IMPACT, height = 60)
                if (hit.hit.hitmarks.sumOf { mark -> mark.damage } > 0) {
                    target.poison(POISON_DAMAGE)
                }
            },
        )
    }
}
