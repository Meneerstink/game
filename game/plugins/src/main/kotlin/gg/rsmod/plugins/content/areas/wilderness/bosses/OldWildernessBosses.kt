package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.LockState
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
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.skills.agility.exactMove

/**
 * N522 bundle 5 (owner 2026-11-01 20:2x: "Callisto + Vet'ion = OUDE (pre-2023) OSRS-versie"): the classic Callisto and Vet'ion as they
 * were before the February 2023 Wilderness boss rework - definitions from OpenRS2 cache 1151 (OSRS build 209, 2022-11-16; the rework
 * made them skeletal, which this client cannot show), imported with RSPS_OSRS_SOURCE_TAG=r1151 so nothing of the pinned cache changed.
 *
 * Mechanics: Kronos-184 (a pre-2023 OSRS server; activities/wilderness/bosses/Callisto.java, Vetion.java, data/npcs/combat) - Callisto
 * claws (80% next to him), else the shockwave (80%, up to 60, "Callisto's fury sends an almighty shockwave through you.") or the roar
 * (up to 4 tiles back, 3 damage, a 2-tick stun, "Callisto's roar throws you backwards."), and 1 in 12 a small heal (4-10, graphic 157);
 * Vet'ion melee (85% next to him), the earthquake (20%, 10-tick cooldown, up to 45 to everyone within 12 tiles, "Vet'ion pummels the ground
 * sending a shattering earthquake shockwave through you.") or three lightning blasts (the target's tile + two within 2, up to 30 three
 * ticks later); below half hitpoints two hellhounds ("Kill, my pets!" / "Bahh! Go, dogs!!", "GRRRRRRRRRRRRRRRRRRR") during which he takes
 * no damage; killed, he rises as Vet'ion Reborn at once ("Now do it again!!") and only that kill counts.
 */
object OldWildernessBosses {
    const val CALLISTO = Npcs.CALLISTO_OLD
    const val VETION = Npcs.VETION_OLD
    const val VETION_REBORN = Npcs.VETION_REBORN_OLD
    const val HELLHOUND = Npcs.SKELETON_HELLHOUND_OLD
    const val GREATER_HELLHOUND = Npcs.GREATER_SKELETON_HELLHOUND_OLD

    // Callisto: BEAR_REWORK_ATTACK / _DEFEND / _DEATH (r1151 copies)
    const val CALLISTO_ATTACK = 16206
    const val CALLISTO_DEFEND = 16207
    const val CALLISTO_DEATH = 16208
    const val GFX_SHOCKWAVE = 3216 // OSRS 395
    const val GFX_ROAR = 3217 // OSRS 245
    const val GFX_HEAL = 3127 // OSRS 157
    const val CALLISTO_MAX = 60
    const val CALLISTO_MELEE_MAX = 50 // pre-rework Wiki infobox, revision 14355607
    const val CALLISTO_KNOCKBACK = 1157 // HUMAN_TROLL_FLYBACK, Kronos Callisto.roarAttack / gameval
    const val ROAR_DAMAGE = 3

    // Vet'ion: SKELETON_UPDATE_GIANT_ATTACK / VARY3_DEFEND / GIANT_DEATH / VARY3_ATTACK (the earthquake)
    const val VETION_ATTACK = 16209
    const val VETION_DEFEND = 16210
    const val VETION_DEATH = 16211
    const val VETION_EARTHQUAKE = 16212
    const val GFX_LIGHTNING = 3218 // OSRS 280
    const val GFX_LIGHTNING_HIT = 3219 // OSRS 281
    const val VETION_MAX = 45
    const val REBORN_MAX = 45 // pre-rework Wiki: stats and abilities do not change between forms
    const val EARTHQUAKE_MAX = 45
    const val LIGHTNING_MAX = 30
    const val EARTHQUAKE_RANGE = 6 // pre-rework Wiki 14352478; donor's 12 is too large
    const val EARTHQUAKE_COOLDOWN = 10

    // Skeleton hellhounds: Kronos 6559 / 6557 / 6558
    const val HOUND_ATTACK = 16213
    const val HOUND_DEFEND = 16214
    const val HOUND_DEATH = 16215

    /**
     * Owner 2026-11-04 17:0x: the old Callisto and Vet'ion move into their OSRS lairs (OsrsRegionImportTool 'wildy-lairs'). The spots
     * are the lair bosses' own (Near Reality CallistosDen / VetionsRest: 3359,10328 plane 0 and 3295,10202 plane 1); until 2026-11-04
     * they stood on the surface (Kronos-184 data/npcs/spawns/wilderness.json 3291,3847 / 3224,3790).
     */
    val CALLISTO_SPAWN = Tile(3359, 10328, 0)
    val VETION_SPAWN = Tile(3295, 10202, 1)
    const val VETION_RESPAWN_TICKS = 50
    const val REBORN_TIMEOUT_TICKS = 500 // five minutes (pre-rework Wiki 14352478)

