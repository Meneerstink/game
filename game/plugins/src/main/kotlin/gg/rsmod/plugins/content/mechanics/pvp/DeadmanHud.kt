package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.hasSkullIcon
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.removeOption
import gg.rsmod.plugins.api.ext.sendOption
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * Deadman HUD (owner instruction 2026-09-16: "a dangerous area icon like deadmanmode", the skull
 * timer "shown on the game HUD and it refreshes every half a minute", Wilderness levels "only in
 * the wilderness", and everything "running correctly in fixed size, resizable and full screen").
 *
 * Built on interface 381, the revision-667 Wilderness/PvP overlay (verified cache decode in
 * `2011scape-client/2011scape-client/NIGHT_WILDERNESS_HANDOFF.md`): component 0 is the visible
 * parent with skull graphic 1 and text 2; component 3 is a second parent with graphic 4 and text 5.
 * The overlay is mounted through [InterfaceDestination.PVP_OVERLAY], which resolves to the fixed
 * (548:19) or resizable/fullscreen (746:10) slot, and is re-mounted after every client
 * window-mode change ([reset] is called from the WINDOW_STATUS handler).
 *
 * Presentation:
 * - Wilderness: skull + "Level: N" (the real level, pushed by the server - the client never
 *   computes it itself, which is what produced the old "-2" readings).
 * - Guarded city: text "Guarded", skull graphic hidden (no PvP here).
 * - Anywhere else: skull + "Dangerous".
 * - PK-skulled: the second parent (component 3) is shown with the remaining skull time as m:ss,
 *   rounded up to the next half minute so it changes every 30 seconds like the OSRS HUD.
 *   PENDING_HUMAN_RETEST: whether the client draws 381:3 next to 381:0 in every window mode.
 *
 * The client "Attack" player option follows the same state: available in every death zone,
 * removed inside a guarded city.
 */
object DeadmanHud {
    const val INTERFACE_ID = 381
    const val ZONE_PARENT = 0
    const val ZONE_SKULL_GRAPHIC = 1
    const val ZONE_TEXT = 2
    const val SKULL_PARENT = 3
    const val SKULL_TEXT = 5

    const val TEXT_GUARDED = "Guarded"
    const val TEXT_DANGEROUS = "Dangerous"

    /** Owner spec: the HUD skull timer refreshes every half a minute. */
    const val SKULL_HUD_STEP_SECONDS = 30

    private val LAST_ZONE_TEXT_ATTR = AttributeKey<String>()
    private val LAST_SKULL_TEXT_ATTR = AttributeKey<String>()
    private val OVERLAY_OPEN_ATTR = AttributeKey<Boolean>()

    enum class State {
        WILDERNESS,
        GUARDED,
        DANGEROUS,
    }

    fun stateOf(player: Player): State =
        when {
            player.tile.getWildernessLevel() > 0 -> State.WILDERNESS
            GuardedZones.contains(player.tile) -> State.GUARDED
            else -> State.DANGEROUS
        }

    fun zoneText(player: Player): String =
        when (stateOf(player)) {
            State.WILDERNESS -> "Level: ${player.tile.getWildernessLevel()}"
            State.GUARDED -> TEXT_GUARDED
            State.DANGEROUS -> TEXT_DANGEROUS
        }

    /** Remaining skull time as shown on the HUD, or null when not PK-skulled. */
    fun skullText(player: Player): String? {
        if (!player.hasSkullIcon(SkullIcon.RED)) return null
        val cyclesLeft = if (player.timers.exists(SKULL_ICON_DURATION_TIMER)) player.timers[SKULL_ICON_DURATION_TIMER] else 0
        return formatHalfMinutes(cyclesLeft)
    }

    /** m:ss rounded UP to the next 30-second step (5:00, 4:30, 4:00, ... 0:30). */
    fun formatHalfMinutes(cyclesLeft: Int): String {
        val secondsLeft = (cyclesLeft * 0.6).toInt().coerceAtLeast(0)
        val stepped = ((secondsLeft + SKULL_HUD_STEP_SECONDS - 1) / SKULL_HUD_STEP_SECONDS) * SKULL_HUD_STEP_SECONDS
        return "%d:%02d".format(stepped / 60, stepped % 60)
    }

    /** Forces the next [refresh] to re-mount and re-send everything (login, window-mode change). */
    fun reset(player: Player) {
        player.attr.remove(LAST_ZONE_TEXT_ATTR)
        player.attr.remove(LAST_SKULL_TEXT_ATTR)
        player.attr.remove(OVERLAY_OPEN_ATTR)
    }

    /** Called every cycle; only sends packets on an actual change. */
    fun refresh(player: Player) {
        val state = stateOf(player)
        val zone = zoneText(player)
        val skull = skullText(player)

        val zoneChanged = player.attr[LAST_ZONE_TEXT_ATTR] != zone
        val skullChanged = player.attr[LAST_SKULL_TEXT_ATTR] != (skull ?: "")
        if (!zoneChanged && !skullChanged) return

        if (player.attr[OVERLAY_OPEN_ATTR] != true) {
            player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = INTERFACE_ID)
            player.attr[OVERLAY_OPEN_ATTR] = true
        }

        if (zoneChanged) {
            player.attr[LAST_ZONE_TEXT_ATTR] = zone
            player.setComponentText(INTERFACE_ID, ZONE_TEXT, zone)
            player.setComponentHidden(INTERFACE_ID, ZONE_SKULL_GRAPHIC, hidden = state == State.GUARDED)
            if (state == State.GUARDED) {
                player.removeOption(2)
            } else {
                player.sendOption("Attack", 2)
            }
        }

        if (skullChanged) {
            player.attr[LAST_SKULL_TEXT_ATTR] = skull ?: ""
            if (skull != null) {
                player.setComponentText(INTERFACE_ID, SKULL_TEXT, skull)
                player.setComponentHidden(INTERFACE_ID, SKULL_PARENT, hidden = false)
            } else {
                player.setComponentHidden(INTERFACE_ID, SKULL_PARENT, hidden = true)
            }
        }
    }

    fun close(player: Player) {
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        reset(player)
    }
}
