package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.skill.ExperienceLocks
import gg.rsmod.plugins.api.cfg.FIRST_OPTION
import gg.rsmod.plugins.api.cfg.SECOND_OPTION
import gg.rsmod.plugins.api.ext.*

/** XP branch only. Claude's Nigel Talk-to menu calls adjust(task, importedNpcId). No guessed NPC id. */
object NigelExperience {
    /** OSRS Transcript:Nigel rev 15394041, 2026-11-04; changes only after Yes. */
    suspend fun adjust(task: QueueTask, npc: Int) = with(task) {
        val combat = when (options(
            "Combat experience.", "Hitpoints experience.",
            title = "Would you like to adjust earning combat experience or hitpoints experience?",
        )) {
            FIRST_OPTION -> true
            SECOND_OPTION -> false
            else -> return@with
        }
        val disabled = if (combat) ExperienceLocks.combatDisabled(player) else ExperienceLocks.hitpointsDisabled(player)
        val name = if (combat) "combat" else "hitpoints"
        chatNpc(
            if (disabled) "I've already stopped you from gaining $name experience, do you want to gain it again?"
            else "Yes, I can help with that.",
            npc = npc,
        )
        if (options("Yes.", "No.", title = "${if (disabled) "Enable" else "Disable"} $name experience gains?") != FIRST_OPTION) return@with
        if (combat) ExperienceLocks.setCombatDisabled(player, !disabled)
        else ExperienceLocks.setHitpointsDisabled(player, !disabled)
        player.persistNow()
        chatNpc(
            if (disabled) "You will now gain $name experience again. Talk to me again if you change your mind."
            else "You will no longer gain $name experience. Talk to me again if you change your mind.",
            npc = npc,
        )
    }
}
