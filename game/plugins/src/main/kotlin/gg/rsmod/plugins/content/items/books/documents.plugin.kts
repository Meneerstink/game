package gg.rsmod.plugins.content.items.books

BookInterface(this).registerBookInterface()

ReadableDocuments.entries.forEach { (id, _) ->
    on_item_option(id, option = "Read") {
        ReadableDocuments.read(player, player.getInteractingItemSlot(), id)
    }
}
