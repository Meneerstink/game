package gg.rsmod.plugins.content.items.osrs

/**
 * Audit C-07: burns end with the death that started the death task, so no stack (and no Eclipse special pay-out) survives the
 * respawn. NPC burns stop in [Burns.apply] itself while the NPC is dead.
 */
on_player_pre_death {
    Burns.clear(player)
}
