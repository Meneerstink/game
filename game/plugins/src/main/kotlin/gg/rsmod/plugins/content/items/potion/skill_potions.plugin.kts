package gg.rsmod.plugins.content.items.potion

import gg.rsmod.plugins.content.mechanics.aid.AcceptAid

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

// OSRS Wiki "Moonlight moth" / "Sunlight Moth": in multicombat a jarred moth used on another player shares its effect (SkillPotions.mothRecipients).
mapOf(Items.MOONLIGHT_MOTH to SkillPotions::moonlight, Items.SUNLIGHT_MOTH to SkillPotions::sunlight).forEach { (moth, effect) ->
    on_item_on_player(item = moth) {
        val target = player.getInteractingPlayer()
        val recipients = SkillPotions.mothRecipients(player, target)
        if (recipients.isEmpty()) {
            if (!AcceptAid.accepts(target)) player.message(AcceptAid.REFUSED)
            else player.message("Nothing interesting happens.")
            return@on_item_on_player
        }
        if (!player.inventory.remove(moth, 1).hasSucceeded()) return@on_item_on_player
        player.inventory.add(Items.BUTTERFLY_JAR, 1)
        recipients.forEach { effect(it) }
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
