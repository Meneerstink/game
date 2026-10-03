package gg.rsmod.plugins.content.areas.lumbridge

import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.quests.getCurrentStage
import gg.rsmod.plugins.content.quests.impl.LostCity

val inShed =
    listOf(
        Tile(3202, 3168),
        Tile(3202, 3169),
        Tile(3203, 3168),
        Tile(3203, 3169),
        Tile(3204, 3168),
    )

val zanarisTile = Tile(2452, 4471)
val shedTile = Tile(3202, 3169)

on_obj_option(Objs.DOOR_2406, "open") {
    val obj = player.getInteractingGameObj()
    val currentStage = player.getCurrentStage(LostCity)

    // Specify the minimum quest stage and the item ID for the Dramen Staff
    val minQuestStage = LostCity.CREATE_DRAMEN_BRANCH // Set your minimum quest stage here
    val dramenStaffId = Items.DRAMEN_STAFF

    // Void LumbridgeSwampShed enterDoor (then the fairy teleport): one locked task - the old script raced two queues and snapped
    // the player onto a door tile before a collision-free walk (audit 2026-10-03).
    val zanaris = player.hasEquipped(intArrayOf(dramenStaffId)) && currentStage >= 5
    player.lockingQueue(lockState = LockState.FULL) {
        handleDoor(this, player, obj)
        if (zanaris) {
            wait(2)
            zanarisTeleport(player)
        }
    }
}

fun zanarisTeleport(player: Player) {
    if (player.tile in inShed) {
        player.canTeleport(TeleportType.FAIRY) {
            player.message("The world starts to shimmer...")
            player.teleport(zanarisTile, TeleportType.FAIRY)
            if (player.getCurrentStage(LostCity) == LostCity.CREATE_DRAMEN_BRANCH) {
                LostCity.finishQuest(player)
            }
        }
    }
}

suspend fun handleDoor(
    task: QueueTask,
    player: Player,
    obj: GameObject,
) {
    val openDoor = DynamicObject(id = 2406, type = 0, rot = 1, tile = Tile(x = 3202, z = 3169))
    val door = DynamicObject(id = 2406, type = 0, rot = 2, tile = Tile(x = 3201, z = 3169))
    val far = if (player.tile in inShed) Tile(3201, 3169) else Tile(3202, 3169)
    world.remove(obj)
    world.spawn(openDoor)
    player.playSound(Sfx.DOOR_OPEN)
    gg.rsmod.plugins.content.skills.agility.doorStepFrom(task, player, far)
    task.wait(1)
    world.remove(openDoor)
    player.playSound(Sfx.DOOR_CLOSE)
    world.spawn(door)
}
