package gg.rsmod.plugins.content.items

import gg.rsmod.game.fs.def.ItemDef

/*
 * Slayer helmet assembly and Disassemble (owner live report 2026-09-17c: "slayer helm disasemble and asemble doesnt work unhandled
 * item"; nothing was bound for either). Rules: OSRS Wiki "Slayer helmet" (raw wikitext 2026-09-17c) - made "by combining a nose peg,
 * facemask, earmuffs, spiny helmet, enchanted gem, and a black mask", "level 55 Crafting (boosts work)", "The helmet is assembled by
 * using any component on another while all components are present in the player's inventory. The helmet can be disassembled with a
 * right-click option, turning it back into its component items", "if a black mask with charges is used to make a Slayer helmet, the
 * mask will lose all of its charges".
 *
 * ADAPTED: the "Malevolent masquerade" unlock (400 Slayer reward points) is not enforced - this server has no Slayer reward shop to
 * buy it from yet (recorded as a dependency, not invented here). The 667-only Full slayer helmet (Slayer helmet + hexcrest + focus
 * sight) follows the same two rules: any component on another, Disassemble returns the component items.
 */
val BLACK_MASKS =
    listOf(
        Items.BLACK_MASK, Items.BLACK_MASK_1, Items.BLACK_MASK_2, Items.BLACK_MASK_3, Items.BLACK_MASK_4, Items.BLACK_MASK_5,
        Items.BLACK_MASK_6, Items.BLACK_MASK_7, Items.BLACK_MASK_8, Items.BLACK_MASK_9, Items.BLACK_MASK_10,
    )
val HELMET_PARTS = listOf(Items.NOSE_PEG, Items.FACE_MASK, Items.EARMUFFS, Items.SPINY_HELMET, Items.ENCHANTED_GEM)
val FULL_HELMET_PARTS = listOf(Items.SLAYER_HELMET, Items.HEXCREST, Items.FOCUS_SIGHT)
val CRAFTING_LEVEL = 55

fun assembleHelmet(player: Player) {
    val mask = BLACK_MASKS.firstOrNull { player.inventory.contains(it) }
    if (mask == null || HELMET_PARTS.any { !player.inventory.contains(it) }) {
        player.message("You need a nose peg, facemask, earmuffs, spiny helmet, enchanted gem and a black mask to make a Slayer helmet.")
        return
    }
    if (player.skills.getCurrentLevel(Skills.CRAFTING) < CRAFTING_LEVEL) {
        player.message("You need a Crafting level of $CRAFTING_LEVEL to make a Slayer helmet.")
        return
    }
    (HELMET_PARTS + mask).forEach { player.inventory.remove(it, 1) }
    player.inventory.add(Items.SLAYER_HELMET, 1)
    player.message("You combine the items into a Slayer helmet.")
}

fun assembleFullHelmet(player: Player) {
    if (FULL_HELMET_PARTS.any { !player.inventory.contains(it) }) {
        player.message("You need a Slayer helmet, a hexcrest and a focus sight to make a full Slayer helmet.")
        return
    }
    if (player.skills.getCurrentLevel(Skills.CRAFTING) < CRAFTING_LEVEL) {
        player.message("You need a Crafting level of $CRAFTING_LEVEL to make a full Slayer helmet.")
        return
    }
    FULL_HELMET_PARTS.forEach { player.inventory.remove(it, 1) }
    player.inventory.add(Items.FULL_SLAYER_HELMET, 1)
    player.message("You combine the items into a full Slayer helmet.")
}

fun disassemble(
    player: Player,
    helmet: Int,
    parts: List<Int>,
) {
    val slot = player.getInteractingItemSlot()
    if (player.inventory[slot]?.id != helmet) return
    // The helmet's own slot is freed by the removal.
    if (player.inventory.freeSlotCount + 1 < parts.size) {
        player.message("You don't have enough inventory space to do that.")
        return
    }
    player.inventory[slot] = null
    parts.forEach { player.inventory.add(it, 1) }
    player.message("You disassemble your Slayer helmet.")
}

