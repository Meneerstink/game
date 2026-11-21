package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.inter.attack.AttackTab

/**
 * PROJECT48 DMM-ITEMS C1 Soulflame horn (OSRS Wiki "Soulflame horn", fetched 2026-11-21): 80 Magic to wield, +1 % magic damage.
 * Special attack Entice: it affects up to 3 other players within 3 tiles for 25 % special attack energy per other player
 * (usable only with at least one other player in range) and always also the user; "the player's next melee attack within 10 ticks
 * (6 seconds) is always a successful hit" - only the first accuracy roll of a multi-hit special.
 * SOURCE_GAP (not built): the Configure option (radius 0-3 / allies 1-20), the 20-player cap and the special-weapon accuracy
 * variants (dragon claws, burning claws) - the defaults above are used.
 */
object SoulflameHorn {
    const val ID = 25493
    const val RADIUS = 3
    const val MAX_ALLIES = 3
    const val COST_PER_ALLY = 25
    const val DURATION_TICKS = 10

    val ENTICE = TimerKey(persistenceKey = "dmm_entice", tickOffline = false, resetOnDeath = true)

    /** The instant special: false (nothing spent) when nobody else is near or the energy does not cover one ally. */
    fun use(player: Player): Boolean {
        val near =
            player.world.players.entries.filterNotNull()
                .filter { it !== player && it.isOnline && it.tile.height == player.tile.height && it.tile.getDistance(player.tile) <= RADIUS }
                .sortedBy { it.tile.getDistance(player.tile) }
        if (near.isEmpty()) {
            player.message("There are no other players close enough to be enticed.")
            return false
        }
        val affordable = AttackTab.getEnergy(player) / COST_PER_ALLY
        val allies = near.take(minOf(MAX_ALLIES, affordable))
        if (allies.isEmpty()) {
            player.message("You don't have enough power left.")
            return false
        }
        AttackTab.spendSpecial(player, COST_PER_ALLY * allies.size)
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
}
