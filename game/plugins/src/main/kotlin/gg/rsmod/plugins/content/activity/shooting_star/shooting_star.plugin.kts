package gg.rsmod.plugins.content.activity.shooting_star

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.ext.getInteractingGameObj

/**
 * Q-056 (part 2/3): Shooting Star. See `ShootingStarHandler.kt`/`ShootingStarMining.kt`/
 * `ShootingStarData.kt` for the mechanic and its sourcing notes.
 */

on_world_init {
    ShootingStarHandler.scheduleNext(world)
}

on_timer(ShootingStarEventTimer) {
    ShootingStarHandler.startEvent(world)
    ShootingStarHandler.scheduleNext(world)
}

on_timer(ShootingStarSpriteDespawnTimer) {
    ShootingStarHandler.despawnSprite(world)
}

on_timer(ShootingStarBonusOreTimer) {
    player.message("<dark_red>The ability to mine an extra ore has worn off.")
}

ShootingStarTierObjects.forEach { tierObj ->
    on_obj_option(obj = tierObj, option = "Mine") {
        val obj = player.getInteractingGameObj()
        player.queue {
            ShootingStarMining.mine(this, obj)
        }
    }
    on_obj_option(obj = tierObj, option = "Prospect") {
        player.filterableMessage("There is ${ShootingStarState.percentageRemaining()}% left of this layer.")
    }
}

on_obj_option(obj = Objs.SHOOTING_STAR_NOTICEBOARD, option = "Read") {
    player.message(
        "A shooting star has crashed nearby! Mine it for stardust, then exchange the stardust " +
            "with the star sprite that appears once it's exhausted.",
    )
}

on_npc_option(npc = Npcs.STAR_SPRITE, option = "Talk-to") {
    val stardust = player.inventory.getItemCount(Items.STARDUST)
    if (stardust <= 0) {
        player.filterableMessage("You don't seem to have any stardust that I can exchange for a reward.")
    } else if (!player.inventory.hasFreeSpace()) {
        player.filterableMessage("You don't have enough inventory space to collect your reward.")
    } else {
        player.inventory.remove(Items.STARDUST, stardust)
        ShootingStarRewards.calculate(stardust).forEach { (item, amount) ->
            if (amount > 0) {
                player.inventory.add(item, amount)
            }
        }
        if (!player.timers.has(ShootingStarBonusOreTimer)) {
            player.timers[ShootingStarBonusOreTimer] = 900
            player.filterableMessage(
                "Thank you for helping me out! I have rewarded you by making it so you can mine " +
                    "an extra ore for the next 15 minutes.",
            )
        } else {
            player.filterableMessage("Thank you for helping me out! You already have the ability to mine extra ore.")
        }
    }
}
