package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.ClientCheatMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.log.LoggerService
import java.util.*

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ClientCheatHandler : MessageHandler<ClientCheatMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: ClientCheatMessage,
    ) {
        val values = message.command.trim().trimStart(':').split(" ")
        val command = values[0].lowercase()
        val args =
            if (values.size >
                1
            ) {
                values.slice(1 until values.size).filter { it.isNotEmpty() }.toTypedArray()
            } else {
                null
            }

        // Audit S-04: arguments of password commands are never written to the packet or command logs.
        val loggedArgs = if (command in SENSITIVE_COMMANDS) arrayOf("<redacted>") else args ?: emptyArray()
        log(client, "Command: cmd=%s, args=%s", command, Arrays.toString(loggedArgs))

        val handled = world.plugins.executeCommand(client, command, args)
        if (handled) {
            world.getService(LoggerService::class.java, searchSubclasses = true)?.logCommand(
                client,
                command,
                *loggedArgs,
            )
        } else {
            client.writeMessage("No valid command found: $command")
        }
    }

    companion object {
        /** Commands whose arguments are secrets (passwords, codes). */
        val SENSITIVE_COMMANDS = setOf("changepass", "confirmemail")
    }
}
