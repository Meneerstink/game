package gg.rsmod.plugins.content.inter.emotes

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.messageBox
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.content.scrolls.ClueScrollManager

/**
 * @author Tom <rspsmods@gmail.com>
 */
object EmotesTab {
    const val COMPONENT_ID = 464

    const val GOBLIN_EMOTES_VARBIT = Varbits.THE_LOST_TRIBE_PROGRESS
    const val GLASS_BOX_EMOTE_VARBIT = Varbits.GLASS_BOX_EMOTE
    const val CLIMB_ROPE_EMOTE_VARBIT = Varbits.CLIMB_ROPE_EMOTE
    const val LEAN_EMOTE_VARBIT = Varbits.LEAN_EMOTE
    const val GLASS_WALL_EMOTE_VARBIT = Varbits.GLASS_WALL_EMOTE
    const val IDEA_EMOTE_VARBIT = Varbits.IDEA_EMOTE
    const val STAMP_EMOTE_VARBIT = Varbits.STAMP_EMOTE
    const val FLAP_EMOTE_VARBIT = Varbits.FLAP_EMOTE
    const val SLAP_HEAD_EMOTE_VARBIT = Varbits.SLAP_HEAD_EMOTE
    const val ZOMBIE_WALK_EMOTE_VARBIT = Varbits.ZOMBIE_WALK_EMOTE
    const val ZOMBIE_DANCE_EMOTE_VARBIT = Varbits.ZOMBIE_DANCE_EMOTE
    const val SCARED_EMOTE_VARBIT = Varbits.SCARED_EMOTE
    const val RABBIT_HOP_EMOTE_VARBIT = Varbits.RABBIT_HOP_EMOTE
    const val ZOMBIE_HAND_EMOTE_VARBIT = Varbits.ZOMBIE_HAND_EMOTE
    const val SKILLCAPE_EMOTE_VARBIT = Varbits.SKILLCAPE_EMOTE
    const val SNOWMAN_EMOTE_VARBIT = Varbits.SNOWMAN_EMOTE
    const val AIR_GUITAR_EMOTE_VARBIT = Varbits.AIR_GUITAR_EMOTE
    const val SAFETY_FIRST_EMOTE_VARBIT = Varbits.SAFETY_FIRST_EMOTE
    const val EXPLORE_EMOTE_VARBIT = Varbits.EXPLORE_EMOTE
    const val TRICK_EMOTE_VARBIT = Varbits.TRICK_EMOTE
    const val FREEZE_EMOTE_VARBIT = Varbits.FREEZE_EMOTE
    const val GIVE_THANKS_EMOTE_VARBIT = Varbits.GIVE_THANKS_EMOTE
    const val AROUND_THE_WORLD_EMOTE_VARBIT = Varbits.AROUND_THE_WORLD_EMOTE // requires 85 value
    const val DRAMATIC_POINT_EMOTE_VARBIT = Varbits.DRAMATIC_POINT_EMOTE
    const val FAINT_EMOTE_VARBIT = Varbits.FAINT_EMOTE
    const val PUPPET_MASTER_EMOTE_VARBIT = Varbits.PUPPET_MASTER_EMOTE // requires 20 value

    // TODO: assume 8601 is task completed amount varbit
    const val TASK_MASTER_EMOTE_VARBIT = Varbits.TASK_MASTER_EMOTE // requires 428 value
    const val SEAL_OF_APPROVAL_EMOTE_VARBIT = Varbits.SEAL_OF_APPROVAL_EMOTE
    const val INVOKE_SPRING_EMOTE_VARBIT = Varbits.INVOKE_SPRING_EMOTE // requires 60 value

    /**
     * EMO + N99 (owner 10-17, VRAGEN 25 = A: "alles open voor iedereen"): every emote is unlocked at login - each emote varbit gets
     * the value the tab script reads as unlocked (Novite EmotesManager.refreshListConfigs unlocks 0..45 for everyone; goblin emotes
     * = Lost Tribe progress 7, Novite / Darkan / Void). The Skill Cape emote stays tied to a worn cape (SkillcapeEmotes).
     */
    fun unlockAll(p: Player) {
        Emote.values.filter { it.varbit != -1 && it != Emote.SKILLCAPE }.forEach { emote ->
            if (p.getVarbit(emote.varbit) < emote.requiredVarbitValue) p.setVarbit(emote.varbit, emote.requiredVarbitValue)
        }
    }

    /** One step of a transformation emote: the animation, an optional spot animation, the npc to look like (-1: yourself again). */
    class Step(val wait: Int, val anim: Int, val gfx: Int = -1, val transmog: Int? = null)

    /**
     * N99: the two emotes that turn the player into an npc for a few seconds (Novite EmotesManager.java:608-673, rev 667):
     * Give Thanks - turkey 8499; Seal of Approval - one of the three seals 13255-13257. The player is locked meanwhile (Novite
     * player.lock(15)), and always gets their own look back.
     */
    fun transformSteps(emote: Emote, seal: Int = 13255): List<Step> =
        when (emote) {
            Emote.GIVE_THANKS -> listOf(Step(0, 10994, 86), Step(1, 10996, transmog = 8499), Step(5, 10995, 86, transmog = -1))
            Emote.SEAL_OF_APPROVAL -> listOf(Step(0, 15104, 1287), Step(1, 15106, transmog = seal), Step(1, 15108), Step(1, 15105, 1287, transmog = -1))
            else -> emptyList()
        }

    private fun transformEmote(
        p: Player,
        emote: Emote,
    ) {
        val steps = transformSteps(emote, seal = 13255 + p.world.random(2))
        p.lockingQueue {
            try {
                for (step in steps) {
                    if (step.wait > 0) wait(step.wait)
                    p.animate(step.anim)
                    if (step.gfx != -1) p.graphic(step.gfx)
                    step.transmog?.let { p.setTransmogId(it) }
                }
            } finally {
                if (p.getTransmogId() != -1) p.setTransmogId(-1)
            }
        }
    }

    fun performEmote(
        p: Player,
        emote: Emote,
    ) {
        if (emote == Emote.SKILLCAPE) {
            if (SkillcapeEmotes.perform(p)) ClueScrollManager.tryEmote(p, emote.component)
            return
        }
        // At least the unlock value: the goblin emotes share the Lost Tribe progress varbit (Void Emotes.kt: questStage >= 7).
        if (emote.varbit != -1 && p.getVarbit(emote.varbit) < emote.requiredVarbitValue) {
            val description = emote.unlockDescription ?: "You have not unlocked this emote yet."
            p.queue { messageBox(description) }
            return
        }

        if (emote == Emote.GIVE_THANKS || emote == Emote.SEAL_OF_APPROVAL) {
            transformEmote(p, emote)
            ClueScrollManager.tryEmote(p, emote.component)
            return
        }

        if (emote.anim != -1) {
            p.animate(emote.anim)
        }

        if (emote.gfx != -1) {
            p.graphic(emote.gfx)
        }

        ClueScrollManager.tryEmote(p, emote.component)
    }
}
