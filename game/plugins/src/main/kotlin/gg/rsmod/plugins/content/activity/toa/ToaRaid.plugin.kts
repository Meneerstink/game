package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.mechanics.death.SafeDeath

SafeDeath.register { player -> ToaLobby.of(player.world).raid(player.uid)?.contains(player) == true }

can_attack { attacker, target ->
    val raidA = (attacker as? Player)?.let { ToaLobby.of(world).raid(it.uid) }
    val raidB = (target as? Player)?.let { ToaLobby.of(world).raid(it.uid) }
    if (raidA == null && raidB == null) true
    else world.instanceAllocator.getMap(attacker.tile) === world.instanceAllocator.getMap(target.tile) &&
        !(attacker is Player && target is Player)
}

ToaPath.values().forEach { path ->
    arrayOf("Enter", "Quick-Enter").forEach { option ->
        on_obj_option(path.door, option) { ToaLobby.of(world).raid(player.uid)?.enterChallenge(player, path) }
        on_obj_option(path.bossDoor, option) { ToaLobby.of(world).raid(player.uid)?.enterBoss(player, path) }
    }
}

mapOf(ToaLocs.KEPHRI_CRYSTAL to ToaPath.SCABARAS, ToaLocs.AKKHA_CRYSTAL to ToaPath.HET,
    ToaLocs.BABA_CRYSTAL to ToaPath.APMEKEN, ToaLocs.ZEBAK_CRYSTAL to ToaPath.CRONDIS).forEach { (id, path) ->
    arrayOf("Use", "Quick-Use").forEach { option ->
        on_obj_option(id, option) { ToaLobby.of(world).raid(player.uid)?.crystal(player, path) }
    }
}

on_obj_option(ToaLocs.WARDENS_CRYSTAL, "Use") { ToaLobby.of(world).raid(player.uid)?.crystal(player, null) }

// Closed root has no option until Claude imports 46168. Numeric binding is ready for the corrected cache definition.
on_obj_option(ToaLocs.WARDENS, 1) { ToaLobby.of(world).raid(player.uid)?.enterWardens(player) }
on_obj_option(ToaLocs.WARDENS, 2) { ToaLobby.of(world).raid(player.uid)?.enterWardens(player) }

on_obj_option(ToaLocs.EXIT, "Leave") { ToaLobby.of(world).leaveRaid(player) }

on_player_death {
    ToaLobby.of(world).raid(player.uid)?.let { raid -> player.teleportTo(ToaMap.instance(raid.map, ToaMap.NEXUS)) }
}

// Test-only lifecycle driver while puzzle/boss implementations and the open Wardens cache variant are pending.
on_command("toatest", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") return@on_command
    val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_command
    val result = when (player.getCommandArgs().firstOrNull()?.lowercase()) {
        "clear" -> raid.progress.current?.clear(world.currentCycle)
        "wipe" -> raid.progress.current?.wipe(world.currentCycle)
        "wardens" -> raid.enterWardens(player)
        "reward" -> raid.crystal(player, null)
        else -> false
    }
    player.message("ToA test: $result; clears=${raid.progress.completedPaths.size}, attempts=${raid.progress.failedAttempts}, ticks=${raid.progress.elapsedCycles(world.currentCycle)}")
}

val TOA_CYCLE = TimerKey()
on_world_init { world.timers[TOA_CYCLE] = 1 }
on_timer(TOA_CYCLE) {
    ToaLobby.of(world).cycle()
    world.timers[TOA_CYCLE] = 1
}
