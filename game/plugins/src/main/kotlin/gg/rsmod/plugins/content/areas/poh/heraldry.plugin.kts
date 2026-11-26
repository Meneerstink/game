package gg.rsmod.plugins.content.areas.poh

/**
 * CONSTRUCTION-99 deel 7: Sir Renitee, the chief herald of Falador (OSRS Wiki "Sir Renitee": White Knights' Castle, first floor,
 * east end) - family crest and paintings ([PohHeraldry]).
 */
on_npc_option(npc = Npcs.SIR_RENITEE, option = "talk-to") {
    player.queue { PohHeraldry.talk(this) }
}