    val DOGS = AttributeKey<MutableList<Npc>>()
    val DOGS_CALLED = AttributeKey<Boolean>()
    val EARTHQUAKE_READY_CYCLE = AttributeKey<Int>()

    fun isVetion(id: Int): Boolean = id == VETION || id == VETION_REBORN

    /** One reward after both phases, still using the normal form's drop table and pet definition. */
    fun rewardNpc(id: Int): Int? = when (id) {
        VETION -> null
        VETION_REBORN -> VETION
        else -> id
    }

    fun dogs(npc: Npc): MutableList<Npc> = npc.attr[DOGS] ?: mutableListOf<Npc>().also { npc.attr[DOGS] = it }

    fun dogsAlive(npc: Npc): Boolean = dogs(npc).any { it.isSpawned() && !it.isDead() }

    /** Vet'ion takes no damage while his hellhounds live (Kronos Vetion.block); called from the shared hit path. */
    fun modifyIncomingDamage(target: Npc, damage: Int, type: HitType = HitType.REGULAR_HIT): Int = when {
        target.id == CALLISTO && type in listOf(HitType.MAGIC, HitType.CRIT_MAGIC) -> 0
        isVetion(target.id) && dogsAlive(target) -> 0
        else -> damage
    }

    /** Check immunity at impact too: a second projectile may arrive after the first hit summoned the hounds. */
    fun modifyIncomingHit(npc: Npc, hit: gg.rsmod.game.model.Hit) {
        hit.hitmarks.forEach { mark ->
            if (mark.damage > 0 && (isVetion(npc.id) && dogsAlive(npc) ||
                    npc.id == CALLISTO && mark.type in intArrayOf(HitType.MAGIC.id, HitType.CRIT_MAGIC.id))) mark.damage = 0
        }
    }

    fun removeDogs(npc: Npc) {
        dogs(npc).forEach { if (it.isSpawned()) npc.world.remove(it) }
        dogs(npc).clear()
    }

