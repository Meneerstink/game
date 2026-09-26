package gg.rsmod.plugins.content.newplayer

import gg.rsmod.game.model.LockState

/*
 * The AFK skill basement under the Grand Exchange hall (AfkArea): the spiral staircases in both directions and the
 * stations. The overrides only answer on the basement's own tiles and ids, so every other staircase and skill loc of the
 * world keeps its normal behaviour.
 */

on_world_init {
    world.plugins.bindObjectOverride { p, obj, _ ->
        val down = obj.id == AfkArea.HALL_STAIRS_DOWN && obj.tile == AfkArea.HALL_STAIRS
        val up = obj.id == AfkArea.BASEMENT_STAIRS_UP && obj.tile == AfkArea.BASEMENT_STAIRS
        val station = if (AfkArea.contains(obj.tile)) AfkArea.station(obj.id) else null
        when {
            down || up -> {
                p.lockingQueue(lockState = LockState.FULL) {
                    wait(2)
                    p.moveTo(if (down) AfkArea.BASEMENT_ARRIVAL else AfkArea.HALL_ARRIVAL)
                    if (down) p.message("You descend into the skilling basement. Click a station once to train it, up to level ${NewPlayerConfig.current.afkLevelCap}.")
                }
                true
            }
            station != null -> {
                startTraining(p, station, obj)
                true
            }
            else -> false
        }
    }
}

fun startTraining(player: Player, station: AfkArea.Station, obj: GameObject) {
    val config = NewPlayerConfig.current
    val name = Skills.getSkillName(world, station.skill)
    if (AfkArea.atCap(player, station.skill, config)) {
        player.message("Your $name is level ${config.afkLevelCap} or higher; this station cannot train it any further.")
        return
    }
    player.faceTile(obj.tile)
    player.message("You start training $name. Move or do something else to stop.")
    // A normal (weak) queue: any movement, click or logout cancels it, which is exactly "until he stops".
    player.queue {
        var tick = 0
        while (true) {
            if (station.animation != -1 && tick % 4 == 0) player.animate(station.animation)
            if (!AfkArea.train(player, station.skill, config)) {
                player.animate(-1)
                player.message("Your $name has reached level ${config.afkLevelCap}, the most this station can teach.")
                break
            }
            tick++
            wait(1)
        }
    }
}
