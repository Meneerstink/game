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
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.skills.agility.exactMove

/**
 * N522 nachtrun deel 2 (owner 2026-11-04 15:5x / 22:2x: Artio via route B, after a good proof build it and take it live): the OSRS Artio
 * (OSRS npc 11992, NpcID CALLISTO_SINGLES) in Hunter's End. Body and sequences are baked skeletal (OsrsPetImportTool skeletal-npc artio,
 * tx-20261104-230952); her graphics and the bear trap are imported OSRS assets (OsrsFxImportTool / OsrsLocImportTool batch artio).
 *
 * Mechanics - OSRS Wiki "Artio" + "Artio/Strategies" (2026-11-05) with the tick details of Near Reality (zaryx-nr) Callisto.java, the only
 * donor with the 2023 rework:
 * - Melee is her main attack (crush, 15-35; Protect from Melee "reduces it by about 50%"), reaching diagonally too.
 * - Out of melee reach: the ranged crescent (max 17, fully blocked by Protect from Missiles) "at everyone in the den", or 1 in 5
 *   (Near Reality Utils.roll(20d)) the white knockback orb on her target that splashes a 3x3: 3 tiles back; 5 damage when the whole push
 *   is made, +15 per blocked tile (20 / 35 / 50); no damage with Protect from Magic; +5 when pushed onto a bear trap.
 * - At 66 % and 33 % hitpoints she roars (NPC_CALLISTO_SPAWN01, Near Reality HOWL_ANIMATION 10016), breaks free of a freeze (immune for 4
 *   ticks) and lays bear traps across the arena; after the second roar every attack lays traps. 4 traps a time on free floor inside the trap
 *   square (Near Reality getTrapCoords(4), x 1751-1767, z 11533-11552), 45 ticks each; stepping on one: up to 15 (Wiki), bound 4 ticks
 *   (Near Reality MovementLock 2400 ms), "The bear trap immobilizes you.".
 * - Attack speed 5, respawn 11 (infobox). Not representable here: the red roar glow (FX_CALLISTO_ENRAGE_SMALL 2353 is skeletal).
 */
object ArtioCombat : CombatScript() {
    const val NPC = gg.rsmod.plugins.api.cfg.Npcs.ARTIO // OSRS 11992 (baked skeletal, tx-20261104-230952)
    override val ids = intArrayOf(NPC)

    const val ANIM_STAND = 16236 // NPC_CALLISTO_IDLE01 10011
    const val ANIM_MELEE = 16238 // NPC_CALLISTO_ATTACK_MELEE01 10012
    const val ANIM_RANGED = 16239 // NPC_CALLISTO_ATTACK_RANGED01 10013
    const val ANIM_MAGIC = 16240 // NPC_CALLISTO_ATTACK_MAGIC01 10014
    const val ANIM_ROAR = 16242 // NPC_CALLISTO_SPAWN01 10016 (Near Reality HOWL_ANIMATION)
    const val ANIM_DEATH = 16243 // NPC_CALLISTO_DEATH01 10017

    const val GFX_TRAP_SPAWN = 3225 // FX_CALLISTO_TRAP 2343
    const val GFX_RANGED_CAST = 3226 // FX_CALLISTO_RANGED_SPOTANIM 2349
    const val GFX_RANGED_PROJECTILE = 3227 // FX_CALLISTO_RANGED_PROJECTILE 2350
    const val GFX_RANGED_IMPACT = 3228 // FX_CALLISTO_RANGED_IMPACT 2351
    const val GFX_ORB = 3105 // WINDBLAST_TRAVEL 133
    const val GFX_ORB_IMPACT = 3106 // WINDBLAST_IMPACT 134
    const val GFX_KNOCKBACK = 3229 // 1255 (Near Reality KNOCKBACK_PLAYER_GRAPHICS)
    const val ANIM_KNOCKBACK = 1157 // HUMAN_TROLL_FLYBACK (Near Reality KNOCKBACK_ANIMATION, same id in both caches)
    const val TRAP_LOC = 63856 // CALLISTO_TRAP_LOC 47146

    const val MELEE_MIN = 15
    const val MELEE_MAX = 35
    const val RANGED_MAX = 17
    const val TRAP_MAX = 15
    const val TRAP_TICKS = 45
    const val TRAP_BIND_TICKS = 4
    const val TRAPS_PER_LAY = 4
    const val PUSH_TILES = 3
    const val ROAR_FREEZE_IMMUNITY = 4

    val SPAWN = Tile(1757, 11543, 0)
    val ARENA_X = 1739..1777
    val ARENA_Z = 11525..11562
    val TRAP_X = 1751..1767
    val TRAP_Z = 11533..11552

    const val MSG_TRAP = "The bear trap immobilizes you."

