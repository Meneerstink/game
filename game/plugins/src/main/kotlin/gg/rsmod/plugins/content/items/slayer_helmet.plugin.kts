package gg.rsmod.plugins.content.items

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

on_item_option(item = Items.SLAYER_HELMET, option = "Disassemble") {
    disassemble(player, Items.SLAYER_HELMET, HELMET_PARTS + Items.BLACK_MASK)
}
on_item_option(item = Items.FULL_SLAYER_HELMET, option = "Disassemble") {
    disassemble(player, Items.FULL_SLAYER_HELMET, HELMET_PARTS + Items.BLACK_MASK + Items.HEXCREST + Items.FOCUS_SIGHT)
}
