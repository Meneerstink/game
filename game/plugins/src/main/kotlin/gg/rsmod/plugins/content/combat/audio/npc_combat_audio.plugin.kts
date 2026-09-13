package gg.rsmod.plugins.content.combat.audio

import gg.rsmod.game.Server.Companion.logger
import java.io.File

/**
 * Loads the shared NPC combat audio table (see [NpcCombatAudio]) and binds the death sound for every
 * npc. Attack and defend sounds are dispatched from the shared hit pipeline (combat PawnExt.dealHit).
 */
val file = File(NpcCombatAudio.DEFAULT_PATH)
if (file.exists()) {
    val count = NpcCombatAudio.load(file)
    logger.info("Npc combat audio: loaded {} npc rows from {}.", count, file.path)
} else {
    logger.warn("Npc combat audio: {} is missing - npcs will have no combat sounds.", file.path)
}

on_npc_killed { killer, npc ->
    NpcCombatAudio.onDeath(killer, npc)
}
