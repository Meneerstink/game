package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.AttributeKey
import java.lang.ref.WeakReference
import gg.rsmod.plugins.content.areas.wilderness.bosses.*

/** OSRS Singles-plus combat / PJ timer: player combat may take priority over an ordinary NPC reservation. */
object SinglesPlus {
    fun contains(tile: Tile): Boolean =
        WildernessLairs.LAIRS.any { it.surfaceExit != null && it.contains(tile) } ||
            VenenatisCombat.SPINDEL_ARENA.inChasm(tile) || RevenantCaveFee.inCaves(tile)

    private data class Reservation(val opponent: WeakReference<Player>, val cycle: Int)
    private val PVP = AttributeKey<Reservation>()

    /** Separate identity from last NPC hit: a continuing boss must not unlock an ongoing PvP fight to a third player. */
    fun recordAttack(pawn: Pawn, target: Pawn) {
        if (pawn !is Player || target !is Player || !contains(pawn.tile) || !contains(target.tile)) return
        pawn.attr[PVP] = Reservation(WeakReference(target), pawn.world.currentCycle)
        target.attr[PVP] = Reservation(WeakReference(pawn), pawn.world.currentCycle)
    }

    fun reservation(pawn: Pawn): Player? {
        if (pawn !is Player || !contains(pawn.tile)) return null
        val held = pawn.attr[PVP] ?: return null
        if (pawn.world.currentCycle - held.cycle !in 0 until Combat.PJ_TICKS) return null
        return held.opponent.get()?.takeIf { it.isOnline && !it.isDead() && contains(it.tile) }
    }

    fun allowsImpact(attacker: Player, target: Player): Boolean =
        (reservation(attacker)?.let { it === target } ?: true) &&
            (reservation(target)?.let { it === attacker } ?: true)

    private val BOSS_ATTACKERS = setOf(ArtioCombat.NPC, CalvarionCombat.NORMAL, CalvarionCombat.ENRAGED, VenenatisCombat.SPINDEL,
        VenenatisCombat.SPINDEL_SPIDERLING, CalvarionCombat.HOUND, CalvarionCombat.GREATER_HOUND,
        RevenantMaledictus.NPC)

    fun ignoresReservation(pawn: Pawn, challenger: Pawn, lastAttacker: Pawn): Boolean {
        if (pawn !is Player || !contains(pawn.tile) || !contains(challenger.tile)) return false
        if (challenger is Player && lastAttacker is Npc && lastAttacker.owner == null) return true
        // These bosses (and their minions) continue to fight through PvP. Ordinary revenants do not.
        return challenger is Npc && challenger.id in BOSS_ATTACKERS && lastAttacker is Player
    }
}
