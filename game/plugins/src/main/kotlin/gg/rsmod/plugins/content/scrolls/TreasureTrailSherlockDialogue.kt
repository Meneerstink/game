package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/** Pinned Transcript:Sherlock; the challenge is performed, then returned to Sherlock. */
object TreasureTrailSherlockDialogue {
    fun talk(player: Player, npc: Npc) {
        val assignment = TreasureTrailSherlock.forDialogue(player)
        player.queue {
            if (assignment == null) {
                chatPlayer("Hey, who're you?", wrap = true)
                chatNpc("I'm Sherlock, master of clues and challenges.", npc = npc.id, wrap = true)
                chatPlayer("Clues and challenges?", wrap = true)
                chatNpc("If you find a scroll that requires my attention, bring it to me and I shall handle it.", npc = npc.id, wrap = true)
                chatPlayer("I will! but...", wrap = true)
                chatPlayer("You're not wearing a shirt?", wrap = true)
                chatNpc("Yeah, no shirt Sherlock.....", npc = npc.id, wrap = true)
                return@queue
            }
            if (!assignment.contract.valid(player)) return@queue
            if (assignment.done) {
                chatNpc("Fantastic! Here, have this.", npc = npc.id, wrap = true)
                if (!terminated) TreasureTrailSherlock.finish(player, assignment)
            } else {
                chatNpc("Ah, just what I was looking for. I have a challenge for you. If you complete it, I'll give you something.", npc = npc.id, wrap = true)
                if (!terminated && assignment.contract.valid(player)) ClueInterfaces.text(player, assignment.task.text)
            }
        }
    }
}
