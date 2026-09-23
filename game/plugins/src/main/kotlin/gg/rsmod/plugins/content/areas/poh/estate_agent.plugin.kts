package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression

/**
 * The Estate agent (owner 2026-09-21: "the estate agent have no chat dialoge just 1 tekst also the animation
 * incorrect ... the chatdialoges need to be good for every talkablke and tradeable npcs").
 *
 * Before this he had no dialogue at all: he fell through to `npcs/talk_to_fallback.plugin.kts`, which binds one
 * random greeting line to every npc in the cache that advertises Talk-to with nothing bound, drawn with the default
 * [FacialExpression.HAPPY_TALKING] head animation. That is what "just 1 tekst" and the wrong animation were.
 *
 * What he does here is what he does in RuneScape: he is the man who redecorates your house. Every player already owns
 * a fully furnished whitewashed-stone house ([PlayerHouse.DEFAULT_STYLE]); the agent is how a player changes it to
 * one of the other five styles, paying that style's price and meeting its Construction level
 * ([PlayerHouse.Style.level]/[PlayerHouse.Style.cost], 2009scape `HousingStyle`).
 *
 * The fallback only binds npcs nobody has written dialogue for - it runs in `on_world_init`, after every script body
 * has registered - so binding Talk-to here is what takes the agent off it.
 */

private val ESTATE_AGENTS = listOf(Npcs.ESTATE_AGENT, Npcs.ESTATE_AGENT_6715)

ESTATE_AGENTS.forEach { npcId ->
    if (if_npc_has_option(npc = npcId, option = "talk-to")) {
        on_npc_option(npc = npcId, option = "talk-to") {
            player.queue { estateAgentChat(this) }
        }
    }
}

suspend fun estateAgentChat(task: QueueTask) {
    val player = task.player
    val style = PlayerHouse.styleOf(player)
    task.chatNpc("Good day. Are you looking to make some changes to your house?", facialExpression = FacialExpression.HAPPY)
    when (
        task.options(
            "I'd like to redecorate.",
            "What style is my house right now?",
            "How do I get to my house?",
            "Nothing, thanks.",
        )
    ) {
        1 -> redecorate(task)
        2 -> {
            task.chatPlayer("What style is my house right now?", facialExpression = FacialExpression.NORMAL)
            task.chatNpc("Your house is ${style.displayName.lowercase()}. A fine choice, if I say so myself.", facialExpression = FacialExpression.HAPPY)
            estateAgentChat(task)
        }
        3 -> {
            task.chatPlayer("How do I get to my house?", facialExpression = FacialExpression.NORMAL)
            task.chatNpc(
                "Break a house teleport tablet, cast the house teleport spell, or walk through the portal in Rimmington.",
                "Your house is yours from the day you arrive - you needn't build a thing.",
                facialExpression = FacialExpression.NORMAL,
            )
            estateAgentChat(task)
        }
    }
}

suspend fun redecorate(task: QueueTask) {
    val player = task.player
    val current = PlayerHouse.styleOf(player)
    task.chatPlayer("I'd like to redecorate.", facialExpression = FacialExpression.NORMAL)
    task.chatNpc("Of course. Which style would you like?", facialExpression = FacialExpression.HAPPY)

    val styles = PlayerHouse.Style.values().filter { it != current }
    var page = 0
    while (true) {
        val slice = styles.drop(page * 4).take(4)
        if (slice.isEmpty()) {
            return
        }
        val more = styles.size > (page + 1) * 4
        val labels = slice.map { "${it.displayName} (${it.cost} coins)" } + (if (more) "More..." else "Cancel")
        val pick = task.options(*labels.toTypedArray(), title = "Redecorate my house")
        if (pick < 1) {
            return
        }
        if (pick <= slice.size) {
            applyStyle(task, slice[pick - 1])
            return
        }
        if (!more) {
            return
        }
        page++
    }
}

suspend fun applyStyle(
    task: QueueTask,
    style: PlayerHouse.Style,
) {
    val player = task.player
    if (player.skills.getMaxLevel(Skills.CONSTRUCTION) < style.level) {
        task.chatNpc(
            "I'm afraid ${style.displayName.lowercase()} needs ${style.level} Construction. Come back when you've learned a little more.",
            facialExpression = FacialExpression.SAD,
        )
        return
    }
    if (player.inventory.getItemCount(Items.COINS_995) < style.cost) {
        task.chatNpc("That will cost ${style.cost} coins, and you don't have them on you.", facialExpression = FacialExpression.SAD)
        return
    }
    player.inventory.remove(Items.COINS_995, style.cost)
    PlayerHouse.setStyle(player, style)
    task.chatNpc(
        "Consider it done. Your house is now ${style.displayName.lowercase()}.",
        "It will be waiting for you the next time you go home.",
        facialExpression = FacialExpression.HAPPY,
    )
}
