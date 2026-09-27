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
    // Breach monsters (owner 2026-09-23: "they need to be aggro and they need to attack multiple players"): the in-combat timer
    // outlives the fight by several ticks, so after its target died, teleported or ran out of the leash a breach monster stood
    // idle while other players walked past it. A breach monster without a live target hunts again at once.
    val breach = gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc)
    val breachIdle = breach && npc.getCombatTarget().let { it == null || it.isDead() }
    // Breach audit 2026-09-27 (B4): a breach monster that still has a live target it may pursue keeps it. The search below
    // looks only 8 tiles around the monster and dropped the target when nobody was that close, so the effective chase was 8
    // tiles instead of the leash (DeadmanBreach.CHASE_RANGE); it also re-picked a random player on every search.
    val breachPursuing =
        breach && npc.getCombatTarget().let { t ->
            t != null && !t.isDead() && NpcLeash.mayPursue(npc, t, gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackRange(npc) ?: 1)
        }
    if (breachPursuing) {
        // keep fighting
    } else if ((!npc.timers.has(ACTIVE_COMBAT_TIMER) || breachIdle || breach) &&
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
            if (breach) {
                gg.rsmod.game.model.BreachTrace.log("target.drop") {
                    val t = npc.getCombatTarget()
                    "npc=${npc.id}#${npc.index} target=${(t as? Player)?.username ?: t?.toString() ?: "-"} dead=${t?.isDead()} reason=no-candidate-and-not-pursuable"
                }
            }
            npc.stopMovement()
            npc.resetInteractions()
            npc.resetFacePawn()
            npc.interruptQueues()
            // Breach conformance 2026-09-27: a target lost this way (teleported, logged out, out of reach) left the npc standing
            // where it was - only the leash path walked it home (B5). Same rule here: back to its spawn area.
            NpcLeash.returnHome(npc)
        }
    }
    npc.timers[AGGRO_CHECK_TIMER] =
        if (npc.combatDef.aggroTargetDelay <= 0) world.random(1..3) else npc.combatDef.aggroTargetDelay
}

/**
 * Picks a random player the npc can really fight within its aggro radius and attacks it.
 *
 * Breach audit 2026-09-27 (B4): the search stopped at the first tile (scanning from the south-west corner) that held any player
 * the aggro rule accepted, and attacked him even when the attack would be refused a tick later (PvP protection, the single-way
 * "someone else is fighting him" rule, a target outside the leash): the refused player "held" the npc forever and every idle npc
 * in single-way combat piled onto the same player. Now every candidate that passes the real attack checks is collected first,
 * refused ones are skipped, and one is picked at random. The radius is measured around the npc's whole footprint.
 */
fun checkRadius(npc: Npc): Boolean {
    val radius = aggroRadius(npc)
    val size = npc.getSize().coerceAtLeast(1)
    val range = gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackRange(npc) ?: 1
    val candidates = ArrayList<Player>()
    for (x in -radius until radius + size) {
        for (z in -radius until radius + size) {
            val tile = npc.tile.transform(x, z)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
            chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { p ->
                if (p !in candidates && mayTarget(npc, p, range)) candidates += p
            }
        }
    }
    val target = candidates.randomOrNull()
    if (gg.rsmod.game.model.BreachTrace.active() && gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc)) {
        // Breach conformance trace: every player in the hunt square with the rule that accepted or refused him, and the pick.
        val seen = ArrayList<String>()
        for (x in -radius until radius + size) for (z in -radius until radius + size) {
            val tile = npc.tile.transform(x, z)
            world.chunks.get(tile, createIfNeeded = false)?.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT)?.forEach { p ->
                seen += "${p.username}:${refusal(npc, p, range) ?: "ok"}"
            }
        }
        gg.rsmod.game.model.BreachTrace.log("aggro.search") {
            "npc=${npc.id}#${npc.index} at=${npc.tile.x},${npc.tile.z} radius=$radius old=${(npc.getCombatTarget() as? Player)?.username ?: "-"} " +
                "picked=${target?.username ?: "-"} candidates=[${seen.joinToString(" ")}]"
        }
    }
    target ?: return false
    if (npc.getCombatTarget() != target && target.getAggressor() == null) {
        target.attr[AGGRESSOR] = WeakReference(npc)
    }
    npc.attack(target)
    return true
}

/** Every check the attack itself would make, so a player that would be refused is never picked. */
fun mayTarget(
    npc: Npc,
    target: Player,
    range: Int,
): Boolean = refusal(npc, target, range) == null

/** Why [npc] may not pick [target] (null = it may); the breach trace logs the reason per candidate. */
fun refusal(
    npc: Npc,
    target: Player,
    range: Int,
): String? {
    if (!canAttack(npc, target)) return "aggro-rule"
    if (!npc.canEngageCombat(target)) return "cannot-engage"
    if (!world.plugins.canAttack(npc, target)) return "can_attack-plugin"
    if (!Combat.canEngage(npc, target)) return "combat-canEngage"
    if (!NpcLeash.mayPursue(npc, target, range)) return "leash"
    // Single-way: a player someone else is already fighting is not a target (the combat cycle would refuse it).
    if (!target.tile.isMulti(world) && target.isBeingAttacked() && target.getLastHitBy() != npc) return "single-under-attack"
    // ... also when another npc picked him on this same tick and has not hit yet (idle breach monsters chose him together).
    val aggressor = target.getAggressor()
    if (!target.tile.isMulti(world) && aggressor is Npc && aggressor !== npc && !aggressor.isDead() && aggressor.getCombatTarget() === target) return "single-picked-by-other"
    return null
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
