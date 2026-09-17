package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.content.combat.getCombatTarget
import java.lang.ref.WeakReference

/**
 * Deadman Mode guards (OSRS Wiki "Guard (Deadman Mode)" / "Wizguard"; owner instructions 2026-09-16
 * and 2026-09-17, retest round 3 the same day).
 *
 * Npcs: the real OSRS Deadman guards, imported into both 667 caches with `OsrsNpcImportTool` batch
 * `deadman-guard` (owner: "gebruik exact de deadmanmode guard van osrs"): OSRS 6582 "Guard" (slash)
 * -> [MELEE_GUARD_ID], OSRS 11203 "Guard" (ranged) -> [RANGED_GUARD_ID], OSRS 14792 "Wizguard" ->
 * [WIZGUARD_ID]. The owner's stationed extras on named tiles (2026-09-17 pin list) are the already
 * imported Third Age Ranger [THIRD_AGE_RANGER_ID], Third Age Mage [THIRD_AGE_MAGE_ID] and Lucien
 * [LUCIEN_ID]; they share the guard combat definition ("ze moeten allemaal dezelfde stats hebben").
 * None of them carries a cache "Attack" option any more (`NpcAttackOptionStripTool`, owner: "Remove
 * the attack option from all guards, a player cannot attack them").
 *
 * Rules (owner 2026-09-17 unless marked wiki):
 * - Guards react only to a PK-skulled player standing inside a guarded zone ([GuardedZones], the
 *   wiki polygons). Outside a zone nothing ever teleports onto you, and a guard never fights from, or
 *   roams to, a tile outside a zone ([mayAttack]; [leash]; the zone-bounded random walk in
 *   `npc_random_walk.plugin.kts`).
 * - Entering a zone skulled brings at most two guards ([MAX_GUARDS_PER_PLAYER]) onto you: a random
 *   group from [SPAWN_COMBINATIONS] - a melee guard that teleports next to you, a ranger that appears
 *   a few tiles away, and/or a Wizguard. The same cap holds for the stationed guards: a guard may
 *   only engage a player who has fewer than two guards on them already.
 * - The guards that came for you do not vanish when you leave ("when the player runs into a
 *   dangerous [zone] again the 2 guards disappear, this should not happen"): they stop at once, stay
 *   in the zone and patrol where the fight ended ([release]/[standDown]); the next skulled intruder
 *   in that zone gets the same guards teleported in again (per-zone pool, [acquire]).
 * - Every guard roams up to [PATROL_RADIUS] tiles around its post ("alle guards 8 tiles kunnen
 *   roamen") and routes with the real breadth-first path finder ([Npc.smartPathfinding]) so it
 *   walks around fences, counters and buildings instead of forgetting the intruder behind them; it
 *   pursues anywhere inside the zone ([mayPursue], hooked into `NpcLeash`).
 * - Wiki: 2-tick attack speed, 800 hitpoints, damage ignores protection prayers, starts at 20% of
 *   the player's Hitpoints level and rises by 2 per attack, several guards regardless of
 *   single-combat, and they stop the instant the player leaves the zone or loses the skull.
 * - Wiki (Wizguard): appears next to the intruder, yells "You shall not pass!", casts Ice Barrage
 *   once (5-tick freeze, not reduced by Protect from Magic), disappears, and comes back every 10
 *   ticks while the player stays. Its damage is not published, so only the sourced freeze is applied.
 */
object CityGuards {
    /** OSRS 6582 "Guard" (Varrock, slash) imported as this local 667 id. */
    const val MELEE_GUARD_ID = 14407

    /** OSRS 11203 "Guard" (Varrock, ranged) imported as this local 667 id. */
    const val RANGED_GUARD_ID = 14408

    /** OSRS 14792 "Wizguard" imported as this local 667 id. */
    const val WIZGUARD_ID = 14409

    /** Owner pin list 2026-09-17: "npc 14404 third age ranger" (OSRS 8636 import, Npcs.THIRD_AGE_RANGER). */
    const val THIRD_AGE_RANGER_ID = 14404

    /** Owner pin list 2026-09-17: "npc 14405 third age mage" (OSRS 8637 import, Npcs.THIRD_AGE_MAGE). */
    const val THIRD_AGE_MAGE_ID = 14405

    /** Owner pin list 2026-09-17: "14256 Lucien" (Npcs.LUCIEN_14256, size 3). */
    const val LUCIEN_ID = 14256

    val MELEE_GUARD_IDS = setOf(MELEE_GUARD_ID, LUCIEN_ID)
    val RANGED_GUARD_IDS = setOf(RANGED_GUARD_ID, THIRD_AGE_RANGER_ID)
    val MAGE_GUARD_IDS = setOf(THIRD_AGE_MAGE_ID)
    val GUARD_IDS = MELEE_GUARD_IDS + RANGED_GUARD_IDS + MAGE_GUARD_IDS + WIZGUARD_ID

