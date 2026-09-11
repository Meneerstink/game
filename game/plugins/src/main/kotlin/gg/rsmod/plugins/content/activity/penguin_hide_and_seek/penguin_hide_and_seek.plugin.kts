package gg.rsmod.plugins.content.activity.penguin_hide_and_seek

import gg.rsmod.plugins.api.ext.getInteractingNpc

/**
 * Q-056 (part 3/3): Penguin Hide and Seek. See `PenguinData.kt`/`PenguinHideAndSeekHandler.kt`
 * for sourcing notes and disclosed simplifications.
 */

on_world_init {
    PenguinHideAndSeekHandler.ensureCurrentWeek(world)
    world.timers[PenguinHideAndSeekHandler.PenguinRotationTimer] = 1
}

on_timer(PenguinHideAndSeekHandler.PenguinRotationTimer) {
    PenguinHideAndSeekHandler.ensureCurrentWeek(world)
    world.timers[PenguinHideAndSeekHandler.PenguinRotationTimer] = 100 * 60 * 60
}

fun updateWeekIfNeeded(player: gg.rsmod.game.model.entity.Player) {
    val week = PenguinHideAndSeekHandler.currentWeekNumber()
    if (player.attr.getOrDefault(PenguinWeek, -1) != week) {
        player.attr[PenguinWeek] = week
        player.attr[PenguinFoundMask] = 0
    }
}

PenguinDisguise.values().forEach { disguise ->
    on_npc_option(npc = disguise.npcId, option = "Spy-on") {
        val npc = player.getInteractingNpc()
        val index = PenguinHideAndSeekHandler.indexOf(npc)
        if (index == -1) {
            return@on_npc_option
        }
        updateWeekIfNeeded(player)
        val mask = player.attr.getOrDefault(PenguinFoundMask, 0)
        val bit = 1 shl index
        if (mask and bit != 0) {
            player.filterableMessage("You've already spotted this penguin spy.")
        } else {
            player.attr[PenguinFoundMask] = mask or bit
            player.attr[PenguinPoints] = player.attr.getOrDefault(PenguinPoints, 0) + 1
            player.filterableMessage(
                "You spy on the penguin. You now have ${player.attr.getOrDefault(PenguinPoints, 0)} Penguin Points.",
            )
        }
    }
}