    /** Preserve the whole fight's damage credit, rather than losing phase one to a fresh damage map. */
    fun nextPhase(npc: Npc, id: Int, hitpoints: Int? = null, keepDogs: Boolean = false): Npc? {
        val next = Npc(id, Tile(npc.tile), npc.world)
        next.respawns = false
        next.walkRadius = 10
        if (!npc.world.spawn(next)) {
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.COMBAT) { "Vetion phase spawn failed: ${npc.id} -> $id at ${npc.tile}" }
            return null
        }
        npc.world.players.forEach { p ->
            val damage = npc.damageMap.getDamageFrom(p)
            if (damage > 0) next.damageMap.add(p, damage)
        }
        if (hitpoints != null) next.setCurrentLifepoints(hitpoints)
        if (keepDogs) {
            next.attr[DOGS_CALLED] = npc.attr[DOGS_CALLED] == true
            next.attr[DOGS] = dogs(npc)
        }
        val target = npc.getCombatTarget() ?: npc.damageMap.getMostDamage()
        if (target != null && !target.isDead() && (target !is Player || target.isOnline)) next.attack(target)
        return next
    }

    fun startRebornTimeout(npc: Npc) {
        val life = WildernessBossLife.token(npc)
        npc.world.queue {
            wait(REBORN_TIMEOUT_TICKS)
            if (!WildernessBossLife.active(npc, life)) return@queue
            if (nextPhase(npc, VETION, npc.getCurrentLifepoints(), keepDogs = true) != null) npc.world.remove(npc)
        }
    }

    /** Melee reach of a large npc: next to its footprint, not diagonal past a corner. */
    fun adjacent(npc: Npc, t: Tile): Boolean = gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(
        npc.world.collision, npc.tile, npc.getSize(), npc.getSize(), t, 1, 1)

    /** The old giant's three-tile reach still cannot pass through melee-blocking scenery. */
    fun vetionMeleeReach(npc: Npc, t: Tile): Boolean {
        if (ScorpiaCombat.distanceToArea(t, npc.tile, npc.getSize()) !in 1..3) return false
        val edge = Tile(t.x.coerceIn(npc.tile.x, npc.tile.x + npc.getSize() - 1),
            t.z.coerceIn(npc.tile.z, npc.tile.z + npc.getSize() - 1), npc.tile.height)
        return npc.world.collision.raycast(edge, t, projectile = false)
    }

    /** One step of the roar's push: away from Callisto along the axis the player stands on (Kronos getClosestX / Y). */
    fun pushVector(npc: Npc, t: Tile): Pair<Int, Int> {
        val size = npc.getSize()
        val cx = t.x.coerceIn(npc.tile.x, npc.tile.x + size - 1)
        val cz = t.z.coerceIn(npc.tile.z, npc.tile.z + size - 1)
        return Integer.signum(t.x - cx) to Integer.signum(t.z - cz)
    }

    /** Check departure AND arrival collision for each step, including diagonal corner clipping. */
    fun pushDestination(npc: Npc, start: Tile): Tile {
        if (start.height != npc.tile.height) return Tile(start)
        val (vx, vz) = pushVector(npc, start)
        var end = Tile(start)
        if (vx == 0 && vz == 0) return end
        repeat(4) {
            val next = end.transform(vx, vz)
            if (!npc.world.collision.canStep(end, Direction.between(end, next))) return end
            end = next
        }
        return end
    }

    /** The donor rolls: Callisto claws next to him 80%, else shockwave 80% / roar; a heal 1 in 12. */
    fun callistoAttack(adjacent: Boolean, roll100: Int, roll100b: Int): String =
        when {
            adjacent && roll100 < 80 -> "claws"
            roll100b < 80 -> "shockwave"
            else -> "roar"
        }

    object CallistoCombat : CombatScript() {
        override val ids = intArrayOf(CALLISTO)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = 8, projectile = true)) {
                    val p = target as? Player
                    when (callistoAttack(adjacent(npc, target.tile), npc.world.random(99), npc.world.random(99))) {
                        "claws" -> claws(npc, target)
                        "shockwave" -> shockwave(npc, target)
                        else -> if (p != null) roar(npc, p) else claws(npc, target)
                    }
                    if (npc.world.random(11) == 0) {
                        npc.graphic(GFX_HEAL)
                        npc.setCurrentLifepoints((npc.getCurrentLifepoints() + npc.world.random(4..10)).coerceAtMost(npc.getMaximumLifepoints()))
                    }
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        private fun claws(npc: Npc, target: Pawn) {
            npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.ACCURATE)
            npc.animate(CALLISTO_ATTACK)
            val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
            npc.dealHit(target = target, maxHit = CALLISTO_MELEE_MAX.toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
        }

        /** Donor projectile (395, 43, 31, 51, 36, 10, 15, 127); the hit ignores defence (always rolls). */
        private fun shockwave(npc: Npc, target: Pawn) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(CALLISTO_ATTACK)
            npc.world.spawn(npc.createProjectile(target, GFX_SHOCKWAVE, startHeight = 43, endHeight = 31, delay = 51, angle = 15))
            val delay = gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
            npc.dealHit(target = target, maxHit = CALLISTO_MAX.toDouble(), landHit = true, delay = delay, hitType = HitType.MAGIC)
            (target as? Player)?.message("Callisto's fury sends an almighty shockwave through you.")
        }

        /** Up to 4 tiles back while each step is free, 3 damage, a 2-tick stun (donor). */
        internal fun roar(npc: Npc, p: Player) {
            npc.animate(CALLISTO_ATTACK)
            val start = Tile(p.tile)
            val end = pushDestination(npc, start)
            p.graphic(GFX_ROAR, height = 124, delay = 5)
            p.animate(CALLISTO_KNOCKBACK)
            npc.dealHit(target = p, minHit = ROAR_DAMAGE.toDouble(), maxHit = ROAR_DAMAGE.toDouble(), landHit = true, delay = 0, hitType = HitType.REGULAR_HIT)
            p.stun(2) {}
            p.message("Callisto's roar knocks you backwards.")
            if (!end.sameAs(p.tile)) {
                val dest = end
                p.lockingQueue(priority = TaskPriority.STRONG, lockState = LockState.FULL) {
                    if (!p.isOnline || p.isDead() || !p.tile.sameAs(start)) return@lockingQueue
                    exactMove(p, dest, delay = 60, direction = Direction.between(dest, npc.tile))
                }
            }
        }
    }

    object VetionCombat : CombatScript() {
        override val ids = intArrayOf(VETION, VETION_REBORN)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                callDogs(npc, target)
                if (npc.moveToAttackRange(it, target, distance = 10, projectile = true)) {
                    val world = npc.world
                    val quakeReady = world.currentCycle >= (npc.attr[EARTHQUAKE_READY_CYCLE] ?: 0)
                    when {
                        vetionMeleeReach(npc, target.tile) && world.random(99) < 85 -> melee(npc, target)
                        quakeReady && world.random(99) < 20 -> earthquake(npc)
                        else -> lightning(npc, target)
                    }
                    // Kronos follow(1): keep closing on a distant target even while casting at up to ten tiles.
                    if (ScorpiaCombat.distanceToArea(target.tile, npc.tile, npc.getSize()) > 3) npc.walkTo(target.tile)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        /** Below half hitpoints, once a phase: two hellhounds (greater for Reborn) next to him, on his target. */
        fun callDogs(npc: Npc, target: Pawn) {
            if (npc.isDead() || npc.attr[DOGS_CALLED] == true || npc.getCurrentLifepoints() >= npc.getMaximumLifepoints() / 2) return
            npc.attr[DOGS_CALLED] = true
            npc.forceChat(if (npc.id == VETION) "Kill, my pets!" else "Bahh! Go, Dogs!!")
            val hound = if (npc.id == VETION) HELLHOUND else GREATER_HELLHOUND
            repeat(2) { i ->
                val dog = Npc(hound, Tile(npc.tile.x + 1 + npc.world.random(-1..1), npc.tile.z + 1 + npc.world.random(-1..1), npc.tile.height), npc.world)
                dog.respawns = false
                if (npc.world.spawn(dog)) {
                    dogs(npc) += dog
                    dog.facePawn(target)
                    dog.attack(target)
                    if (i == 0) dog.forceChat("GRRRRRRRRRRRRRRRRRRR")
                }
            }
        }

        private fun melee(npc: Npc, target: Pawn) {
            val reborn = npc.id == VETION_REBORN
            npc.prepareAttack(CombatClass.MELEE, StyleType.STAB, WeaponStyle.ACCURATE)
            npc.animate(VETION_ATTACK)
            val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
            npc.dealHit(target = target, maxHit = (if (reborn) REBORN_MAX else VETION_MAX).toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
        }

        private fun earthquake(npc: Npc) {
            npc.attr[EARTHQUAKE_READY_CYCLE] = npc.world.currentCycle + EARTHQUAKE_COOLDOWN
            npc.animate(VETION_EARTHQUAKE)
            val source = Tile(npc.tile.x + 1, npc.tile.z + 1, npc.tile.height)
            npc.world.players.forEach { p ->
                if (p.isOnline && !p.isDead() && p.tile.height == source.height && p.tile.isWithinRadius(source, EARTHQUAKE_RANGE)) {
                    val damage = npc.world.random(EARTHQUAKE_MAX)
                    npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = damage > 0, delay = 1, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
                    p.message("Vet'ion pummels the ground sending a shattering earthquake shockwave through you.")
                }
            }
        }

        /** The target's tile and two more within 2 (not under him); whoever stands on one 3 ticks later takes up to 30 (donor). */
        private fun lightning(npc: Npc, target: Pawn) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(VETION_ATTACK)
            val world = npc.world
            val life = WildernessBossLife.token(npc)
            val centre = Tile(target.tile)
            val options = mutableListOf<Tile>()
            for (dx in -2..2) for (dz in -2..2) {
                if (dx == 0 && dz == 0) continue
                val t = Tile(centre.x + dx, centre.z + dz, centre.height)
                if (t.x in npc.tile.x until npc.tile.x + npc.getSize() && t.z in npc.tile.z until npc.tile.z + npc.getSize()) continue
                options += t
            }
            options.shuffle()
            val tiles = listOf(centre) + options.take(2)
            tiles.forEach { t ->
                // Donor projectile (280, 40, 2, 41, 150, 0, 40, 11) from his centre.
                world.spawn(npc.createProjectile(t, GFX_LIGHTNING, startHeight = 40, endHeight = 2, delay = 41, angle = 40, lifespan = 41 + 60))
            }
            world.queue {
                wait(3)
                if (!WildernessBossLife.active(npc, life)) return@queue
                tiles.forEach { t -> world.spawn(TileGraphic(t, GFX_LIGHTNING_HIT, 0)) }
                if (npc.isDead() || !npc.isSpawned()) return@queue
                world.players.forEach { p ->
                    if (p.isOnline && !p.isDead() && tiles.any { it.sameAs(p.tile) }) {
                        npc.dealHit(target = p, maxHit = LIGHTNING_MAX.toDouble(), landHit = true, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
                    }
                }
            }
        }
    }

    /** The skeleton hellhounds: crush melee, max 26 / greater 32 (Kronos-184 data/npcs/combat). */
    object HoundCombat : CombatScript() {
        override val ids = intArrayOf(HELLHOUND, GREATER_HELLHOUND)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.ACCURATE)
                    npc.animate(HOUND_ATTACK)
                    val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
                    npc.dealHit(target = target, maxHit = (if (npc.id == HELLHOUND) 26 else 32).toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
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
