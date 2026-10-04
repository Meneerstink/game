package gg.rsmod.plugins.content.objs

import gg.rsmod.plugins.content.quests.finishedQuest
import gg.rsmod.plugins.content.quests.impl.LostCity

/**
 * @author Harley <https://github.com/HarleyGilpin>
 */

// The outer-ring obstacles (rock, tendrils, eyes, gap, boil, passage) are abyss_obstacles.plugin.kts (Void AbyssObstacles port).

on_obj_option(obj = Objs.LAW_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3049 -> player.moveTo(2466, 4831)
        }
    }
}

on_obj_option(obj = Objs.CHAOS_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3044 -> player.moveTo(2273, 4841)
        }
    }
}

on_obj_option(obj = Objs.NATURE_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3035 -> player.moveTo(2398, 4842)
        }
    }
}

on_obj_option(obj = Objs.COSMIC_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (player.finishedQuest(LostCity))
        {
            if (obj.isSpawned(world)) {
                player.faceTile(obj.tile)
                when (obj.tile.x) {
                    3028 -> player.moveTo(2144, 4831)
                }
            }
        } else {
        player.message("You need to have completed <col=0000ff>Lost City</col> to use this rift.")
    }
}

on_obj_option(obj = Objs.BLOOD_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3027 -> player.moveTo(2468, 4889, 1)
        }
    }
}

on_obj_option(obj = Objs.FIRE_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3029 -> player.moveTo(2574, 4848)
        }
    }
}

on_obj_option(obj = Objs.EARTH_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3031 -> player.moveTo(2655, 4830)
        }
    }
}

on_obj_option(obj = Objs.BODY_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3039 -> player.moveTo(2521, 4834)
        }
    }
}

on_obj_option(obj = Objs.MIND_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3044 -> player.moveTo(2793, 4828)
        }
    }
}

on_obj_option(obj = Objs.AIR_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3047 -> player.moveTo(2841, 4829)
        }
    }
}

on_obj_option(obj = Objs.SOUL_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3050 -> player.message("A strange power blocks your entrance.")
        }
    }
}

on_obj_option(obj = Objs.WATER_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3051 -> player.moveTo(2725, 4832)
        }
    }
}

on_obj_option(obj = Objs.DEATH_RIFT, option = "exit-through", lineOfSightDistance = 1) {
    val obj = player.getInteractingGameObj()
    if (obj.isSpawned(world)) {
        player.faceTile(obj.tile)
        when (obj.tile.x) {
            3050 -> player.moveTo(2208, 4830)
        }
    }
}
