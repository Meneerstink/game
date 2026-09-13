package gg.rsmod.plugins.content.combat

import gg.rsmod.game.Server.Companion.logger
import java.io.File

/**
 * Loads the per-npc spawn leash (see [NpcLeash]). Without the table every npc falls back to Void's
 * default max range of 7.
 */
val file = File(NpcLeash.DEFAULT_PATH)
if (file.exists()) {
    val count = NpcLeash.load(file)
    logger.info("Npc leash: loaded {} npc max-range rows.", count)
} else {
    logger.warn("Npc leash: {} is missing - every npc uses the default max range of {}.", file.path, NpcLeash.DEFAULT_MAX_RANGE)
}