    /** 1 = full health, 2 = roared at 66 %, 3 = roared at 33 % (traps with every attack). */
    val PHASE = AttributeKey<Int>()
    private val TRAPS = AttributeKey<MutableList<DynamicObject>>()

    fun inArena(t: Tile): Boolean = t.height == SPAWN.height && t.x in ARENA_X && t.z in ARENA_Z

    /** Melee reach of the 3x3: next to the footprint, diagonals included (Wiki: "can be used diagonally across a target"). */
    fun inMeleeReach(npc: Npc, t: Tile): Boolean = ScorpiaCombat.distanceToArea(t, npc.tile, npc.getSize()) == 1

    /** Knockback damage (Wiki): 5 when all 3 tiles are made, +15 per blocked tile. */
    fun knockbackDamage(moved: Int): Int = 5 + 15 * (PUSH_TILES - moved.coerceIn(0, PUSH_TILES))

    /** Protect from Melee about halves the melee hit (Wiki "reduces the damage by ~50%"). */
    fun meleeDamage(roll: Int, protected: Boolean): Int = if (protected) roll / 2 else roll

    /** The phase after a hit at [hp] of [max]: roar once below 66 % and once below 33 % (Near Reality postHitProcess). */
    fun phaseFor(current: Int, hp: Int, max: Int): Int =
        when {
            hp * 3 < max && current < 3 -> 3
            hp * 3 < max * 2 && current < 2 -> 2
            else -> current
        }

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (roarIfDue(npc)) {
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
                continue
            }
            if (inMeleeReach(npc, target.tile)) {
                melee(npc, target)
            } else if (npc.moveToAttackRange(it, target, distance = 10, projectile = true)) {
                if (npc.world.random(4) == 0) orb(npc, target) else ranged(npc)
            }
            if ((npc.attr[PHASE] ?: 1) >= 3) layTraps(npc)
            npc.postAttackLogic(target)
            // Tick by tick, so a roar threshold crossed between attacks roars at once (Near Reality: on the hit).
            for (t in 0 until npc.combatDef.attackSpeed) {
                it.wait(1)
                if (roarIfDue(npc)) break
            }
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** The roar at 66 % / 33 %: breaks a freeze, 4 ticks freeze immunity, lays traps. */
    fun roarIfDue(npc: Npc): Boolean {
        val current = npc.attr[PHASE] ?: 1
        val next = phaseFor(current, npc.getCurrentLifepoints(), npc.getMaximumLifepoints())
        if (next == current) return false
        npc.attr[PHASE] = next
        npc.animate(ANIM_ROAR)
        npc.timers.remove(FROZEN_TIMER)
        npc.timers[FREEZE_IMMUNITY_TIMER] = ROAR_FREEZE_IMMUNITY
        layTraps(npc)
        return true
    }

