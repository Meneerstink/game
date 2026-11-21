package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player

/**
 * PROJECT48 SIGILS - the one file the combat formulas call (a single line per formula, not 65): accuracy, damage, defence and
 * after-hit factors of the attuned sigils. Each function returns the neutral value (1.0 / 0) when no sigil applies.
 * Filled per sigil row (S01..S65); the functions already exist so the formula hooks are added once.
 */
object SigilCombat {
    /** Whether any sigil is attuned at all (the cheap early exit of every hook). */
    fun any(player: Player): Boolean = Sigils.attuned(player).isNotEmpty()
}
