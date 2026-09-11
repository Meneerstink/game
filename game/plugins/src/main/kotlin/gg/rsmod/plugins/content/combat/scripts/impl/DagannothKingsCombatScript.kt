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
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy

/**
 * Dagannoth Kings (Waterbirth Island dungeon). Each king uses a single style at fixed max hits
 * (2011: Supreme ranged 30, Prime magic 61 (58 in later 2011 balancing, Novite/Matrix uses 61),
 * Rex melee 28); the combat triangle is expressed through their very high defensive bonuses in
 * the bulk definitions rather than hard immunities.
 *
 * Projectiles from the Novite donor DagganothSupreme script: Supreme 475, Prime 2707.
 */
object DagannothKingsCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.DAGANNOTH_SUPREME, Npcs.DAGANNOTH_PRIME, Npcs.DAGANNOTH_REX)

    private const val SUPREME_MAX = 30.0
    private const val PRIME_MAX = 61.0
    private const val REX_MAX = 28.0

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            when (npc.id) {
                Npcs.DAGANNOTH_SUPREME -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) ranged(npc, target)
                Npcs.DAGANNOTH_PRIME -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) magic(npc, target)
                else -> if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) melee(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun ranged(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(npc.combatDef.attackAnimation)
        val world = npc.world
        world.spawn(npc.createProjectile(target, 475, ProjectileType.ARROW))
        val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val land = RangedCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        npc.dealHit(target = target, maxHit = SUPREME_MAX, landHit = land, delay = delay, hitType = HitType.RANGE)
    }

    private fun magic(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(npc.combatDef.attackAnimation)
        val world = npc.world
        world.spawn(npc.createProjectile(target, 2707, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val land = MagicCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        npc.dealHit(target = target, maxHit = PRIME_MAX, landHit = land, delay = delay, hitType = HitType.MAGIC)
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = REX_MAX, landHit = land, delay = 1, hitType = HitType.MELEE)
    }
}
