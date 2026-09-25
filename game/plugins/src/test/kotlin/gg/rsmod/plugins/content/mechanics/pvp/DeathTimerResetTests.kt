package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.STUN_TIMER
import gg.rsmod.game.model.timer.TimerMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit C-13: a freeze, a stun and the freeze immunity end with the death (PlayerDeathAction removes every
 * `resetOnDeath` timer), so they never carry over the respawn.
 */
class DeathTimerResetTests {
    @Test
    fun `freeze, stun and freeze immunity are cleared by a death`() {
        val timers = TimerMap()
        timers[FROZEN_TIMER] = 32
        timers[FREEZE_IMMUNITY_TIMER] = 37
        timers[STUN_TIMER] = 8
        timers[SKULL_ICON_DURATION_TIMER] = 100

        // The same removal PlayerDeathAction runs on death.
        timers.removeIf { it.resetOnDeath }

        assertFalse(timers.exists(FROZEN_TIMER))
        assertFalse(timers.exists(FREEZE_IMMUNITY_TIMER))
        assertFalse(timers.exists(STUN_TIMER))
        assertFalse(timers.exists(SKULL_ICON_DURATION_TIMER))
        assertTrue(FROZEN_TIMER.resetOnDeath && FREEZE_IMMUNITY_TIMER.resetOnDeath && STUN_TIMER.resetOnDeath)
    }
}
