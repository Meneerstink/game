package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.attr.GUARD_FROZEN_UNTIL_CYCLE_ATTR
import gg.rsmod.game.model.attr.HOLD_FACING_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.content.combat.getCombatTarget
import java.lang.ref.WeakReference

/**
 * Deadman Mode guards (OSRS Wiki "Guard (Deadman Mode)" / "Wizguard"; owner 2026-09-19: zones and
 * guards 100 % OSRS, which replaced the earlier 2-guard cap, the unattackable guards and the "1337
 * guard" rename).
 *
 * Npcs: every per-city OSRS Deadman guard variant from the wiki infobox, imported into both 667 caches
 * with `OsrsNpcImportTool` batch `deadman-guard` with its OSRS name, models and "Attack" option
 * ([VARIANTS]), plus OSRS 14792 "Wizguard" -> [WIZGUARD_ID] (no options, unattackable).
 *
 * Rules (wiki unless marked owner):
 * - Guards act only on a PK-skulled player standing inside a guarded zone ([GuardedZones], the
 *   wiki polygons) and stop the instant the player leaves it; a guard never fights from, or roams to,
 *   a tile outside a zone ([mayAttack]; [leash]; the zone-bounded random walk).
 * - "Will also spawn on top of any PK skulled player in a safe zone and attack them ... either a melee
 *   or a ranged one" plus a Wizguard ([SPAWN_COMBINATIONS]). "Multiple guards are able to attack the
 *   player" - there is no cap; every stationed guard in aggro range joins in.
 * - "The guards can only be attacked inside the guarded area while skulled" ([mayBeAttackedBy]);
 *   otherwise "You probably don't want to do that.". The Wizguard is unattackable.
 * - Owner: the guards that came for you do not vanish when you leave ("when the player runs into a
 *   dangerous [zone] again the 2 guards disappear, this should not happen"): they stop at once, stay
 *   in the zone and patrol where the fight ended ([release]/[standDown]); the next skulled intruder
 *   in that zone gets the same guards teleported in again (per-zone pool, [acquire]).
 * - Owner: every guard roams up to [PATROL_RADIUS] tiles around its post ("alle guards 8 tiles kunnen
 *   roamen") and routes with the real breadth-first path finder ([Npc.smartPathfinding]) so it
 *   walks around fences, counters and buildings instead of forgetting the intruder behind them; it
 *   pursues anywhere inside the zone ([mayPursue], hooked into `NpcLeash`).
 * - Wiki: 2-tick attack speed, 800 hitpoints, damage ignores protection prayers, starts at 20% of
 *   the player's Hitpoints level and rises by 2 per attack, several guards regardless of
 *   single-combat, and they stop the instant the player leaves the zone or loses the skull.
 * - Wiki (Wizguard): appears next to the intruder, yells "You shall not pass!", casts Ice Barrage
 *   once (5-tick freeze; damage and freeze not reduced by Protect from Magic), disappears, and comes
 *   back every 10 ticks while the player stays. The wiki gives no Wizguard max hit; the hit is rolled
 *   up to Ice Barrage's own base max ([WIZGUARD_MAX_HIT]). A frozen player cannot pick up or
 *   telegrab items (wiki changelog).
 */
object CityGuards {
    /** OSRS 6582 "Guard" (Varrock, slash) imported as this local 667 id. */
    const val MELEE_GUARD_ID = 14407

    /** OSRS 11203 "Guard" (Varrock, ranged) imported as this local 667 id. */
    const val RANGED_GUARD_ID = 14408

    /** OSRS 14792 "Wizguard" imported as this local 667 id. */
    const val WIZGUARD_ID = 14409

    /** A zone's OSRS guard pair (local 667 ids); [melee] is null where the wiki only lists a ranged guard. */
    data class Variant(
        val melee: Int?,
        val ranged: Int,
    )

