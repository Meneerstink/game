package gg.rsmod.plugins.content.mechanics.npcwalk

import gg.rsmod.game.model.attr.FACING_PAWN_ATTR
import gg.rsmod.game.model.attr.NO_CLIP_ATTR
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards

val SEARCH_FOR_PATH_TIMER = NpcRandomWalk.TIMER
val SEARCH_FOR_PATH_DELAY = NpcRandomWalk.DELAY

on_global_npc_spawn {
    // Owner 2026-09-18: "all guards we have in our rsps in a safezone need to be able to roam! max
    // 8 tiles only in the safezone" - the ordinary city guards too, not just the Deadman guards
    // (those patrol from CityGuards.leash).
    if (CityGuards.isOrdinaryZoneGuard(npc)) {
        npc.walkRadius = CityGuards.PATROL_RADIUS
    }
    if (npc.walkRadius > 0) {
        npc.timers[SEARCH_FOR_PATH_TIMER] = world.random(SEARCH_FOR_PATH_DELAY)
    }
}

on_timer(SEARCH_FOR_PATH_TIMER) {
    // CityGuards.leash is the sole patrol driver for Deadman guards. A second generic route
    // could overwrite the safe patrol path and make the guard look stationary at fences.
    if (!CityGuards.isGuard(npc) && npc.isActive() && npc.lock.canMove()) {
        val facing = npc.attr[FACING_PAWN_ATTR]?.get()

        /*
         * The npc is not facing a player, so it can walk.
         */
        if (facing == null) {
            val rx = world.random(-npc.walkRadius..npc.walkRadius)
            val rz = world.random(-npc.walkRadius..npc.walkRadius)

            val start = npc.spawnTile
            val dest = start.transform(rx, rz)

            val noClip = npc.attr[NO_CLIP_ATTR] ?: false

            /*
             * Only walk to destination if the chunk has previously been created. A city guard
             * posted in a safe zone only ever picks a roaming destination inside that zone.
             */
            val allowed = !CityGuards.isOrdinaryZoneGuard(npc) || CityGuards.isGuardedZone(dest)
            if (allowed && world.collision.chunks.get(dest, createIfNeeded = false) != null) {
                npc.walkMask = npc.def.walkMask
                npc.walkTo(dest, detectCollision = !noClip)
            }
        }
    }

    npc.timers[SEARCH_FOR_PATH_TIMER] = world.random(SEARCH_FOR_PATH_DELAY)
}
