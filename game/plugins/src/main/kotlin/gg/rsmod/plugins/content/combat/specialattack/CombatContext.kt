package gg.rsmod.plugins.content.combat.specialattack

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player

/**
 * @author Tom <rspsmods@gmail.com>
 */
data class CombatContext(
    val world: World,
    val player: Player,
) {
    lateinit var target: Pawn

    /** Audit C-15: set by a special that could not be performed (no ammo, wrong target, missing set); see [specialFailed]. */
    var failed: Boolean = false
        private set

    /**
     * Audit C-15: marks this special as not performed. [SpecialAttacks.perform] then returns the energy and the combat cycle neither
     * swings nor starts an attack delay. Call it only before anything was fired or dealt.
     */
    fun specialFailed() {
        failed = true
    }
}