    /** Owner 2026-09-17: "MAXIMAAL 2 GUARDS MOGEN 1 PLAYER ATTACKEN". */
    const val MAX_GUARDS_PER_PLAYER = 2

    /** OSRS Wiki: "Combat Level: 1337". Also baked into the imported cache definitions. */
    const val DISPLAYED_COMBAT_LEVEL = 1337

    /** OSRS Wiki: "Attack Speed: 2 ticks (1.2 seconds)". */
    const val ATTACK_SPEED_CYCLES = 2

    /** OSRS Wiki: "Hitpoints: 800" (NpcCombatDsl takes real HP times ten). */
    const val HITPOINTS_TIMES_TEN = 8000

    /** OSRS Wiki (Wizguard): "freezing them for 3 seconds (5 ticks)". */
    const val WIZGUARD_FREEZE_CYCLES = 5

    /** OSRS Wiki (Wizguard): "will appear again after 6 seconds (10 ticks)". */
    const val WIZGUARD_REAPPEAR_CYCLES = 10

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

    /** How far from you the ranger appears ("1 ranger van ver"). */
    val RANGER_SPAWN_DISTANCE = 4..6

    const val GREETING = "We don't want your sort here, %s!"
    const val WIZGUARD_SHOUT = "You shall not pass!"

    /** Owner "deadmanmode vervijning" 2026-09-17: every Deadman guard is named "1337 guard" (cache
     * name, written by `NpcRenameTool`), examines as [EXAMINE] and refuses attacks with
     * [ATTACK_REFUSED_MESSAGE]. */
    const val DISPLAY_NAME = "1337 guard"
    const val EXAMINE = "I wonder if he gets stuck behind fences."
    const val ATTACK_REFUSED_MESSAGE = "You probably don't want to do that."

    enum class Kind { MELEE, RANGED, MAGE }

    /**
     * Owner 2026-09-17: "1 ranger van ver en/of 1 melee die in je teleport of 1 mager, compleet
     * random, maar maximaal 2". Every combination has at most two guards and at least one guard
     * that deals damage (the Wizguard only freezes - its damage is not published on the wiki).
     */
    val SPAWN_COMBINATIONS: List<List<Kind>> =
        listOf(
            listOf(Kind.MELEE),
            listOf(Kind.RANGED),
            listOf(Kind.MELEE, Kind.RANGED),
            listOf(Kind.MELEE, Kind.MAGE),
            listOf(Kind.RANGED, Kind.MAGE),
        )

    /** Marks a guard that came for an intruder (kept in the zone pool instead of a fixed post). */
    val REACTIVE_GUARD_ATTR = AttributeKey<Boolean>()

    /** Per-cycle leash driver for every Deadman guard (armed in [configure]). */
    val GUARD_LEASH_TIMER = TimerKey()

    private val OUTSIDE_CYCLES_ATTR = AttributeKey<Int>()

    fun isGuard(npc: Npc): Boolean = npc.id in GUARD_IDS

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
     * Whether [guard] may engage [target]: the target is a skulled intruder, the guard itself stands
     * inside a guarded zone, and the target does not already have [MAX_GUARDS_PER_PLAYER] other
     * guards on them. Re-checked every combat cycle by the shared combat gate, which is what makes
     * a guard "stop attacking the player immediately after they leave the guarded area".
     */
    fun mayAttack(
        guard: Npc,
        target: Player,
    ): Boolean {
        if (!isSkulledIntruder(target)) return false
        if (!isGuardedZone(guard.tile)) return false
        return reserveSlot(target, guard)
    }

    /** Leash rule for `NpcLeash`: a guard keeps chasing anywhere inside the zone, never outside it. */
    fun mayPursue(
        guard: Npc,
        target: Pawn,
    ): Boolean = target is Player && isSkulledIntruder(target)

    // ---- max-two-guards cap ----

    private val REACTIVE_GUARDS_ATTR = AttributeKey<MutableList<WeakReference<Npc>>>()
    private val REACTIVE_PLAN_ATTR = AttributeKey<List<Kind>>()
    private val ENGAGED_STATIONED_ATTR = AttributeKey<MutableList<Reservation>>()

    /** A stationed guard holding one of a player's guard slots; [cycle] is when it was taken, so a
     * guard that reserved a slot but has not started its first attack yet is not pruned early. */
    class Reservation(
        val guard: WeakReference<Npc>,
        val cycle: Int,
    )

    private const val RESERVATION_GRACE_CYCLES = 3

    /** Guards currently counted against [target]'s cap: the reactive group (its planned size,
     * so a Wizguard between two appearances still holds its slot) plus stationed guards that are
     * actively fighting the target. */
    fun engagedGuardCount(target: Player): Int {
        val reactive = target.attr[REACTIVE_PLAN_ATTR]?.size ?: 0
        return reactive + engagedStationed(target).size
    }

