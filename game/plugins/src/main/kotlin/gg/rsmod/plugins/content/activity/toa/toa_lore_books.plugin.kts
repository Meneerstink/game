package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.content.items.books.Book

// N462 ToA lore books (ToaLoreBooks): Read with the existing book interface; the bags in the burial chamber give The jackal's torch.
val books = ToaLoreBooks.ALL.mapNotNull { b -> ToaIds.item(b.osrs)?.let { it to Book.fromText(b.title, b.text) } }.toMap()

books.forEach { (id, book) ->
    on_item_option(id, option = "Read") {
        if (player.inventory[player.getInteractingItemSlot()]?.id != id) return@on_item_option
        book.open(player)
    }
}

// Wiki The jackal's torch: 'found by searching the bags in the western end of Osmumten's burial chamber ... after defeating Tumeken's
// Warden and Elidinis' Warden'. ONZEKER: no OSRS message for a second search / a full inventory is published; one copy at a time.
ToaIds.loc(ToaLoreBooks.BAGS)?.let { bags ->
    on_obj_option(obj = bags, option = "Search") {
        val raid = ToaLobby.of(world).raid(player.uid) ?: return@on_obj_option
        if (!raid.progress.finished) return@on_obj_option
        val torch = ToaIds.item(ToaLoreBooks.JACKALS_TORCH) ?: return@on_obj_option
        if (player.inventory.contains(torch) || player.bank.contains(torch)) return@on_obj_option
        if (player.inventory.isFull) { player.message("You don't have enough inventory space."); return@on_obj_option }
        player.inventory.add(torch)
    }
}
