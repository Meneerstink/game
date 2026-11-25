package gg.rsmod.plugins.content.magic.teleports

MinigameTeleport.SPELLS.forEach { spell ->
    on_button(spell.interfaceId, spell.component) {
        if (!gg.rsmod.plugins.content.magic.MagicSpells.isLoaded()) gg.rsmod.plugins.content.magic.MagicSpells.loadSpellRequirements()
        val problem = MinigameTeleport.refusal(player, spell)
        if (problem != null) { player.message(problem); return@on_button }
        player.queue {
            val choice = options(*(MinigameTeleport.DESTINATIONS.map { it.name } + "Cancel").toTypedArray(), title = "Minigame Teleport")
            val destination = MinigameTeleport.DESTINATIONS.getOrNull(choice - 1) ?: return@queue
            MinigameTeleport.cast(player, spell, destination)
        }
    }
}