    private fun engagedStationed(target: Player): MutableList<Reservation> {
        val list = target.attr[ENGAGED_STATIONED_ATTR] ?: mutableListOf<Reservation>().also { target.attr[ENGAGED_STATIONED_ATTR] = it }
        val now = target.world.currentCycle
        list.removeAll { r ->
            val g = r.guard.get()
            g == null || !g.isActive() || (g.getCombatTarget() !== target && now - r.cycle > RESERVATION_GRACE_CYCLES)
        }
        return list
    }

    private fun isReactiveGuardOf(
        target: Player,
        guard: Npc,
    ): Boolean = target.attr[REACTIVE_GUARDS_ATTR]?.any { it.get() === guard } == true

    /** True if [guard] already holds, or can take, one of [target]'s [MAX_GUARDS_PER_PLAYER] slots. */
    fun reserveSlot(
        target: Player,
        guard: Npc,
    ): Boolean {
        if (isReactiveGuardOf(target, guard)) return true
        val stationed = engagedStationed(target)
        if (stationed.any { it.guard.get() === guard }) return true
        val reactive = target.attr[REACTIVE_PLAN_ATTR]?.size ?: 0
        if (reactive + stationed.size >= MAX_GUARDS_PER_PLAYER) return false
        stationed += Reservation(WeakReference(guard), target.world.currentCycle)
        return true
    }

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
            val id = post.npcId ?: if (post.ranged) RANGED_GUARD_ID else MELEE_GUARD_ID
            val guard =
                Npc(id, tile, world).also {
                    it.respawnOverride = true
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
        if (guard.getCombatTarget() == null &&
            guard.attr[REACTIVE_GUARD_ATTR] != true &&
            !guard.movementQueue.hasDestination() &&
            guard.tile.getDistance(guard.spawnTile) > PATROL_RADIUS + 2
        ) {
            guard.walkTo(guard.spawnTile)
        }
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
        player.attr.remove(REACTIVE_GUARDS_ATTR)
        player.attr.remove(REACTIVE_PLAN_ATTR)
        player.attr.remove(WIZGUARD_NEXT_CYCLE_ATTR)
        player.attr.remove(ENGAGED_STATIONED_ATTR)
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
            val tile = if (kind == Kind.MELEE) meleeTile(world, player) else rangerTile(world, player)
            val guard = acquire(world, zone, kind, tile) ?: return@forEach
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
        val id = if (kind == Kind.MELEE) MELEE_GUARD_ID else RANGED_GUARD_ID
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
            }
        world.spawn(guard)
        configure(guard)
        pool += WeakReference(guard)
        return guard
    }

    /** The walkable guarded tile next to [player] for the melee guard - never the player's own
     * tile, so the guard visibly stands beside and faces the intruder. */
    private fun meleeTile(
        world: World,
        player: Player,
    ): Tile = nearestWalkable(world, player.tile, radius = 2) { it != player.tile && isGuardedZone(it) } ?: Tile(player.tile)

    /** A walkable guarded tile [RANGER_SPAWN_DISTANCE] tiles from [player], in a random direction;
     * falls back to the nearest walkable guarded tile next to the player. */
    private fun rangerTile(
        world: World,
        player: Player,
    ): Tile {
        val distance = world.random(RANGER_SPAWN_DISTANCE)
        val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1).shuffled()
        for ((dx, dz) in directions) {
            val wanted = player.tile.transform(dx * distance, dz * distance)
            val tile = nearestWalkable(world, wanted, radius = 2) { isGuardedZone(it) && it.getDistance(player.tile) >= 3 }
            if (tile != null) return tile
        }
        return meleeTile(world, player)
    }

    /**
     * OSRS Wiki (Wizguard): appears, yells, casts Ice Barrage once (5-tick freeze, unaffected by
     * Protect from Magic) and disappears. The Wizguard's own damage roll is not published on the
     * wiki, so only the sourced freeze + graphics are applied - no invented hit.
     */
    private fun wizguardStrike(
        world: World,
        player: Player,
    ) {
        val tile = meleeTile(world, player)
        val wizguard =
            Npc(WIZGUARD_ID, tile, world).also {
                it.respawnOverride = false
                it.walkRadius = 0
                it.attr[REACTIVE_GUARD_ATTR] = true
            }
        world.spawn(wizguard)
        wizguard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
        wizguard.forceChat(WIZGUARD_SHOUT)
        wizguard.facePawn(player)
        wizguard.animate(ICE_BARRAGE_CAST_ANIM)
        player.graphic(Graphic(ICE_BARRAGE_IMPACT_GFX, 0))
        player.freeze(WIZGUARD_FREEZE_CYCLES) {
            player.filterableMessage("A Wizguard's Ice Barrage freezes you in place!")
        }
        world.queue {
            wait(3)
            if (wizguard.isActive()) world.remove(wizguard)
        }
    }
}
