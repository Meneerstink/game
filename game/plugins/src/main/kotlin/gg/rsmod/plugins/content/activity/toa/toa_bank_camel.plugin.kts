package gg.rsmod.plugins.content.activity.toa

/*
 * N462 (owner 2026-11-07 'dat hele gebied correct integreren net zoals osrs'): Cam the Bank Camel in the Jaltevas pyramid lobby.
 * OSRS Wiki Bank Camel (action=raw): options "Bank, Collect", examine "A well equipped camel.", map pin x:3352,y:9119 (lobby, plane 0);
 * Wiki Necropolis: "a friendly camel in the pyramid lobby, who lets players access their bank". OSRS npc TOA_BANK_CAMEL 11806
 * (RuneLite gameval) = local ToaIds.npc(11806). 'Collect' is bound for every Bank+Collect npc by banker_collect.plugin.kts.
 * ONZEKER: facing (the pin gives none) - static, facing south into the lobby.
 */
ToaIds.npc(11806)?.let { camel ->
    spawn_npc(npc = camel, x = 3352, z = 9119, height = 0, walkRadius = 0, direction = Direction.SOUTH, static = true)
    on_npc_option(camel, option = "Bank", lineOfSightDistance = 2) { player.openBank() }
}
