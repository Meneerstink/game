package gg.rsmod.game.sync.task

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.sync.SynchronizationTask

/**
 * @author Tom <rspsmods@gmail.com>
 */
object NpcPostSynchronizationTask : SynchronizationTask<Npc> {
    override fun run(pawn: Npc) {
        val oldTile = pawn.lastTile
        val moved = oldTile == null || !oldTile.sameAs(pawn.tile)

        if (moved) {
            pawn.lastTile = Tile(pawn.tile)
        }
        // Breach conformance 2026-09-27: a moved (moveTo/teleportTo) npc is removed from every player's local list this cycle and
        // re-added next cycle (NpcSynchronizationTask, RCV-012 B3/B4), so nobody received its update blocks of this cycle - the
        // attack animation and hitsplat of a Greater abyssal demon that teleports and strikes on the same tick were lost for every
        // player (in-engine run: 3 of 10 swings unseen). Those blocks are kept and go out with the re-add on the next cycle.
        val carry = pawn.moved && pawn.blockBuffer.isDirty()
        // A carried block goes out (and is recorded) with the re-add on the next cycle.
        if (!carry) gg.rsmod.game.model.Probe.endOfCycle(pawn)
        pawn.moved = false
        pawn.steps = null
        if (!carry) {
            pawn.blockBuffer.clean()
        } else {
            pawn.blockBuffer.teleport = false
            gg.rsmod.game.model.BreachTrace.log("sync.carry") { "npc=${pawn.id}#${pawn.index} anim=${pawn.blockBuffer.animation} hits=${pawn.blockBuffer.hits.size}" }
        }
        if (pawn.teleported) {
            pawn.invisible = false
            pawn.teleported = false
        }
    }
}
