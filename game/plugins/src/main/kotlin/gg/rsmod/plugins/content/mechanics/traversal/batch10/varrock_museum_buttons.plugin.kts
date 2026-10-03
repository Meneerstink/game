package gg.rsmod.plugins.content.mechanics.traversal.batch10

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.plugins.content.skills.agility.animateLoc
import gg.rsmod.plugins.content.skills.agility.locAt

/*
 * Varrock Museum basement, Natural History displays: the 14 "Push" buttons 24590-24603 (obstacle batch 10, 2026-10-03 - the traversal
 * census lists them as levers, so the conform traversal sweep counted them as unhandled). The basement is reachable (museum stairs in
 * object-teleports.json, 1758,4959 / 1759,4958) and the display NPCs are spawned (areas/spawns/spawns_6989.plugin.kts).
 *
 * Source: Void content/area/misthalin/varrock/museum/MuseumDisplays.kt objectOperate("Push", "vm_button_*") with Void's
 * varrock_museum.objs/anims/sounds.toml ids: the button is swapped for the pressed button (vm_button_1x1 24588 / vm_button_2x1 24589)
 * for 14 ticks; after 1 tick the player plays vm_player_button_push 6462, the camera moves/turns to the display, vm_push_button 3664
 * sounds; 4 ticks later the display (an NPC, or for the Kalphite Queen and the snake a loc) plays its animation, the case's cogs play
 * vm_display_case_cogs 6427 and the animal's sound plays around the button; after 9 ticks (7 + the "fixed" pose + 2 for the loc
 * displays) the camera is reset. Void's tiles are this map's tiles (the button placements match Void's camera targets).
 * Void areaSound(radius = 10, repeat) is sent here to every player within 10 tiles on the plane, [repeat] times.
 */

data class Display(
    val pressed: Int,
    val camMove: Tile,
    val camMoveHeight: Int,
    val camTurn: Tile,
    val camTurnHeight: Int,
    val npc: Int?,
    val displayTile: Tile,
    val displayLoc: Int?,
    val anim: Int,
    val fixedAnim: Int?,
    val cogs: Int,
    val cogsTile: Tile,
    val sound: Int,
    val soundOffset: Pair<Int, Int>,
    val repeat: Int,
)

val BUTTON_1X1 = 24588
val BUTTON_2X1 = 24589
val ANIM_PUSH = 6462
val ANIM_COGS = 6427
val SOUND_PUSH = 3664

val DISPLAYS =
    mapOf(
        24602 to Display(BUTTON_1X1, Tile(1757, 4942), 375, Tile(1756, 4941), 300, Npcs.TERRORBIRD_DISPLAY, Tile(1752, 4938), null, 6442, null, 24586, Tile(1751, 4938), 3668, -1 to 0, 1),
        24603 to Display(BUTTON_1X1, Tile(1759, 4946), 700, Tile(1764, 4939), 100, null, Tile(1763, 4937), 15490, 6476, 6477, 24587, Tile(1768, 4938), 3655, 1 to 0, 1),
        24600 to Display(BUTTON_1X1, Tile(1778, 4958), 550, Tile(1776, 4956), 325, Npcs.MONKEY_DISPLAY, Tile(1774, 4954), null, 6434, null, 24584, Tile(1774, 4953), 3662, -1 to 0, 2),
        24601 to Display(BUTTON_1X1, Tile(1782, 4959), 350, Tile(1782, 4958), 300, Npcs.SEA_SLUGS_DISPLAY, Tile(1781, 4954), null, 6432, null, 24585, Tile(1781, 4953), 3665, -1 to 0, 4),
        24599 to Display(BUTTON_1X1, Tile(1779, 4962), 350, Tile(1781, 4964), 325, null, Tile(1781, 4964), 24569, 6452, 6453, 24583, Tile(1781, 4967), 3665, 1 to 0, 4),
        24598 to Display(BUTTON_1X1, Tile(1778, 4960), 425, Tile(1775, 4965), 300, Npcs.SNAIL_DISPLAY, Tile(1774, 4964), null, 6430, null, 24582, Tile(1774, 4967), 3666, 1 to 0, 3),
        24597 to Display(BUTTON_1X1, Tile(1749, 4959), 500, Tile(1743, 4956), 300, Npcs.PENGUIN_DISPLAY, Tile(1742, 4954), null, 6438, null, 24581, Tile(1742, 4953), 3663, 0 to -1, 1),
        24596 to Display(BUTTON_1X1, Tile(1733, 4959), 375, Tile(1734, 4958), 300, Npcs.MOLE_DISPLAY, Tile(1735, 4954), null, 6450, null, 24580, Tile(1735, 4953), 3661, 0 to -1, 1),
        24594 to Display(BUTTON_1X1, Tile(1732, 4958), 500, Tile(1735, 4962), 300, Npcs.CAMEL_DISPLAY, Tile(1735, 4964), null, 6440, null, 24578, Tile(1735, 4967), 3647, 0 to 1, 1),
        24595 to Display(BUTTON_1X1, Tile(1743, 4956), 650, Tile(1743, 4962), 200, Npcs.LEECH_DISPLAY, Tile(1742, 4964), null, 6428, null, 24579, Tile(1742, 4967), 3656, 0 to 1, 1),
        24590 to Display(BUTTON_2X1, Tile(1747, 4975), 525, Tile(1742, 4980), 300, Npcs.LIZARD_DISPLAY, Tile(1740, 4979), null, 6436, null, 24574, Tile(1740, 4983), 3657, 0 to 1, 1),
        24591 to Display(BUTTON_2X1, Tile(1756, 4976), 425, Tile(1753, 4979), 250, Npcs.BATTLE_TORTOISE_DISPLAY, Tile(1750, 4979), null, 6448, null, 24575, Tile(1750, 4984), 3669, 0 to 1, 1),
        24592 to Display(BUTTON_2X1, Tile(1774, 4978), 925, Tile(1766, 4981), 300, Npcs.DRAGON_DISPLAY, Tile(1765, 4979), null, 14254, null, 24576, Tile(1765, 4984), 3649, 0 to 1, 1),
        24593 to Display(BUTTON_2X1, Tile(1781, 4977), 800, Tile(1776, 4980), 300, Npcs.WYVERN_DISPLAY, Tile(1775, 4979), null, 6444, null, 24577, Tile(1775, 4983), 3671, 0 to 1, 1),
    )