    /**
     * OSRS Wiki "Guard (Deadman Mode)" infobox versions per guarded zone (OSRS id -> local id from the
     * `deadman-guard` import, tx-20260919-150209): Varrock 6582/11203 -> 14407/14408, Gnome Stronghold
     * 6574/11199 -> 14414/14415, Seers' Village (ranged) 6575 -> 14416, Catherby (ranged) 6576 -> 14417,
     * East Ardougne 6579/11200 -> 14418/14419, Yanille 6580/11201 -> 14420/14421, Falador 6581/11202 ->
     * 14422/14423, Lumbridge 6583/11204 -> 14424/14425, Port Phasmatys 6698/11205 -> 14426/14427,
     * Sophanem 6699/11206 -> 14428/14429, Fremennik Isles 6700/11207 -> 14430/14431, Void Knights'
     * Outpost 6701/11208 -> 14432/14433, Rellekka 6702/11209 -> 14434/14435.
     */
    val VARIANTS: Map<String, Variant> =
        mapOf(
            "Varrock" to Variant(MELEE_GUARD_ID, RANGED_GUARD_ID),
            "Tree Gnome Stronghold" to Variant(14414, 14415),
            "Seers' Village bank" to Variant(null, 14416),
            "Catherby bank" to Variant(null, 14417),
            "East Ardougne" to Variant(14418, 14419),
            "Yanille" to Variant(14420, 14421),
            "Falador" to Variant(14422, 14423),
            "Lumbridge" to Variant(14424, 14425),
            "Port Phasmatys" to Variant(14426, 14427),
            "Sophanem" to Variant(14428, 14429),
            "Jatizso" to Variant(14430, 14431),
            "Neitiznot" to Variant(14430, 14431),
            "Void Knights' Outpost" to Variant(14432, 14433),
            "Rellekka" to Variant(14434, 14435),
        )

    /** Tutorial Island has no guard in the wiki tables; a skulled intruder there gets the Lumbridge pair. */
    private val FALLBACK_VARIANT = VARIANTS.getValue("Lumbridge")

    fun variantFor(zone: String?): Variant = zone?.let { VARIANTS[it] } ?: FALLBACK_VARIANT

    val MELEE_GUARD_IDS: Set<Int> = VARIANTS.values.mapNotNull { it.melee }.toSet()
    val RANGED_GUARD_IDS: Set<Int> = VARIANTS.values.map { it.ranged }.toSet()
    val GUARD_IDS = MELEE_GUARD_IDS + RANGED_GUARD_IDS + WIZGUARD_ID

    /** OSRS Wiki: "Combat Level: 1337". Also baked into the imported cache definitions. */
    const val DISPLAYED_COMBAT_LEVEL = 1337

    /** OSRS Wiki: "Attack Speed: 2 ticks (1.2 seconds)". */
    const val ATTACK_SPEED_CYCLES = 2

    /** OSRS Wiki: "Hitpoints: 800" (NpcCombatDsl takes real HP times ten). */
    const val HITPOINTS_TIMES_TEN = 8000

    /** OSRS Wiki Guard (Deadman Mode) combat definition. */
    const val ATTACK_LEVEL = 800
    const val STRENGTH_LEVEL = 400
    const val DEFENCE_LEVEL = 300
    const val MAGIC_LEVEL = 1
    const val RANGED_LEVEL = 1
    const val ATTACK_BONUS = 60
    const val STRENGTH_BONUS = 7
    const val DEFENCE_STAB_BONUS = 8
    const val DEFENCE_SLASH_BONUS = 9
    const val DEFENCE_CRUSH_BONUS = 7
    const val DEFENCE_MAGIC_BONUS = 0
    const val DEFENCE_RANGED_BONUS = 8

    /** OSRS Wiki (Wizguard): "freezing them for 3 seconds (5 ticks)". */
    const val WIZGUARD_FREEZE_CYCLES = 5
    const val WIZGUARD_CAST_SOUND = 171
    const val WIZGUARD_IMPACT_SOUND = 169

    /** OSRS Wiki (Wizguard): "will appear again after 6 seconds (10 ticks)". */
    const val WIZGUARD_REAPPEAR_CYCLES = 10

    /** OSRS Wiki "Ice Barrage": base max hit 30 - the spell the Wizguard casts (its own max hit is
     * not published). Rolled 0..30 and, like the freeze, not reduced by Protect from Magic. */
    const val WIZGUARD_MAX_HIT = 30

    /** Ice Barrage cast animation / impact graphic - the same 667 ids the player spell uses
     * ([gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.ICE_BARRAGE]). */
    const val ICE_BARRAGE_CAST_ANIM = 1979
    const val ICE_BARRAGE_IMPACT_GFX = 369

    /** Owner 2026-09-17: "alle guards 8 tiles kunnen roamen" - roaming radius around a guard's post. */
    const val PATROL_RADIUS = 8

    /** Reactive guards retained per zone and kind; beyond this the idle ones are reused. */
    const val POOL_PER_KIND = 2

    /** Cycles a guard may spend walking back after ending up outside every zone before it is put back. */
    const val OUTSIDE_GRACE_CYCLES = 10

