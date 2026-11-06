package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.activity.toa.bosses.*

on_command("toaboss", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val name = args.getOrNull(0)?.lowercase()?.replace("-", "")
    if (name == "stop") {
        player.attr[ToaEncounter.SESSION]?.let { it.close(); if (it.npc.isSpawned()) world.remove(it.npc) }
        player.attr.remove(ToaEncounter.SESSION)
        return@on_command
    }
    val raid = args.getOrNull(1)?.toIntOrNull() ?: 0
    val size = args.getOrNull(2)?.toIntOrNull() ?: 1
    val definition = ToaEncounter.definitions[name]
    if (definition == null || raid !in 0..600 || size !in 1..8) {
        player.message("Usage: ::toaboss ${ToaEncounter.definitions.keys.joinToString("|")} [raidlevel 0-600] [partysize 1-8], or ::toaboss stop")
        return@on_command
    }
    val id = ToaBossNpcs.local(definition.osrsNpc)
    if (id == null) { player.message("ToA NPC import mapping is not available yet (N462 T02)."); return@on_command }
    player.attr[ToaEncounter.SESSION]?.let { it.close(); if (it.npc.isSpawned()) world.remove(it.npc) }
    val tile = player.tile.transform(0, 4)
    val c = StandaloneToaContext(player, Tile(player.tile), raid, size)
    val boss = Npc(id, tile, world)
    boss.respawnOverride = false
    boss.attr[ToaEncounter.CONTEXT] = c
    world.spawn(boss)
    val session = ToaEncounter.attach(boss, c, definition.factory)
    player.attr[ToaEncounter.SESSION] = session
    player.message("ToA test encounter: $name RL=$raid party=$size.")
}
