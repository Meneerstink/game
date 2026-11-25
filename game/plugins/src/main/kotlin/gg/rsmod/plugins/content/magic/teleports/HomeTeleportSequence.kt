package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanHomeTeleport

/** Shared native OSRS Home/Minigame long cast. Only the caller supplies the destination/timer.
 * Source: existing HomeTeleport native stages; Wiki Minigame Teleport default24 ticks.
 */
object HomeTeleportSequence {
    val ANIMATIONS = intArrayOf(1722, 1723, 1724, 1725, 2798, 2799, 2800, 3195, 4643, 4645, 4646, 4847, 4848, 4849, 4850, 4851, 4852)
    val GRAPHICS = intArrayOf(775, 800, 801, 802, 803, 804, 1703, 1704, 1705, 1706, 1707, 1708, 1709, 1710, 1711, 1712, 1713)

    suspend fun QueueTask.play(defaultExtraTicks: Int = 0, interruptOnAttack: Boolean = false, allowed: () -> Boolean = { true }): Boolean {
        terminateAction = { player.animate(-1); player.graphic(-1) }
        fun combatTicks() = if (player.timers.exists(TELEPORT_COMBAT_TIMER)) player.timers[TELEPORT_COMBAT_TIMER] else 0
        var combat = combatTicks()
        suspend fun tick(): Boolean {
            wait(1)
            val now = combatTicks()
            val hit = now > combat
            combat = now
            return !hit && (!interruptOnAttack || !player.isAttacking()) && allowed()
        }
        repeat(2) { if (!tick()) return false }
        val override = DeadmanHomeTeleport.stages(player)
        if (override != null) {
            for (stage in override) {
                player.animate(stage.animation); player.graphic(stage.graphic)
                repeat(stage.ticks) { if (!tick()) return false }
            }
        } else {
            for (stage in ANIMATIONS.indices) {
                player.animate(ANIMATIONS[stage]); player.graphic(GRAPHICS[stage])
                if (!tick()) return false
            }
            repeat(defaultExtraTicks) { if (!tick()) return false }
        }
        return true
    }
}