    private fun melee(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MELEE)
        val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        val damage = meleeDamage(npc.world.random(MELEE_MIN..MELEE_MAX), target.isProtectedFrom(CombatClass.MELEE))
        npc.dealHit(target = target, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE, applyDeflectProtection = false)
    }

    /** The crescent at everyone in the den (Wiki), fully blocked by Protect from Missiles. */
    private fun ranged(npc: Npc) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(ANIM_RANGED)
        npc.graphic(GFX_RANGED_CAST)
        players(npc).forEach { p ->
            // Near Reality RANGED_PROJECTILE(2350, 5, 25, 35, 15, 55, 64, 0): start 5, end 25, delay 35, angle 15.
            npc.world.spawn(npc.createProjectile(p, GFX_RANGED_PROJECTILE, startHeight = 5, endHeight = 25, delay = 35, angle = 15))
            val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(p), p.getCentreTile())
            val land = gg.rsmod.plugins.content.combat.formula.RangedCombatFormula.getAccuracy(npc, p) >= npc.world.randomDouble()
            npc.dealHit(target = p, maxHit = RANGED_MAX.toDouble(), landHit = land, delay = delay, hitType = HitType.RANGE, onHit = { p.graphic(GFX_RANGED_IMPACT) })
        }
    }

    /** The white orb on the target; on impact every player within its 3x3 is pushed back (Wiki). */
    private fun orb(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_MAGIC)
        // Near Reality MAGIC_PROJECTILE(133, 50, 25, 60, 15, 55, 64, 0): start 50, end 25, delay 60, angle 15.
        npc.world.spawn(npc.createProjectile(target, GFX_ORB, startHeight = 50, endHeight = 25, delay = 60, angle = 15))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val life = WildernessBossLife.token(npc)
        npc.world.queue {
            wait(delay)
            if (!WildernessBossLife.active(npc, life)) return@queue
            val centre = Tile(target.tile)
            if (!inArena(centre)) return@queue
            npc.world.spawn(TileGraphic(centre, GFX_ORB_IMPACT, 0))
            players(npc).filter { it.tile.isWithinRadius(centre, 1) }.forEach { p -> knockback(npc, p) }
        }
    }

    /** Up to [PUSH_TILES] straight away from the footprint, stopping at the first blocked step (departure and arrival collision). */
    fun pushDestination(npc: Npc, start: Tile): Pair<Tile, Int> {
        val (vx, vz) = OldWildernessBosses.pushVector(npc, start)
        var end = Tile(start)
        if (vx == 0 && vz == 0) return end to 0
        var moved = 0
        while (moved < PUSH_TILES) {
            val next = end.transform(vx, vz)
            if (!npc.world.collision.canStep(end, Direction.between(end, next))) break
            end = next
            moved++
        }
        return end to moved
    }

    private fun knockback(npc: Npc, p: Player) {
        if (!p.isOnline || p.isDead()) return
        val start = Tile(p.tile)
        val (dest, moved) = pushDestination(npc, start)
        val onTrap = trapAt(npc, dest)
        p.graphic(GFX_KNOCKBACK, height = 92)
        p.animate(ANIM_KNOCKBACK)
        if (!p.isProtectedFrom(CombatClass.MAGIC)) {
            val damage = knockbackDamage(moved) + if (onTrap != null) 5 else 0
            npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = true, delay = 0, hitType = HitType.MAGIC, applyDeflectProtection = false)
        }
        if (!dest.sameAs(start)) {
            p.lockingQueue(priority = TaskPriority.STRONG, lockState = LockState.FULL) {
                if (!p.isOnline || p.isDead() || !p.tile.sameAs(start)) return@lockingQueue
                exactMove(p, dest, delay = 30, direction = Direction.between(dest, npc.tile))
            }
        }
    }

    private fun players(npc: Npc): List<Player> {
        val out = ArrayList<Player>()
        npc.world.players.forEach { p -> if (p.isOnline && !p.isDead() && inArena(p.tile)) out += p }
        return out
    }

    private fun traps(npc: Npc): MutableList<DynamicObject> = npc.attr[TRAPS] ?: mutableListOf<DynamicObject>().also { npc.attr[TRAPS] = it }

    private fun trapAt(npc: Npc, t: Tile): DynamicObject? = synchronized(traps(npc)) { traps(npc).firstOrNull { it.tile.sameAs(t) } }

    /** Four traps on free floor of the trap square, never on a trap, under Artio or on a player (Near Reality getTrapCoords). */
    fun trapTiles(npc: Npc, taken: Set<Tile>, pick: (List<Tile>) -> List<Tile> = { it.shuffled() }): List<Tile> {
        val free = TRAP_X.flatMap { x -> TRAP_Z.map { z -> Tile(x, z, SPAWN.height) } }.filter { t ->
            taken.none { it.sameAs(t) } && ScorpiaCombat.distanceToArea(t, npc.tile, npc.getSize()) > 0 && !npc.world.collision.isClipped(t)
        }
        return pick(free).take(TRAPS_PER_LAY)
    }

    fun layTraps(npc: Npc) {
        val world = npc.world
        val life = WildernessBossLife.token(npc)
        val list = traps(npc)
        val tiles = synchronized(list) { trapTiles(npc, list.map { it.tile }.toSet()) }
        tiles.forEach { t ->
            val trap = DynamicObject(TRAP_LOC, 10, 0, t)
            world.spawn(trap)
            world.spawn(TileGraphic(t, GFX_TRAP_SPAWN, 0))
            synchronized(list) { list += trap }
            world.queue {
                for (tick in 0 until TRAP_TICKS) {
                    if (!WildernessBossLife.active(npc, life)) break
                    val victim = players(npc).firstOrNull { it.tile.sameAs(t) }
                    if (victim != null) {
                        spring(npc, victim)
                        break
                    }
                    wait(1)
                }
                synchronized(list) { list.remove(trap) }
                if (trap.isSpawned(world)) world.remove(trap)
            }
        }
    }

    /** Up to 15 (Wiki) and bound for 4 ticks (Near Reality MovementLock 2400 ms). */
    private fun spring(npc: Npc, p: Player) {
        p.stopMovement()
        p.timers[FROZEN_TIMER] = TRAP_BIND_TICKS
        p.message("<col=ff0000>$MSG_TRAP</col>")
        val damage = npc.world.random(TRAP_MAX)
        npc.dealHit(target = p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = damage > 0, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
    }

    /** Death / respawn: every trap goes, the phase starts again. */
    fun reset(npc: Npc) {
        val list = traps(npc)
        synchronized(list) {
            list.forEach { if (it.isSpawned(npc.world)) npc.world.remove(it) }
            list.clear()
        }
        npc.attr[PHASE] = 1
    }
}
