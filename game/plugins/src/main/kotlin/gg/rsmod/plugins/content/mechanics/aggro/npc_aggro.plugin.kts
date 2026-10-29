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

/** N417: failed route searches in a row after which an npc gives up a target it cannot hit from where it stands. */
val UNREACHABLE_FAILS = 8

/** N417: how long (ticks) an npc leaves a player alone after giving him up as unreachable. */
val UNREACHABLE_TICKS = 50

/** N417: the player an npc gave up as unreachable, and the cycle until which it leaves him alone. */
val UNREACHABLE_ATTR = AttributeKey<Pair<String, Int>>()

/** Above this aggro radius [checkRadius] walks the player list instead of every tile of the square. */
val PLAYER_LIST_SCAN_RADIUS = 16

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

    // OSRS Wiki Crypt rat: "As with all monsters in the crypts, they are aggressive to any player regardless of their combat level."
    // Checked before the 10-minute tolerance: Darkan BarrowsController keeps every crypt creature hunting (darkan-bug-map 7i).
    if (gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.aggressiveRegardlessOfLevel(n)) {
        return@boolean n.tile.height == p.tile.height && world.collision.raycast(n.tile, p.tile, projectile = true)
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
    // Owner 2026-10-03 20:46 ("verac the defiled loses agro ... when u 1 hit him then clicking away") and E21 (npcs not facing you):
    // the same keep-the-target rule for EVERY npc, not only breach monsters. An npc that is not being hit itself has no
    // ACTIVE_COMBAT_TIMER, so this search ran against its live fight every 1-3 ticks: the acquisition rules (hunt radius 5,
    // tolerance, the cowardly level cap, "target already under attack" - by this very npc, in single-way) refused its own target
    // and the npc dropped the fight (Verac idle 23 s), or npc.attack() restarted the fight and its resetInteractions cleared the
    // npc's facing for a tick each time. The acquisition rules pick a NEW target; the combat cycle alone ends a fight
    // (canEngage / NpcLeash.mayPursue) and walks the npc home.
    val currentTarget = npc.getCombatTarget()
    val attackRange = gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackRange(npc) ?: 1
    // N417 (Hill giant 117 at 3111,9841: 89 failed routes to the player, never one hit, every other giant refused him as "picked by
    // another"): a target the npc keeps failing to reach and cannot hit from where it stands is dropped, and that player is left
    // alone by this npc for [UNREACHABLE_TICKS] so the next search does not pick him straight back.
    val unreachable =
        currentTarget != null && (npc.attr[gg.rsmod.game.model.attr.NPC_ROUTE_FAILS] ?: 0) >= UNREACHABLE_FAILS &&
            !npc.hasLineOfSightTo(currentTarget, projectile = attackRange > 1, maximumDistance = attackRange)
    if (unreachable) {
        val player = currentTarget as? Player
        if (player != null) {
            npc.attr[UNREACHABLE_ATTR] = player.username to (world.currentCycle + UNREACHABLE_TICKS)
            if (player.getAggressor() === npc) player.attr.remove(AGGRESSOR)
        }
        npc.attr.remove(gg.rsmod.game.model.attr.NPC_ROUTE_FAILS)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            "aggro npc ${npc.id}#${npc.index} drops unreachable ${(currentTarget as? Player)?.username ?: currentTarget}"
        }
    }
    val pursuing =
        !unreachable && currentTarget != null && !currentTarget.isDead() && Combat.canEngage(npc, currentTarget) &&
            NpcLeash.mayPursue(npc, currentTarget, attackRange)
    if (unreachable) {
        // N417: give the unreachable target up the way a lost target is dropped below, whatever the combat timers say.
        npc.stopMovement()
        npc.resetInteractions()
        npc.resetFacePawn()
        npc.interruptQueues()
        NpcLeash.returnHome(npc)
    } else if (pursuing) {
        // keep fighting. The combat cycle faces its target every cycle; a target with no face means the fight loop was ended by
        // something else (an interrupted queue) while the target stayed set - start it again instead of standing idle.
        if (npc.attr[gg.rsmod.game.model.attr.FACING_PAWN_ATTR]?.get() !== currentTarget) npc.attack(currentTarget!!)
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
    if (radius > PLAYER_LIST_SCAN_RADIUS) {
        // A whole-chamber hunt (GWD generals, radius 64) would look up 129 x 129 tiles every search: walk the player list instead,
        // over the same square.
        world.players.forEach { p ->
            val dx = p.tile.x - npc.tile.x
            val dz = p.tile.z - npc.tile.z
            if (p.tile.height == npc.tile.height && dx >= -radius && dx < radius + size && dz >= -radius && dz < radius + size &&
                mayTarget(npc, p, range)
            ) {
                candidates += p
            }
        }
    } else {
        for (x in -radius until radius + size) {
            for (z in -radius until radius + size) {
                val tile = npc.tile.transform(x, z)
                val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
                chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { p ->
                    if (p !in candidates && mayTarget(npc, p, range)) candidates += p
                }
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
    // Debug toolkit (PvM): every aggressive pick, so "this npc attacks me / never attacks me" has evidence for all npcs.
    gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
        "aggro npc ${npc.id}#${npc.index} at ${npc.tile.x},${npc.tile.z} picks ${target.username} (radius $radius, ${candidates.size} candidate(s))"
    }
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
    // N417: the player this npc just gave up on as unreachable.
    npc.attr[UNREACHABLE_ATTR]?.let { (name, until) -> if (name == target.username && world.currentCycle < until) return "unreachable" }
    if (!npc.canEngageCombat(target)) return "cannot-engage"
    if (!world.plugins.canAttack(npc, target)) return "can_attack-plugin"
    if (!Combat.canEngage(npc, target)) return "combat-canEngage"
    if (!NpcLeash.mayPursue(npc, target, range)) return "leash"
    // Single-way: a player someone else is already fighting is not a target (the combat cycle would refuse it).
    if (!target.tile.isMulti(world) && target.isAttackedByOtherThan(npc)) return "single-under-attack"
    // ... also when another npc picked him on this same tick and has not hit yet (idle breach monsters chose him together).
    val aggressor = target.getAggressor()
    if (!target.tile.isMulti(world) && aggressor is Npc && aggressor !== npc && !aggressor.isDead() && world.npcs.contains(aggressor) && aggressor.getCombatTarget() === target) return "single-picked-by-other"
    // ... and for breach monsters any other live breach npc that targets him (a retaliating one never sets the aggressor attribute)
    if (!target.tile.isMulti(world) && gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc) &&
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.live.any { it !== npc && !it.isDead() && it.getCombatTarget() === target }
    ) {
        return "single-targeted-by-other"
    }
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
