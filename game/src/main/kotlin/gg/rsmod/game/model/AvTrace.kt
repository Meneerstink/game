package gg.rsmod.game.model

import mu.KLogging
import java.io.File

/**
 * RSPS RCV-005 server-side trace for graphics, sounds and summoning casts, paired with the client's
 * `AvTrace`. On at boot while `C:/RSPS/avtrace.on` (or the all-traces file `C:/RSPS/trace.on`) exists, or toggled with the
 * `avtrace` / `traces` command. It proves whether the server actually issued a graphic/sound, so a missing effect can be placed
 * on the server, the packet, or the client instead of being guessed. Lines naming a failure start with `PROBLEM`.
 */
object AvTrace : KLogging() {
    const val TOGGLE_FILE = "C:/RSPS/avtrace.on"

    @Volatile
    var enabled: Boolean = File(TOGGLE_FILE).exists() || File(Trace.ALL_FILE).exists()

    inline fun log(crossinline message: () -> String) {
        if (enabled) {
            val line = "[avtrace] ${message()}"
            Trace.record(line)
            logger.info { line }
        }
    }
}
