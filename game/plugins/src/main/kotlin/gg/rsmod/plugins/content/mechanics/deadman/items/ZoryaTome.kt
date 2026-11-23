package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/** Zorya's flow, Wiki rev15405627. Buffs are transient, survive switches, expire after 30s, and clear on teleport. */
object ZoryaTome {
    const val SPECIAL_ENERGY = 50
    const val MAX_BUFFS = 5
    const val BUFF_TICKS = 50
    data class Buff(val attacks: Int, val expires: Int, val teleport: Int)
    data class Shot(val empowered: Boolean, val accuracy: Double, val damage: Double, val delay: Int)
    private val BUFF = AttributeKey<Buff>()
    private val LAST_DELAY = AttributeKey<Int>()

    fun remaining(player: Player): Int {
        val saved = player.attr[BUFF] ?: return 0
        if (player.world.currentCycle >= saved.expires || player.lastTeleportCycle != saved.teleport || player.isDead()) {
            player.attr.remove(BUFF)
            return 0
        }
        return saved.attacks
    }

    fun prepare(player: Player, special: Boolean): Shot {
        val count = remaining(player)
        val empowered = count > 0
        if (empowered) player.attr[BUFF] = Buff(count - 1, player.world.currentCycle + BUFF_TICKS, player.lastTeleportCycle)
        val increments = (if (special) 1 else 0) + (if (empowered) 1 else 0)
        val delay = if (empowered) 2 else 3
        player.attr[LAST_DELAY] = delay
        return Shot(empowered, 1.0 + 1.5 * increments, 1.0 + 0.6 * increments, delay)
    }

    fun successfulSpecial(player: Player) {
        player.attr[BUFF] = Buff((remaining(player) + 3).coerceAtMost(MAX_BUFFS),
            player.world.currentCycle + BUFF_TICKS, player.lastTeleportCycle)
    }

    fun lastDelay(player: Player): Int = player.attr[LAST_DELAY] ?: 3
}
