package gg.rsmod.plugins.content.areas.tzhaar.fightcaves

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
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy

/**
 * Fight Cave creature behaviour (Void donor tzhaar_fight_cave combat/anims/gfx data, 634 ids
 * identical in 667):
 *
 * - Tz-Kih: melee that drains prayer by the damage dealt.
 * - Tz-Kek: melee; recoils 10 damage on melee attackers; splits into two on death (FightCaves).
 * - Tok-Xil: melee in range, otherwise ranged (anim 9243, projectile 1616).
 * - Yt-MejKot: hard melee; heals weakened nearby TzHaar (anim 9249, gfx 444).
 * - Ket-Zek: melee in range, otherwise magic (anim 9266, gfx 1622 / proj 1623 / impact 1624).
 * - TzTok-Jad: melee (9277) when adjacent, otherwise ranged stomp (9276, gfx 1625, impact 451)
 *   or magic (9300, gfx 1626, proj 1627). Both deliberately telegraphed with a long delay so the
 *   matching protection prayer negates them.
 * - Yt-HurKot: heals Jad; attacks the player only when attacked.
 */
object FightCaveCombatScripts {
    const val GFX_HEAL = 444

    private fun melee(npc: Npc, target: Pawn, max: Double, style: StyleType = StyleType.CRUSH, delay: Int = 1, onHit: (Int) -> Unit = {}) {
        npc.prepareAttack(CombatClass.MELEE, style, WeaponStyle.AGGRESSIVE)
        npc.animate(npc.combatDef.attackAnimation)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = max, landHit = land, delay = delay, hitType = HitType.MELEE, onHit = { hit ->
            onHit(hit.hit.hitmarks.sumOf { it.damage })
        })
    }

    object TzKih : CombatScript() {
        override val ids = intArrayOf(Npcs.TZKIH_2734, Npcs.TZKIH_2735)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                    val victim = target
                    melee(npc, victim, 4.0, StyleType.STAB) { damage ->
                        if (victim is Player) victim.alterPrayerPoints(-(damage + 1))
                    }


                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }
    }

    object TokXil : CombatScript() {
        override val ids = intArrayOf(Npcs.TOKXIL_2739, Npcs.TOKXIL_2740)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            val world = npc.world
            while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
                npc.facePawn(target)
                val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
                if (distance <= 1 && world.random(1) == 0) {
                    melee(npc, target, 13.0)
                } else if (npc.moveToAttackRange(it, target, distance = 14, projectile = true)) {
                    npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
                    npc.animate(9243)
                    val projectile = npc.createProjectile(target, 1616, ProjectileType.ARROW)
                    world.spawn(projectile)
                    val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
                    val land = RangedCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
                    npc.dealHit(target = target, maxHit = 14.0, landHit = land, delay = delay, hitType = HitType.RANGE)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }
    }

    object YtMejKot : CombatScript() {
        override val ids = intArrayOf(Npcs.YTMEJKOT, Npcs.YTMEJKOT_2742)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            val world = npc.world
            while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
                npc.facePawn(target)
                val weakened = nearbyWeakened(npc)
                if (weakened != null) {
                    npc.animate(9249)
                    weakened.graphic(GFX_HEAL)
                    weakened.setCurrentLifepoints((weakened.getCurrentLifepoints() + 100).coerceAtMost(weakened.getMaximumLifepoints()))
                } else if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                    melee(npc, target, 25.0)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        private fun nearbyWeakened(npc: Npc): Npc? {
            val session = FightCaves.sessionFor(npc) ?: return null
            return session.npcs.firstOrNull { other ->
                other !== npc && other.isSpawned() && !other.isDead() &&
                    other.tile.isWithinRadius(npc.tile, 2) &&
                    other.getCurrentLifepoints() < other.getMaximumLifepoints() / 2
            } ?: npc.takeIf { it.getCurrentLifepoints() < it.getMaximumLifepoints() / 2 }
        }
    }

    object KetZek : CombatScript() {
        override val ids = intArrayOf(Npcs.KETZEK, Npcs.KETZEK_2744)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            val world = npc.world
            while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
                npc.facePawn(target)
                val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
                if (distance <= 1 && world.random(1) == 0) {
                    melee(npc, target, 54.0, StyleType.STAB)
                } else if (npc.moveToAttackRange(it, target, distance = 14, projectile = true)) {
                    npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                    npc.animate(9266)
                    npc.graphic(1622)
                    val projectile = npc.createProjectile(target, 1623, ProjectileType.MAGIC)
                    world.spawn(projectile)
                    val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
                    val land = MagicCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
                    target.graphic(Graphic(1624, 96, projectile.lifespan))
                    npc.dealHit(target = target, maxHit = 49.0, landHit = land, delay = delay, hitType = HitType.MAGIC)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }
    }

    object TzTokJad : CombatScript() {
        override val ids = intArrayOf(Npcs.TZTOKJAD)

        private const val MAX_HIT = 97.0

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            val world = npc.world
            while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
                npc.facePawn(target)
                FightCaves.sessionFor(npc)?.let { FightCaves.checkJadHealers(it) }
                val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
                when {
                    distance <= 1 && world.random(2) == 0 -> melee(npc, target, MAX_HIT, StyleType.STAB, delay = 1)
                    world.random(1) == 0 -> rangedAttack(npc, target)
                    else -> magicAttack(npc, target)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        private fun rangedAttack(npc: Npc, target: Pawn) {
            npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            npc.animate(9276)
            npc.graphic(1625)
            val world = npc.world
            world.queue {
                wait(3)
                if (target.isDead()) return@queue
                target.graphic(Graphic(451, 52, 0))
                val land = RangedCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
                npc.dealHit(target = target, maxHit = MAX_HIT, landHit = land, delay = 1, hitType = HitType.RANGE)
            }
        }

        private fun magicAttack(npc: Npc, target: Pawn) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(9300)
            npc.graphic(1626)
            val world = npc.world
            world.queue {
                wait(2)
                if (target.isDead()) return@queue
                val projectile = npc.createProjectile(target, 1627, ProjectileType.MAGIC)
                world.spawn(projectile)
                val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
                val land = MagicCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
                npc.dealHit(target = target, maxHit = MAX_HIT - 2, landHit = land, delay = delay, hitType = HitType.MAGIC)
            }
        }
    }

    object YtHurKot : CombatScript() {
        override val ids = intArrayOf(Npcs.YTHURKOT)

        fun startHealing(healer: Npc, jad: Npc) {
            val world = healer.world
            healer.followRadius = 6
            world.queue {
                while (healer.isSpawned() && !healer.isDead() && jad.isSpawned() && !jad.isDead()) {
                    if (healer.getCombatTarget() == null) {
                        if (!healer.tile.isWithinRadius(jad.getCentreTile(), 4)) {
                            healer.walkTo(this, world.findRandomTileAround(jad.getCentreTile(), radius = 3) ?: jad.tile)
                        } else if (jad.getCurrentLifepoints() < jad.getMaximumLifepoints()) {
                            healer.faceTile(jad.getCentreTile())
                            healer.animate(9254)
                            jad.graphic(GFX_HEAL)
                            jad.setCurrentLifepoints((jad.getCurrentLifepoints() + 50).coerceAtMost(jad.getMaximumLifepoints()))
                        }
                    }
                    wait(4)
                }
            }
        }

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                    melee(npc, target, 14.0)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }
    }
}
