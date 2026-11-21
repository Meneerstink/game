package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.inputInt
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.content.inter.attack.AttackTab

/**
 * PROJECT48 DMM-ITEMS C1 Soulflame horn (OSRS Wiki "Soulflame horn", fetched 2026-11-21): 80 Magic to wield, +1 % magic damage.
 * Special attack Entice: it affects up to 3 other players within 3 tiles for 25 % special attack energy per other player
 * (usable only with at least one other player in range) and always also the user; "the player's next melee attack within 10 ticks
 * (6 seconds) is always a successful hit" - only the first accuracy roll of a multi-hit special.
 * Configure (OSRS Wiki, read 21-11): radius 0-3 tiles and the number of other players affected 1-20; the cost stays 25 % per other player,
 * up to 3 others. SOURCE_GAP (not built): the special-weapon accuracy variants (dragon claws, burning claws).
 */
object SoulflameHorn {
    const val ID = 25493
    const val RADIUS = 3
    const val MAX_ALLIES = 3
    const val COST_PER_ALLY = 25
    const val DURATION_TICKS = 10

    val RADIUS_ATTR = AttributeKey<Int>(persistenceKey = "horn_radius")
    val MAX_OTHERS_ATTR = AttributeKey<Int>(persistenceKey = "horn_max_others")

    val ENTICE = TimerKey(persistenceKey = "dmm_entice", tickOffline = false, resetOnDeath = true)

    /** The instant special: false (nothing spent) when nobody else is near or the energy does not cover one ally. */
    fun use(player: Player): Boolean {
        val near =
            player.world.players.entries.filterNotNull()
                .filter { it !== player && it.isOnline && it.tile.height == player.tile.height && it.tile.getDistance(player.tile) <= (player.attr[RADIUS_ATTR] ?: RADIUS) }
                .sortedBy { it.tile.getDistance(player.tile) }
        if (near.isEmpty()) {
            player.message("There are no other players close enough to be enticed.")
            return false
        }
        val affordable = AttackTab.getEnergy(player) / COST_PER_ALLY
        val maxOthers = player.attr[MAX_OTHERS_ATTR] ?: MAX_ALLIES
        val allies = near.take(minOf(maxOthers, if (affordable >= MAX_ALLIES) 20 else affordable))
        if (allies.isEmpty()) {
            player.message("You don't have enough power left.")
            return false
        }
        AttackTab.spendSpecial(player, COST_PER_ALLY * minOf(allies.size, MAX_ALLIES))
        (allies + player).forEach {
            it.timers[ENTICE] = DURATION_TICKS
            it.message("You feel enticed: your next melee attack will hit.")
        }
        return false // the energy is spent above (25 % per ally), not the registered flat cost
    }

    /** The first melee accuracy roll while enticed always hits (the timer is used up). */
    fun consume(player: Player): Boolean {
        if (!player.timers.has(ENTICE)) return false
        player.timers.remove(ENTICE)
        return true
    }

    /** The Configure option: pick the radius or the number of other players, then type the value. */
    suspend fun configure(task: QueueTask, player: Player) {
        when (task.options("Radius (0-3 tiles).", "Max players (1-20).", title = "Soulflame horn")) {
            1 -> {
                val r = task.inputInt("Radius in tiles (0-3):").coerceIn(0, 3)
                player.attr[RADIUS_ATTR] = r
                player.message("Entice radius set to $r.")
            }
            else -> {
                val n = task.inputInt("Other players affected (1-20):").coerceIn(1, 20)
                player.attr[MAX_OTHERS_ATTR] = n
                player.message("Entice will affect up to $n other players.")
            }
        }
    }
}
