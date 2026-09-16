package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.setComponentText

val wildernessCheckTimer = TimerKey()

val INTERFACE_ID = 381

/**
 * Client evidence (`2011scape-client/2011scape-client/NIGHT_WILDERNESS_HANDOFF.md`, verified
 * against a read-only decode of the real revision-667 cache): interface 381 has TWO parallel
 * text components - `2` on the visible fixed-mode parent (component 0) and `5` on the alternate
 * parent (component 3) used in other display modes. Both must be kept in sync; only one is ever
 * actually visible at a time per the client's own display-mode handling.
 */
private val WILDERNESS_LEVEL_TEXT_COMPONENTS = intArrayOf(2, 5)

/**
 * Deadman PvP guards plan (2026-09-16) root-cause fix: the overlay used to open/close on the
 * broad PvP-danger flag (`!AreaState.isSafe`), which is true almost everywhere outside an
 * explicit bank safe zone in this build (R03.1) - not only in the Wilderness - so the level
 * indicator showed outside the actual Wilderness. It is now driven by the real Wilderness level
 * (`Tile.getWildernessLevel() > 0`) instead, and the level number is pushed to the client via
 * [gg.rsmod.plugins.api.ext.setComponentText] on every real transition or in-Wilderness level
 * change - previously nothing ever wrote that text, so the component showed whatever the client
 * had left over (the reported "-2 or other nonsense values"). Tracks the last-pushed level
 * (null = not in the Wilderness) rather than a boolean so walking to a different level while
 * still inside the Wilderness also refreshes the text without re-opening the interface. Missing
 * on login/reconnect (null) means the first tick after login always applies the real state
 * instead of skipping it as a no-op. Location-only Wilderness rules (deaths, hotspots, Breaches)
 * continue to call [gg.rsmod.plugins.content.areas.home.BountyHunterHome.isDangerousWilderness]
 * directly where their product rule requires it; this attribute is HUD-only.
 */
val LAST_WILDERNESS_LEVEL = AttributeKey<Int>()

on_login {
    player.timers[wildernessCheckTimer] = 1
}

on_timer(wildernessCheckTimer) {
    checkWildernessLevel(player)
    player.timers[wildernessCheckTimer] = 1
}

fun checkWildernessLevel(player: Player) {
    val level = player.tile.getWildernessLevel()
    val previous = player.attr[LAST_WILDERNESS_LEVEL]
    // Only push interface/option/text state on an actual transition or level change, not every tick.
    if (level == (previous ?: 0)) {
        return
    }
    val wasInWilderness = previous != null && previous > 0
    val nowInWilderness = level > 0
    if (nowInWilderness) {
        player.attr[LAST_WILDERNESS_LEVEL] = level
    } else {
        player.attr.remove(LAST_WILDERNESS_LEVEL)
    }
    if (nowInWilderness) {
        if (!wasInWilderness) {
            player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = INTERFACE_ID)
            player.sendOption("Attack", 2)
        }
        val text = "Level: $level"
        for (component in WILDERNESS_LEVEL_TEXT_COMPONENTS) {
            player.setComponentText(INTERFACE_ID, component, text)
        }
    } else {
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        player.removeOption(2)
    }
}
