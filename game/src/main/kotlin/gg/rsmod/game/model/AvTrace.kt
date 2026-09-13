package gg.rsmod.game.model

import mu.KLogging
import java.io.File

/**
 * RSPS RCV-005 server-side trace for graphics, sounds and summoning casts, paired with the client's
 * `AvTrace`. On at boot while `C:/RSPS/avtrace.on` exists, or toggled with the `avtrace` command.
 * It proves whether the server actually issued a graphic/sound, so a missing effect can be placed on
 * the server, the packet, or the client instead of being guessed.
 */
object AvTrace : KLogging() {
    const val TOGGLE_FILE = "C:/RSPS/avtrace.on"

    @Volatile
    var enabled: Boolean = File(TOGGLE_FILE).exists()

    inline fun log(crossinline message: () -> String) {
        if (enabled) {
            logger.info { "[avtrace] ${message()}" }
        }
    }
}
