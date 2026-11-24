package gg.rsmod.plugins.content.items

// OSRS Wiki Demonic sigil / Demonic tome: after the quest the five words may be chanted in any order,
// without summoning anything. Shadow of the Storm is a completed-only quest here (no playable stages).
// OSRS gameval AnimationID: SUMMON_SUMMON_START 2879, SUMMON_SUMMON 2880; both exist in the 667 cache.
val CHANT_WORDS = arrayOf("Tarren", "Caldar", "Camerinthum", "Agrith-Naar", "Nahudu")

on_item_option(Items.DEMONIC_SIGIL, "Chant") {
    val slot = player.getInteractingItemSlot()
    val sigil = player.inventory[slot] ?: return@on_item_option
    if (sigil.id != Items.DEMONIC_SIGIL) return@on_item_option
    player.queue {
        player.animate(Anims.HUMANOID_2879)
        repeat(5) {
            val choice = options(*CHANT_WORDS)
            if (player.inventory[slot] !== sigil || choice !in 1..5) return@queue
            player.animate(Anims.HUMANOID_2880)
            player.forceChat(CHANT_WORDS[choice - 1])
        }
    }
}
