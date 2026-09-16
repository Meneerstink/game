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
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.content.combat.getCombatTarget
import java.lang.ref.WeakReference

/**
 * Deadman Mode guards (OSRS Wiki "Guard (Deadman Mode)" / "Wizguard"; owner instructions 2026-09-16
 * and 2026-09-17).
 *
 * Npcs: the real OSRS Deadman guards, imported into both 667 caches with
 * `OsrsNpcImportTool` batch `deadman-guard` (owner 2026-09-17: "gebruik exact de deadmanmode guard
 * van osrs, als t moet importeer je die"): OSRS 6582 "Guard" (Varrock, slash) -> [MELEE_GUARD_ID],
 * OSRS 11203 "Guard" (Varrock, ranged) -> [RANGED_GUARD_ID], OSRS 14792 "Wizguard" -> [WIZGUARD_ID].
 * Their cache definitions carry the wiki's combat level 1337 and the wiki names.
 *
 * Rules (all owner 2026-09-17 unless marked wiki):
 * - Guards react only to a PK-skulled player standing inside a guarded zone ([GuardedZones], the
 *   wiki polygons). Outside a zone nothing ever spawns on you, and a guard never fights from, or
 *   walks onto, a tile outside a zone ([mayAttack] checks both tiles; the per-cycle leash in
 *   `city_guards.plugin.kts` sends a stationed guard back to its post and removes a reactive one).
 * - Entering a zone skulled spawns a random group of at most two guards ([MAX_GUARDS_PER_PLAYER]):
 *   a melee guard that teleports onto you, a ranger that appears a few tiles away, and/or a
 *   Wizguard, drawn from [SPAWN_COMBINATIONS] (always at least one damaging guard). The same cap
 *   holds for the stationed guards on the owner's pins: a guard may only engage a player who has
 *   fewer than two guards on them already, so never more than two guards attack one player.
 * - Wiki: 2-tick attack speed, 800 hitpoints, damage ignores protection prayers, starts at 20% of
 *   the player's Hitpoints level and rises by 2 per attack, several guards regardless of
 *   single-combat, attackable only while the intruder is skulled inside the zone, and they stop the
 *   instant the player leaves the zone or loses the skull ([Combat.canEngage] re-checks [mayAttack]
 *   every cycle; [onZoneCheck] removes the reactive guards).
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

    val GUARD_IDS = setOf(MELEE_GUARD_ID, RANGED_GUARD_ID, WIZGUARD_ID)

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

    /** Patrol radius of a stationed guard around its post. */
    const val PATROL_RADIUS = 2

    /** How far from you the ranger appears ("1 ranger van ver"). */
    val RANGER_SPAWN_DISTANCE = 4..6

    const val GREETING = "We don't want your sort here, %s!"
    const val WIZGUARD_SHOUT = "You shall not pass!"

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

    /** Marks a guard spawned by [spawnReactiveGuards] (removed instead of leashed back). */
    val REACTIVE_GUARD_ATTR = AttributeKey<Boolean>()

    /** Per-cycle leash driver for every Deadman guard (armed in [configure]). */
    val GUARD_LEASH_TIMER = TimerKey()

    fun isGuard(npc: Npc): Boolean = npc.id in GUARD_IDS

    /** OSRS Wiki: "Protection prayers are ineffective against their damage." Shared by the melee
     * and ranged formulas so they cannot drift on which ids are exempt. */
    fun bypassesProtectionPrayer(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    /** OSRS Wiki: "Multiple guards are able to attack the player regardless of the location's
     * multicombat area status." */
    fun ignoresSingleCombat(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    /**
     * Whether [guard] may engage [target]: the target is skulled AND inside a guarded zone, the
     * guard itself stands inside a guarded zone, and the target does not already have
     * [MAX_GUARDS_PER_PLAYER] other guards on them. Re-checked every combat cycle by the shared
     * combat gate, which is what makes a guard "stop attacking the player immediately after they
     * leave the guarded area".
     */
    fun mayAttack(
        guard: Npc,
        target: Player,
    ): Boolean {
        if (!target.hasSkullIcon(SkullIcon.RED) || !isGuardedZone(target.tile)) return false
        if (!isGuardedZone(guard.tile)) return false
        return reserveSlot(target, guard)
    }

    fun isGuardedZone(tile: Tile): Boolean = GuardedZones.contains(tile)

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

    // ---- stationed guards ----

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

    /** Spawns one stationed guard per [GuardPosts] entry; returns a boot summary line. */
    fun spawnStationedGuards(world: World): String {
        var spawned = 0
        val unplaceable = ArrayList<String>()
        GuardPosts.ALL.forEach { post ->
            val tile = nearestWalkable(world, post.tile) { isGuardedZone(it) }
            if (tile == null) {
                unplaceable += "${post.city} ${post.tile.x},${post.tile.z}"
                return@forEach
            }
            val guard =
                Npc(if (post.ranged) RANGED_GUARD_ID else MELEE_GUARD_ID, tile, world).also {
                    it.respawnOverride = true
                    it.walkRadius = PATROL_RADIUS
                }
            world.spawn(guard)
            configure(guard)
            spawned++
        }
        return "CityGuards: stationed $spawned/${GuardPosts.ALL.size} Deadman guards" +
            (if (unplaceable.isEmpty()) "" else "; no walkable guarded tile near: $unplaceable")
    }

    /** Applies the per-instance state every Deadman guard needs (also on respawn via the
     * global npc-spawn hook). */
    fun configure(guard: Npc) {
        guard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
        guard.aggroCheck = { g, target -> mayAttack(g, target) }
        guard.timers[GUARD_LEASH_TIMER] = 2
    }

    /**
     * Per-cycle leash (owner 2026-09-17: "de guards mogen nooit een safe zone verlaten"). A guard
     * outside every guarded zone, or fighting a target it may no longer attack, is pulled back:
     * a reactive guard is removed, a stationed guard drops its fight and returns to its post.
     */
    fun leash(guard: Npc) {
        val world = guard.world
        val target = guard.getCombatTarget() as? Player
        val outside = !isGuardedZone(guard.tile)
        val badTarget = target != null && (!target.hasSkullIcon(SkullIcon.RED) || !isGuardedZone(target.tile))
        if (guard.attr[REACTIVE_GUARD_ATTR] == true) {
            if (outside && guard.isActive()) world.remove(guard)
            return
        }
        if (outside || badTarget) {
            Combat.reset(guard)
            guard.resetInteractions()
            guard.stopMovement()
            if (outside || guard.tile.getDistance(guard.spawnTile) > PATROL_RADIUS + 2) {
                guard.moveTo(guard.spawnTile)
            }
        }
    }

    // ---- reactive spawn-on-skulled-entry ----

    private val WAS_IN_GUARDED_ZONE_ATTR = AttributeKey<Boolean>()
    private val WIZGUARD_NEXT_CYCLE_ATTR = AttributeKey<Int>()

    /** Called every cycle for every online player from the shared per-cycle poll. */
    fun onZoneCheck(player: Player) {
        val world = player.world
        val inZone = isGuardedZone(player.tile)
        val wasInZone = player.attr[WAS_IN_GUARDED_ZONE_ATTR] ?: false
        player.attr[WAS_IN_GUARDED_ZONE_ATTR] = inZone

        if (!inZone || !player.hasSkullIcon(SkullIcon.RED)) {
            if (player.attr.has(REACTIVE_PLAN_ATTR) || (wasInZone && !inZone)) {
                release(player)
            }
            return
        }

        val plan = player.attr[REACTIVE_PLAN_ATTR]
        if (plan == null) {
            // A skulled player arriving (by foot or teleport) with a bank/deposit modal already
            // open must not keep banking behind the guards: close both sides of the bank UI.
            player.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
            player.closeInterface(dest = InterfaceDestination.TAB_AREA)
            despawnReactiveGuards(world, player)
            spawnReactiveGuards(world, player)
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

    /** Ends the guard response for [player]: reactive guards vanish, the ramp and cap reset.
     * Also used on logout and death. */
    fun release(player: Player) {
        resetDamageRamp(player)
        despawnReactiveGuards(player.world, player)
        player.attr.remove(REACTIVE_PLAN_ATTR)
        player.attr.remove(WIZGUARD_NEXT_CYCLE_ATTR)
        player.attr.remove(ENGAGED_STATIONED_ATTR)
    }

    /** Random group per [SPAWN_COMBINATIONS]; exposed for tests. */
    fun pickCombination(random: Int): List<Kind> = SPAWN_COMBINATIONS[Math.floorMod(random, SPAWN_COMBINATIONS.size)]

    private fun spawnReactiveGuards(
        world: World,
        player: Player,
    ) {
        val plan = pickCombination(world.random(SPAWN_COMBINATIONS.size - 1))
        player.attr[REACTIVE_PLAN_ATTR] = plan
        val spawned = ArrayList<WeakReference<Npc>>(2)
        plan.forEach { kind ->
            val (id, tile) =
                when (kind) {
                    Kind.MELEE -> MELEE_GUARD_ID to Tile(player.tile)
                    Kind.RANGED -> RANGED_GUARD_ID to rangerTile(world, player)
                    Kind.MAGE -> return@forEach // the Wizguard is driven by wizguardStrike
                }
            val guard =
                Npc(id, tile, world).also {
                    it.respawnOverride = false
                    it.walkRadius = 0
                    it.attr[REACTIVE_GUARD_ATTR] = true
                }
            world.spawn(guard)
            configure(guard)
            spawned += WeakReference(guard)
            player.attr[REACTIVE_GUARDS_ATTR] = spawned
            guard.forceChat(GREETING.format(player.username))
            if (guard.canEngageCombat(player)) {
                guard.attack(player)
            }
        }
        player.attr[REACTIVE_GUARDS_ATTR] = spawned
    }

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
        return nearestWalkable(world, player.tile.transform(1, 0), radius = 3) { isGuardedZone(it) } ?: Tile(player.tile)
    }

    private fun despawnReactiveGuards(
        world: World,
        player: Player,
    ) {
        player.attr[REACTIVE_GUARDS_ATTR]?.forEach { ref ->
            ref.get()?.let { guard -> if (guard.isActive()) world.remove(guard) }
        }
        player.attr.remove(REACTIVE_GUARDS_ATTR)
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
        val tile = nearestWalkable(world, player.tile.transform(1, 0), radius = 2) { isGuardedZone(it) } ?: Tile(player.tile)
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
