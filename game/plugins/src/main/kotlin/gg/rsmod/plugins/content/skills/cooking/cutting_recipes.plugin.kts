package gg.rsmod.plugins.content.skills.cooking

/**
 * Bowl-cutting/prep recipes that feed the combo-food chains (chilli_con_carne,
 * egg_and_tomato, mushroom_and_onion, tuna_and_corn -> potato toppings, etc).
 * Ported from Donors/void data/skill/cooking/cooking.recipes.toml (chopped_onion,
 * chopped_garlic, uncooked_egg, sliced_mushrooms, sweetcorn_bowl entries).
 * None of these have a level/xp field in the donor data (0 xp mixing steps,
 * same precedent as prior cooking-chain batches).
 */
on_item_on_item(item1 = Items.BOWL, item2 = Items.ONION) {
    if (!player.inventory.contains(Items.KNIFE)) {
        player.message("You need a knife to cut the onion.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BOWL)
    player.inventory.remove(Items.ONION)
    player.inventory.add(Items.CHOPPED_ONION)
    player.filterableMessage("You cut the onion into the bowl.")
}

on_item_on_item(item1 = Items.BOWL, item2 = Items.GARLIC) {
    if (!player.inventory.contains(Items.KNIFE)) {
        player.message("You need a knife to chop the garlic.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BOWL)
    player.inventory.remove(Items.GARLIC)
    player.inventory.add(Items.CHOPPED_GARLIC)
    player.filterableMessage("You chop the garlic into the bowl.")
}

on_item_on_item(item1 = Items.BOWL, item2 = Items.EGG) {
    player.inventory.remove(Items.BOWL)
    player.inventory.remove(Items.EGG)
    player.inventory.add(Items.UNCOOKED_EGG)
    player.filterableMessage("You carefully break the egg into the bowl.")
}

on_item_on_item(item1 = Items.BOWL, item2 = Items.BITTERCAP_MUSHROOM) {
    if (!player.inventory.contains(Items.KNIFE)) {
        player.message("You need a knife to slice the mushroom.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BOWL)
    player.inventory.remove(Items.BITTERCAP_MUSHROOM)
    player.inventory.add(Items.SLICED_MUSHROOMS)
    player.filterableMessage("You slice the mushroom into the bowl.")
}

on_item_on_item(item1 = Items.BOWL, item2 = Items.COOKED_SWEETCORN) {
    player.inventory.remove(Items.BOWL)
    player.inventory.remove(Items.COOKED_SWEETCORN)
    player.inventory.add(Items.SWEETCORN_7088)
    player.filterableMessage("You put the cooked sweetcorn into the bowl.")
}
