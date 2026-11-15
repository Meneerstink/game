package gg.rsmod.plugins.content.scrolls

// IDs are published only after Claude's verified cache transaction.
ClueInterfaces.of(73)?.let { reward ->
    on_button(reward, 4) { player.closeInterface(reward) }
    repeat(ClueInterfaces.REWARD_SLOTS) { slot ->
        val component = ClueInterfaces.REWARD_FIRST + slot
        on_button(reward, component) { ClueInterfaces.examine(player, component) }
    }
}
ClueInterfaces.of(203)?.let { text ->
    on_button(text, 1) { player.closeInterface(text) }
}
