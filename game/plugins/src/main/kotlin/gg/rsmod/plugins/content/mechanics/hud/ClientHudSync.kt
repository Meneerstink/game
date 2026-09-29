package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
import gg.rsmod.plugins.content.items.potion.DivinePotions
import gg.rsmod.plugins.content.mechanics.restoration.BoostedCombatStats
import gg.rsmod.plugins.content.mechanics.restoration.RestorationRates

/**
 * End-of-cycle server -> client sync for the Boosts Information, Opponent Information and Timers & Buffs overlays (owner
 * 2026-09-29). Everything is read from the live server state every tick - the timers in [Player.timers], the Vengeance
 * attribute, the restoration timers, the combat target - and only a change is sent ([ClientHud] payloads). A countdown
 * is resent only when the server value leaves the client's own countdown (a new cast, a shortened Teleblock, a freeze
 * broken by distance, a death, a relog), so every writer of these states is covered without hooking each one.
 */
object ClientHudSync {
    /**
     * The client counts each value down itself; a server value that leaves that countdown by more than this is a real change.
     * The sync runs at the same point of every cycle (end), so a plain countdown never deviates at all.
     */
    private const val JITTER = 0

    /** Opponent Information: a target further than this (or on another level) is no longer shown. */
    private const val OPPONENT_RANGE = 15

    private class State {
        var cycle = 0
        var boost = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE)
        var timers = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
        var opponent: String? = null
    }

    private val STATE = AttributeKey<State>()

    /** A fresh session (login / relog): the client starts empty, so everything is sent again. */
    fun reset(player: Player) {
        player.attr[STATE] = State()
    }

    fun tick(player: Player) {
        // Not logged in yet (on_login creates the state): nothing is sent before the login push.
        val state = player.attr[STATE] ?: return
        val now = player.world.currentCycle
        val elapsed = now - state.cycle

        val boost = intArrayOf(boostDecay(player), loweredRestore(player))
        val timers =
            intArrayOf(
                if (player.attr[VENGEANCE_ACTIVE_ATTR] == true) 1 else 0,
                remaining(player, VENGEANCE_COOLDOWN),
                remaining(player, TELEBLOCK_TIMER),
                remaining(player, FROZEN_TIMER),
            )
        val boostChanged = boost.indices.any { drifted(state.boost[it], boost[it], elapsed) }
        // Index 0 is a flag, not a countdown.
        val timersChanged = state.timers[0] != timers[0] || (1 until timers.size).any { drifted(state.timers[it], timers[it], elapsed) }

        if (boostChanged || timersChanged) {
            // Both groups are countdowns from the same cycle, so they are always sent together from here.
            ClientHud.send(player, "boost|${boost[0]}|${boost[1]}")
            ClientHud.send(player, "tmr|${timers.joinToString("|")}")
            state.boost = boost
            state.timers = timers
            state.cycle = now
        }

        val opponent = opponentPayload(player)
        if (opponent != state.opponent) {
            ClientHud.send(player, opponent)
            state.opponent = opponent
        }
    }

    private fun remaining(
        player: Player,
        key: TimerKey,
    ): Int = if (player.timers.has(key)) player.timers[key] else 0

    private fun drifted(
        sent: Int,
        actual: Int,
        elapsed: Int,
    ): Boolean {
        if (sent < 0 || actual < 0) return sent != actual
        val expected = maxOf(0, sent - elapsed)
        if ((expected == 0) != (actual == 0)) return true
        return kotlin.math.abs(expected - actual) > JITTER
    }

    private fun boostDecay(player: Player): Int {
        val boosted =
            BoostedCombatStats.skills.any {
                player.skills.getCurrentLevel(it) > player.skills.getMaxLevel(it) && !DivinePotions.protects(player, it)
            }
        return if (boosted) BoostedCombatStats.ticksUntilDecay(player) else -1
    }

    private fun loweredRestore(player: Player): Int {
        val lowered = BoostedCombatStats.skills.any { player.skills.getCurrentLevel(it) < player.skills.getMaxLevel(it) }
        return if (lowered) RestorationRates.ticksUntilLoweredRestore(player) else -1
    }

    /**
     * The player's active opponent: the combat target, or - after a manual single cast, which ends the fight but keeps
     * facing the target - the pawn the player faces, once the two have fought. Gone as soon as neither holds.
     */
    fun opponent(player: Player): Pawn? {
        val pawn =
            player.attr[COMBAT_TARGET_FOCUS_ATTR]?.get()
                ?: player.attr[FACING_PAWN_ATTR]?.get()?.takeIf { it.damageMap[player] != null || player.damageMap[it] != null }
                ?: return null
        if (pawn === player) return null
        if (pawn is Npc && (!pawn.isSpawned() || pawn.getMaximumLifepoints() <= 0)) return null
        if (pawn is Player && !pawn.isOnline) return null
        if (pawn.tile.height != player.tile.height || !pawn.tile.isWithinRadius(player.tile, OPPONENT_RANGE)) return null
        return pawn
    }

    fun opponentPayload(player: Player): String {
        val pawn = opponent(player) ?: return "opp|none"
        val max = pawn.getMaximumLifepoints()
        val cur = pawn.getCurrentLifepoints()
        // The hit mask's own ratio (PlayerUpdateBlockSegment / NpcUpdateBlockSegment), so the bar matches the one over the head.
        val ratio = if (max <= 0) 0 else if (cur > max) 255 else cur * 255 / max
        return if (pawn is Npc) "opp|n|${pawn.index}|$ratio|$cur|$max" else "opp|p|${pawn.index}|$ratio"
    }
}
