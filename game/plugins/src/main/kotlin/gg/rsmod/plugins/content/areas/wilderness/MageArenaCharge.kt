package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.GOD_SPELL_CHARGE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.GOD_SPELL_CHARGE_TIMER
import gg.rsmod.game.model.timer.TimerKey

/** OSRS Wiki Charge: a 100-tick cast cooldown is independent of the 700-tick buff. */
object MageArenaCharge {
    val COOLDOWN = TimerKey()
    const val COOLDOWN_TICKS = 100
    const val BUFF_TICKS = 700

    /** Called only after the shared Magic-level, staff unlock and rune checks have succeeded. */
    fun activate(player: Player) {
        player.timers[COOLDOWN] = COOLDOWN_TICKS
        player.timers[GOD_SPELL_CHARGE_TIMER] = BUFF_TICKS
        player.attr[GOD_SPELL_CHARGE_ATTR] = true
    }
}
