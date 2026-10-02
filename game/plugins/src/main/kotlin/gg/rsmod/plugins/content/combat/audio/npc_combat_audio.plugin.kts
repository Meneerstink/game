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

val deathScripts = File("./data/cfg/npcs/death-sound-scripts.json")
if (deathScripts.exists()) NpcCombatAudio.loadDeathByScript(deathScripts)

on_world_init {
    val families = NpcCombatAudio.resolveFamilies(world)
    logger.info("Npc combat audio: {} more npc ids share the row of the same creature (same name and combat animations).", families)
    val pouches = gg.rsmod.plugins.content.skills.summoning.SummoningPouchData.values
    val familiarIds = pouches.map { it.npc }
    val ownAttack = pouches.filter { gg.rsmod.plugins.content.skills.summoning.FamiliarCombat.sendsOwnAttackSound(it) }.map { it.npc }.toSet()
    val adapted = NpcCombatAudio.resolveFamiliars(world, familiarIds) { it in ownAttack }
    logger.info("Npc combat audio: {} familiars borrow missing combat sounds from their creature (ADAPTED): {}", adapted, NpcCombatAudio.familiarDonors.entries.sortedBy { it.key }.joinToString { "${it.key}<-${it.value}" })
    familiarIds.distinct().filter { id -> NpcCombatAudio.rowFor(id).let { it == null || (it.attack.isEmpty() && id !in ownAttack) || it.defend < 0 } }.forEach { id ->
        gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.SUMMON) { "familiar npc $id has no attack or block sound (no row and no creature to borrow from)" }
    }
}

on_npc_killed { killer, npc ->
    NpcCombatAudio.onDeath(killer, npc)
}
