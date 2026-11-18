package gg.rsmod.plugins.content.scrolls

// CLUE-QH 18-11: the helped clue's panel, arrow and map icon follow the player (ClueHelper).
on_timer(ClueHelper.TIMER) { ClueHelper.tick(player) }
