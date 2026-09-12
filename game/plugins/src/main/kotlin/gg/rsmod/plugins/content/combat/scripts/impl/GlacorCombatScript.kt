package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.attr.AttributeKey
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
 * Glacors (Ritual of Mahjarrat, Nov 2011). Ranged (projectile 962, max 29.4), magic (projectile
 * 634, max 26.4, 1/6 freeze 10s), melee (anim 9955, max 35) and the icicle-fall (projectile 2314,
 * gfx 2315) that halves the life points of anyone still standing on the marked tile. At half
 * health the glacor summons three glacytes (unstable 14302, sapping 14303, enduring 14304) and
 * cannot be damaged until they die; killing the enduring glacyte last makes the glacor share
 * its resistance, killing the sapping last drains prayer on every hit, unstable last explodes.
 *
 * Ids from the Novite donor Glacor/GlacorCombat/Glacyte/GlacyteType; stats from Matrix 718.
 */
object GlacorCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.GLACOR)

    val GLACYTES = AttributeKey<MutableList<Npc>>()
    val GLACYTES_SPAWNED = AttributeKey<Boolean>()
    val LAST_GLACYTE = AttributeKey<Int>()
    val GLACYTE_PARENT = AttributeKey<java.lang.ref.WeakReference<Npc>>()
    val UNSTABLE_TICKS = AttributeKey<Int>()

    const val ANIM_MELEE = 9955
    const val ANIM_RANGED = 9968
    const val ANIM_MAGIC = 9967
    const val PROJ_RANGED = 962
    const val PROJ_MAGIC = 634
    const val PROJ_ICICLE = 2314
    const val GFX_ICICLE = 2315
    const val GFX_FREEZE = 369
    const val GFX_EXPLODE = 956

    // Novite stores these caps in the historical x10 hitmark unit; this runtime is 1:1.
    private const val GLACOR_DAMAGE_CAP = 250
    private const val GLACYTE_DAMAGE_CAP = 90

    val GLACYTE_IDS = intArrayOf(Npcs.UNSTABLE_GLACYTE, Npcs.SAPPING_GLACYTE, Npcs.ENDURING_GLACYTE)

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        installHitModifier(npc)
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            checkGlacytes(npc, target)
            if (npc.attr[GLACYTES]?.any { it.isSpawned() && !it.isDead() } == true) {
                it.wait(2)
                target = npc.getCombatTarget() ?: break
                continue
            }
            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            when (world.random(4)) {
                0, 1, 2 -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) distanced(npc, target)
                3 -> if (distance <= 1) melee(npc, target) else if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) distanced(npc, target)
                else -> if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) icicleFall(npc, target)
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun installHitModifier(npc: Npc) {
        if (npc.hitModifier != null) return
        npc.hitModifier = { hit ->
            val glacytes = npc.attr[GLACYTES]
            if (glacytes != null && glacytes.any { it.isSpawned() && !it.isDead() }) {
                hit.hitmarks.forEach { it.damage = 0 }
            } else if (npc.attr[LAST_GLACYTE] == Npcs.ENDURING_GLACYTE) {
                hit.hitmarks.forEach { it.damage = (it.damage * 0.6).toInt() }
            }
            hit.hitmarks.forEach { if (it.damage > GLACOR_DAMAGE_CAP) it.damage = GLACOR_DAMAGE_CAP }
        }
    }

    private fun checkGlacytes(npc: Npc, target: Pawn) {
        if (npc.attr[GLACYTES_SPAWNED] == true) return
        if (npc.getCurrentLifepoints() > npc.getMaximumLifepoints() / 2) return
        npc.attr[GLACYTES_SPAWNED] = true
        val world = npc.world
        val list = ArrayList<Npc>()
        GLACYTE_IDS.forEach { id ->
            val tile = world.findRandomTileAround(npc.getCentreTile(), radius = 3) ?: Tile(npc.tile)
            val glacyte = Npc(id, tile, world)
            glacyte.respawns = false
            glacyte.walkRadius = 4
            glacyte.attr[GLACYTE_PARENT] = java.lang.ref.WeakReference(npc)
            glacyte.hitModifier = { hit ->
                if (id == Npcs.ENDURING_GLACYTE) {
                    val near = npc.isSpawned() && glacyte.tile.isWithinRadius(npc.tile, 14)
                    hit.hitmarks.forEach { it.damage = (it.damage * if (near) 0.4 else 0.8).toInt() }
                }
                hit.hitmarks.forEach { if (it.damage > GLACYTE_DAMAGE_CAP) it.damage = GLACYTE_DAMAGE_CAP }
            }
            if (world.spawn(glacyte)) {
                list.add(glacyte)
                glacyte.attack(target)
                if (id == Npcs.UNSTABLE_GLACYTE) runUnstable(glacyte)
            }
        }
        npc.attr[GLACYTES] = list
    }

    /** Unstable glacyte: explodes every 20 ticks for a third of nearby players' life points. */
    private fun runUnstable(glacyte: Npc) {
        val world = glacyte.world
        world.queue {
            while (glacyte.isSpawned() && !glacyte.isDead()) {
                wait(20)
                if (!glacyte.isSpawned() || glacyte.isDead()) break
                glacyte.graphic(GFX_EXPLODE)
                world.players.forEach { player ->
                    if (player.tile.isWithinRadius(glacyte.tile, 1) && !player.isDead()) {
                        player.hit(player.getCurrentLifepoints() / 3, HitType.REGULAR_HIT)
                    }
                }
                glacyte.setCurrentLifepoints((glacyte.getCurrentLifepoints() * 0.1).toInt().coerceAtLeast(1))
            }
        }
    }

    /** Called when a glacyte dies; records which one died last for the glacor's final phase. */
    fun onGlacyteDeath(glacyte: Npc) {
        val parent = glacyte.attr[GLACYTE_PARENT]?.get() ?: return
        val list = parent.attr[GLACYTES] ?: return
        list.remove(glacyte)
        if (list.none { it.isSpawned() && !it.isDead() }) {
            parent.attr[LAST_GLACYTE] = glacyte.id
            if (glacyte.id == Npcs.UNSTABLE_GLACYTE) {
                parent.graphic(GFX_EXPLODE)
                parent.world.players.forEach { player ->
                    if (player.tile.isWithinRadius(parent.tile, 2)) player.hit(player.getCurrentLifepoints() / 3, HitType.REGULAR_HIT)
                }
            }
        }
    }

    fun reset(npc: Npc) {
        npc.attr[GLACYTES]?.forEach { if (it.isSpawned()) npc.world.remove(it) }
        npc.attr.remove(GLACYTES)
        npc.attr.remove(GLACYTES_SPAWNED)
        npc.attr.remove(LAST_GLACYTE)
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
        npc.animate(ANIM_MELEE)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = 35.0, landHit = land, delay = 1, hitType = HitType.MELEE, onHit = { sapping(npc, target, it.hit.hitmarks.sumOf { h -> h.damage }) })
    }

    private fun distanced(npc: Npc, target: Pawn) {
        val world = npc.world
        if (world.random(1) == 0) {
            npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            npc.animate(ANIM_RANGED)
            world.spawn(npc.createProjectile(target, PROJ_RANGED, ProjectileType.ARROW))
            val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
            val land = RangedCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
            npc.dealHit(target = target, maxHit = 29.4, landHit = land, delay = delay, hitType = HitType.RANGE, onHit = { sapping(npc, target, it.hit.hitmarks.sumOf { h -> h.damage }) })
        } else {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(ANIM_MAGIC)
            world.spawn(npc.createProjectile(target, PROJ_MAGIC, ProjectileType.MAGIC))
            val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
            val land = MagicCombatFormula.getAccuracy(npc, target) >= world.randomDouble()
            npc.dealHit(target = target, maxHit = 26.4, landHit = land, delay = delay, hitType = HitType.MAGIC, onHit = { hit ->
                val damage = hit.hit.hitmarks.sumOf { h -> h.damage }
                sapping(npc, target, damage)
                if (damage > 0 && world.random(5) == 0) {
                    target.graphic(GFX_FREEZE)
                    target.freeze(16)
                }
            })
        }
    }

    private fun icicleFall(npc: Npc, target: Pawn) {
        npc.animate(ANIM_MAGIC)
        val world = npc.world
        val tile = Tile(target.tile)
        world.spawn(npc.createProjectile(tile, PROJ_ICICLE, ProjectileType.MAGIC))
        if (target is Player) target.message("<col=00ffff>The glacor hurls a shard of ice at you - move!")
        world.queue {
            wait(3)
            world.spawn(TileGraphic(tile, GFX_ICICLE, 0))
            world.players.forEach { player ->
                if (player.tile == tile && !player.isDead()) {
                    player.hit(player.getCurrentLifepoints() / 2, HitType.RANGE)
                }
            }
        }
    }

    /** Sapping glacyte killed last: every glacor hit drains 20 prayer points. */
    private fun sapping(npc: Npc, target: Pawn, damage: Int) {
        if (damage > 0 && npc.attr[LAST_GLACYTE] == Npcs.SAPPING_GLACYTE && target is Player) {
            target.alterPrayerPoints(-200)
        }
    }
}

/**
 * Glacytes: melee that mirrors the glacor's parent behaviour; sapping glacytes drain 20 prayer
 * points per hit.
 */
object GlacyteCombatScript : CombatScript() {
    override val ids = GlacorCombatScript.GLACYTE_IDS

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
                npc.animate(npc.combatDef.attackAnimation)
                val victim = target
                npc.dealHit(target = victim, formula = MeleeCombatFormula, delay = 1, type = HitType.MELEE, onHit = { hit ->
                    if (npc.id == Npcs.SAPPING_GLACYTE && victim is Player && hit.hit.hitmarks.sumOf { h -> h.damage } > 0) {
                        victim.alterPrayerPoints(-200)
                    }
                })
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }
}
