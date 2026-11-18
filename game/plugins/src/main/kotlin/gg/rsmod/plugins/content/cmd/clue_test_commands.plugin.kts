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