    /** OSRS Wiki overhead text of the spawned guard and of the Wizguard. */
    const val GREETING = "We don't want your sort here, %s!"
    const val WIZGUARD_SHOUT = "You shall not pass!"

    /** OSRS Wiki trivia: "Attempting to attack one of the guards displays the message ...". Names and
     * examines are the OSRS ones per variant (cache names, `data/cfg/npcs.yml`). */
    const val ATTACK_REFUSED_MESSAGE = "You probably don't want to do that."

    enum class Kind { MELEE, RANGED, MAGE }

    /**
     * OSRS Wiki: a guard spawns on top of the skulled intruder - "either a melee or a ranged one" -
     * and a Wizguard freezes them (then reappears every [WIZGUARD_REAPPEAR_CYCLES]).
     */
    val SPAWN_COMBINATIONS: List<List<Kind>> =
        listOf(
            listOf(Kind.MELEE, Kind.MAGE),
            listOf(Kind.RANGED, Kind.MAGE),
        )

    /** Marks a guard that came for an intruder (kept in the zone pool instead of a fixed post). */
    val REACTIVE_GUARD_ATTR = AttributeKey<Boolean>()

    /** Per-cycle leash driver for every Deadman guard (armed in [configure]). */
    val GUARD_LEASH_TIMER = TimerKey()

    private val OUTSIDE_CYCLES_ATTR = AttributeKey<Int>()

    fun isGuard(npc: Npc): Boolean = npc.id in GUARD_IDS

    /**
     * An ordinary cache "Guard" (not a Deadman guard) posted inside a guarded zone. Owner
     * 2026-09-18: every guard in a safe zone roams, at most [PATROL_RADIUS] tiles and only inside
     * the zone; these use the generic random walk (`npc_random_walk.plugin.kts`).
     */
    fun isOrdinaryZoneGuard(npc: Npc): Boolean =
        !isGuard(npc) && npc.def.name.equals("Guard", ignoreCase = true) && isGuardedZone(npc.spawnTile)

