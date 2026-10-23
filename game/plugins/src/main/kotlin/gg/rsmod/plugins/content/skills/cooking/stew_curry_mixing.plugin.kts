package gg.rsmod.plugins.content.skills.cooking

/**
 * Stew and curry mixing chain: bowl_of_water+potato -> incomplete_stew -> (+cooked meat/chicken)
 * -> uncooked_stew -> (+spice or 3x curry_leaf) -> uncooked_curry. Ported from Donors/void
 * data/skill/cooking/cooking.recipes.toml (incomplete_stew_potato, incomplete_stew_meat,
 * incomplete_stew_meat_chicken, uncooked_stew, uncooked_chicken_stew, uncooked_curry,
 * uncooked_curry_leaf entries). Target only has a single INCOMPLETE_STEW item id (1997), unlike
 * void's separate potato/meat recipe names, so both mixing paths converge on it here, matching
 * CookingData's own single UNCOOKED_STEW row.
 */
private val STEW_LEVEL = 25
private val CURRY_LEVEL = 60
private val CURRY_LEAF_AMOUNT = 3

on_item_on_item(item1 = Items.BOWL_OF_WATER, item2 = Items.POTATO) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < STEW_LEVEL) {
        player.message("You need a Cooking level of $STEW_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BOWL_OF_WATER)
    player.inventory.remove(Items.POTATO)
    player.inventory.add(Items.INCOMPLETE_STEW)
    player.filterableMessage("You cut up the potato and put it into the bowl.")
}

on_item_on_item(itemUsed = Items.INCOMPLETE_STEW, itemsList = intArrayOf(Items.COOKED_MEAT, Items.COOKED_CHICKEN)) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < STEW_LEVEL) {
        player.message("You need a Cooking level of $STEW_LEVEL to make this.")
        return@on_item_on_item
    }
    val meat = if (player.inventory.contains(Items.COOKED_MEAT)) Items.COOKED_MEAT else Items.COOKED_CHICKEN
    player.inventory.remove(Items.INCOMPLETE_STEW)
    player.inventory.remove(meat)
    player.inventory.add(Items.UNCOOKED_STEW)
    player.filterableMessage("You cut up the meat and put it into the stew.")
}

on_item_on_item(item1 = Items.UNCOOKED_STEW, item2 = Items.SPICE) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < CURRY_LEVEL) {
        player.message("You need a Cooking level of $CURRY_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.UNCOOKED_STEW)
    player.inventory.remove(Items.SPICE)
    player.inventory.add(Items.UNCOOKED_CURRY)
    player.filterableMessage("You mix the spice into the stew.")
}

on_item_on_item(item1 = Items.UNCOOKED_STEW, item2 = Items.CURRY_LEAF) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < CURRY_LEVEL) {
        player.message("You need a Cooking level of $CURRY_LEVEL to make this.")
        return@on_item_on_item
    }
    if (player.inventory.getItemCount(Items.CURRY_LEAF) < CURRY_LEAF_AMOUNT) {
        player.message("You need $CURRY_LEAF_AMOUNT curry leaves to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.UNCOOKED_STEW)
    player.inventory.remove(Items.CURRY_LEAF, CURRY_LEAF_AMOUNT)
    player.inventory.add(Items.UNCOOKED_CURRY)
    player.filterableMessage("You mix the curry leaves into the stew.")
}
