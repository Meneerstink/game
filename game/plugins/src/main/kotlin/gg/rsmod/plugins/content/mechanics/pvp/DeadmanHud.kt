package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.removeOption
import gg.rsmod.plugins.api.ext.sendOption
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText

/**
 * Deadman HUD - server side of the owner's reference screenshots ("deadmanmode vervijning",
 * 2026-09-17: the HUD "needs to be exactly visually the same"):
 *
 * <pre>
 *        [skull-and-crossbones] [!]        (Guarded: the crossed-out skull instead)
 *              Deadman                     ("Guarded" / "Level: N" in the Wilderness)
 *               81-95                      (the +/-14 combat bracket, everywhere)
 *           [own skull] 5:00               (only while PK-skulled; refreshes every 30 s)
 * </pre>
 *
 * Built on interface 381, the revision-667 Wilderness/PvP overlay: component 0 is the zone layer
 * whose text component 2 carries `"<state>|<lo>-<hi>"`; component 3 is the skull-timer layer whose
 * text component 5 carries `m:ss`. The client (`DeadmanSkullHud`) never draws the cache layouts of
 * either layer; it renders the screenshot layout from these two strings at the zone layer's own
 * screen position, so the HUD is identical in fixed, resizable and fullscreen mode and in every
 * graphics mode. The overlay is mounted through [InterfaceDestination.PVP_OVERLAY] and re-mounted
 * after every window-mode change ([reset] runs from the WINDOW_STATUS handler).
 *
 * Every number here comes from the same shared predicates the gameplay uses: the zone from
 * [GuardedZones] (also the PvP gate, the guards and the danger signs), the bracket from
 * [AreaState.bracketOf] (also the attack gate), the timer from [PvpSkull.isSkulled] (also the guards,
 * the logout/teleport gate and the risk skull). The HUD can therefore never disagree with the rules.
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

    /** Owner screenshot: a dangerous (non-Wilderness) zone is labelled "Deadman". */
    const val TEXT_DANGEROUS = "Deadman"

    /** Separator between the zone label and the combat bracket in component 381:2. */
    const val FIELD_SEPARATOR = '|'

    /** Owner 2026-09-17: shown once on every guarded -> dangerous transition. */
    const val DANGER_WARNING = "Warning: You are entering a dangerous zone."

    /** Owner spec: the HUD skull timer refreshes every half a minute. */
    const val SKULL_HUD_STEP_SECONDS = 30

    private val LAST_ZONE_TEXT_ATTR = AttributeKey<String>()
    private val LAST_SKULL_TEXT_ATTR = AttributeKey<String>()
    private val LAST_STATE_ATTR = AttributeKey<State>()
    private val OVERLAY_OPEN_ATTR = AttributeKey<Boolean>()

    enum class State {
        WILDERNESS,
        GUARDED,
        DANGEROUS,
    }

    fun stateOf(player: Player): State =
        when {
            !AreaState.isDangerous(player.tile) -> State.GUARDED
            player.tile.getWildernessLevel() > 0 -> State.WILDERNESS
            else -> State.DANGEROUS
        }

    fun zoneLabel(player: Player): String =
        when (stateOf(player)) {
            State.WILDERNESS -> "Level: ${player.tile.getWildernessLevel()}"
            State.GUARDED -> TEXT_GUARDED
            State.DANGEROUS -> TEXT_DANGEROUS
        }

    /** "81-95": the levels this player may attack and be attacked by, the same everywhere. */
    fun bracketText(combatLevel: Int): String {
        val bracket = AreaState.bracketOf(combatLevel)
        return "${bracket.first}-${bracket.last}"
    }

    /** The full component-381:2 payload: label, separator, bracket. */
    fun zoneText(player: Player): String = zoneLabel(player) + FIELD_SEPARATOR + bracketText(player.combatLevel)

    /** Remaining skull time as shown on the HUD, or null when not PK-skulled. */
    fun skullText(player: Player): String? {
        if (!PvpSkull.isSkulled(player)) return null
        val cyclesLeft = if (player.timers.exists(SKULL_ICON_DURATION_TIMER)) player.timers[SKULL_ICON_DURATION_TIMER] else 0
        return formatHalfMinutes(cyclesLeft)
    }

    /** m:ss rounded UP to the next 30-second step (5:00, 4:30, 4:00, ... 0:30). */
    fun formatHalfMinutes(cyclesLeft: Int): String {
        val secondsLeft = Math.ceil(cyclesLeft * 0.6).toInt().coerceAtLeast(0)
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

        val previousState = player.attr[LAST_STATE_ATTR]
        if (previousState != state) {
            player.attr[LAST_STATE_ATTR] = state
            if (previousState == State.GUARDED) {
                player.message(DANGER_WARNING)
            }
        }

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
                // Clear the text too: the client draws the timer row from the text alone.
                player.setComponentText(INTERFACE_ID, SKULL_TEXT, "")
                player.setComponentHidden(INTERFACE_ID, SKULL_PARENT, hidden = true)
            }
        }
    }

    fun close(player: Player) {
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        reset(player)
    }
}
