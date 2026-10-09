package gg.rsmod.plugins.content.mechanics.hud

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.attr.VENGEANCE_ACTIVE_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.POISON_IMMUNITY
import gg.rsmod.game.model.timer.POISON_TIMER
import gg.rsmod.game.model.timer.VENOM_IMMUNITY
import gg.rsmod.game.model.timer.VENOM_TIMER
import gg.rsmod.plugins.content.items.osrs.Burns
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN
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

    /**
     * RuneLite OpponentInfoPlugin: `WAIT = Duration.ofSeconds(5)` - the last opponent stays shown until five seconds after
     * the interaction ended (owner 2026-09-29: "de opponent information verdwijnt gelijk ... dit hoort niet").
     */
    const val HOLD_MS = 5_000L

    private class State {
        var cycle = 0
        var boost = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
        var timers = IntArray(TIMER_FIELDS) { Int.MIN_VALUE }
        var opponent: String? = null
        var last: java.lang.ref.WeakReference<Pawn>? = null
        var lastPayload: String? = null
        var endedCycle = -1
    }

    private val STATE = AttributeKey<State>()

    /** Fields of the `tmr` payload (ClientHud); the ones in [TIMER_FLAGS] are states compared exactly, the rest countdowns. */
    const val TIMER_FIELDS = 12
    private val TIMER_FLAGS = setOf(0, 4, 5, 9)

    /** A fresh session (login / relog): the client starts empty, so everything is sent again. */
    fun reset(player: Player) {
        player.attr[STATE] = State()
    }

    fun tick(player: Player) {
        // Not logged in yet (on_login creates the state): nothing is sent before the login push.
        val state = player.attr[STATE] ?: return
        val now = player.world.currentCycle
        val elapsed = now - state.cycle

        // The three restoration cycles are always sent; the client's "next buff / debuff change" settings (RuneLite's
        // ALWAYS / BOOSTED / NEVER) decide what shows.
        val boost =
            intArrayOf(
                BoostedCombatStats.ticksUntilDecay(player),
                RestorationRates.ticksUntilLoweredRestore(player),
                RestorationRates.ticksUntilBoostedRestore(player),
            )
        val timers = timers(player)
        val boostChanged = boost.indices.any { drifted(state.boost[it], boost[it], elapsed) }
        // Index 0 is a flag, not a countdown.
        val timersChanged =
            timers.indices.any { if (it in TIMER_FLAGS) state.timers[it] != timers[it] else drifted(state.timers[it], timers[it], elapsed) }

        if (boostChanged || timersChanged) {
            // Both groups are countdowns from the same cycle, so they are always sent together from here.
            ClientHud.send(player, "boost|${boost.joinToString("|")}")
            ClientHud.send(player, "tmr|${timers.joinToString("|")}")
            state.boost = boost
            state.timers = timers
            state.cycle = now
        }

        val opponent = heldOpponent(player, state)
        if (opponent != state.opponent) {
            ClientHud.send(player, opponent)
            state.opponent = opponent
        }
    }

    /**
     * The `tmr` fields, all live server state: Vengeance (active flag, cooldown), Teleblock, freeze, then the damage-over-time block -
     * poison/venom (0 none, 1 poison, 2 venom), the damage of its next hit (Poison.getDamageForTicks / Venom.damageForTick, the exact
     * numbers the timer plugins deal), ticks to that hit (the POISON_TIMER / VENOM_TIMER itself), antipoison and antivenom immunity,
     * burn stacks (= the next combined burn hit) and ticks to that hit (the Burns cycle driver), then ticks until regular poison
     * wears off (RuneLite Poison tooltip "Time until cure": the next hit, then one hit per 30 ticks for the hits left; venom never).
     */
    fun timers(player: Player): IntArray {
        val venomTicks = player.attr[VENOM_TICKS_ELAPSED_ATTR]
        val poisonTicks = player.attr[POISON_TICKS_LEFT_ATTR]
        val dot =
            when {
                venomTicks != null && player.timers.has(VENOM_TIMER) -> 2
                poisonTicks != null && poisonTicks >= 0 && player.timers.has(POISON_TIMER) -> 1
                else -> 0
            }
        val dotDamage =
            when (dot) {
                2 -> Venom.damageForTick(venomTicks!!)
                1 -> Poison.getDamageForTicks(poisonTicks!!)
                else -> 0
            }
        val dotNext =
            when (dot) {
                2 -> remaining(player, VENOM_TIMER)
                1 -> remaining(player, POISON_TIMER)
                else -> 0
            }
        val burns = Burns.nextHitDamage(player)
        val burnNext = if (burns > 0) ((player.attr[Burns.NEXT_HIT_CYCLE] ?: 0) - player.world.currentCycle).coerceAtLeast(1) else 0
        return intArrayOf(
            if (player.attr[VENGEANCE_ACTIVE_ATTR] == true) 1 else 0,
            remaining(player, VENGEANCE_COOLDOWN),
            remaining(player, TELEBLOCK_TIMER),
            remaining(player, FROZEN_TIMER),
            dot,
            dotDamage,
            dotNext,
            remaining(player, POISON_IMMUNITY),
            remaining(player, VENOM_IMMUNITY),
            burns,
            burnNext,
            if (dot == 1) dotNext + poisonTicks!! * Poison.POISON_TICK_DELAY else 0,
        )
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

    /** Cycles of [HOLD_MS] at this server's tick length (fast ticks in the headless tests). */
    fun holdCycles(player: Player): Int {
        val cycle = player.world.gameContext.cycleTime.toLong().coerceAtLeast(1L)
        return ((HOLD_MS + cycle - 1) / cycle).toInt()
    }

    /** The opponent to show: the live one, else the last one until [HOLD_MS] after the fight ended (its health kept fresh). */
    private fun heldOpponent(
        player: Player,
        state: State,
    ): String {
        val now = player.world.currentCycle
        val live = opponent(player)
        if (live != null) {
            state.last = java.lang.ref.WeakReference(live)
            state.endedCycle = -1
            return payloadOf(live).also { state.lastPayload = it }
        }
        val shown = state.lastPayload ?: return "opp|none"
        if (state.endedCycle < 0) state.endedCycle = now
        if (now - state.endedCycle >= holdCycles(player)) {
            state.last = null
            state.lastPayload = null
            state.endedCycle = -1
            return "opp|none"
        }
        val pawn = state.last?.get()
        val stillThere = pawn != null && (if (pawn is Npc) pawn.isSpawned() else (pawn as Player).isOnline)
        return if (stillThere) payloadOf(pawn!!).also { state.lastPayload = it } else shown
    }

    /** The live opponent's payload (no hold): what the client must show while the fight goes on. */
    fun opponentPayload(player: Player): String {
        val pawn = opponent(player) ?: return "opp|none"
        return payloadOf(pawn)
    }

    private fun payloadOf(pawn: Pawn): String {
        val max = pawn.getMaximumLifepoints()
        val cur = pawn.getCurrentLifepoints()
        // The hit mask's own ratio (PlayerUpdateBlockSegment / NpcUpdateBlockSegment), so the bar matches the one over the head.
        val ratio = if (max <= 0) 0 else if (cur > max) 255 else cur * 255 / max
        return if (pawn is Npc) "opp|n|${pawn.index}|$ratio|$cur|$max" else "opp|p|${pawn.index}|$ratio"
    }
}
