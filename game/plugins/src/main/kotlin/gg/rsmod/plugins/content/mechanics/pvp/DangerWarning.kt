package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.isInterfaceVisible

/**
 * The revision-667 Wilderness warning (interface 382) as the one Dangerous-area warning. It opens before a player leaves a
 * Safe zone ([AreaState.isDangerous] is false) for a Dangerous tile - walking, any shortcut, and every teleport whatever
 * started it - because it is decided in [gg.rsmod.game.model.MoveGate] from the source and destination tiles, not per
 * route. Cancel/close keeps the player where they are; "Enter" continues the move.
 *
 * Owner 2026-09-23: the warning is only for a brand-new account, and only until its welcome introduction (starter kit)
 * is finished or skipped ([INTRO], never saved). After that it never shows again, for new and existing accounts alike,
 * unless the player explicitly turns it back on at the Doomsayer or with `warnings` ([ENABLED], saved). The warning's
 * own "don't show again" row switches it off at any time.
 */
object DangerWarning {
    const val INTERFACE_ID = 382

    /** Interface 382 (667 cache layout): the stacked Enter pause button (normal / hover), the close cross, texts, "don't ask". */
    const val ENTER = 19
    const val ENTER_HOVER = 20
    const val CLOSE = 14
    const val BODY = 26
    const val DONT_ASK = 31
    const val DONT_ASK_TEXT = 32

    /** Ticks a confirmed crossing stays valid (the walk or teleport that follows the confirmation). */
    const val BYPASS_TICKS = 50

    const val BODY_TEXT =
        "You are about to enter a <col=ff0000>Dangerous</col> area. Other players can attack you here, and if you die " +
            "you can lose your items. Are you sure you want to continue?"

    /** The new account's welcome introduction is still running (set and cleared by the starter kit, never saved). */
    val INTRO = AttributeKey<Boolean>()

    /** The player explicitly turned the warning back on (Doomsayer / `warnings`); saved with the player. */
    val ENABLED = AttributeKey<Boolean>("danger_warnings_on")
    private val BYPASS_UNTIL = AttributeKey<Int>()

    /** Opens the warning for a held-back move; installed by `danger_warning.plugin.kts`. */
    var open: ((Player, Tile, Boolean) -> Unit)? = null

    fun isActive(player: Player): Boolean = player.attr[INTRO] == true || player.attr[ENABLED] == true

    fun startIntro(player: Player) {
        player.attr[INTRO] = true
        syncVarp(player)
    }

    fun endIntro(player: Player) {
        if (!player.attr.has(INTRO)) return
        player.attr.remove(INTRO)
        syncVarp(player)
    }

    fun setActive(
        player: Player,
        active: Boolean,
    ) {
        if (active) {
            player.attr[ENABLED] = true
        } else {
            player.attr.remove(ENABLED)
            player.attr.remove(INTRO)
        }
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
        val next = if (isActive(player)) value and (1 shl WARNING_BIT).inv() else value or (1 shl WARNING_BIT)
        if (next != value) player.varps.setState(WARNING_VARP, next)
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
        if (!isActive(player)) return false
        if (AreaState.isDangerous(from) || !AreaState.isDangerous(to)) return false
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
