package gg.rsmod.plugins.content.combat.attack

import gg.rsmod.game.Server.Companion.logger
import java.io.File

/**
 * Loads the shared data-driven npc attack model (see [NpcAttacks]).
 */
val file = File(NpcAttacks.DEFAULT_PATH)
if (file.exists()) {
    val count = NpcAttacks.load(file)
    logger.info("Npc attacks: loaded {} npc attack rows from {}.", count, file.path)
} else {
    logger.warn("Npc attacks: {} is missing - npcs keep their generic strategy attacks.", file.path)
}
