package gg.rsmod.plugins.content.areas.daemonheim

on_npc_option(npc = Npcs.DUNGEONEERING_TUTOR, option = "Talk-to") {
    player.queue {
        mainChat(this)
    }
}

suspend fun mainChat(it: QueueTask) {
    val player = it.player
    it.chatNpc("Greetings, adventurer.")
    if (!player.hasItem(Items.RING_OF_KINSHIP)) {
        it.chatNpc("Before we carry on, let me give you this.")
        it.itemMessageBox("He hands you a ring.", item = Items.RING_OF_KINSHIP)
        player.inventory.add(Items.RING_OF_KINSHIP)
    }
    it.chatNpc("Ready to delve into the dungeon?")
    Dungeoneering.join(player)
}

/*
 * Bug search 2026-10-02: the command teleported straight to Daemonheim from anywhere - deep Wilderness, Tele Blocked,
 * mid-fight. It is a remote travel route now: safe zone, out of combat, and the full teleport gate (RemoteAccess.travel).
 */
on_command("dungeon") {
    gg.rsmod.plugins.content.mechanics.pvp.RemoteAccess.travel(player, "travel to Daemonheim") { Dungeoneering.join(player) }
}
