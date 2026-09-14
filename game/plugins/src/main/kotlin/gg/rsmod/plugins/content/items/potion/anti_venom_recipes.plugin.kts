package gg.rsmod.plugins.content.items.potion

/**
 * OSRS-IMPORT potions-venom recipes (OSRS Wiki Recipe templates, 2026-09-14):
 * - Anti-venom(n): Antidote++(n) + 5 x n Zulrah's scales, 87 Herblore (boostable), 30 x n experience.
 * - Anti-venom+(4): Anti-venom(4) + Torstol or Torstol potion (unf), 94 Herblore (boostable), 125 experience.
 * BLOCKED: Extended anti-venom+ (araxyte venom sacs, absent in 667). ADAPTED: messages.
 */

val antidotePlusPlusToAntiVenom =
    mapOf(
        Items.ANTIPOISON_4_5952 to Pair(Items.ANTI_VENOM_4, 4),
        Items.ANTIPOISON_3_5954 to Pair(Items.ANTI_VENOM_3, 3),
        Items.ANTIPOISON_2_5956 to Pair(Items.ANTI_VENOM_2, 2),
        Items.ANTIPOISON_1_5958 to Pair(Items.ANTI_VENOM_1, 1),
    )

antidotePlusPlusToAntiVenom.forEach { (antidote, product) ->
    val (antiVenom, doses) = product
    on_item_on_item(item1 = Items.ZULRAHS_SCALES, item2 = antidote) {
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < 87) {
            player.message("You need a Herblore level of 87 to make an anti-venom potion.")
            return@on_item_on_item
        }
        val scales = 5 * doses
        if (player.inventory.getItemCount(Items.ZULRAHS_SCALES) < scales) {
            player.message("You need $scales Zulrah's scales to do that.")
            return@on_item_on_item
        }
        if (!player.inventory.contains(antidote)) return@on_item_on_item
        player.inventory.remove(Items.ZULRAHS_SCALES, scales)
        player.inventory.remove(antidote, 1)
        player.inventory.add(antiVenom, 1)
        player.addXp(Skills.HERBLORE, 30.0 * doses)
    }
}

listOf(Items.CLEAN_TORSTOL, Items.TORSTOL_POTION_UNF).forEach { torstol ->
    on_item_on_item(item1 = torstol, item2 = Items.ANTI_VENOM_4) {
        if (player.skills.getCurrentLevel(Skills.HERBLORE) < 94) {
            player.message("You need a Herblore level of 94 to make an anti-venom+ potion.")
            return@on_item_on_item
        }
        if (!player.inventory.contains(torstol) || !player.inventory.contains(Items.ANTI_VENOM_4)) return@on_item_on_item
        player.inventory.remove(torstol, 1)
        player.inventory.remove(Items.ANTI_VENOM_4, 1)
        player.inventory.add(Items.ANTI_VENOM_PLUS_4, 1)
        player.addXp(Skills.HERBLORE, 125.0)
    }
}
