package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS-IMPORT item actions (`C:\RSPS\OSRS_IMPORT_MASTER.yml`). Combining is registered in
 * `CombinationData`; this file binds the cache "Dismantle" options of the imported definitions.
 *
 * - Ornamented items ([OsrsOrnamentKits]): Dismantle returns the base item and the ornament kit.
 * - Avernic defender: Dismantle returns the Dragon defender; the hilt is destroyed (OSRS Wiki), so
 *   the player confirms first.
 */

// Used-up kits (OsrsOrnamentKits.CONSUMED): a cleaning cloth on the frozen/volcanic whip, or the Revert option on the (or) staves,
// returns only the base item.
OsrsOrnamentKits.CONSUMED.forEach { kit ->
    fun revert(player: Player, slot: Int) {
        if (player.inventory[slot]?.id != kit.ornamented) return
        if (kit.returnsKit && player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return
        }
        player.inventory[slot] = gg.rsmod.game.model.item.Item(kit.base)
        if (kit.returnsKit) player.inventory.add(kit.kit, 1)
    }
    if (kit.cleaningCloth) {
        on_item_on_item(item1 = Items.CLEANING_CLOTH, item2 = kit.ornamented) {
            val first = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
            val second = player.attr[gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
            val slot = if (player.inventory[first]?.id == kit.ornamented) first else second
            revert(player, slot)
            // The cleaning cloth is an item-on-item detach: ItemActionGuard only sounds the Revert / Dismantle options.
            if (player.inventory[slot]?.id == kit.base) gg.rsmod.plugins.content.items.combine.ItemAttachSounds.detached(player)
        }
    } else {
        on_item_option(item = kit.ornamented, option = "Revert") { revert(player, player.getInteractingItemSlot()) }
    }
}

OsrsOrnamentKits.ALL.forEach { ornament ->
    on_item_option(item = ornament.ornamented, option = ornament.detachOption) {
        if (player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        if (!player.inventory.remove(item = ornament.ornamented, beginSlot = player.getInteractingItemSlot()).hasSucceeded()) {
            return@on_item_option
        }
        player.inventory.add(item = ornament.base, assureFullInsertion = true)
        player.inventory.add(item = ornament.kit, assureFullInsertion = true)
        // "Dismantle" already sounds through ItemActionGuard; the 667 "Split" option is not one of its options.
        if (!ornament.detachOption.equals("Dismantle", ignoreCase = true)) gg.rsmod.plugins.content.items.combine.ItemAttachSounds.detached(player)
        // Owner 2026-09-18: dismantling says the opposite of attaching, in the item GUI.
        player.queue { doubleItemMessageBox(ornament.detachMessage, item1 = ornament.base, item2 = ornament.kit) }
    }
}

// N537 stap 3b: asks its own question - without this the generic "Are you sure" came first (2x confirm).
gg.rsmod.plugins.content.items.ItemActionGuard.selfConfirming("dismantle", Items.AVERNIC_DEFENDER)
on_item_option(item = Items.AVERNIC_DEFENDER, option = "Dismantle") {
    val slot = player.getInteractingItemSlot()
    player.queue {
        when (options("Dismantle it. The hilt will be destroyed.", "Cancel.")) {
            1 -> {
                if (player.inventory[slot]?.id != Items.AVERNIC_DEFENDER) return@queue
                if (!player.inventory.remove(item = Items.AVERNIC_DEFENDER, beginSlot = slot).hasSucceeded()) return@queue
                player.inventory.add(item = Items.DRAGON_DEFENDER, assureFullInsertion = true)
            }
        }
    }
}

/*
 * N640 (red OsrsEquipment2ImportTests, key 24661): the ToA ornaments (Cursed phalanx on Osmumten's fang -> fang (or); the breaching /
 * corruption / sun / Amascut jewels on the Keris partisan) have local, imported ids, so they cannot be CombinationData rows - their
 * Dismantle existed above but nothing attached the kit. Same behaviour as every ornament kit: "Are you sure" item GUI (AttachConfirm,
 * the result has Dismantle), the kit is used up in the combination, the base becomes the ornamented item, attach sound + item GUI.
 */
val combinationResults = gg.rsmod.plugins.content.items.combine.CombinationData.values().map { it.resultItem }.toSet()
OsrsOrnamentKits.ALL.filter { it.ornamented !in combinationResults }.forEach { ornament ->
    gg.rsmod.plugins.content.items.combine.AttachConfirm.route(ornament.kit, ornament.base, ornament.ornamented)
    on_item_on_item(item1 = ornament.kit, item2 = ornament.base) {
        val first = player.attr[gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val kitSlot = if (player.inventory[first]?.id == ornament.kit) first else second
        val baseSlot = if (kitSlot == first) second else first
        if (player.inventory[kitSlot]?.id != ornament.kit || player.inventory[baseSlot]?.id != ornament.base) return@on_item_on_item
        player.inventory.remove(item = ornament.kit, beginSlot = kitSlot)
        player.inventory[baseSlot] = gg.rsmod.game.model.item.Item(ornament.ornamented)
        gg.rsmod.plugins.content.items.combine.ItemAttachSounds.attached(player, ornament.kit, ornament.base)
        player.queue { itemMessageBox(ornament.attachMessage, item = ornament.ornamented) }
    }
}

/*
 * N537 stap 3a (census 25-11): the ToA jewels and the Cursed phalanx list "Attach" in the cache but the option did nothing - only
 * use-on worked. The option now does the same attach onto the matching item in the backpack (same "Are you sure" item GUI, same
 * checks, the kit used up), or says which item it needs.
 */
OsrsOrnamentKits.ALL.filter { it.ornamented !in combinationResults }.groupBy { it.kit }.forEach { (kit, ornaments) ->
    if (world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, kit).inventoryMenu.none { it.equals("Attach", ignoreCase = true) }) return@forEach
    on_item_option(item = kit, option = "Attach") {
        val kitSlot = player.getInteractingItemSlot()
        if (player.inventory[kitSlot]?.id != kit) return@on_item_option
        val ornament = ornaments.firstOrNull { player.inventory.contains(it.base) }
        if (ornament == null) {
            val baseName = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, ornaments.first().base).name
            player.message("You need ${if (baseName.first().lowercaseChar() in "aeiou") "an" else "a"} ${baseName.lowercase()} in your inventory to attach this to.")
            return@on_item_option
        }
        val kitName = world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, kit).name
        player.queue {
            if (!confirmItemAction(ornament.base, gg.rsmod.plugins.content.items.combine.AttachConfirm.question(kitName),
                    gg.rsmod.plugins.content.items.combine.AttachConfirm.detail(kitName))) return@queue
            val baseSlot = player.inventory.getItemIndex(ornament.base, skipAttrItems = false)
            if (player.inventory[kitSlot]?.id != kit || baseSlot == -1) return@queue
            player.inventory.remove(item = kit, beginSlot = kitSlot)
            player.inventory[baseSlot] = gg.rsmod.game.model.item.Item(ornament.ornamented)
            gg.rsmod.plugins.content.items.combine.ItemAttachSounds.attached(player, kit, ornament.base)
            itemMessageBox(ornament.attachMessage, item = ornament.ornamented)
        }
    }
}