// "using any component on another": every pair of distinct components.
val helmetComponents = HELMET_PARTS + BLACK_MASKS
helmetComponents.forEachIndexed { i, first ->
    helmetComponents.drop(i + 1).forEach { second ->
        if (first in BLACK_MASKS && second in BLACK_MASKS) return@forEach
        on_item_on_item(item1 = first, item2 = second) { assembleHelmet(player) }
    }
}
FULL_HELMET_PARTS.forEachIndexed { i, first ->
    FULL_HELMET_PARTS.drop(i + 1).forEach { second -> on_item_on_item(item1 = first, item2 = second) { assembleFullHelmet(player) } }
}

/*
 * Owner 2026-09-19: "Slayer helm has a commune option this should not be the case ... the correct is Wear, Check, Disassemble, Drop"
 * (OSRS 2686 Slayer helmet 11864 / (i) 11865: inventory "Wear, Check, Disassemble", worn "Check"). Every 667 slayer helmet variant
 * carries exactly that menu now (cache menu edit 2026-09-19), including the Summoning-enchanted (e) / (charged) forms that used to
 * offer Commune / Uncharge. Check = the Slayer task line of the enchanted gem. Disassemble returns the components; a (charged)
 * helmet first returns its stored scrolls (the old Uncharge), and the (e) enchantment is lost with the helmet (ADAPTED: no source).
 */
val SLAYER_HELMET_PARTS = HELMET_PARTS + Items.BLACK_MASK
val FULL_SLAYER_HELMET_PARTS = SLAYER_HELMET_PARTS + Items.HEXCREST + Items.FOCUS_SIGHT
val SLAYER_HELMET_VARIANTS =
    mapOf(
        Items.SLAYER_HELMET to SLAYER_HELMET_PARTS,
        Items.SLAYER_HELMET_E to SLAYER_HELMET_PARTS,
        Items.SLAYER_HELMET_CHARGED to SLAYER_HELMET_PARTS,
        Items.FULL_SLAYER_HELMET to FULL_SLAYER_HELMET_PARTS,
        Items.FULL_SLAYER_HELMET_E to FULL_SLAYER_HELMET_PARTS,
        Items.FULL_SLAYER_HELMET_CHARGED to FULL_SLAYER_HELMET_PARTS,
    )

fun hasMenu(
    id: Int,
    option: String,
    worn: Boolean,
): Boolean {
    val def = world.definitions.get(ItemDef::class.java, id)
    return (if (worn) def.equipmentMenu else def.inventoryMenu).any { it.equals(option, ignoreCase = true) }
}

SLAYER_HELMET_VARIANTS.forEach { (helmet, parts) ->
    if (hasMenu(helmet, "Disassemble", worn = false)) {
        on_item_option(item = helmet, option = "Disassemble") {
            val slot = player.getInteractingItemSlot()
            if (player.inventory[slot]?.id != helmet) return@on_item_option
            val headgear = gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear.forItem(helmet)
            val scroll = player.attr[gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear.SCROLL_ATTR]
            val scrolls = player.attr[gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear.COUNT_ATTR] ?: 0
            if (headgear != null && helmet == headgear.charged && scroll != null && scrolls > 0) {
                val needed = parts.size + (if (player.inventory.contains(scroll)) 0 else 1)
                if (player.inventory.freeSlotCount + 1 < needed) {
                    player.message("You don't have enough inventory space to do that.")
                    return@on_item_option
                }
                player.inventory.add(scroll, scrolls)
                player.attr.remove(gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear.SCROLL_ATTR)
                player.attr.remove(gg.rsmod.plugins.content.skills.summoning.EnchantedHeadgear.COUNT_ATTR)
            }
            disassemble(player, helmet, parts)
        }
    }
    if (hasMenu(helmet, "Check", worn = false)) {
        on_item_option(item = helmet, option = "Check") { player.getSlayerKillsRemaining() }
    }
    if (hasMenu(helmet, "Check", worn = true)) {
        on_equipment_option(item = helmet, option = "Check") { player.getSlayerKillsRemaining() }
    }
}
