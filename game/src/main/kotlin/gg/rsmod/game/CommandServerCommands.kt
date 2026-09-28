package gg.rsmod.game

import gg.rsmod.game.model.World
import java.util.concurrent.ConcurrentHashMap

/**
 * Commands that plugins add to the local command port (50017, token-protected - CommandServerAuth). The first word of the
 * line selects the handler; it runs on the game thread and returns the reply line. Used by the conformance suite
 * (`conform <domain>` from tools\conformance\Send-GameCommand.ps1) so an agent can start a run without an in-game admin.
 */
object CommandServerCommands {
    val handlers = ConcurrentHashMap<String, (World, List<String>) -> String>()
}
