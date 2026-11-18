package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.Combat
import java.lang.ref.WeakReference

/** Wiki Double agent, 10 September 2015: trail NPCs cannot shield a player from
 * PvP in singles, nor let several players attack that player simultaneously.
 * Keep a separate player reservation: a subsequent wizard hit must not erase it.
 */
object ClueGuardianPvp {
    private data class Reservation(val opponent: WeakReference<Player>, val cycle: Int)
    private val PVP = AttributeKey<Reservation>()

    fun reservation(pawn: Pawn): Player? {
        if (pawn !is Player || pawn.tile.isMulti(pawn.world)) return null
        val held = pawn.attr[PVP] ?: return null
        if (pawn.world.currentCycle - held.cycle !in 0 until Combat.PJ_TICKS) return null
        return held.opponent.get()?.takeIf { it.isOnline && !it.isDead() }
    }

    fun recordAttack(attacker: Pawn, target: Pawn) {
        if (attacker !is Player || target !is Player || attacker === target) return
        if (attacker.tile.isMulti(attacker.world) && target.tile.isMulti(target.world)) return
        if (attacker.tile.getWildernessLevel() <= 0 && target.tile.getWildernessLevel() <= 0) return
        if (TreasureTrailGuardians.session(attacker) == null && TreasureTrailGuardians.session(target) == null &&
            reservation(attacker) == null && reservation(target) == null) return
        // A stale queued attack may not overwrite an existing player opponent.
        if (!allowsImpact(attacker, target)) return
        attacker.attr[PVP] = Reservation(WeakReference(target), attacker.world.currentCycle)
        target.attr[PVP] = Reservation(WeakReference(attacker), target.world.currentCycle)
    }

    fun allowsImpact(attacker: Player, target: Player): Boolean =
        (reservation(attacker)?.let { it === target } ?: true) &&
            (reservation(target)?.let { it === attacker } ?: true)

    fun ignoresNpc(pawn: Pawn, challenger: Pawn, lastAttacker: Pawn): Boolean =
        pawn is Player && (challenger is Player || challenger.attr[DAMAGE_CREDIT_ATTR]?.get() is Player) && lastAttacker is Npc &&
            pawn.tile.getWildernessLevel() > 0 &&
            TreasureTrailGuardians.owner(lastAttacker) === pawn
}
