package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.Graphic
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
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula
import gg.rsmod.plugins.content.combat.formula.DragonfireTable
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * Metallic dragons (bronze/iron/steel/mithril): melee when adjacent, otherwise long-range
 * dragonfire (Novite MetalDragonCombat: anim 13160, projectile 393). Void's rev-667 sound map
 * uses 3750 for close breath and 3749 for the long-range metal fireball. Dragonfire max hit 50
 * (bronze/iron/steel) and 60 for mithril, the 2011 wiki values; the shared [DragonfireFormula]
 * applies shield/prayer/potion reduction.
 */
object MetalDragonCombatScript : CombatScript() {
    override val ids = intArrayOf(
        Npcs.BRONZE_DRAGON, Npcs.IRON_DRAGON, Npcs.STEEL_DRAGON, Npcs.STEEL_DRAGON_3590, Npcs.MITHRIL_DRAGON, Npcs.MITHRIL_DRAGON_8424,
        Npcs.IRON_DRAGON_10776, Npcs.IRON_DRAGON_10777, Npcs.IRON_DRAGON_10778, Npcs.IRON_DRAGON_10779, Npcs.IRON_DRAGON_10780, Npcs.IRON_DRAGON_10781,
    )

    private const val ANIM_RANGED_FIRE = 13160
    private const val PROJ_FIRE = 393

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            if (distance <= 1 && world.random(1) == 0) {
                melee(npc, target)
            } else if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) {
                fire(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        if (target is Player) target.playSound(Sfx.DRAGON_ATTACK)
        npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
    }

    private fun fire(npc: Npc, target: Pawn) {
        // RCV-012: dragonfire max now comes from the OSRS metallic table (DragonfireTable).
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_RANGED_FIRE, priority = true)
        val projectile = npc.createProjectile(target, PROJ_FIRE, ProjectileType.FIERY_BREATH)
        npc.world.spawn(projectile)
        target.graphic(Graphic(Gfx.RESET, 110, projectile.lifespan))
        if (target is Player) target.playSound(Sfx.DRAGONSLAYER_DRAGONBALL, delay = 2)
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        npc.dealHit(target = target, formula = DragonfireFormula(DragonfireTable.Type.METALLIC), delay = delay)
    }
}

/**
 * Frost dragons (Asgarnian Ice Dungeon, 2011): melee, close dragonfire (anim 13152, gfx 2465),
 * ranged dragonfire (anim 13155, projectile 393), ranged ice arrows (projectile 369) and the
 * frost-breath orb (projectile 2707). Dragonfire max 65; ice arrows and orb max 25. Ids from the
 * Novite donor FrostDragonCombat.
 */
object FrostDragonCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.FROST_DRAGON)

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            when (world.random(3)) {
                0 -> if (distance <= 1) melee(npc, target) else if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) rangedFire(npc, target)
                1 -> if (distance <= 1) closeFire(npc, target) else if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) rangedFire(npc, target)
                2 -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) iceArrows(npc, target)
                else -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) frostOrb(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        if (target is Player) target.playSound(Sfx.DRAGON_ATTACK)
        npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
    }

    private fun closeFire(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(13152, priority = true)
        npc.graphic(2465)
        if (target is Player) target.playSound(Sfx.DRAGONSLAYER_DRAGONBREATH, delay = 2)
        npc.dealHit(target = target, formula = DragonfireFormula(DragonfireTable.Type.METALLIC), delay = 2)
    }

    private fun rangedFire(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(13155, priority = true)
        val projectile = npc.createProjectile(target, 393, ProjectileType.FIERY_BREATH)
        npc.world.spawn(projectile)
        target.graphic(Graphic(Gfx.RESET, 110, projectile.lifespan))
        if (target is Player) target.playSound(Sfx.DRAGONSLAYER_DRAGONBREATH, delay = 2)
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        npc.dealHit(target = target, formula = DragonfireFormula(DragonfireTable.Type.METALLIC), delay = delay)
    }

    private fun iceArrows(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(13155)
        val world = npc.world
        val projectile = npc.createProjectile(target, 369, ProjectileType.ARROW)
        world.spawn(projectile)
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        npc.dealHit(target = target, maxHit = 25.0, landHit = world.random(3) != 0, delay = delay, hitType = HitType.RANGE)
    }

    private fun frostOrb(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(13155)
        val world = npc.world
        val projectile = npc.createProjectile(target, 2707, ProjectileType.MAGIC)
        world.spawn(projectile)
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        npc.dealHit(target = target, maxHit = 25.0, landHit = world.random(3) != 0, delay = delay, hitType = HitType.RANGE)
    }
}

/**
 * Skeletal wyverns (Asgarnian Ice Dungeon): icy breath (anim 1592, gfx 501/502, up to 60 or 15
 * with an elemental/mind/dragonfire shield, 1/10 freeze), ranged (anim 1593, gfx 499) and melee
 * (anims 1589/1590). Ported from the Novite donor SkeletalWyvernCombat.
 */
object SkeletalWyvernCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.SKELETAL_WYVERN, 3069, 3070, 3071)

    private val BREATH_SHIELDS = intArrayOf(2890, 9731, 20436, 20438, 18691, 11283, 11284, 11285, 11286, 11287, 11288, 11289, 11290, 11291, 11292, 11293, 11294, 11295, 11296, 11297, 11298, 11299)

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            when (if (distance <= 1) world.random(2) else world.random(1)) {
                0 -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) breath(npc, target)
                1 -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) ranged(npc, target)
                else -> melee(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun hasShield(target: Pawn): Boolean =
        target is Player && BREATH_SHIELDS.any { target.hasEquipped(gg.rsmod.plugins.api.EquipmentType.SHIELD, it) }

    private fun breath(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(1592)
        npc.graphic(501)
        target.graphic(502)
        val world = npc.world
        val shielded = hasShield(target)
        if (target is Player) {
            target.filterableMessage(if (shielded) "Your shield absorbs most of the icy breath!" else "You are frozen by the wyvern's icy breath!")
        }
        if (world.random(9) == 0) target.freeze(8)
        npc.dealHit(target = target, maxHit = if (shielded) 15.0 else 60.0, landHit = true, delay = 1, hitType = HitType.REGULAR_HIT)
    }

    private fun ranged(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(1593)
        npc.graphic(499)
        npc.dealHit(target = target, formula = gg.rsmod.plugins.content.combat.formula.RangedCombatFormula, delay = 1, type = HitType.RANGE)
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(1589 + npc.world.random(1))
        npc.dealHit(target = target, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE)
    }
}
