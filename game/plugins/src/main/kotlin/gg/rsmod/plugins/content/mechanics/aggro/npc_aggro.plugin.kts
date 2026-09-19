package gg.rsmod.plugins.content.mechanics.aggro

import gg.rsmod.game.model.attr.AGGRESSOR
import gg.rsmod.game.model.attr.LAST_MAP_BUILD_TIME
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.plugins.content.areas.godwars.GodWars
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import java.lang.ref.WeakReference
import kotlin.math.abs

val AGGRO_CHECK_TIMER = TimerKey()

// RCV-012.B14: load data/cfg/npcs/hunt-modes.json when the plugin loads, so a missing or malformed table fails at boot instead of
// inside every aggressive npc's AGGRO_CHECK_TIMER.
val huntModeTable = NpcHuntModes.table

val defaultAggressiveness: (Npc, Player) -> Boolean = boolean@{ n, p ->
    /*
     * God Wars Dungeon npcs hunt through their own Void hunt mode (see GodWars.HuntMode), not the bulk table's
     * aggressive flag or combat-level tolerance.
     */
    if (GodWars.inDungeon(n.tile)) {
        val god = GodWars.God.forNpc(n)
        if (god != null) {
            // Void check_visual "line_of_sight" (CollisionManager.raycast requires one height level)
            val seen = { n.tile.height == p.tile.height && world.collision.raycast(n.tile, p.tile, projectile = true) }
            when (GodWars.huntMode(n.id)) {
                GodWars.HuntMode.GENERAL -> return@boolean seen()
                GodWars.HuntMode.FOLLOWER -> return@boolean !GodWars.followerIgnores(p, god) && seen()
                GodWars.HuntMode.COWARDLY -> return@boolean p.combatLevel <= n.def.combatLevel * 2 && seen()
                null -> if (god != GodWars.God.ZAROS) return@boolean false // Nex's encounter keeps its own rule below
            }
            return@boolean !GodWars.isProtected(p, god)
        }
    }
    if (n.combatDef.aggressiveTimer == Int.MAX_VALUE) {
        return@boolean true
    } else if (n.combatDef.aggressiveTimer == Int.MIN_VALUE) {
        return@boolean false
    }

    if (abs(world.currentCycle - (p.attr[LAST_MAP_BUILD_TIME] ?: 0)) > n.combatDef.aggressiveTimer) {
        return@boolean false
    }

    val npcLvl = n.def.combatLevel
    // RCV-012.B14: an npc that also has a Void player hunt mode applies that mode's checks (Void Hunting.canHunt) - e.g. "aggressive"
    // has no level cap - instead of the one "cowardly" level rule every other npc keeps (NpcHuntModes).
    val mode = NpcHuntModes.table.mode(n.id) ?: return@boolean p.combatLevel <= npcLvl * 2
    val visible =
        when (mode.checkVisual) {
            "line_of_sight" -> n.tile.height == p.tile.height && world.collision.raycast(n.tile, p.tile, projectile = true)
            "line_of_walk" -> n.tile.height == p.tile.height && world.collision.raycast(n.tile, p.tile, projectile = false)
            else -> true
        }
    return@boolean NpcHuntModes.allows(
        mode,
        playerCombatLevel = p.combatLevel,
        npcCombatLevel = npcLvl,
        playerUnderAttack = p.timers.has(ACTIVE_COMBAT_TIMER),
        playerInMulti = p.tile.isMulti(world),
        playerMenuOpen = world.plugins.isMenuOpened(p),
        visible = visible,
    )
}

/**
 * GWD hunters use Void's hunt range; an aggressive npc with a Void player hunt mode uses its Void hunt_range (default 5, RCV-012.B14);
 * everything else the combat definition's aggressive radius. A Void hunt mode never makes a non-aggressive npc aggressive here.
 */
fun aggroRadius(npc: Npc): Int =
    if (GodWars.inDungeon(npc.tile) && GodWars.huntMode(npc.id) != null) {
        GodWars.huntRange(npc.id)
    } else {
        if (npc.combatDef.aggressiveRadius <= 0) 0 else NpcHuntModes.table.range(npc.id) ?: npc.combatDef.aggressiveRadius
    }

on_global_npc_spawn {
    if (aggroRadius(npc) > 0) {
        npc.aggroCheck =
            if (CityGuards.isGuard(npc)) {
                { guard, player -> CityGuards.mayAttack(guard, player) }
            } else {
                defaultAggressiveness
            }
        npc.timers[AGGRO_CHECK_TIMER] = 1
    }
}

on_timer(AGGRO_CHECK_TIMER) {
    if (!npc.timers.has(ACTIVE_COMBAT_TIMER) &&
        npc.lock.canAttack() &&
        npc.isActive()) {
        // A Deadman guard may pursue a valid skulled target anywhere inside the guarded polygon.
        // The 8-tile aggro radius is acquisition range, not a combat leash. Resetting here used
        // to drop the target as soon as it moved eight tiles away and cleared face-pawn state.
        val pursuingGuard =
            CityGuards.isGuard(npc) &&
                (npc.getCombatTarget() as? Player)?.let { CityGuards.mayPursue(npc, it) } == true
        // Only an npc that actually had a target has anything to drop. Running this for an idle
        // npc stopped its movement on every aggro search (every cycle for a Deadman guard), which
        // cancelled each patrol / random-walk route on its first step (owner 2026-09-18: guards
        // "do not roam") and cleared the face-pawn of an aggressive npc a player was talking to.
        val hadTarget = npc.getCombatTarget() != null
        if (!checkRadius(npc) && !pursuingGuard && hadTarget) {
            npc.stopMovement()
            npc.resetInteractions()
            npc.resetFacePawn()
            npc.interruptQueues()
        }
    }
    npc.timers[AGGRO_CHECK_TIMER] =
        if (npc.combatDef.aggroTargetDelay <= 0) world.random(1..3) else npc.combatDef.aggroTargetDelay
}

fun checkRadius(npc: Npc): Boolean {
    val radius = aggroRadius(npc)
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            val tile = npc.tile.transform(x, z)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue

            val players = chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT)
            if (players.isEmpty()) {
                continue
            }

            val targets = players.filter { canAttack(npc, it) }
            if (targets.isEmpty()) {
                continue
            }

            val target = targets.random()
            if (npc.canEngageCombat(target)) {
                if (npc.getCombatTarget() != target && target.getAggressor() == null) {
                    target.attr[AGGRESSOR] = WeakReference(npc)
                }
                npc.attack(target)
                return true
            }
        }
    }
    return false
}

fun canAttack(
    npc: Npc,
    target: Player,
): Boolean {
    if (!target.isOnline || target.invisible) {
        return false
    }
    return npc.aggroCheck == null || npc.aggroCheck?.invoke(npc, target) == true
}
