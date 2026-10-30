package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.items.combine.ItemAttachSounds

/** OSRS-IMPORT batch capes: max cape variant creation, knife separation and the Masori crafting kit (rules in [MaxCapes]). */

fun slotOf(
    player: Player,
    itemId: Int,
): Int = player.inventory.getItemIndex(itemId, skipAttrItems = false)

fun itemName(
    player: Player,
    itemId: Int,
): String = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, itemId).name

/** The max hood and (for the Masori assembler) the needle must be carried; the messages say which is missing. */
fun canCombine(
    player: Player,
    variant: MaxCapes.Variant,
): Boolean {
    val inventory = player.inventory
    if (!inventory.contains(variant.component) || !inventory.contains(MaxCapes.MAX_CAPE)) return false
    if (!inventory.contains(MaxCapes.MAX_HOOD)) {
        player.message("You need a max hood in your inventory to do that.")
        return false
    }
    val tool = variant.tool
    if (tool != null && !inventory.contains(tool)) {
        player.message("You need a needle to do that.")
        return false
    }
    return true
}

MaxCapes.VARIANTS.forEach { variant ->
    on_item_on_item(item1 = variant.component, item2 = MaxCapes.MAX_CAPE) {
        if (!canCombine(player, variant)) return@on_item_on_item
        // Owner 2026-10-03 (buglist E3): combining asks first, then says what happened and sounds (MaxCapes.combine...).
        player.queue {
            val componentName = itemName(player, variant.component)
            if (!confirmItemAction(MaxCapes.MAX_CAPE, MaxCapes.combineQuestion(componentName), MaxCapes.COMBINE_DETAIL)) return@queue
            // The items must still be there after the GUI (they may have been moved, dropped or banked meanwhile).
            if (!canCombine(player, variant)) return@queue
            val inventory = player.inventory
            val componentSlot = slotOf(player, variant.component)
            val capeSlot = slotOf(player, MaxCapes.MAX_CAPE)
            val hoodSlot = slotOf(player, MaxCapes.MAX_HOOD)
            if (componentSlot == -1 || capeSlot == -1 || hoodSlot == -1) return@queue
            val component = inventory[componentSlot]!!
            inventory[capeSlot] = null
            inventory[componentSlot] = Item(variant.cape).copyAttr(component)
            inventory[hoodSlot] = Item(variant.hood)
            ItemAttachSounds.attached(player, variant.component, MaxCapes.MAX_CAPE)
            player.filterableMessage(MaxCapes.combinedMessage(componentName))
            doubleItemMessageBox(MaxCapes.combinedMessage(componentName), item1 = variant.cape, item2 = variant.hood)
        }
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
        ItemAttachSounds.detached(player)
        player.filterableMessage(MaxCapes.separatedMessage(itemName(player, variant.component)))
    }
}

// Masori assembler: Ava's assembler + Masori crafting kit, with a needle.
gg.rsmod.plugins.content.items.combine.AttachConfirm.route(Items.MASORI_CRAFTING_KIT, Items.AVAS_ASSEMBLER, Items.MASORI_ASSEMBLER) {
    it.inventory.contains(Items.NEEDLE)
}
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
    ItemAttachSounds.attached(player, Items.MASORI_CRAFTING_KIT, Items.AVAS_ASSEMBLER)
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
    ItemAttachSounds.attached(player, Items.MASORI_CRAFTING_KIT, Items.ASSEMBLER_MAX_CAPE)
}
