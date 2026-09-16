package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.content.combat.canEngageCombat

/**
 * Deadman Mode guards (OSRS Wiki "Guard (Deadman Mode)" / "Wizguard", owner instruction 2026-09-16).
 *
 * - Level 1337 guards are stationed at every owner-pinned post ([GuardPosts]) inside the guarded
 *   cities ([GuardedZones]) and patrol a small radius around it.
 * - Any PK-skulled player inside a guarded zone additionally gets a melee + ranged guard spawned
 *   on top of them ("We don't want your sort here, [name]!") and a Wizguard that yells "You shall
 *   not pass!", casts Ice Barrage once (freezing for 5 ticks, not reduced by Protect from Magic) and
 *   disappears; it reappears every 10 ticks while the player stays skulled inside the zone.
 * - Guards attack every 2 ticks (1.2 s), their damage ignores protection prayers, starts at 20% of
 *   the player's Hitpoints level and rises by 2 per attack, they ignore single-combat rules
 *   (several guards at once), can only be attacked while the intruder is skulled inside the zone,
 *   and stop the instant the player leaves the zone or loses the skull
 *   ([gg.rsmod.plugins.content.combat.Combat.canEngage] re-checks [mayAttack] every cycle).
 */
object CityGuards {
    /** Unused "Guard" cache id (Ardougne guard model; every neighbour 1142-1150 is spawned in
     * region 11421, this one is not), so its combat def can be dedicated to the Deadman guard
     * without touching any ordinary guard in the world. */
    const val MELEE_GUARD_ID = Npcs.GUARD_1145

    /** Falador crossbow "Guard" cache id with no world spawn (removed from the ordinary
     * guard_level_21 definition list so the two defs cannot collide). */
    const val RANGED_GUARD_ID = Npcs.GUARD_3231

    /** Wizard-robed cache id with no other use in this codebase: the Wizguard. */
    const val WIZGUARD_ID = Npcs.WIZARD_12231

    val GUARD_IDS = setOf(MELEE_GUARD_ID, RANGED_GUARD_ID, WIZGUARD_ID)

    /** OSRS Wiki: "Combat Level: 1337". Sent through the COMBAT_LEVEL extended-info block. */
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

    const val GREETING = "We don't want your sort here, %s!"
    const val WIZGUARD_SHOUT = "You shall not pass!"

    fun isGuard(npc: Npc): Boolean = npc.id in GUARD_IDS

    /** OSRS Wiki: "Protection prayers are ineffective against their damage." Shared by the melee
     * and ranged formulas so they cannot drift on which ids are exempt. */
    fun bypassesProtectionPrayer(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    /** OSRS Wiki: "Multiple guards are able to attack the player regardless of the location's
     * multicombat area status." */
    fun ignoresSingleCombat(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    /**
     * Whether [guard] may engage [target]: skulled AND inside a guarded zone. Re-checked every
     * combat cycle by the shared combat gate, which is what makes a guard "stop attacking the
     * player immediately after they leave the guarded area".
     */
    fun mayAttack(
        @Suppress("UNUSED_PARAMETER") guard: Npc,
        target: Player,
    ): Boolean = target.hasSkullIcon(SkullIcon.RED) && isGuardedZone(target.tile)

    fun isGuardedZone(tile: Tile): Boolean = GuardedZones.contains(tile)

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
    ): Tile? {
        if (!world.collision.isClipped(tile)) return tile
        for (r in 1..radius) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                    val candidate = tile.transform(dx, dz)
                    if (!world.collision.isClipped(candidate)) return candidate
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
            val tile = nearestWalkable(world, post.tile)
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
            (if (unplaceable.isEmpty()) "" else "; no walkable tile near: $unplaceable")
    }

    /** Applies the per-instance state every Deadman guard needs (also on respawn via the
     * global npc-spawn hook). */
    fun configure(guard: Npc) {
        guard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
        guard.aggroCheck = { g, target -> mayAttack(g, target) }
    }

    // ---- reactive spawn-on-skulled-entry ----

    private val WAS_IN_GUARDED_ZONE_ATTR = AttributeKey<Boolean>()
    private val REACTIVE_GUARDS_ATTR = AttributeKey<MutableList<java.lang.ref.WeakReference<Npc>>>()
    private val WIZGUARD_NEXT_CYCLE_ATTR = AttributeKey<Int>()

    /** Called every cycle for every online player from the shared per-cycle poll. */
    fun onZoneCheck(player: Player) {
        val world = player.world
        val inZone = isGuardedZone(player.tile)
        val wasInZone = player.attr[WAS_IN_GUARDED_ZONE_ATTR] ?: false
        player.attr[WAS_IN_GUARDED_ZONE_ATTR] = inZone

        if (!inZone || !player.hasSkullIcon(SkullIcon.RED)) {
            if (player.attr.has(REACTIVE_GUARDS_ATTR) || (wasInZone && !inZone)) {
                resetDamageRamp(player)
                despawnReactiveGuards(world, player)
                player.attr.remove(WIZGUARD_NEXT_CYCLE_ATTR)
            }
            return
        }

        val existing = player.attr[REACTIVE_GUARDS_ATTR]?.mapNotNull { it.get() }?.filter { it.isActive() } ?: emptyList()
        if (existing.isEmpty()) {
            // A skulled player arriving (by foot or teleport) with a bank/deposit modal already
            // open must not keep banking behind the guards: close both sides of the bank UI.
            player.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
            player.closeInterface(dest = InterfaceDestination.TAB_AREA)
            despawnReactiveGuards(world, player)
            spawnReactiveGuards(world, player)
            player.attr[WIZGUARD_NEXT_CYCLE_ATTR] = world.currentCycle
        }
        val next = player.attr[WIZGUARD_NEXT_CYCLE_ATTR] ?: world.currentCycle
        if (world.currentCycle >= next) {
            wizguardStrike(world, player)
            player.attr[WIZGUARD_NEXT_CYCLE_ATTR] = world.currentCycle + WIZGUARD_REAPPEAR_CYCLES
        }
    }

    private fun spawnReactiveGuards(
        world: World,
        player: Player,
    ) {
        val spawned = ArrayList<java.lang.ref.WeakReference<Npc>>(2)
        for (guardId in listOf(MELEE_GUARD_ID, RANGED_GUARD_ID)) {
            val guard =
                Npc(guardId, Tile(player.tile), world).also {
                    it.respawnOverride = false
                    it.walkRadius = 0
                }
            world.spawn(guard)
            configure(guard)
            guard.forceChat(GREETING.format(player.username))
            if (guard.canEngageCombat(player)) {
                guard.attack(player)
            }
            spawned += java.lang.ref.WeakReference(guard)
        }
        player.attr[REACTIVE_GUARDS_ATTR] = spawned
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
        val tile = nearestWalkable(world, player.tile.transform(1, 0), radius = 2) ?: Tile(player.tile)
        val wizguard =
            Npc(WIZGUARD_ID, tile, world).also {
                it.respawnOverride = false
                it.walkRadius = 0
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
