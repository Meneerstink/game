package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.isInterfaceVisible

/**
 * Owner 2026-09-23: the revision-667 Wilderness warning (interface 382) is the one Dangerous-area warning. It opens
 * before a player leaves a Safe zone ([AreaState.isDangerous] is false) for a Dangerous tile - walking out of the Grand
 * Exchange gate, any shortcut, and every teleport whatever started it - because it is decided in
 * [gg.rsmod.game.model.MoveGate] from the source and destination tiles, not per route. Cancel/close keeps the player
 * where they are; "Enter" continues the same move.
 *
 * The "don't ask again" row is hidden until the player has [UNLOCK_TICKS] of genuine active play: a minute only counts
 * when the player sent a real action packet ([LAST_ACTIVE_CYCLE_ATTR]) within [ACTIVE_WINDOW_TICKS]. Both the counter
 * and the preference are saved with the player. Warnings are turned back on at the Doomsayer or with `warnings`.
 */
object DangerWarning {
    const val INTERFACE_ID = 382

    /** Interface 382 (667 cache layout): the stacked Enter button (normal / hover), the close cross, texts, "don't ask". */
    const val ENTER = 19
    const val ENTER_HOVER = 20
    const val CLOSE = 14
    const val BODY = 26
    const val DONT_ASK = 31

    /** One hour of 600 ms game ticks. */
    const val UNLOCK_TICKS = 6000

    /** A minute counts as active play only if the player acted within the last three minutes. */
    const val ACTIVE_WINDOW_TICKS = 300

    /** Ticks a confirmed crossing stays valid (the walk or teleport that follows the confirmation). */
    const val BYPASS_TICKS = 50

    const val BODY_TEXT =
        "You are about to enter a <col=ff0000>Dangerous</col> area. Other players can attack you here, and if you die " +
            "you can lose your items. Are you sure you want to continue?"

    val DISABLED = AttributeKey<Boolean>("danger_warnings_off")
    val ACTIVE_TICKS = AttributeKey<Int>("active_play_ticks")
    private val BYPASS_UNTIL = AttributeKey<Int>()

    /** Opens the warning for a held-back move; installed by `danger_warning.plugin.kts`. */
    var open: ((Player, Tile, Boolean) -> Unit)? = null

    fun activeTicks(player: Player): Int = player.attr[ACTIVE_TICKS] ?: 0

    fun canDisable(player: Player): Boolean = activeTicks(player) >= UNLOCK_TICKS

    /** A disabled preference only counts once it could legitimately have been chosen. */
    fun isDisabled(player: Player): Boolean = player.attr[DISABLED] == true && canDisable(player)

    fun setDisabled(
        player: Player,
        disabled: Boolean,
    ) {
        if (disabled && !canDisable(player)) return
        player.attr[DISABLED] = disabled
        syncVarp(player)
    }

    /**
     * The 667 client keeps its warning switches in varp 1045 (and 1046): interface 382's "don't ask again" row reads bit
     * [WARNING_BIT] (clientscript 348 args 382:31, 382:33, 7) and the Doomsayer's warning screen 583 draws the same bit on its
     * Wilderness tile (583:35, clientscript 165 arg 7; its Toggle button is 583:[SETTINGS_TOGGLE]). A set bit = warning off.
     * The server preference stays the only truth; this mirrors it so both screens show it.
     */
    const val WARNING_VARP = 1045
    const val WARNING_BIT = 7
    const val SETTINGS_INTERFACE = 583
    const val SETTINGS_TOGGLE = 66

    fun syncVarp(player: Player) {
        val value = player.varps.getState(WARNING_VARP)
        val next = if (isDisabled(player)) value or (1 shl WARNING_BIT) else value and (1 shl WARNING_BIT).inv()
        if (next != value) player.varps.setState(WARNING_VARP, next)
    }

    /** Adds [ticks] of play time when the player really acted recently (AFK / idle logged-in time never counts). */
    fun countActive(
        player: Player,
        ticks: Int,
    ) {
        val last = player.attr[LAST_ACTIVE_CYCLE_ATTR] ?: return
        if (player.world.currentCycle - last > ACTIVE_WINDOW_TICKS) return
        player.attr[ACTIVE_TICKS] = (activeTicks(player) + ticks).coerceAtMost(Int.MAX_VALUE / 2)
    }

    fun allowNextCrossing(player: Player) {
        player.attr[BYPASS_UNTIL] = player.world.currentCycle + BYPASS_TICKS
    }

    fun needsWarning(
        player: Player,
        from: Tile,
        to: Tile,
    ): Boolean {
        if (player.entityType != EntityType.CLIENT || !player.initiated) return false
        if (AreaState.isDangerous(from) || !AreaState.isDangerous(to)) return false
        if (isDisabled(player)) return false
        val bypass = player.attr[BYPASS_UNTIL]
        if (bypass != null) {
            player.attr.remove(BYPASS_UNTIL)
            if (bypass >= player.world.currentCycle) return false
        }
        return true
    }

    /** The [gg.rsmod.game.model.MoveGate] body: true holds the move back (warning shown or already open). */
    fun intercept(
        player: Player,
        from: Tile,
        to: Tile,
        destination: Tile,
        walk: Boolean,
    ): Boolean {
        if (!needsWarning(player, from, to)) return false
        if (player.isInterfaceVisible(INTERFACE_ID)) return true
        open?.invoke(player, destination, walk)
        return true
    }
}