    /** OSRS Wiki: "Protection prayers are ineffective against their damage." Shared by the melee,
     * ranged and magic formulas so they cannot drift on which ids are exempt. */
    fun bypassesProtectionPrayer(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    /** OSRS Wiki: "Multiple guards are able to attack the player regardless of the location's
     * multicombat area status." */
    fun ignoresSingleCombat(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    fun isGuardedZone(tile: Tile): Boolean = GuardedZones.contains(tile)

    /** The only thing a guard ever acts on: a PK-skulled player standing inside a guarded zone. */
    fun isSkulledIntruder(target: Player): Boolean = PvpSkull.isSkulled(target) && isGuardedZone(target.tile)

    /**
     * Whether [guard] may engage [target]: the target is a skulled intruder and the guard itself
     * stands inside a guarded zone (no cap - wiki: "Multiple guards are able to attack the player").
     * Re-checked every combat cycle by the shared combat gate, which is what makes a guard "stop
     * attacking the player immediately after they leave the guarded area".
     */
    fun mayAttack(
        guard: Npc,
        target: Player,
    ): Boolean = isSkulledIntruder(target) && isGuardedZone(guard.tile)

    /** OSRS Wiki: "The guards can only be attacked inside the guarded area while skulled." The
     * Wizguard is "unattackable". */
    fun mayBeAttackedBy(
        guard: Npc,
        attacker: Player,
    ): Boolean = guard.id != WIZGUARD_ID && isSkulledIntruder(attacker) && isGuardedZone(guard.tile)

    /** Leash rule for `NpcLeash`: a guard keeps chasing anywhere inside the zone, never outside it. */
    fun mayPursue(
        guard: Npc,
        target: Pawn,
    ): Boolean = isGuard(guard) && isGuardedZone(guard.tile) && target is Player && isSkulledIntruder(target)

    private val REACTIVE_GUARDS_ATTR = AttributeKey<MutableList<WeakReference<Npc>>>()
    private val REACTIVE_PLAN_ATTR = AttributeKey<List<Kind>>()

    // ---- consecutive-hit damage ramp ----

    private val CONSECUTIVE_GUARD_HITS_ATTR = AttributeKey<Int>()
    private val LAST_RAMP_CYCLE_ATTR = AttributeKey<Int>()

    /** OSRS Wiki: "starting from 20% their hitpoints level and increases by 2 damage per each
     * attack". Idempotent per world cycle so a formula queried twice for one real attack cannot
     * double-advance the ramp. Hitpoints are 1:1 units here (see the HP unit migration). */
    fun rampedMaxHit(
        guard: Npc,
        target: Player,
    ): Int {
        val cycle = guard.world.currentCycle
        val lastCycle = target.attr[LAST_RAMP_CYCLE_ATTR]
        val hits =
            if (lastCycle == cycle) {
                target.attr[CONSECUTIVE_GUARD_HITS_ATTR] ?: 1
            } else {
                val next = (target.attr[CONSECUTIVE_GUARD_HITS_ATTR] ?: 0) + 1
                target.attr[CONSECUTIVE_GUARD_HITS_ATTR] = next
                target.attr[LAST_RAMP_CYCLE_ATTR] = cycle
                next
            }
        val start = target.getMaximumLifepoints() / 5
        return (start + 2 * (hits - 1)).coerceAtLeast(1)
    }

    /** Call when [target] leaves a guarded zone or its skull clears, so a later encounter starts
     * the ramp fresh at 20%. */
    fun resetDamageRamp(target: Player) {
        target.attr.remove(CONSECUTIVE_GUARD_HITS_ATTR)
        target.attr.remove(LAST_RAMP_CYCLE_ATTR)
    }

    // ---- placement helpers ----

    /** Nearest tile within [radius] of [tile] a pawn can stand on, or null. */
    fun nearestWalkable(
        world: World,
        tile: Tile,
        radius: Int = 4,
        accept: (Tile) -> Boolean = { true },
    ): Tile? {
        if (!world.collision.isClipped(tile) && accept(tile)) return tile
        for (r in 1..radius) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                    val candidate = tile.transform(dx, dz)
                    if (!world.collision.isClipped(candidate) && accept(candidate)) return candidate
                }
            }
        }
        return null
    }

    private fun nearestGuardedTile(
        world: World,
        tile: Tile,
        radius: Int = 6,
    ): Tile? = nearestWalkable(world, tile, radius) { isGuardedZone(it) }

    // ---- stationed guards ----

    /** Spawns one stationed guard per [GuardPosts] entry; returns a boot summary line. */
    fun spawnStationedGuards(world: World): String {
        var spawned = 0
        val unplaceable = ArrayList<String>()
        val snapped = ArrayList<String>()
        GuardPosts.ALL.forEach { post ->
            val tile = nearestGuardedTile(world, post.tile)
            if (tile == null) {
                unplaceable += "${post.city} ${post.tile.x},${post.tile.z}"
                return@forEach
            }
            if (tile != post.tile) {
                snapped += "${post.city} ${post.tile.x},${post.tile.z}->${tile.x},${tile.z}"
            }
            val variant = variantFor(GuardedZones.zoneAt(tile)?.name)
            val id = if (post.ranged || variant.melee == null) variant.ranged else variant.melee
            val guard =
                Npc(id, tile, world).also {
                    it.respawnOverride = true
                    // Before spawn: the random-walk plugin arms its timer in the spawn hook only
                    // when walkRadius is already > 0 (owner live retest: guards "still not roaming").
                    it.walkRadius = PATROL_RADIUS
                }
            world.spawn(guard)
            configure(guard)
            spawned++
        }
        return "CityGuards: stationed $spawned/${GuardPosts.ALL.size} Deadman guards" +
            (if (snapped.isEmpty()) "" else "; snapped to walkable guarded tile: $snapped") +
            (if (unplaceable.isEmpty()) "" else "; no walkable guarded tile near: $unplaceable")
    }

    /** Applies the per-instance state every Deadman guard needs (also on respawn via the
     * global npc-spawn hook). */
    fun configure(guard: Npc) {
        guard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
        guard.aggroCheck = { g, target -> mayAttack(g, target) }
        guard.smartPathfinding = true
        guard.walkRadius = PATROL_RADIUS
        guard.timers[GUARD_LEASH_TIMER] = 2
    }

    /**
     * Per-cycle leash (owner 2026-09-17: "de guards mogen nooit een safe zone verlaten"). A guard
     * fighting a target it may no longer attack drops the fight at once; a guard that ended up
     * outside every guarded zone walks back to the nearest guarded tile (and is put back there if
     * it fails to for [OUTSIDE_GRACE_CYCLES]); a stationed guard that drifted far from its post
     * after a chase walks - never teleports - back to it.
     */
    fun leash(guard: Npc) {
        val world = guard.world
        val target = guard.getCombatTarget() as? Player
        if (target != null && mayPursue(guard, target)) {
            // Reassert the face-pawn block on the same cadence as the leash. This covers guards
            // reacquired by aggro and keeps all imported visual variants aligned.
            faceAndResend(guard, target)
        }
        if (target != null && !mayPursue(guard, target)) {
            standDown(guard)
        }
        if (!isGuardedZone(guard.tile)) {
            val outside = (guard.attr[OUTSIDE_CYCLES_ATTR] ?: 0) + 2
            guard.attr[OUTSIDE_CYCLES_ATTR] = outside
            val back = nearestGuardedTile(world, guard.tile) ?: guard.spawnTile
            if (outside > OUTSIDE_GRACE_CYCLES) {
                guard.attr.remove(OUTSIDE_CYCLES_ATTR)
                guard.stopMovement()
                guard.moveTo(back)
                if (guard.attr[REACTIVE_GUARD_ATTR] == true) guard.spawnTile = Tile(back)
            } else if (!guard.movementQueue.hasDestination()) {
                guard.walkTo(back)
            }
            return
        }
        guard.attr.remove(OUTSIDE_CYCLES_ATTR)
        if (guard.getCombatTarget() == null) {
            // Owner 2026-09-18: every guard faces the player with a skull in a safe zone. A guard
            // that is not (yet) in the fight stops and watches the nearest skulled intruder in view; HOLD_FACING_ATTR
            // keeps the engine from dropping the non-combat face-pawn while the intruder is not
            // adjacent (Npc.cycle). The watch ends, and patrolling resumes, when they leave.
            val intruder = nearestSkulledIntruder(guard)
            if (intruder != null) {
                guard.attr[HOLD_FACING_ATTR] = true
                if (guard.movementQueue.hasDestination()) guard.stopMovement()
                faceAndResend(guard, intruder)
                return
            }
            if (guard.attr.has(HOLD_FACING_ATTR)) {
                guard.attr.remove(HOLD_FACING_ATTR)
                guard.resetFacePawn()
            }
        }
        if (guard.getCombatTarget() == null && !guard.movementQueue.hasDestination()) {
            if (guard.attr[REACTIVE_GUARD_ATTR] != true && guard.tile.getDistance(guard.spawnTile) > PATROL_RADIUS + 2) {
                guard.walkTo(guard.spawnTile)
            } else if (world.random(PATROL_STEP_CHANCE) == 0) {
                // Owner live retest 2026-09-17 ("does not roam the tiles in the safe zone"): patrol
                // from the leash itself, independent of the generic random-walk plugin. A face-pawn
                // left over from a fight would otherwise keep the generic walk from ever starting.
                // resetFacePawn, not a bare attr removal: that left the block buffer's face index
                // on the old target, so the client kept the guard turned to it while it patrolled.
                guard.resetFacePawn()
                val patrol = patrolDestination(world, guard.spawnTile, guard)
                if (patrol != null) {
                    guard.walkPath(patrol.second, MovementQueue.StepType.NORMAL, detectCollision = true)
                    guard.attr[LAST_PATROL_ATTR] = "walk ${patrol.first.x},${patrol.first.z} (${patrol.second.size} steps) @${world.currentCycle}"
                } else {
                    // Owner 2026-09-18 ("still not actively roaming"): a post whose zone polygon is
                    // tight can fail every full-route candidate; fall back to the engine's own
                    // routing towards any walkable in-zone tile within the radius rather than
                    // standing still (the leash still stops the guard if it ever steps outside).
                    val fallback = nearestWalkable(world, guard.spawnTile.transform(world.random(-PATROL_RADIUS..PATROL_RADIUS), world.random(-PATROL_RADIUS..PATROL_RADIUS)), radius = 2) {
                        isGuardedZone(it) && isWithinPatrolRadius(guard.spawnTile, it)
                    }
                    if (fallback != null) guard.walkTo(fallback)
                    guard.attr[LAST_PATROL_ATTR] = "no full route; fallback ${fallback?.let { "${it.x},${it.z}" } ?: "none"} @${world.currentCycle}"
                }
            }
        }
    }

    /**
     * Owner live retest 2026-09-19 ("guards are not facing me when attacking", picture guardnotfacing):
     * [Npc.facePawn] only emits a FACE_PAWN block when the target index changes, so a single lost
     * update (e.g. sent on a cycle the client did not yet track the guard) left the guard turned
     * away for the whole fight. The leash re-sends the block every leash tick (2 cycles) so any
     * client that missed it is corrected within about a second.
     */
    fun faceAndResend(
        guard: Npc,
        target: Pawn,
    ) {
        guard.facePawn(target)
        guard.addBlock(gg.rsmod.game.sync.block.UpdateBlockType.FACE_PAWN)
    }

    /**
     * One patrol walk roughly every 8 cycles (~5 s) per idle guard (the leash runs every 2 cycles;
     * `world.random(n)` is inclusive, so 1 in n+1). Owner live retest 2026-09-18 ("guards still not
     * actively roaming"): the previous 1-in-16 roll gave one walk per ~19 s, which reads as
     * standing still; the ordinary cache guards' random walk fires every 15-30 cycles too.
     */
    private const val PATROL_STEP_CHANCE = 3

    private const val PATROL_CANDIDATE_ATTEMPTS = 32

    /** How far a guard notices a skulled intruder to watch (its aggro radius plus a little). */
    const val WATCH_RADIUS = 10

    /** Owner retest aid (`guardinfo` command): what the last patrol roll did for this guard. */
    val LAST_PATROL_ATTR = AttributeKey<String>()

    /** One diagnostic line per Deadman guard within [radius] of [tile], for the `guardinfo` command. */
    fun describeNearby(
        world: World,
        tile: Tile,
        radius: Int = 15,
    ): List<String> {
        val lines = ArrayList<String>()
        world.npcs.forEach { npc ->
            if (isGuard(npc) && npc.tile.height == tile.height && npc.tile.getDistance(tile) <= radius) {
                lines +=
                    "${npc.def.name}#${npc.id} @${npc.tile.x},${npc.tile.z} post ${npc.spawnTile.x},${npc.spawnTile.z}" +
                        " target=${(npc.getCombatTarget() as? Player)?.username ?: "-"}" +
                        " facing=${(npc.attr[FACING_PAWN_ATTR]?.get() as? Player)?.username ?: "-"}" +
                        " faceIndex=${npc.facePawnIndex}" +
                        " hold=${npc.attr[HOLD_FACING_ATTR] == true} walking=${npc.movementQueue.hasDestination()}" +
                        " reactive=${npc.attr[REACTIVE_GUARD_ATTR] == true} inZone=${isGuardedZone(npc.tile)}" +
                        " leash=${npc.timers.has(GUARD_LEASH_TIMER)} lastPatrol=${npc.attr[LAST_PATROL_ATTR] ?: "-"}"
            }
        }
        return lines
    }

    /** The nearest skulled intruder inside a guarded zone within [WATCH_RADIUS] of [guard], if any. */
    fun nearestSkulledIntruder(guard: Npc): Player? {
        var best: Player? = null
        var bestDistance = Int.MAX_VALUE
        guard.world.players.forEach { player ->
            if (player.isOnline && !player.isDead() && player.tile.height == guard.tile.height && isSkulledIntruder(player)) {
                val distance = guard.tile.getDistance(player.tile)
                if (distance <= WATCH_RADIUS && distance < bestDistance) {
                    best = player
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /** Chebyshev radius is the same tile radius used by NPC walk bounds and the owner request. */
    fun isWithinPatrolRadius(base: Tile, candidate: Tile): Boolean =
        base.height == candidate.height && base.getDistance(candidate) <= PATROL_RADIUS

    /** A patrol candidate must be inside the exact zone and reachable around current collision. */
    private fun patrolDestination(world: World, base: Tile, guard: Npc): Pair<Tile, java.util.Queue<Tile>>? {
        repeat(PATROL_CANDIDATE_ATTEMPTS) {
            val candidate = base.transform(world.random(-PATROL_RADIUS..PATROL_RADIUS), world.random(-PATROL_RADIUS..PATROL_RADIUS))
            if (!isWithinPatrolRadius(base, candidate) || !isGuardedZone(candidate)) return@repeat
            if (world.chunks.get(candidate, createIfNeeded = false) == null || world.collision.isClipped(candidate)) return@repeat
            val route =
                BFSPathFindingStrategy(world.collision).calculateRoute(
                    PathRequest.createWalkRequest(guard, candidate.x, candidate.z, projectile = false, detectCollision = true),
                )
            if (route.success && route.path.isNotEmpty() && route.path.all(::isGuardedZone)) {
                return candidate to route.path
            }
        }
        return null
    }

    /** Ends whatever fight [guard] is in and leaves it idle where it stands. */
    private fun standDown(guard: Npc) {
        Combat.reset(guard)
        guard.resetInteractions()
        guard.stopMovement()
        if (guard.attr[REACTIVE_GUARD_ATTR] == true) {
            // Patrol from here on (owner: "the guards need to be roaming inside the safe area").
            guard.spawnTile = Tile(guard.tile)
        }
    }

    // ---- reactive guards: teleport in on a skulled entry, stay in the zone afterwards ----

    private val WAS_IN_GUARDED_ZONE_ATTR = AttributeKey<Boolean>()
    private val WIZGUARD_NEXT_CYCLE_ATTR = AttributeKey<Int>()
    /** The currently visible Wizguard, so every release route can remove it immediately. */
    private val ACTIVE_WIZGUARD_ATTR = AttributeKey<WeakReference<Npc>>()

    /** Reactive guards per zone name (weak: a removed npc simply drops out). */
    private val pools = HashMap<String, MutableList<WeakReference<Npc>>>()

    /** Called every cycle for every online player from the shared per-cycle poll. */
    fun onZoneCheck(player: Player) {
        val world = player.world
        val inZone = isGuardedZone(player.tile)
        val wasInZone = player.attr[WAS_IN_GUARDED_ZONE_ATTR] ?: false
        player.attr[WAS_IN_GUARDED_ZONE_ATTR] = inZone

        if (!inZone || !PvpSkull.isSkulled(player)) {
            if (player.attr.has(REACTIVE_PLAN_ATTR) || (wasInZone && !inZone)) {
                release(player)
            }
            return
        }

        if (!player.attr.has(REACTIVE_PLAN_ATTR)) {
            // A skulled player arriving (by foot or teleport) with a bank/deposit modal already
            // open must not keep banking behind the guards: close both sides of the bank UI.
            player.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
            player.closeInterface(dest = InterfaceDestination.TAB_AREA)
            engageReactiveGuards(world, player)
            player.attr[WIZGUARD_NEXT_CYCLE_ATTR] = world.currentCycle
        }
        if (player.attr[REACTIVE_PLAN_ATTR]?.contains(Kind.MAGE) == true) {
            val next = player.attr[WIZGUARD_NEXT_CYCLE_ATTR] ?: world.currentCycle
            if (world.currentCycle >= next) {
                wizguardStrike(world, player)
                player.attr[WIZGUARD_NEXT_CYCLE_ATTR] = world.currentCycle + WIZGUARD_REAPPEAR_CYCLES
            }
        }
    }

    /** Ends the guard response for [player]: the guards stand down (and stay in their zone), the
     * ramp and cap reset. Also used on logout and death. */
    fun release(player: Player) {
        resetDamageRamp(player)
        player.attr[REACTIVE_GUARDS_ATTR]?.forEach { ref ->
            ref.get()?.let { guard -> if (guard.isActive()) standDown(guard) }
        }
        player.attr[ACTIVE_WIZGUARD_ATTR]?.get()?.let { wizguard ->
            if (wizguard.isActive()) player.world.remove(wizguard)
        }
        player.attr.remove(REACTIVE_GUARDS_ATTR)
        player.attr.remove(REACTIVE_PLAN_ATTR)
        player.attr.remove(WIZGUARD_NEXT_CYCLE_ATTR)
        player.attr.remove(ACTIVE_WIZGUARD_ATTR)
    }

    /** Random group per [SPAWN_COMBINATIONS]; exposed for tests. */
    fun pickCombination(random: Int): List<Kind> = SPAWN_COMBINATIONS[Math.floorMod(random, SPAWN_COMBINATIONS.size)]

    private fun engageReactiveGuards(
        world: World,
        player: Player,
    ) {
        val zone = GuardedZones.zoneAt(player.tile)
        val plan = pickCombination(world.random(SPAWN_COMBINATIONS.size - 1))
        val engaged = ArrayList<Kind>(2)
        val guards = ArrayList<WeakReference<Npc>>(2)
        plan.forEach { kind ->
            if (kind == Kind.MAGE) {
                engaged += kind // the Wizguard is driven by wizguardStrike
                return@forEach
            }
            if (zone == null) return@forEach
            // Wiki: the guard "will also spawn on top of" the intruder (the adjacent tile, so it
            // visibly stands beside and faces them).
            val guard = acquire(world, zone, kind, spawnTileNextTo(world, player)) ?: return@forEach
            engaged += kind
            guards += WeakReference(guard)
            player.attr[REACTIVE_GUARDS_ATTR] = guards
            guard.forceChat(GREETING.format(player.username))
            guard.facePawn(player)
            if (guard.canEngageCombat(player)) {
                guard.attack(player)
            }
        }
        player.attr[REACTIVE_GUARDS_ATTR] = guards
        player.attr[REACTIVE_PLAN_ATTR] = engaged
    }

    /**
     * A guard of [kind] for [zone], teleported to [tile]: an idle guard from the zone's pool when
     * one exists, otherwise a new one while the pool for that kind is below [POOL_PER_KIND]. Null
     * when every guard of that kind in the zone is busy with another intruder.
     */
    private fun acquire(
        world: World,
        zone: GuardedZones.Zone,
        kind: Kind,
        tile: Tile,
    ): Npc? {
        val pool = pools.getOrPut(zone.name) { ArrayList() }
        pool.removeAll { it.get()?.isActive() != true }
        val variant = variantFor(zone.name)
        val id = if (kind == Kind.RANGED || variant.melee == null) variant.ranged else variant.melee
        val idle = pool.mapNotNull { it.get() }.firstOrNull { it.id == id && it.getCombatTarget() == null }
        if (idle != null) {
            idle.stopMovement()
            idle.moveTo(tile)
            idle.spawnTile = Tile(tile)
            return idle
        }
        if (pool.count { it.get()?.id == id } >= POOL_PER_KIND) return null
        val guard =
            Npc(id, tile, world).also {
                it.respawnOverride = false
                it.attr[REACTIVE_GUARD_ATTR] = true
                it.walkRadius = PATROL_RADIUS
            }
        world.spawn(guard)
        configure(guard)
        pool += WeakReference(guard)
        return guard
    }

    /** The walkable guarded tile next to [player] - never the player's own tile, so the guard
     * visibly stands beside and faces the intruder. */
    private fun spawnTileNextTo(
        world: World,
        player: Player,
    ): Tile = nearestWalkable(world, player.tile, radius = 2) { it != player.tile && isGuardedZone(it) } ?: Tile(player.tile)

    /**
     * OSRS Wiki (Wizguard): appears, yells, casts Ice Barrage once - freezing the player for 5 ticks
     * "and dealing damage", neither affected by Protect from Magic - and disappears. The hit is rolled
     * up to Ice Barrage's base max ([WIZGUARD_MAX_HIT]); while frozen the player cannot pick up or
     * telegrab items ([GUARD_FROZEN_UNTIL_CYCLE_ATTR]).
     */
    private fun wizguardStrike(
        world: World,
        player: Player,
    ) {
        player.attr[ACTIVE_WIZGUARD_ATTR]?.get()?.let { previous ->
            if (previous.isActive()) world.remove(previous)
        }
        val tile = spawnTileNextTo(world, player)
        val wizguard =
            Npc(WIZGUARD_ID, tile, world).also {
                it.respawnOverride = false
                it.walkRadius = 0
                it.attr[REACTIVE_GUARD_ATTR] = true
            }
        world.spawn(wizguard)
        player.attr[ACTIVE_WIZGUARD_ATTR] = WeakReference(wizguard)
        wizguard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
        wizguard.forceChat(WIZGUARD_SHOUT)
        wizguard.facePawn(player)
        wizguard.animate(ICE_BARRAGE_CAST_ANIM)
        player.graphic(Graphic(ICE_BARRAGE_IMPACT_GFX, 0))
        // Ice Barrage cast / impact sounds, the same ids the player's spell plays (SpellSounds: 171, 169).
        player.playSound(WIZGUARD_CAST_SOUND)
        player.playSound(WIZGUARD_IMPACT_SOUND, delay = 1)
        player.freeze(WIZGUARD_FREEZE_CYCLES) {
            player.filterableMessage("A Wizguard's Ice Barrage freezes you in place!")
        }
        player.attr[GUARD_FROZEN_UNTIL_CYCLE_ATTR] = world.currentCycle + WIZGUARD_FREEZE_CYCLES
        player.hit(world.random(WIZGUARD_MAX_HIT), delay = 1)
        world.queue {
            wait(1)
            if (wizguard.isActive()) faceAndResend(wizguard, player)
            wait(2)
            if (player.attr[ACTIVE_WIZGUARD_ATTR]?.get() === wizguard) {
                player.attr.remove(ACTIVE_WIZGUARD_ATTR)
                if (wizguard.isActive()) world.remove(wizguard)
            }
        }
    }
}
