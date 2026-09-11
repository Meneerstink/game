package gg.rsmod.plugins.content.skills.cooking

/**
 * Item-on-item combine steps for the four "filled jacket potato" topping chains
 * (chilli, egg&tomato, mushroom&onion, tuna&corn), plus the final topping-onto-potato
 * step. Ported from Donors/void data/skill/cooking/cooking.recipes.toml (spicy_sauce,
 * chilli_con_carne, egg_and_tomato, mushroom_and_onion, tuna_and_corn, sweetcorn_and_tuna,
 * chilli_potato, egg_potato, mushroom_potato, tuna_potato entries). Precursors
 * (chopped_garlic, scrambled_egg, fried_onions, fried_mushrooms, cooked_sweetcorn,
 * sweetcorn_bowl/SWEETCORN_7088) are produced by cutting_recipes.plugin.kts and the
 * existing CookingData raw/cooked table.
 */
on_item_on_item(item1 = Items.CHOPPED_GARLIC, item2 = Items.GNOME_SPICE) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 9) {
        player.message("You need a Cooking level of 9 to make spicy sauce.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.CHOPPED_GARLIC)
    player.inventory.remove(Items.GNOME_SPICE)
    player.inventory.add(Items.SPICY_SAUCE)
    player.addXp(Skills.COOKING, 25.0)
    player.filterableMessage("You add the spices to the garlic to make a spicy sauce.")
}

on_item_on_item(item1 = Items.COOKED_MEAT, item2 = Items.SPICY_SAUCE) {
    if (!player.inventory.contains(Items.KNIFE)) {
        player.message("You need a knife to chop the meat.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.COOKED_MEAT)
    player.inventory.remove(Items.SPICY_SAUCE)
    player.inventory.add(Items.CHILLI_CON_CARNE)
    player.addXp(Skills.COOKING, 25.0)
    player.filterableMessage("You chop up the meat and add it to the sauce in the bowl.")
}

on_item_on_item(item1 = Items.SCRAMBLED_EGG, item2 = Items.TOMATO) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 23) {
        player.message("You need a Cooking level of 23 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.SCRAMBLED_EGG)
    player.inventory.remove(Items.TOMATO)
    player.inventory.add(Items.EGG_AND_TOMATO)
    player.filterableMessage("You mix the ingredients to make the topping.")
}

on_item_on_item(item1 = Items.FRIED_MUSHROOMS, item2 = Items.FRIED_ONIONS) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 57) {
        player.message("You need a Cooking level of 57 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.FRIED_MUSHROOMS)
    player.inventory.remove(Items.FRIED_ONIONS)
    player.inventory.add(Items.MUSHROOM__ONION)
    player.inventory.add(Items.BOWL)
    player.filterableMessage("You mix the ingredients to make the topping.")
}

on_item_on_item(item1 = Items.SWEETCORN_7088, item2 = Items.TUNA) {
    if (!player.inventory.contains(Items.KNIFE)) {
        player.message("You need a knife to make this.")
        return@on_item_on_item
    }
    if (player.skills.getCurrentLevel(Skills.COOKING) < 67) {
        player.message("You need a Cooking level of 67 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.SWEETCORN_7088)
    player.inventory.remove(Items.TUNA)
    player.inventory.add(Items.TUNA_AND_CORN)
    player.filterableMessage("You mix the ingredients to make the topping.")
}

on_item_on_item(item1 = Items.CHILLI_CON_CARNE, item2 = Items.POTATO_WITH_BUTTER) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 41) {
        player.message("You need a Cooking level of 41 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.CHILLI_CON_CARNE)
    player.inventory.remove(Items.POTATO_WITH_BUTTER)
    player.inventory.add(Items.CHILLI_POTATO)
    player.inventory.add(Items.BOWL)
    player.addXp(Skills.COOKING, 15.0)
    player.filterableMessage("You add the topping to the potato.")
}

on_item_on_item(item1 = Items.EGG_AND_TOMATO, item2 = Items.POTATO_WITH_BUTTER) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 51) {
        player.message("You need a Cooking level of 51 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.EGG_AND_TOMATO)
    player.inventory.remove(Items.POTATO_WITH_BUTTER)
    player.inventory.add(Items.EGG_POTATO)
    player.inventory.add(Items.BOWL)
    player.addXp(Skills.COOKING, 45.0)
    player.filterableMessage("You add the topping to the potato.")
}

on_item_on_item(item1 = Items.MUSHROOM__ONION, item2 = Items.POTATO_WITH_BUTTER) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 64) {
        player.message("You need a Cooking level of 64 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.MUSHROOM__ONION)
    player.inventory.remove(Items.POTATO_WITH_BUTTER)
    player.inventory.add(Items.MUSHROOM_POTATO)
    player.inventory.add(Items.BOWL)
    player.addXp(Skills.COOKING, 55.0)
    player.filterableMessage("You add the topping to the potato.")
}

on_item_on_item(item1 = Items.TUNA_AND_CORN, item2 = Items.POTATO_WITH_BUTTER) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < 68) {
        player.message("You need a Cooking level of 68 to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.TUNA_AND_CORN)
    player.inventory.remove(Items.POTATO_WITH_BUTTER)
    player.inventory.add(Items.TUNA_POTATO)
    player.inventory.add(Items.BOWL)
    player.addXp(Skills.COOKING, 10.0)
    player.filterableMessage("You add the topping to the potato.")
}
