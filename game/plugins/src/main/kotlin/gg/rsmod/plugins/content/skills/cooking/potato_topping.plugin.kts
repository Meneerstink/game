package gg.rsmod.plugins.content.skills.cooking

/**
 * Baked potato topping chain: baked_potato+pat_of_butter -> potato_with_butter ->
 * (+cheese) -> potato_with_cheese.
 * Ported from Donors/void data/skill/cooking/cooking.recipes.toml (potato_with_butter,
 * potato_with_cheese entries). Deeper topping recipes (chilli_potato, egg_potato,
 * mushroom_potato, tuna_potato) depend on other unported combo-food chains
 * (chilli_con_carne, egg_and_tomato, mushroom_and_onion, tuna_and_corn) and remain deferred.
 */
private val BUTTER_LEVEL = 39
private val BUTTER_XP = 40.0
private val CHEESE_LEVEL = 47
private val CHEESE_XP = 40.0

on_item_on_item(item1 = Items.BAKED_POTATO, item2 = Items.PAT_OF_BUTTER) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < BUTTER_LEVEL) {
        player.message("You need a Cooking level of $BUTTER_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BAKED_POTATO)
    player.inventory.remove(Items.PAT_OF_BUTTER)
    player.inventory.add(Items.POTATO_WITH_BUTTER)
    player.addXp(Skills.COOKING, BUTTER_XP)
    player.filterableMessage("You add the butter to the potato.")
}

on_item_on_item(item1 = Items.POTATO_WITH_BUTTER, item2 = Items.CHEESE) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < CHEESE_LEVEL) {
        player.message("You need a Cooking level of $CHEESE_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.POTATO_WITH_BUTTER)
    player.inventory.remove(Items.CHEESE)
    player.inventory.add(Items.POTATO_WITH_CHEESE)
    player.addXp(Skills.COOKING, CHEESE_XP)
    player.filterableMessage("You add the cheese to the potato.")
}
