package gg.rsmod.plugins.content.areas.alkharid

/**
 * RCV-012 B12: binds the cache "Heal" option of every Duel Arena healer and the nurses' Talk-to (Void Nurses.kt).
 * Tafani's Talk-to lives in surgeon_general_tafani.plugin.kts and uses the same [DuelArenaNurses.heal].
 */
DuelArenaNurses.HEALERS.forEach { healer ->
    on_npc_option(npc = healer, option = "heal") {
        val npc = player.getInteractingNpc()
        player.queue { DuelArenaNurses.heal(this, npc) }
    }
}

listOf(Npcs.SABREEN, DuelArenaNurses.A_ABLA).forEach { nurse ->
    on_npc_option(npc = nurse, option = "talk-to") {
        val npc = player.getInteractingNpc()
        player.queue {
            chatPlayer("Hi!")
            chatNpc("Hi. How can I help?")
            when (options("Can you heal me?", "Do you see a lot of injured fighters?", "Do you come here often?")) {
                FIRST_OPTION -> {
                    chatPlayer("Can you heal me?")
                    DuelArenaNurses.heal(this, npc)
                }
                SECOND_OPTION -> {
                    chatPlayer("Do you see a lot of injured fighters?")
                    chatNpc(
                        "Yes I do. Thankfully we can cope with almost anything.",
                        "Jaraah really is a wonderful surgeon, his methods are",
                        "a little unorthodox but he gets the job done.",
                    )
                    chatNpc("I shouldn't tell you this but his nickname is", "'The Butcher'.")
                    chatPlayer("That's reassuring.")
                }
                THIRD_OPTION -> {
                    chatPlayer("Do you come here often?")
                    chatNpc("I work here, so yes!")
                    chatNpc("You're silly!")
                }
            }
        }
    }
}
