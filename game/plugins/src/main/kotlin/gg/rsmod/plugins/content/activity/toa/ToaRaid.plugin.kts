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
        !(attacker is Player && target is Player) &&
        (attacker !is Player || raidA?.canFight(attacker) == true) &&
        (target !is Player || raidB?.canFight(target) == true)
}

ToaPath.values().forEach { path ->
    arrayOf("Enter", "Quick-Enter").forEach { option ->
        val unselectedSource = when (path) {
            ToaPath.SCABARAS -> 46156; ToaPath.APMEKEN -> 46159; ToaPath.CRONDIS -> 46162; ToaPath.HET -> 46165
        }
        listOfNotNull(path.door, ToaIds.loc(unselectedSource)).distinct().forEach { door ->
            on_obj_option(door, option) {
                val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_obj_option
                player.queue {
                    if (!raid.checkpoints.offered(player.uid) || ToaUi.confirmSupplies(this)) raid.enterChallenge(player, path)
                }
            }
        }
        on_obj_option(path.bossDoor, option) { ToaLobby.of(world).raid(player.uid)?.enterBoss(player, path) }
    }
}

arrayOf("Pass", "Quick-Pass").forEach { option ->
    on_obj_option(ToaLocs.BARRIER, option) {
        ToaLobby.of(world).raid(player.uid)?.passBarrier(player, player.getInteractingGameObj())
    }
}

mapOf(ToaLocs.KEPHRI_CRYSTAL to ToaPath.SCABARAS, ToaLocs.AKKHA_CRYSTAL to ToaPath.HET,
    ToaLocs.BABA_CRYSTAL to ToaPath.APMEKEN, ToaLocs.ZEBAK_CRYSTAL to ToaPath.CRONDIS).forEach { (id, path) ->
    arrayOf("Use", "Quick-Use").forEach { option ->
        // <boss>_teleport (cache2686 loc.sym): the crystal in the room's entrance takes the party over the arena's invisible wall.
        // Plain adjacent reach: a line-of-sight range failed on Akkha's solid 2x2 crystal ('I can't reach that!', 07-11 testclient).
        on_obj_option(id, option) { ToaLobby.of(world).raid(player.uid)?.crystal(player, path) }
    }
}

on_obj_option(ToaLocs.WARDENS_CRYSTAL, "Use") { ToaLobby.of(world).raid(player.uid)?.crystal(player, null) }

// Closed root has no option until Claude imports 46168. Numeric binding is ready for the corrected cache definition.
on_obj_option(ToaLocs.WARDENS, 1) { ToaLobby.of(world).raid(player.uid)?.enterWardens(player) }
on_obj_option(ToaLocs.WARDENS, 2) { ToaLobby.of(world).raid(player.uid)?.enterWardens(player) }
ToaIds.loc(46168)?.let { id ->
    arrayOf("Enter", "Quick-Enter").forEach { option ->
        on_obj_option(id, option) {
            val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_obj_option
            player.queue {
                if (!raid.checkpoints.offered(player.uid) || ToaUi.confirmSupplies(this)) raid.enterWardens(player)
            }
        }
    }
}

on_obj_option(ToaLocs.EXIT, "Leave") { ToaLobby.of(world).leaveRaid(player) }
on_obj_option(ToaLocs.WARDENS_EXIT, "Leave") { ToaLobby.of(world).leaveRaid(player) }
listOf(ToaLocs.KEPHRI_EXIT, ToaLocs.AKKHA_EXIT, ToaLocs.BABA_EXIT, ToaLocs.ZEBAK_EXIT).forEach { id ->
    on_obj_option(id, "Leave") { ToaLobby.of(world).leaveRaid(player) }
}

listOfNotNull(ToaIds.loc(44786), ToaIds.loc(44787)).distinct().forEach { id ->
    on_obj_option(id, 1) { ToaLobby.of(world).raid(player.uid)?.openChest(player, player.getInteractingGameObj()) }
}

// cache2686 2/9/11689: Osmumten after a demi-boss, ops Talk-to / Proceed (ToaScene.BOSS_OSMUMTEN).
ToaNpcs.TOA_OSMUMTEN_VIS.localId?.let { id ->
    arrayOf("Talk-to", "Proceed").forEach { option ->
        on_npc_option(id, option) { ToaLobby.of(world).raid(player.uid)?.proceed(player) }
    }
}

ToaNpcs.TOA_OSMUMTEN_VAULT_VIS_2.localId?.let { id ->
    arrayOf("Talk-to", "Leave").forEach { option ->
        on_npc_option(id, option) {
            val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_npc_option
            if (raid.progress.finished && raid.contains(player)) player.queue {
                // Transcript:Osmumten, leaving the reward vault.
                if (options("Yes.", "No.", title = "Are you ready to leave the Tombs of Amascut?") == 1)
                    ToaLobby.of(world).leaveRaid(player)
            }
        }
    }
}

ToaNpcs.TOA_MIDRAIDLOOT_TRADER.localId?.let { id ->
    on_npc_option(id, "Claim") {
        ToaLobby.of(world).raid(player.uid)?.let { raid ->
            if (raid.contains(player) && raid.checkpoints.offered(player.uid)) ToaUi.showSupplies(player, raid)
        }
    }
}

ToaIds.loc(49583)?.let { id ->
    on_obj_option(id, "Deposit") {
        val raid = ToaLobby.of(world).raid(player.uid)
        if (raid?.contains(player) == true && raid.progress.completedPaths.size in listOf(2, 4))
            gg.rsmod.plugins.content.inter.bank.Bank.openDepositBox(player)
    }
}

ToaIds.item(ToaSupplies.BAG_SOURCE)?.let { id ->
    on_item_option(id, "Open") {
        ToaLobby.of(world).raid(player.uid)?.let { ToaUi.showBag(player, it, player.getInteractingItemSlot()) }
    }
    on_item_option(id, "Withdraw 1") {
        ToaLobby.of(world).raid(player.uid)?.supplies?.withdraw(player, player.getInteractingItemSlot())
    }
    on_item_option(id, "Withdraw All") {
        ToaLobby.of(world).raid(player.uid)?.supplies?.withdraw(player, player.getInteractingItemSlot(), all = true)
    }
    on_item_option(id, "Resupply") {
        ToaLobby.of(world).raid(player.uid)?.supplies?.resupply(player, player.getInteractingItemSlot())
    }
    on_item_option(id, "Destroy") {
        val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_item_option
        val slot = player.getInteractingItemSlot()
        val item = player.inventory[slot] ?: return@on_item_option
        player.queue {
            if (confirmWarning("You will lose any items inside.") && player.inventory[slot] === item) raid.supplies.destroy(player, slot)
        }
    }
}

// N462 points + chest (ToaChest): room points at each clear (ToaRaid), the Wardens' death rolls the chest once.
ToaChest.install()
run {
    val previous = ToaRaidEvents.completed
    ToaRaidEvents.completed = { raid, record -> ToaChest.fill(raid); previous(raid, record) }
}

on_player_death_start { ToaLobby.of(world).raid(player.uid)?.deathStart(player) }

on_player_death {
    ToaLobby.of(world).raid(player.uid)?.respawn(player)
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
