package gg.rsmod.plugins.content.inter.attack

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.SPECIAL_ATTACK_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped

/**
 * Special attack energy regeneration (OSRS Wiki "Special attacks", "Lightbearer"):
 * - 10 % every 30 seconds (50 ticks); the timer is player-wide and restarts on login.
 * - Lightbearer doubles it: 10 % every 15 seconds (25 ticks).
 * - Equipping the Lightbearer resets the timer only if natural regeneration is below half (more than 25 ticks
 *   left), so equipping just before a restore never delays it; unequipping always resets the timer.
 * One timer ([SPECIAL_ATTACK_TIMER]) carries every rule, so swapping the ring can never restore energy faster than
 * one 10 % per 25 ticks.
 */
object SpecialEnergyRegen {
    const val RESTORE_PERCENT = 10
    const val NORMAL_TICKS = 50
    const val LIGHTBEARER_TICKS = 25

    fun wearingLightbearer(player: Player): Boolean = player.hasEquipped(EquipmentType.RING, Items.LIGHTBEARER)

    fun interval(wearingLightbearer: Boolean): Int = if (wearingLightbearer) LIGHTBEARER_TICKS else NORMAL_TICKS

    /** Ticks left on the regeneration timer, 0 when it is not running. */
    fun remaining(timers: TimerMap): Int = if (timers.exists(SPECIAL_ATTACK_TIMER)) timers[SPECIAL_ATTACK_TIMER] else 0

    fun onLogin(
        timers: TimerMap,
        wearingLightbearer: Boolean,
    ) {
        timers[SPECIAL_ATTACK_TIMER] = interval(wearingLightbearer)
    }

    fun onLightbearerEquipped(timers: TimerMap) {
        if (remaining(timers) > LIGHTBEARER_TICKS) {
            timers[SPECIAL_ATTACK_TIMER] = LIGHTBEARER_TICKS
        }
    }

    fun onLightbearerUnequipped(timers: TimerMap) {
        timers[SPECIAL_ATTACK_TIMER] = NORMAL_TICKS
    }

    /** Called when the timer fires: the restored energy (capped at 100) and the timer re-armed. */
    fun onTimer(
        timers: TimerMap,
        energy: Int,
        wearingLightbearer: Boolean,
    ): Int {
        timers[SPECIAL_ATTACK_TIMER] = interval(wearingLightbearer)
        return minOf(100, energy + RESTORE_PERCENT)
    }
}
