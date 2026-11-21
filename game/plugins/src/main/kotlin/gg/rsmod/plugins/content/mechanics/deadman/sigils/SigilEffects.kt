package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player

/**
 * PROJECT48 SIGILS - what activating a sigil does at once. The windowed sigils (Binding, Specialised Strikes, Porcupine, Finality) only
 * open their window here (Sigils.activate did that); their effect is read by the combat hooks ([SigilCombat]) while the window is open.
 * The instant sigils (Last Recall, Versatility, Serpent) are filled in by their own rows (S45, S48, S55).
 */
object SigilEffects {
    fun activated(
        player: Player,
        sigil: Sigil,
    ) {
        when (sigil) {
            Sigil.BINDING -> player.message("Binding: your next binding spell within ${sigil.windowSeconds} seconds is much more accurate.")
            Sigil.SPECIALISED_STRIKES -> player.message("Specialised Strikes: your next special attack costs 10 less for ${sigil.windowSeconds} seconds.")
            Sigil.THE_PORCUPINE -> player.message("Porcupine: attackers are hurt by recoil for ${sigil.windowSeconds} seconds.")
            Sigil.FINALITY -> player.message("Finality: your next special attack within ${sigil.windowSeconds} seconds cannot miss.")
            else -> instant(player, sigil)
        }
    }

    /** Instant sigils: replaced per row (S45 Last Recall, S48 Versatility, S55 Serpent). */
    private fun instant(
        player: Player,
        sigil: Sigil,
    ) {
        player.message("${sigil.label} is not ready yet.")
    }
}
