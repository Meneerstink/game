package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.plugins.content.activity.toa.*

ToaRooms.register(ToaRoomKey(ToaPath.SCABARAS, false), ::ScabarasRoom)
listOf(45338, 45355).mapNotNull(ToaIds::loc).distinct().forEach { id ->
    on_obj_option(id, "Push") { (ToaPuzzles.of(player) as? ScabarasRoom)?.button(player, player.getInteractingGameObj()) }
}
ToaIds.loc(45339)?.let { id -> on_obj_option(id, "Inspect") { (ToaPuzzles.of(player) as? ScabarasRoom)?.inspect(player, player.getInteractingGameObj()) } }
ToaIds.loc(45344)?.let { id -> on_obj_option(id, "Flip") { (ToaPuzzles.of(player) as? ScabarasRoom)?.flip(player, player.getInteractingGameObj()) } }
(45356..45364).mapNotNull(ToaIds::loc).distinct().forEach { id ->
    on_obj_option(id, "Activate") { (ToaPuzzles.of(player) as? ScabarasRoom)?.activate(player, player.getInteractingGameObj()) }
}
ToaIds.loc(45343)?.let { id -> on_obj_option(id, "Crawl-through") { (ToaPuzzles.of(player) as? ScabarasRoom)?.shortcut(player, player.getInteractingGameObj()) } }
ToaIds.loc(45396)?.let { id -> on_obj_option(id, "Jump-to", lineOfSightDistance = 4) { (ToaPuzzles.of(player) as? ScabarasRoom)?.shortcut(player, player.getInteractingGameObj()) } }
ToaIds.npc(ScabarasRoom.OBELISK)?.let { id -> on_npc_option(npc = id, option = "Hit", lineOfSightDistance = 10) {
    (ToaPuzzles.of(player) as? ScabarasRoom)?.hitObelisk(player, player.getInteractingNpc())
} }
listOf(ScabarasRoom.SCARAB, ScabarasRoom.OBELISK, ScabarasRoom.OBELISK_LIT).mapNotNull(ToaIds::npc).distinct().forEach { id ->
    on_npc_combat(npc = id) { }
}
