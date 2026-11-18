package gg.rsmod.plugins.content.activity.cox

/**
 * CoX supplies: Herblore in the raid (CoxHerblore) - Clean on the grimy herbs, any two ingredients used together mix.
 */
fun hasOp(item: Int, op: String): Boolean =
    world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).inventoryMenu.any { it == op }

CoxHerblore.HERBS.forEach { herb ->
    CoxIds.item(herb.grimy)?.takeIf { hasOp(it, "Clean") }?.let { id -> on_item_option(item = id, option = "Clean") { CoxHerblore.clean(player, herb) } }
}

val ingredients = CoxHerblore.INGREDIENTS.mapNotNull(CoxIds::item).distinct().sorted()
for (i in ingredients.indices) for (j in i + 1 until ingredients.size) {
    on_item_on_item(item1 = ingredients[i], item2 = ingredients[j]) { CoxHerblore.mix(player) }
}

/** Storage units (CoxStorage): Private / Shared / Build / Upgrade on every tier (only the ops the cache gives), the mountain's unit 30107. */
fun locOps(id: Int): List<String> = world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, id).options.filterNotNull()
CoxStorage.TIERS.mapNotNull { CoxIds.loc(it.osrsLoc) }.forEach { id ->
    val ops = locOps(id)
    if ("Private" in ops) on_obj_option(obj = id, option = "Private") { CoxStorage.openPrivate(player, false) }
    if ("Shared" in ops) on_obj_option(obj = id, option = "Shared") { CoxStorage.openShared(player) }
    if ("Build" in ops) on_obj_option(obj = id, option = "Build") { CoxStorage.build(player) }
    if ("Upgrade" in ops) on_obj_option(obj = id, option = "Upgrade") { CoxStorage.build(player) }
}
CoxIds.loc(CoxStorage.MOUNT_UNIT)?.let { id -> if ("Private" in locOps(id)) on_obj_option(obj = id, option = "Private") { CoxStorage.openPrivate(player, true) } }
CoxInterfaces.of(271)?.let { iface ->
    (listOf(CoxStorage.PRIVATE_CLOSE, CoxStorage.TO_SHARED) + (0 until CoxStorage.PRIVATE_SHOWN).map { CoxStorage.PRIVATE_SLOTS + it }).forEach { c ->
        on_button(iface, c) { CoxStorage.click(player, false, c, gg.rsmod.plugins.content.inter.slayer.SlayerRewardsUi.opOf(player.getInteractingOpcode())) }
    }
    on_interface_close(iface) { CoxStorage.close(player) }
}
CoxInterfaces.of(550)?.let { iface ->
    (listOf(CoxStorage.SHARED_CLOSE, CoxStorage.TO_PRIVATE) + (0 until CoxStorage.SHARED_SHOWN).map { CoxStorage.SHARED_SLOTS + it }).forEach { c ->
        on_button(iface, c) { CoxStorage.click(player, true, c, gg.rsmod.plugins.content.inter.slayer.SlayerRewardsUi.opOf(player.getInteractingOpcode())) }
    }
    on_interface_close(iface) { CoxStorage.close(player) }
}

/** CoX lore books: Read (book interface, marks it read), the creature keeper's corpse 29875 'Search' gives his journal (Wiki). */
CoxLoreBooks.ALL.forEach { b ->
    CoxIds.item(b.osrs)?.let { id ->
        val book = gg.rsmod.plugins.content.items.books.Book.fromText(b.title, b.text)
        on_item_option(id, option = "Read") {
            if (player.inventory[player.getInteractingItemSlot()]?.id != id) return@on_item_option
            CoxLoreBooks.markRead(player, b.osrs)
            book.open(player)
        }
    }
}
CoxIds.loc(29875)?.let { body ->
    on_obj_option(obj = body, option = "Search") {
        if (CoxRaids.raidAt(player.tile) == null) return@on_obj_option
        val journal = CoxIds.item(CoxLoreBooks.KEEPER) ?: return@on_obj_option
        // tsps (code help): 'You need some more free space to search the body.' / 'You already have a journal.' / 'You find the creature keeper's journal.'
        if (player.inventory.contains(journal)) { player.queue { messageBox("You already have a journal.") }; return@on_obj_option }
        if (player.inventory.isFull) { player.message("You need some more free space to search the body."); return@on_obj_option }
        player.inventory.add(journal)
        player.queue { messageBox("You find the creature keeper's journal.") }
    }
}
