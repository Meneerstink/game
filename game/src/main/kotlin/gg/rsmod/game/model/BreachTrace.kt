package gg.rsmod.game.model

import mu.KLogging
import java.io.File

/**
 * Breach OSRS-conformance trace (owner 2026-09-27: "test literally everything - pathing, single, multi"): one line per decision
 * of a watched npc - aggro candidates, target switches, single/multi rulings, route and step results, attack selection, every
 * target of an attack, hits, sounds and the players they reach, update masks per viewer, deaths and rewards. Each line is
 * `[breachtrace] t=<cycle> <category> <fields>`.
 *
 * On at boot with `-Drsps.breachTrace=true` or while `C:/RSPS/breachtrace.on` exists, or with the `breachtrace` command. The
 * in-engine test run ([sink]) receives every line even when logging is off, so it can assert on them.
 *
 * Game-module hooks (update masks, area-sound receivers, movement) only know npc ids, so the breach plugin registers the
 * watched ids in [watchedNpcIds]; plugin-side hooks decide with their own breach check.
 */
object BreachTrace : KLogging() {
    const val TOGGLE_FILE = "C:/RSPS/breachtrace.on"
    const val PROPERTY = "rsps.breachTrace"

    @Volatile
    var enabled: Boolean = System.getProperty(PROPERTY) == "true" || File(TOGGLE_FILE).exists() || File(Trace.ALL_FILE).exists()

    /** The game cycle stamped on every line (set by [World] each cycle). */
    @Volatile
    var cycle: Int = 0

    /** Npc ids whose engine-level events (masks per viewer, movement, area sounds at their tile) are traced. */
    @Volatile
    var watchedNpcIds: Set<Int> = emptySet()

    /** In-memory listener of the in-engine test run; receives every line whether or not [enabled] is set. */
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun active(): Boolean = enabled || sink != null

    fun watches(npcId: Int): Boolean = npcId in watchedNpcIds

    inline fun log(
        category: String,
        crossinline message: () -> String,
    ) {
        val out = sink
        if (!enabled && out == null) return
        val line = "t=$cycle $category ${message()}"
        out?.invoke(line)
        if (enabled) {
            // A "PROBLEM" category marks a failure; the bug report and the live chat lines get every breach line too.
            Trace.record("[breachtrace] $line")
            logger.info { "[breachtrace] $line" }
        }
    }
}
