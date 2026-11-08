package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.messageBox

/** Literal Transcript:Osmumten?oldid=15116795 and Transcript:Helpful_Spirit?oldid=14889419. */
object ToaDialogues {
    val MET_OSMUMTEN = AttributeKey<Boolean>(persistenceKey = "toa_met_osmumten")
    const val OFFER = "The spirit looks at you without saying a word. However, as you approach, he offers you some items."
    const val CLAIMED = "The spirit gives you a strange look. You've clearly claimed all you can for now."
    const val DEMI = "<col=000080>We are done here, but the Devourer's thralls are everywhere. They must be removed.</col>"
    private val AFTER = listOf(
        "You have done well, but it is time for you to leave. The Devourer's thralls will soon return.",
        "You fought well. The Devourer's influence here has lessened, but it will soon be restored. It is time for you to leave.",
        "The Devourer's thralls will be back, but they have been weakened because of you. Now, leave this place.",
    )

    private fun speech(text: String): String = "<col=000080>$text</col>"

    suspend fun vault(task: QueueTask, player: Player, npc: Int) {
        if (player.attr[MET_OSMUMTEN] != true) {
            task.messageBox("As you approach the spirit, you hear a voice echoing in your head.")
            task.chatNpc(speech("You. You fought against the Devourer. Her influence here has been lessened as a result. You are to be commended for your strength."), npc = npc, title = "Spirit")
            task.chatPlayer("I take it you're Osmumten?")
            task.chatNpc(speech("Once, but who I was in life means nothing. Vengeance is all that matters now. The Devourer may be gone, but my home has been corrupted. This corruption must be destroyed."), npc = npc)
            task.chatPlayer("Can I help?")
            task.chatNpc(speech("You can. Take your prize and leave, but when you are ready, return here. The Devourer's thralls will soon be back. You must fight them."), npc = npc)
            task.chatPlayer("I'll do what I can.")
            task.chatNpc(speech("We shall see if it is enough."), npc = npc)
            player.attr[MET_OSMUMTEN] = true
        } else {
            val text = if (ToaRewards.hasUnclaimed(player))
                "The Devourer's thralls will be back, but they have been weakened because of you. Your prize awaits."
                else AFTER[player.world.random(AFTER.lastIndex)] // Transcript: trandom; relative probabilities unpublished.
            task.chatNpc(speech(text), npc = npc)
        }
    }
}
