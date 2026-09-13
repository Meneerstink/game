package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.TaskPriority

/**
 * RCV-010 A4 (Follower Details tab disappears after clicks): every time the server re-sends the gameframe top level
 * (`IF_OPENTOP` 548/746) the client rebuilds the whole tab strip with the cache's baked flags, which drops every piece
 * of server-sent gameframe state (the spare tab's events/sprite/unhide, the Summoning orb and panel gating).
 *
 * The login path and the window-mode switch already re-armed that state, but `closeFullscreenInterface` (world map,
 * canoe travel screens) re-sent the top level with nothing re-arming it. This is the one shared hook every top-level
 * rebuild calls; content registers what it must restore (see familiar.plugin.kts).
 */
object GameframeRebuild {
    private val listeners = linkedMapOf<String, (Player) -> Unit>()

    /** Registers (or replaces, on script reload) the restore action named [key]. */
    fun register(
        key: String,
        restore: (Player) -> Unit,
    ) {
        listeners[key] = restore
    }

    fun registeredKeys(): Set<String> = listeners.keys.toSet()

    /** Re-arms now and once more a few ticks later, in case the client rebuild lands after the first pass. */
    fun rearm(player: Player) {
        listeners.values.forEach { it(player) }
        player.queue(TaskPriority.WEAK) {
            wait(3)
            listeners.values.forEach { it(player) }
        }
    }

    fun clearForTests() = listeners.clear()
}
