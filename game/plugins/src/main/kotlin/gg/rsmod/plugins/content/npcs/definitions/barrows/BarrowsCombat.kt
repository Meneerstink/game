package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.plugins.content.combat.*
import java.lang.ref.WeakReference

/** OSRS Wiki Single-way combat#Barrows brothers: owned brothers take priority over crypt creatures. */
object BarrowsCombat {
    private fun owner(pawn: Pawn): Player? = (pawn as? Npc)?.takeIf {
        Barrows.Brother.byNpc(it.id) != null && Barrows.underground(it.tile)
    }?.owner

    fun sameEncounter(a: Pawn, b: Pawn): Boolean = owner(a)?.let { it === owner(b) } == true

    fun ignoresReservation(pawn: Pawn, challenger: Pawn, last: Pawn): Boolean =
        pawn is Player && owner(challenger) === pawn && last is Npc &&
            Barrows.underground(pawn.tile) && Barrows.underground(last.tile) &&
            last.id in Barrows.CRYPT_MONSTERS && (last.owner == null || last.owner === pawn)

    /** Stop both directions of the old crypt fight, without clearing a player/PvP reservation. */
    fun takePriority(player: Player, brother: Npc): Boolean {
        if (owner(brother) !== player) return false
        val oldTarget = player.getCombatTarget()
        val oldAttacker = player.liveLastHitBy()
        if (oldAttacker is Player) return false
        val crypts = listOfNotNull(oldTarget, oldAttacker).filter { ignoresReservation(player, brother, it) }.distinct()
        crypts.forEach { crypt ->
            if (crypt.getCombatTarget() === player) Combat.reset(crypt)
            if (crypt.liveLastHitBy() === player) crypt.clearActiveCombatTimer()
        }
        if (oldAttacker in crypts) {
            // Reserve the handover now, so the crypt cannot reacquire the player before the brother's first hit.
            player.attr[LAST_HIT_BY_ATTR] = WeakReference(brother)
            player.timers[Combat.PJ_TIMER] = Combat.PJ_TICKS
        }
        if (oldTarget in crypts) Combat.reset(player)
        // Retarget an existing player attack only; a player with auto-retaliate off is not forced into attacking.
        return oldTarget in crypts
    }
}
