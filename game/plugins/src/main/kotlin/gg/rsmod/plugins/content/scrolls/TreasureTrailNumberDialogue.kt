package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/** Conditional branches from the pinned Transcript pages in NPC-DIALOGUE-SOURCES.json. */
object TreasureTrailNumberDialogue {
    data class Lines(val initial: List<String>, val prompt: List<String>, val wrong: String?, val correct: String, val handover: String? = null)
    // The Player's interjection is represented explicitly, never attributed to the NPC.
    const val PLAYER_WHAT = "<player>What?"
    private val handovers = mapOf(
        "Bolkoy" to "Bolkoy has given you a challenge scroll!",
        "Gnome Coach" to "The coach has given you a challenge scroll!",
        "Brother Omad" to "Brother Omad has given you a challenge scroll!",
        "Wise Old Man" to "The wise old man has given you a challenge scroll!",
        "Otto Godblessed" to "Otto Godblessed has given you a challenge scroll!",
        "Baraek" to "Baraek has given you a challenge scroll!",
        "Father Aereck" to "Father Aereck has given you a challenge scroll!",
        "Drezel" to "Drezel has given you a challenge scroll!",
        "Wizard Traiborn" to "Wizard Traiborn has given you a challenge scroll!",
        "Doomsayer" to "The Doomsayer has given you a challenge scroll!",
        "Martin Thwait" to "Martin has given you a challenge scroll!",
        "Weird Old Man" to "The Weird Old Man has given you a challenge scroll!",
        "old crone" to "You've been given a challenge scroll!",
    )
    val lines = mapOf(
        "Bolkoy" to Lines(listOf("Hello! I take it you're here about the trail?", "<player>Yes, do you have a challenge for me?", "Yes, right here!"), listOf("Please enter the answer to the question."), "What's the matter with you? Can't you count? Try harder!", "Lucky for you, that's the right answer!"),
        "Gnome Coach" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to answer this for me."), listOf("Please enter the answer to the question."), "How can that be? Go check again!", "Spot on!"),
        "Brother Omad" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to answer this for me."), listOf("Please enter the answer to the question."), "How can that be? Go check again!", "Spot on!"),
        "Wise Old Man" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to answer this for me."), listOf("Please enter the answer to the question."), "How can that be? Try again!", "Spot on!"),
        "Otto Godblessed" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to answer this for me."), listOf("Please tell me your answer."), "Nope, Go check again!", "Spot on!"),
        "Baraek" to Lines(listOf("I must ask you something..."), emptyList(), "Wrong! Try again.", "Correct."),
        // Baraek's source wrongly labels the repeated prompt as Dunstan. Omit that unverified line.
        "Father Aereck" to Lines(listOf("I have a question for you."), listOf("Please enter the answer to the question."), "No, try again.", "Correct!"),
        "Drezel" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to help me with this."), listOf("Please give me your answer!"), "Nope, not correct!", "Correct!"),
        "Wizard Traiborn" to Lines(listOf("Ello young thingummywut.", "Here you go!", PLAYER_WHAT, "I need you to answer this for me like a good thingummywut."),
            listOf("Ello young thingummywut.", "Please give me your answer, young thingummywut!"), "How can that be? Go check again, silly thingummywut!", "Spot on!"),
        "Doomsayer" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to answer this for me."), listOf("Please enter the answer to the question."), "How can that be? Try again!", "Spot on!"),
        "Martin Thwait" to Lines(listOf("Ah! Here you go!", PLAYER_WHAT, "I need you to answer this for me."), listOf("Please enter the answer to the question."), "How can that be? Go check again!", "Spot on!"),
        "Weird Old Man" to Lines(listOf("Ah! This is for you.", PLAYER_WHAT, "I need answers."), listOf("AHHH HELP! What's the answer?"), "They like it when you scream! AHHHHHHH. That's not right!", "Spot on!"),
        "Mandrith" to Lines(listOf("Answer this, please."), emptyList(), null, "Well done."),
        "old crone" to Lines(listOf("Answer this, please."), emptyList(), null, "Well done."),
    ).mapValues { (name, text) -> text.copy(handover = handovers[name]) }

    fun talk(player: Player, npc: Npc, step: TreasureTrailSourceStep): Boolean {
        val text = lines[step.source.npcName] ?: return false
        val before = ClueStepContract.capture(player, step) ?: return false
        val current = TreasureTrailNumberChallenge.current(player, step)
        player.queue {
            if (current == null) {
                for (line in text.initial) {
                    if (line.startsWith("<player>")) chatPlayer(line.removePrefix("<player>"), wrap = true) else chatNpc(line, npc = npc.id, wrap = true)
                    if (terminated || !before.valid(player)) return@queue
                }
                // Same-slot replacement after dialogue: cancelling/stale clues lose nothing.
                val issued = TreasureTrailNumberChallenge.begin(player, step)
                if (issued != null && issued.contract.valid(player)) text.handover?.let { messageBox(it) }
            } else {
                for (line in text.prompt) {
                    chatNpc(line, npc = npc.id, wrap = true)
                    if (terminated || !current.contract.valid(player)) return@queue
                }
                val answer = inputInt(step.source.question!!)
                if (terminated || !current.contract.valid(player)) return@queue
                if (answer !in step.source.answers) {
                    text.wrong?.let { chatNpc(it, npc = npc.id, wrap = true) }
                    return@queue
                }
                chatNpc(text.correct, npc = npc.id, wrap = true)
                // Claude 18-11: OSRS chat 'You have completed this challenge scroll.' (RuneLite ClueScrollPlugin.onChatMessage matches it).
                if (!terminated && TreasureTrailNumberChallenge.answer(player, current, answer) == TreasureTrailNumberChallenge.Answer.CORRECT)
                    player.message("You have completed this challenge scroll.")
            }
        }
        return true
    }
}
