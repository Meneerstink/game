package gg.rsmod.plugins.content.mechanics.traversal.batch3

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Trace
import gg.rsmod.plugins.content.skills.agility.isUsableLanding
import gg.rsmod.plugins.content.skills.agility.shortcut

/**
 * Swensen the Navigator's portal maze under his house in Rellekka (music "The Navigator", region 10652), obstacle batch 3, owner
 * priority #1 2026-10-03. Reachable: Swensen's ladder 4158 (2644,3657) -> (2631,10006) is in object-teleports.json (Void), so the
 * maze is open on this server; its portals answered "Nothing interesting happens" (the escape ropes 4161 are batch-3.json).
 *
 * Source: 2009scape content/region/fremennik/.../TFTInteractionListeners.kt (no Void / Novite port): the named portals 2273, 2274,
 * 2503-2507 each lead to a fixed room (DestRoom(swx, swy, nex, ney).getCenter() = the room's middle, one tile east), 2504 being the
 * way to the final room; every other portal (5138) leads to a random one of the maze's portals. The OSRS Wiki (Swensen the Navigator)
 * describes the same maze of portal rooms with an escape rope in each room.
 *
 * Owner E17 "never a teleport into scenery": the donor's random portal landed ON the portal loc (a solid 1x1); here the landing is a
 * usable tile right beside it ([isUsableLanding]), and a room centre that is not usable falls back to the nearest usable tile of
 * that room. No animation in the source (a plain teleport after the click).
 */

data class Room(val swx: Int, val swz: Int, val nex: Int, val nez: Int) {
    fun centre(): Tile = Tile((swx + nex) / 2 + 1, (swz + nez) / 2, 0)

    fun contains(t: Tile) = t.x in swx..nex && t.z in swz..nez
}

val FIXED_PORTALS =
    mapOf(
        2273 to Room(2639, 10012, 2645, 10018),
        2274 to Room(2650, 10034, 2656, 10040),
        2506 to Room(2662, 10023, 2669, 10029),
        2507 to Room(2626, 10023, 2633, 10029),
        2505 to Room(2650, 10001, 2656, 10007),
        2503 to Room(2662, 10012, 2668, 10018),
        2504 to Room(2662, 10034, 2668, 10039),
    )

/** Every 5138 placement of this 667 map (census tools/conformance/traversal/placements_667.tsv). */
val RANDOM_PORTALS =
    listOf(
        Tile(2627, 10026), Tile(2627, 10037), Tile(2628, 10005), Tile(2628, 10015), Tile(2630, 10029), Tile(2630, 10034),
        Tile(2630, 10040), Tile(2631, 10008), Tile(2631, 10012), Tile(2631, 10018), Tile(2633, 10026), Tile(2633, 10037),
        Tile(2634, 10005), Tile(2634, 10015), Tile(2639, 10005), Tile(2639, 10026), Tile(2639, 10039), Tile(2642, 10002),
        Tile(2642, 10008), Tile(2642, 10012), Tile(2642, 10018), Tile(2642, 10023), Tile(2642, 10029), Tile(2642, 10036),
        Tile(2642, 10042), Tile(2645, 10005), Tile(2645, 10015), Tile(2645, 10026), Tile(2645, 10039), Tile(2650, 10004),
        Tile(2650, 10015), Tile(2650, 10037), Tile(2651, 10026), Tile(2653, 10001), Tile(2653, 10007), Tile(2653, 10012),
        Tile(2653, 10018), Tile(2653, 10034), Tile(2653, 10040), Tile(2654, 10023), Tile(2654, 10029), Tile(2656, 10015),
        Tile(2657, 10026), Tile(2661, 10004), Tile(2662, 10015), Tile(2663, 10026), Tile(2664, 10001), Tile(2664, 10007),
        Tile(2665, 10012), Tile(2666, 10023), Tile(2667, 10004), Tile(2668, 10015), Tile(2669, 10026),
    )

/** The room's centre when a player can stand there, else the nearest usable tile of the room. */
fun roomLanding(room: Room): Tile? {
    val centre = room.centre()
    if (isUsableLanding(world, centre)) return centre
    return (room.swx..room.nex).flatMap { x -> (room.swz..room.nez).map { z -> Tile(x, z, 0) } }
        .filter { isUsableLanding(world, it) }
        .minByOrNull { (it.x - centre.x) * (it.x - centre.x) + (it.z - centre.z) * (it.z - centre.z) }
}

/** A usable tile right beside a random maze portal other than the one clicked. */
fun randomPortalLanding(clicked: Tile): Tile? {
    for (portal in RANDOM_PORTALS.filter { !it.sameAs(clicked) }.shuffled()) {
        val beside = Direction.NESW.map { portal.step(it) }.firstOrNull { isUsableLanding(world, it) }
        if (beside != null) return beside
    }
    return null
}

fun enterPortal(player: Player, landing: Tile?) {
    val obj = player.getInteractingGameObj()
    if (landing == null) {
        Trace.problem(Trace.Cat.PLUGIN) { "swensen maze portal ${obj.id} at ${obj.tile.x},${obj.tile.z}: no usable landing" }
        player.message("Nothing interesting happens.")
        return
    }
    player.lockingQueue(lockState = LockState.FULL) {
        wait(1)
        player.moveTo(landing)
    }
}

FIXED_PORTALS.forEach { (id, room) ->
    shortcut(id, "Enter") { enterPortal(player, roomLanding(room)) }
}

shortcut(5138, "Enter") { enterPortal(player, randomPortalLanding(player.getInteractingGameObj().tile)) }
