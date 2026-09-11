package gg.rsmod.plugins.content.activity.pest_control

/**
 * Hook wiring for Q-050 Pest Control. See PestControlHandler.kt for the full sourcing note and
 * disclosed simplifications.
 */

on_command("pestcontrolnovice") {
    PestControlHandler.join(player, PestControlTier.NOVICE)
}

on_command("pestcontrolintermediate") {
    PestControlHandler.join(player, PestControlTier.INTERMEDIATE)
}

on_command("pestcontrolveteran") {
    PestControlHandler.join(player, PestControlTier.VETERAN)
}

world.timers[PC_MASTER_TICK] = 1
on_timer(PC_MASTER_TICK) {
    PestControlHandler.tick(world)
    world.timers[PC_MASTER_TICK] = 1
}

on_npc_death(
    Npcs.PORTAL, Npcs.PORTAL_6143, Npcs.PORTAL_6144, Npcs.PORTAL_6145,
    Npcs.PORTAL_6150, Npcs.PORTAL_6151, Npcs.PORTAL_6152, Npcs.PORTAL_6153,
    Npcs.GENERAL_KHAZARD_7551, Npcs.GENERAL_KHAZARD_7552, Npcs.GENERAL_KHAZARD_7553, Npcs.CITIZEN,
) {
    PestControlHandler.onPortalDestroyed(npc)
}

on_npc_death(Npcs.VOID_KNIGHT, Npcs.VOID_KNIGHT_3785) {
    PestControlHandler.onSquireKilled(npc)
}
