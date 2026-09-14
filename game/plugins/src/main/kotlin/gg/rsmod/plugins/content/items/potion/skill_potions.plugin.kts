package gg.rsmod.plugins.content.items.potion

/**
 * OSRS-IMPORT potions-skill: moth "Release" and haemostatic dressing "Apply" (rules and sources in [SkillPotions]).
 */

on_item_option(item = Items.MOONLIGHT_MOTH, option = "Release") {
    if (player.inventory.remove(Items.MOONLIGHT_MOTH, 1).hasSucceeded()) {
        SkillPotions.moonlight(player)
        player.inventory.add(Items.BUTTERFLY_JAR, 1)
    }
}

on_item_option(item = Items.SUNLIGHT_MOTH, option = "Release") {
    if (player.inventory.remove(Items.SUNLIGHT_MOTH, 1).hasSucceeded()) {
        SkillPotions.sunlight(player)
        player.inventory.add(Items.BUTTERFLY_JAR, 1)
    }
}

// "Haemostatic dressings can be consumed even if the player is not bleeding"; no OSRS bleed status exists here, so a dose is only used up.
val dressings =
    listOf(Items.HAEMOSTATIC_DRESSING_4, Items.HAEMOSTATIC_DRESSING_3, Items.HAEMOSTATIC_DRESSING_2, Items.HAEMOSTATIC_DRESSING_1)

dressings.forEachIndexed { index, dressing ->
    on_item_option(item = dressing, option = "Apply") {
        val slot = player.getInteractingItemSlot()
        if (!player.inventory.remove(item = dressing, beginSlot = slot).hasSucceeded()) return@on_item_option
        dressings.getOrNull(index + 1)?.let { player.inventory.add(item = it, beginSlot = slot) }
    }
}
