package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.Combat
import java.lang.ref.WeakReference

/** Mage Arena only: Kolodion/MA2 cannot shield their owner from PKers (Wiki Mage Arena; owner 14-11).
 * As with clue guardians, keep the ordinary player PJ reservation separate from continuing NPC hits.
 */
object MageArenaPvp {
    private data class Reservation(val opponent: WeakReference<Player>, val cycle: Int)
    private val PVP = AttributeKey<Reservation>()

    fun ownsOpponent(player: Pawn, npc: Pawn): Boolean = player is Player && npc is Npc &&
        (MageArena.ownsDuelNpc(player, npc) || MageArenaIIEncounter.stateOf(npc)?.owner?.get() === player)

    private fun fighting(player: Player): Boolean = MageArena.dueling(player) ||
        MageArenaII.God.values().any { MageArenaIIEncounter.active(player, it) != null }

    fun reservation(pawn: Pawn): Player? {
        if (pawn !is Player || pawn.tile.isMulti(pawn.world)) return null
        val held = pawn.attr[PVP] ?: return null
        if (pawn.world.currentCycle - held.cycle !in 0 until Combat.PJ_TICKS) return null
        return held.opponent.get()?.takeIf { it.isOnline && !it.isDead() }
    }

    fun involved(attacker: Player, target: Player): Boolean = fighting(attacker) || fighting(target) ||
        reservation(attacker) != null || reservation(target) != null

    fun recordAttack(attacker: Pawn, target: Pawn) {
        if (attacker !is Player || target !is Player || attacker === target) return
        if (attacker.tile.isMulti(attacker.world) && target.tile.isMulti(target.world)) return
        if (attacker.tile.getWildernessLevel() <= 0 && target.tile.getWildernessLevel() <= 0) return
        if (!involved(attacker, target) || !allowsImpact(attacker, target)) return
        attacker.attr[PVP] = Reservation(WeakReference(target), attacker.world.currentCycle)
        target.attr[PVP] = Reservation(WeakReference(attacker), target.world.currentCycle)
    }

    fun allowsImpact(attacker: Player, target: Player): Boolean =
        (reservation(attacker)?.let { it === target } ?: true) &&
            (reservation(target)?.let { it === attacker } ?: true)

    fun ignoresReservation(pawn: Pawn, challenger: Pawn, lastAttacker: Pawn): Boolean {
        if (pawn !is Player || pawn.tile.getWildernessLevel() <= 0) return false
        if (ownsOpponent(pawn, lastAttacker) &&
            (challenger is Player || challenger.attr[DAMAGE_CREDIT_ATTR]?.get() is Player)) return true
        // The owner's actual arena opponent can keep fighting while one PKer attacks the player.
        return lastAttacker is Player && ownsOpponent(pawn, challenger)
    }
}
