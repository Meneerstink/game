package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.service.recovery.AccountRecovery

/**
 * OSRS-style account recovery, in-game half (owner 2026-09-19): link and confirm a recovery e-mail. The reset itself happens on
 * the recovery web page (AccountRecoveryService). Commands without "::": setemail <address>, confirmemail <code>, recovery.
 */

on_command("setemail") {
    val recovery = AccountRecovery.instance
    val address = player.getCommandArgs().joinToString(" ").trim()
    when {
        recovery == null || !recovery.enabled -> player.message(AccountRecovery.NOT_CONFIGURED)
        address.isEmpty() -> player.message("Type: setemail <your e-mail address>")
        // Sending the mail blocks on the network: do it off the game thread.
        else -> java.util.concurrent.CompletableFuture.supplyAsync { recovery.requestEmailLink(player, address) }
            .thenAccept { msg -> world.queue { player.message(msg) } }
    }
}

on_command("confirmemail") {
    val recovery = AccountRecovery.instance
    val code = player.getCommandArgs().joinToString("").trim()
    if (recovery == null || !recovery.enabled) {
        player.message(AccountRecovery.NOT_CONFIGURED)
        return@on_command
    }
    player.message(recovery.confirmEmailLink(player, code))
}

on_command("recovery") {
    val recovery = AccountRecovery.instance
    val email = player.attr[AccountRecovery.RECOVERY_EMAIL]
    when {
        recovery == null || !recovery.enabled -> player.message(AccountRecovery.NOT_CONFIGURED)
        email == null -> player.message("You have no recovery e-mail. Type: setemail <address>")
        else -> player.message("Recovery e-mail: ${AccountRecovery.mask(email)}. Lost password? ${recovery.publicUrl}")
    }
}

on_login {
    val recovery = AccountRecovery.instance ?: return@on_login
    if (recovery.enabled && player.attr[AccountRecovery.RECOVERY_EMAIL] == null) {
        player.message("Protect your account: type setemail <address> so you can reset a lost password.")
    }
}
