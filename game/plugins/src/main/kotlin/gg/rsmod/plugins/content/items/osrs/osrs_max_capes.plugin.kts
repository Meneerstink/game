package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item

/** OSRS-IMPORT batch capes: max cape variant creation, knife separation and the Masori crafting kit (rules in [MaxCapes]). */

fun slotOf(
    player: Player,
    itemId: Int,
): Int = player.inventory.getItemIndex(itemId, skipAttrItems = false)

MaxCapes.VARIANTS.forEach { variant ->
    on_item_on_item(item1 = variant.component, item2 = MaxCapes.MAX_CAPE) {
        val inventory = player.inventory
        if (!inventory.contains(MaxCapes.MAX_HOOD)) {
            player.message("You need a max hood in your inventory to do that.")
            return@on_item_on_item
        }
        if (variant.tool != null && !inventory.contains(variant.tool)) {
            player.message("You need a needle to do that.")
            return@on_item_on_item
        }
        val componentSlot = slotOf(player, variant.component)
        val capeSlot = slotOf(player, MaxCapes.MAX_CAPE)
        val hoodSlot = slotOf(player, MaxCapes.MAX_HOOD)
        if (componentSlot == -1 || capeSlot == -1 || hoodSlot == -1) return@on_item_on_item
        val component = inventory[componentSlot]!!
        inventory[capeSlot] = null
        inventory[componentSlot] = Item(variant.cape).copyAttr(component)
        inventory[hoodSlot] = Item(variant.hood)
    }

    on_item_on_item(item1 = Items.KNIFE, item2 = variant.cape) {
        val inventory = player.inventory
        val capeSlot = slotOf(player, variant.cape)
        if (capeSlot == -1) return@on_item_on_item
        if (inventory.freeSlotCount < 1) {
            player.message("You need a free inventory space to do that.")
            return@on_item_on_item
        }
        val cape = inventory[capeSlot]!!
        inventory[capeSlot] = Item(variant.component).copyAttr(cape)
        inventory.add(MaxCapes.MAX_CAPE, 1)
        val hoodSlot = slotOf(player, variant.hood)
        if (hoodSlot != -1) inventory[hoodSlot] = Item(MaxCapes.MAX_HOOD)
    }
}

// Masori assembler: Ava's assembler + Masori crafting kit, with a needle.
on_item_on_item(item1 = Items.MASORI_CRAFTING_KIT, item2 = Items.AVAS_ASSEMBLER) {
    val inventory = player.inventory
    if (!inventory.contains(Items.NEEDLE)) {
        player.message("You need a needle to do that.")
        return@on_item_on_item
    }
    val assemblerSlot = slotOf(player, Items.AVAS_ASSEMBLER)
    if (assemblerSlot == -1 || !inventory.contains(Items.MASORI_CRAFTING_KIT)) return@on_item_on_item
    inventory.remove(Items.MASORI_CRAFTING_KIT, 1)
    inventory[assemblerSlot] = Item(Items.MASORI_ASSEMBLER).copyAttr(inventory[assemblerSlot]!!)
}

// "A needle is required in order to create and dismantle the Masori assembler."
on_item_option(item = Items.MASORI_ASSEMBLER, option = "Dismantle") {
    val inventory = player.inventory
    val slot = player.getInteractingItemSlot()
    if (inventory[slot]?.id != Items.MASORI_ASSEMBLER) return@on_item_option
    if (!inventory.contains(Items.NEEDLE)) {
        player.message("You need a needle to do that.")
        return@on_item_option
    }
    if (inventory.freeSlotCount < 1) {
        player.message("You need a free inventory space to do that.")
        return@on_item_option
    }
    inventory[slot] = Item(Items.AVAS_ASSEMBLER).copyAttr(inventory[slot]!!)
    inventory.add(Items.MASORI_CRAFTING_KIT, 1)
}

// "a Masori crafting kit on an Assembler max cape" (needle; the Assembler max hood becomes the Masori assembler max hood).
on_item_on_item(item1 = Items.MASORI_CRAFTING_KIT, item2 = Items.ASSEMBLER_MAX_CAPE) {
    val inventory = player.inventory
    if (!inventory.contains(Items.NEEDLE)) {
        player.message("You need a needle to do that.")
        return@on_item_on_item
    }
    val capeSlot = slotOf(player, Items.ASSEMBLER_MAX_CAPE)
    val hoodSlot = slotOf(player, Items.ASSEMBLER_MAX_HOOD)
    if (hoodSlot == -1) {
        player.message("You need the assembler max hood in your inventory to do that.")
        return@on_item_on_item
    }
    if (capeSlot == -1 || !inventory.contains(Items.MASORI_CRAFTING_KIT)) return@on_item_on_item
    inventory.remove(Items.MASORI_CRAFTING_KIT, 1)
    inventory[capeSlot] = Item(Items.MASORI_ASSEMBLER_MAX_CAPE).copyAttr(inventory[capeSlot]!!)
    inventory[hoodSlot] = Item(Items.MASORI_ASSEMBLER_MAX_HOOD)
}
