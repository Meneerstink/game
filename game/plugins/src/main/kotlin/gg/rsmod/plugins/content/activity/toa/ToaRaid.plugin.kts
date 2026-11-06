package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.content.mechanics.death.SafeDeath
import gg.rsmod.game.model.entity.Player

// OSRS ToA is cooperative. Also refuse cross-instance attacks from either side of a raid boundary.
can_attack { attacker, target ->
    val ar = world.instanceAllocator.getMap(attacker.tile)
    val tr = world.instanceAllocator.getMap(target.tile)
    val raidA = (attacker as? Player)?.let { ToaLobby.of(world).raid(it.uid) }
    val raidB = (target as? Player)?.let { ToaLobby.of(world).raid(it.uid) }
    if (raidA == null && raidB == null) true
    else ar === tr && !(attacker is Player && target is Player)
}

SafeDeath.register { player ->
    ToaLobby.of(player.world).raid(player.uid)?.map?.area?.contains(player.tile) == true
}

ToaPath.values().forEach { path ->
    arrayOf("Enter", "Quick-Enter").forEach { option ->
        on_obj_option(path.door, option) {
            val raid = ToaLobby.of(world).raid(player.uid)
            if (raid != null && raid.map.area.contains(player.tile)) {
                player.teleportTo(ToaMap.instance(raid.map, path.challengeEntry))
            }
        }
    }
}

on_player_death {
    ToaLobby.of(world).raid(player.uid)?.let { raid -> player.teleportTo(ToaMap.instance(raid.map, ToaMap.NEXUS)) }
}

on_obj_option(ToaLocs.EXIT, "Leave") {
    ToaLobby.of(world).leaveRaid(player)
}