/** Void moveCamera/turnCamera(tile, height, speed = 10, acceleration = 10): the camera packets take scene-local tiles. */
fun Player.localTile(tile: Tile): Pair<Int, Int>? {
    val base = lastKnownRegionBase ?: return null
    return (tile.x - base.x) to (tile.z - base.z)
}

/** Void areaSound(id, tile, radius = 10, repeat). */
fun areaSound(
    world: World,
    sound: Int,
    tile: Tile,
    repeat: Int,
) {
    world.players.forEach { p ->
        if (p.tile.height == tile.height && p.tile.isWithinRadius(tile, 10)) p.playSound(sound, loops = repeat)
    }
}

DISPLAYS.forEach { (button, display) ->
    on_obj_option(obj = button, option = "Push") {
        val target = player.getInteractingGameObj()
        player.lockingQueue {
            // Void target.replace(pressed, ticks = 14).
            val chunk = world.chunks.get(target.tile, createIfNeeded = true)
            val original =
                chunk?.getEntities<GameObject>(target.tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)?.firstOrNull { it.id == button }
            if (original != null) {
                world.spawn(DynamicObject(original, display.pressed))
                world.queue {
                    wait(14)
                    world.spawn(DynamicObject(original))
                }
            }
            wait(1)
            player.animate(ANIM_PUSH)
            player.localTile(display.camMove)?.let { (x, z) -> player.moveCameraTo(10, x, z, display.camMoveHeight, 10) }
            player.localTile(display.camTurn)?.let { (x, z) -> player.cameraLookAt(x, z, display.camTurnHeight, 10, 10) }
            player.playSound(SOUND_PUSH)
            wait(4)
            val animal = display.npc
            if (animal != null) {
                var shown: Npc? = null
                world.npcs.forEach { if (shown == null && it.id == animal && it.tile.isWithinRadius(display.displayTile, 8)) shown = it }
                shown?.animate(display.anim)
                    ?: gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                        "museum button $button: display npc $animal not found near ${display.displayTile.x},${display.displayTile.z}"
                    }
            } else {
                locAt(world, display.displayTile, display.displayLoc!!)?.let { animateLoc(world, it, display.anim) }
            }
            locAt(world, display.cogsTile, display.cogs)?.let { animateLoc(world, it, ANIM_COGS) }
            areaSound(world, display.sound, target.tile.transform(display.soundOffset.first, display.soundOffset.second), display.repeat)
            val fixed = display.fixedAnim
            if (fixed != null) {
                wait(7)
                locAt(world, display.displayTile, display.displayLoc!!)?.let { animateLoc(world, it, fixed) }
                wait(2)
            } else {
                wait(9)
            }
            player.resetCamera()
        }
    }
}
