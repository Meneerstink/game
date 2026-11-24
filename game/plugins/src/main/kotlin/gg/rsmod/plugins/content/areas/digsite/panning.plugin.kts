package gg.rsmod.plugins.content.areas.digsite

// Missing from the region's old spawn list. OSRS Wiki Panning guide map: 3376,3379; native 667 npc620.
spawn_npc(npc = Npcs.PANNING_GUIDE, x = 3376, z = 3379)
on_npc_option(Npcs.PANNING_GUIDE, "Talk-to") {
    player.queue {
        chatNpc("Hello, I am the panning guide. I'm here to teach you how to pan for gold.", npc = Npcs.PANNING_GUIDE)
    }
}

fun panHere(player: Player, slot: Int = player.inventory.getItemIndex(Items.PANNING_TRAY, skipAttrItems = false)) {
    if (player.attr[Panning.PERMISSION] != true) {
        player.queue {
            // Transcript:Panning guide, attempting to pan without permission. No invented reward/refusal text.
            chatNpc("Hey! You can't pan yet!", npc = Npcs.PANNING_GUIDE)
            chatNpc("Well, to be honest, what I would really like is... a nice cup of tea!", npc = Npcs.PANNING_GUIDE)
        }
    } else Panning.pan(player, slot)
}

on_obj_option(Objs.PANNING_POINT, "Pan") { panHere(player) }
on_item_on_obj(obj = Objs.PANNING_POINT, item = Items.PANNING_TRAY) {
    val slot = player.getInteractingItemSlot()
    val held = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get() ?: return@on_item_on_obj
    if (player.inventory[slot] === held && held.id == Items.PANNING_TRAY) panHere(player, slot)
}

on_item_option(Items.PANNING_TRAY, "Search") { player.message("An empty tray for panning.") }
on_item_option(Items.PANNING_TRAY_679, "Search") {
    val slot = player.getInteractingItemSlot()
    val held = player.inventory[slot] ?: return@on_item_option
    Panning.search(player, slot, held)
}
Panning.TEA.forEach { tea ->
    on_item_on_npc(item = tea, npc = Npcs.PANNING_GUIDE) {
        val slot = player.getInteractingItemSlot()
        val held = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get() ?: return@on_item_on_npc
        Panning.bribe(player, slot, held)
    }
}
on_item_on_npc(item = Items.NUGGETS, npc = Npcs.ARCHAEOLOGICAL_EXPERT) {
    val slot = player.getInteractingItemSlot()
    val held = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM]?.get() ?: return@on_item_on_npc
    if (player.inventory[slot] === held && held.id == Items.NUGGETS) Panning.exchangeNuggets(player)
}
