package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.priv.Privilege

/**
 * N462 test-server-only driver for the night run (07-11): '::toastart [entry|normal|expert]' puts the player in the lobby (BCS quest
 * marked done through its varp by the caller) and starts a solo raid with that mode; '::toaroom <het|crondis|apmeken|scabaras> [boss]'
 * enters a room of the player's raid directly. Disabled outside the test server (system property rsps.testServer).
 */
on_command("toastart", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") return@on_command
    val lobby = ToaLobby.of(world)
    if (!ToaMap.inLobby(player.tile)) { player.moveTo(ToaMap.LOBBY); }
    val mode = player.getCommandArgs().firstOrNull()?.lowercase()
    val party = lobby.party(player.uid) ?: lobby.create(player.uid)
    gg.rsmod.plugins.content.activity.toa.core.ToaMode.values().firstOrNull { it.label.lowercase() == mode }?.let {
        party?.settings = ToaRaidSettings(raidLevel = it.raidLevel)
    }
    player.queue {
        wait(2)
        val raid = lobby.start(player)
        player.message("ToA test start: ${raid != null} canEnter=${ToaLobby.canEnter(player)} level=${party?.settings?.raidLevel}")
    }
}

/** '::toahp <n>' - set every living npc of the current ToA room to n hitpoints (test server; phase/death tests without a long fight). */
on_command("toahp", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") return@on_command
    val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_command
    val hp = player.getCommandArgs().firstOrNull()?.toIntOrNull() ?: return@on_command
    val run = raid.progress.current ?: return@on_command
    val npcs = raid.scope(run)?.spawned?.filter { it.isSpawned() && !it.isDead() }.orEmpty()
    npcs.forEach { it.setCurrentLifepoints(hp.coerceAtMost(it.getMaximumLifepoints())) }
    player.message("ToA test hp $hp on ${npcs.size} npc(s): ${npcs.joinToString { "${it.id}" }}")
}

on_command("toaroom", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") return@on_command
    val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_command
    val args = player.getCommandArgs()
    val path = ToaPath.values().firstOrNull { it.name.lowercase() == args.firstOrNull()?.lowercase() } ?: return@on_command
    val ok = if (args.getOrNull(1) == "boss") raid.enterBoss(player, path) else raid.enterChallenge(player, path)
    player.message("ToA test room $path boss=${args.getOrNull(1) == "boss"}: $ok")
}
