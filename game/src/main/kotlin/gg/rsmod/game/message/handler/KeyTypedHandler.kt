package gg.rsmod.game.message.handler

import gg.rsmod.game.message.MessageHandler
import gg.rsmod.game.message.impl.KeyTypedMessage
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player

/**
 * @author Alycia <https://github.com/alycii>
 */
class KeyTypedHandler : MessageHandler<KeyTypedMessage> {
    override fun handle(
        client: Client,
        world: World,
        message: KeyTypedMessage,
    ) {
        // Every batched key, in the order typed, exactly as if each had come in its own packet.
        (listOf(message.keycode) + message.following).forEach { key -> handleKey(client, KeyTypedMessage(key)) }
    }

    private fun handleKey(
        client: Client,
        message: KeyTypedMessage,
    ) {
        val keycode = message.keycode
        val player = client as Player
        // Owner A42 2026-10-11: during an "interface hang" only two KeyTyped packets arrived and nobody could tell which keys they
        // were. Every typed key is in the input trace now, with whether a window was open and what Esc does for this player.
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.INPUT) {
            "key ${player.username} code=$keycode${if (keycode == 13) " (Esc)" else ""} modal=${player.interfaces.getModal()}" +
                if (keycode == 13) " escClosesInterface=${gg.rsmod.game.model.hud.ClientHud.escClosesInterface(player)}" else ""
        }

        when (keycode) {
            13 -> { // ESC
                if (player.interfaces.getModal() != -1 && player.lock.canInterfaceInteract() && gg.rsmod.game.model.hud.ClientHud.escClosesInterface(player)) {
                    player.closeInterfaceModal()
                } else if (player.interfaces.getModal() != -1) {
                    gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.INPUT) {
                        "Esc left window ${player.interfaces.getModal()} open for ${player.username}: " +
                            if (!player.lock.canInterfaceInteract()) "locked (${player.lock})" else "the player's keybinding 'Esc closes the current interface' is off"
                    }
                }
            }
            83 -> { // SPACE
                if (!player.interfaces.optionsOpen &&
                    (player.interfaces.isOccupied(752, 12) || player.interfaces.isOccupied(752, 13))
                ) {
                    client.queues.submitReturnValue(message)
                }
            }
            16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26 -> { // Keys 1..10
                if (player.interfaces.optionsOpen || player.interfaces.isVisible(905)) {
                    client.queues.submitReturnValue(message)
                }
            }
        }
    }
}
