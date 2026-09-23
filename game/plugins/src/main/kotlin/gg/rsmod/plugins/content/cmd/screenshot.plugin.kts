package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.priv.Privilege

/*
 * `screenshot [distance]`: fixed camera tour around the player for layout reviews anywhere in the world. For each
 * viewpoint the camera is snapped (CAM_MOVETO / CAM_LOOKAT), and the RSPS client, on the console marker
 * "__RSPS_SHOT__:<name>", saves the game canvas to C:/RSPS/screens. Same viewpoints every time, so before/after
 * comparisons line up.
 */

val SHOT_PREFIX = "__RSPS_SHOT__:"

on_command("screenshot", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val distance = args.getOrNull(0)?.toIntOrNull()?.coerceIn(4, 30) ?: 12
    val base = player.lastKnownRegionBase
    if (base == null) {
        player.message("Screenshot: the map is not loaded yet.")
        return@on_command
    }
    val focus = player.tile
    val lx = focus.x - base.x
    val lz = focus.z - base.z

    // name, camera offset (tiles), camera height (client units / 4; 128 = one tile).
    val viewpoints =
        listOf(
            Triple("south", 0 to -distance, 3 * 128),
            Triple("west", -distance to 0, 3 * 128),
            Triple("north", 0 to distance, 3 * 128),
            Triple("east", distance to 0, 3 * 128),
            Triple("overhead", 0 to -(distance / 3).coerceAtLeast(2), 8 * 128),
        )
    player.message("Screenshot tour of ${focus.x}, ${focus.z}: stand still for a few seconds.")
    player.queue {
        for ((name, offset, height) in viewpoints) {
            val cx = (lx + offset.first).coerceIn(1, 102)
            val cz = (lz + offset.second).coerceIn(1, 102)
            player.moveCameraTo(100, cx, cz, height, 0)
            player.cameraLookAt(lx, lz, 64, 0, 100)
            wait(2)
            player.message("$SHOT_PREFIX${focus.x}_${focus.z}_$name", type = ChatMessageType.CONSOLE)
            wait(1)
        }
        player.resetCamera()
        player.message("Screenshots saved to C:/RSPS/screens.")
    }
}
