package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc

/**
 * N606 12-11 (owner: "bij ba ba zie je geen ... boulders", trace run 21:4x: the client never built npc 15180/15181): a mechanic npc that
 * moves one tile EVERY tick must WALK there, not teleport. NpcSynchronizationTask removes a moveTo'd npc from the client on that cycle
 * and only adds it back on a cycle in which it did not move (RCV-012) - an npc moveTo'd every tick (Ba-Ba's rolling boulders, Akkha's
 * orbs, the Wardens' orb pulse, Zebak's jugs and wave, Het's orbs) was therefore never on any client. A walking step is a normal npc
 * movement update (smooth, like OSRS). Same rules as the old moveTo: no wall check and no entity blocking (the mechanics decide what
 * a step hits themselves); the step lands within the tick, so the caller's next-tick logic reads the new tile as before.
 */
object ToaSlide {
    fun step(npc: Npc, dest: Tile) {
        if (!npc.isSpawned()) return
        val here = npc.tile
        if (here.height != dest.height || Math.max(Math.abs(here.x - dest.x), Math.abs(here.z - dest.z)) > 1) {
            npc.moveTo(dest) // not a single step (a jump): keep the teleport; it is shown again the tick after
            return
        }
        npc.ignoresEntityCollision = true
        npc.movementQueue.clear()
        npc.movementQueue.addStep(dest, MovementQueue.StepType.FORCED_WALK, false)
    }
}
