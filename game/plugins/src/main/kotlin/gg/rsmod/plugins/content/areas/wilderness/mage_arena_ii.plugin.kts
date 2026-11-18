package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.api.cfg.Sfx

MageArenaII.destroyDetails().forEach { (id, detail) ->
    val destroy: Plugin.() -> Unit = {
        val slot = player.getInteractingItemSlot()
        player.queue {
            if (!confirmItemAction(id, "Are you sure you want to destroy this item?", detail)) return@queue
            if (MageArenaII.destroy(player, id, slot)) player.playSound(Sfx.DESTROY_OBJECT)
        }
    }
    on_item_option(item = id, option = "Destroy", logic = destroy)
    // Both inventory protocol routes, as in locator_orb.plugin.kts.
    on_item_option(item = id, option = 10, logic = destroy)
}

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
