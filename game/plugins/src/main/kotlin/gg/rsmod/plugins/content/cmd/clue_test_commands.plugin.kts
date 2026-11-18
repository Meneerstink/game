package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.scrolls.ClueAdminSteps
import gg.rsmod.plugins.content.scrolls.ClueScrollManager

on_command("cluestep", Privilege.ADMIN_POWER) {
    val args=player.getCommandArgs()
    val tier=args.getOrNull(0)?.let(ClueAdminSteps::tier)
    if(tier==null) {
        player.message("Usage: ::cluestep <easy|medium|hard|elite|78> [list|step id|unique text]")
        return@on_command
    }
    val query=args.drop(1).joinToString(" ")
    val rows=ClueScrollManager.registeredSteps(tier).sortedBy { it.persistenceId }
    val matches=if(query.isBlank() || query.equals("list",true))rows else ClueAdminSteps.matching(rows,query)
    if(query.isBlank() || query.equals("list",true) || matches.size!=1) {
        player.message("${matches.size}/${rows.size} installed ${tier.displayName} steps. Supply an exact id or unique text to select.")
        matches.forEach { player.message("${it.persistenceId}: ${ClueAdminSteps.label(it)}") }
    } else if(ClueAdminSteps.assign(player,tier,matches.single().persistenceId)) {
        player.message("Selected ${ClueAdminSteps.label(matches.single())}; Read the held clue. Normal trail length, requirements and rewards apply.")
    } else {
        player.message("Carry exactly one ordinary clue of that tier, with no pending challenge, puzzle or guardian fight.")
    }
}

// Claude 18-11 (eigenaar 'volg je adviezen'): TEST SERVER ONLY - show the gold 78 mega-rare world message without a real roll.
on_command("cluemega", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") { player.message("Only on the test server."); return@on_command }
    val id = player.getCommandArgs().getOrNull(0)?.toIntOrNull() ?: 20011
    gg.rsmod.plugins.content.scrolls.ClueCasketRewards.broadcastMegaRare(player, id)
}

// Claude 18-11 (nachtrun): TEST SERVER ONLY - give a converted Mimic casket (elite|master) at once, no 1/35 / 1/15 roll.
on_command("cluemimic", Privilege.ADMIN_POWER) {
    if (System.getProperty("rsps.testServer") != "true") { player.message("Only on the test server."); return@on_command }
    val a = gg.rsmod.plugins.content.areas.watson.MimicEncounter.assets
    player.message("gates: imported=${a.imported} keyhole=${a.keyhole} region=${a.arenaRegion} timings=${a.timingsReady} combat=${a.combatImplemented} plan=${gg.rsmod.plugins.content.areas.watson.MimicAttacks.plan != null} minions=${gg.rsmod.plugins.content.areas.watson.MimicMinions.available(player.world)} anim=${player.world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, 15405) != null}")
    val tier = if (player.getCommandArgs().getOrNull(0).equals("elite", true)) gg.rsmod.plugins.content.scrolls.ClueScrollTier.ELITE
               else gg.rsmod.plugins.content.scrolls.ClueScrollTier.MASTER
    player.attr[gg.rsmod.plugins.content.areas.watson.StrangeCasket.MIMIC_CHALLENGES] = true
    player.attr.remove(gg.rsmod.plugins.content.areas.watson.MimicCaskets.RECEIVED)
    if (!player.inventory.add(gg.rsmod.game.model.item.Item(tier.casketId)).hasSucceeded()) { player.message("Free an inventory slot."); return@on_command }
    val slot = (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == tier.casketId } ?: return@on_command
    val source = player.inventory[slot]!!
    player.message(if (gg.rsmod.plugins.content.areas.watson.MimicCaskets.convert(player, slot, source, tier)) "The chest turned out to be the Mimic!" else "Mimic conversion refused (held Mimic or encounter not ready).")
}
