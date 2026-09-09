package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
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
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * Corporeal Beast (npc 8133).
 *
 * Ported from the Matrix 718 CorporealBeastCombat script (same-era ids: melee 10057/10058,
 * magic 10410, stomp 10496 + gfx 1834, projectiles 1825 spiky ball, 1823 drain ball, 1824
 * scatter ball, dark energy core npc 8127) with the 2011 wiki damage caps: melee 51, magic
 * 65, stat-drain ball 55, scatter 35 per splash. Attack pick: stomp when a player stands under
 * it, else 2/5 melee (magic if out of reach), 1/5 spiky ball, 1/5 drain ball, 1/5 scatter ball.
 * A Dark energy core is summoned on average once every 40 attacks and drains 5-13 life points
 * a tick from whoever it sits on, healing the beast for the same amount.
 */
object CorporealBeastCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.CORPOREAL_BEAST)

    private const val MELEE_MAX = 51.0
    private const val MAGIC_MAX = 65.0
    private const val DRAIN_MAX = 55.0
    private const val SCATTER_MAX = 35.0

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val nearby = nearbyPlayers(npc)
            if (world.random(40) == 0) {
                spawnDarkEnergyCore(npc, nearby)
            }

            val underneath = nearby.filter { isUnder(npc, it) }
            if (underneath.isNotEmpty()) {
                stomp(npc, underneath)
            } else {
                val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
                val inMelee = distance <= 1
                when (if (inMelee) world.random(4) else 2 + world.random(2)) {
                    0, 1 -> melee(npc, target, world)
                    2 -> spikyBall(npc, target)
                    3 -> drainBall(npc, target, world)
                    else -> scatterBall(npc, target, nearby, world)
                }
                if (!inMelee) {
                    npc.moveToAttackRange(it, target, distance = 8, projectile = true)
                }
            }

            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }

        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun isUnder(
        npc: Npc,
        pawn: Pawn,
    ): Boolean {
        val size = npc.getSize()
        val dx = pawn.tile.x - npc.tile.x
        val dz = pawn.tile.z - npc.tile.z
        return dx in 0 until size && dz in 0 until size
    }

    fun nearbyPlayers(
        npc: Npc,
        radius: Int = 12,
    ): List<Player> {
        val world = npc.world
        val result = mutableListOf<Player>()
        for (x in -radius..radius + npc.getSize()) {
            for (z in -radius..radius + npc.getSize()) {
                val tile = npc.tile.transform(x, z)
                val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
                chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { p ->
                    if (p.isOnline && !p.isDead() && p !in result) result.add(p)
                }
            }
        }
        return result
    }

    private fun stomp(
        npc: Npc,
        victims: List<Player>,
    ) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
        npc.animate(10496)
        npc.graphic(1834)
        victims.forEach { victim ->
            val landHit = MeleeCombatFormula.getAccuracy(npc, victim) >= npc.world.randomDouble()
            npc.dealHit(target = victim, maxHit = MELEE_MAX, landHit = landHit, delay = 0, hitType = HitType.MELEE)
        }
    }

    private fun melee(
        npc: Npc,
        target: Pawn,
        world: World,
    ) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
        npc.animate(if (world.random(1) == 0) 10057 else 10058)
        val landHit = MeleeCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        npc.dealHit(target = target, maxHit = MELEE_MAX, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    }

    private fun spikyBall(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(10410)
        val projectile = npc.createProjectile(target, 1825, ProjectileType.MAGIC)
        npc.world.spawn(projectile)
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val landHit = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAGIC_MAX, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
    }

    private fun drainBall(
        npc: Npc,
        target: Pawn,
        world: World,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(10410)
        world.spawn(npc.createProjectile(target, 1823, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val landHit = MagicCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
        val hit = npc.dealHit(target = target, maxHit = DRAIN_MAX, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
        if (target is Player) {
            hit.hit.addAction {
                when (world.random(2)) {
                    0 -> {
                        target.skills.decrementCurrentLevel(Skills.MAGIC, 1 + world.random(4), capped = false)
                        target.message("Your Magic has been slightly drained!")
                    }
                    1 -> {
                        target.skills.decrementCurrentLevel(Skills.SUMMONING, 1 + world.random(4), capped = false)
                        target.message("Your Summoning has been slightly drained!")
                    }
                    else -> {
                        target.setCurrentPrayerPoints((target.getCurrentPrayerPoints() - (100 + world.random(400))).coerceAtLeast(0))
                        target.message("Your Prayer has been slightly drained!")
                    }
                }
            }
        }
    }

    private fun scatterBall(
        npc: Npc,
        target: Pawn,
        nearby: List<Player>,
        world: World,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(10410)
        val centre = target.tile
        world.spawn(npc.createProjectile(centre, 1824, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), centre)
        world.queue {
            wait(delay)
            repeat(6) {
                val splash = Tile(centre.x - 3 + world.random(6), centre.z - 3 + world.random(6), centre.height)
                if (world.collision.isClipped(splash)) return@repeat
                world.spawn(npc.createProjectile(centre, splash, 1824, ProjectileType.MAGIC))
                nearby.forEach { victim ->
                    if (victim.tile.getDistance(splash) <= 1 && !victim.isDead()) {
                        val landHit = MagicCombatFormula.getAccuracy(npc, victim) >= world.randomDouble()
                        npc.dealHit(target = victim, maxHit = SCATTER_MAX, landHit = landHit, delay = 1, hitType = HitType.MAGIC)
                    }
                }
            }
        }
    }

    /** One core at a time; it sits on a random player, drains them and heals the beast, and fades after 30 ticks. */
    private fun spawnDarkEnergyCore(
        npc: Npc,
        nearby: List<Player>,
    ) {
        val world = npc.world
        if (nearby.isEmpty() || npc.attr[CORE_ACTIVE] == true) return
        val victim = nearby.random()
        val core = Npc(Npcs.DARK_ENERGY_CORE_8127, Tile(victim.tile), world)
        core.respawns = false
        if (!world.spawn(core)) return
        npc.attr[CORE_ACTIVE] = true
        world.queue {
            var ticks = 0
            while (ticks++ < 30 && !npc.isDead() && core.isSpawned()) {
                nearbyPlayers(npc).filter { it.tile.getDistance(core.tile) <= 1 }.forEach { p ->
                    val damage = 50 + world.random(80)
                    p.hit(damage = damage, type = HitType.REGULAR_HIT.id)
                    npc.setCurrentLifepoints(minOf(npc.getCurrentLifepoints() + damage, npc.getMaximumLifepoints()))
                }
                if (ticks % 5 == 0) {
                    val next = nearbyPlayers(npc).randomOrNull()
                    if (next != null) core.moveTo(next.tile.x, next.tile.z, next.tile.height)
                }
                wait(1)
            }
            if (core.isSpawned()) world.remove(core)
            npc.attr.remove(CORE_ACTIVE)
        }
    }

    private val CORE_ACTIVE = gg.rsmod.game.model.attr.AttributeKey<Boolean>()
}
