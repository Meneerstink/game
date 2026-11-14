package gg.rsmod.plugins.content.areas.wilderness

MageArenaII.God.values().forEach { god ->
    on_item_on_npc(item = god.cape, npc = Npcs.KOLODION) {
        val slot = player.getInteractingItemSlot()
        player.queue { MageArenaIIDialogue.imbue(this, god, slot) }
    }
    MageArenaII.assets.remains(god)?.let { remains ->
        on_item_on_npc(item = remains, npc = Npcs.KOLODION) {
            if (MageArenaII.started(player)) player.queue { MageArenaIIDialogue.talk(this) }
        }
    }
}
