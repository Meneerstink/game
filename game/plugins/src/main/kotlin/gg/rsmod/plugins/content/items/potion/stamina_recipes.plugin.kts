package gg.rsmod.plugins.content.items.potion

/**
 * OSRS-IMPORT potions-stamina recipe (OSRS Wiki "Stamina potion" Recipe templates, 2026-09-14): Super energy(n) + n Amylase crystals ->
 * Stamina potion(n), 77 Herblore (boostable), 25.5 x n experience. The Stamina mix recipe is a PotionData row. ADAPTED: messages.
 */

val superEnergyToStamina =
    mapOf(
        Items.SUPER_ENERGY_4 to Pair(Items.STAMINA_POTION_4, 4),
        Items.SUPER_ENERGY_3 to Pair(Items.STAMINA_POTION_3, 3),
        Items.SUPER_ENERGY_2 to Pair(Items.STAMINA_POTION_2, 2),
        Items.SUPER_ENERGY_1 to Pair(Items.STAMINA_POTION_1, 1),
    )

superEnergyToStamina.forEach { (superEnergy, product) ->
    val (stamina, doses) = product
    on_item_on_item(item1 = Items.AMYLASE_CRYSTAL, item2 = superEnergy) {
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < 77) {
            player.message("You need a Herblore level of 77 to make a stamina potion.")
            return@on_item_on_item
        }
        if (player.inventory.getItemCount(Items.AMYLASE_CRYSTAL) < doses) {
            player.message("You need $doses amylase crystals to do that.")
            return@on_item_on_item
        }
        if (!player.inventory.contains(superEnergy)) return@on_item_on_item
        player.inventory.remove(Items.AMYLASE_CRYSTAL, doses)
        player.inventory.remove(superEnergy, 1)
        player.inventory.add(stamina, 1)
        player.addXp(Skills.HERBLORE, 25.5 * doses)
    }
}
