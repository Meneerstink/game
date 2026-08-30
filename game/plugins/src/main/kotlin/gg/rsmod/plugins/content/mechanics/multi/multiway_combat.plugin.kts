package gg.rsmod.plugins.content.mechanics.multi

import gg.rsmod.plugins.api.ext.isMulti

val MULTIWAY_VARC = 616

load_service(MultiService())

// R03.4: the crossed-swords multi-combat icon previously had no login/reconnect check at all -
// only on_enter/on_exit region/chunk set it, so a player logging in (or reconnecting) already
// standing inside a multi-combat zone would see no icon until they next crossed a region/chunk
// boundary. Set the correct initial state immediately, same pattern wilderness.plugin.kts uses
// for the danger icon.
on_login {
    player.setVarc(MULTIWAY_VARC, if (player.tile.isMulti(world)) 1 else 0)
}

on_world_init {
    world.getService(MultiService::class.java)!!.let { service ->
        // Handling Regions
        service.multiRegions.forEach { region ->
            set_multi_combat_region(region)
            on_enter_exit_region(region)
        }
        // Handling Chunks
        service.multiChunks.forEach { chunk ->
            set_multi_combat_chunk(chunk)
            on_enter_exit_chunk(chunk)
        }
    }
}

fun on_enter_exit_region(region: Int) {
    on_enter_region(region) {
        player.setVarc(MULTIWAY_VARC, 1)
    }

    on_exit_region(region) {
        player.setVarc(MULTIWAY_VARC, 0)
    }
}

fun on_enter_exit_chunk(chunk: Int) {
    on_enter_chunk(chunk) {
        player.setVarc(MULTIWAY_VARC, 1)
    }

    on_exit_chunk(chunk) {
        player.setVarc(MULTIWAY_VARC, 0)
    }
}
