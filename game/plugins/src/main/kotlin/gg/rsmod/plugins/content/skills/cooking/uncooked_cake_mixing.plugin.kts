package gg.rsmod.plugins.content.skills.cooking

/**
 * Uncooked cake recipe: pot_of_flour + egg + bucket_of_milk + cake_tin -> uncooked_cake +
 * empty_pot + bucket. Ported from Donors/void data/skill/cooking/cooking.recipes.toml
 * `[uncooked_cake]` (4-in/3-out, no xp — xp only comes from the actual bake step in
 * CookingData/CookingAction, same precedent as the batch-21 CAKE row and the batch-22
 * stew/curry mixing chain). The engine's on_item_on_item DSL only pairs two trigger items, so
 * the recipe is anchored on pot_of_flour+egg (matching the stew_curry_mixing precedent of
 * checking extra required items already in inventory) rather than inventing an intermediate
 * item, since no such item exists in the target's item list and none should be added without
 * cache work.
 */
private val CAKE_LEVEL = 40

on_item_on_item(item1 = Items.POT_OF_FLOUR, item2 = Items.EGG) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < CAKE_LEVEL) {
        player.message("You need a Cooking level of $CAKE_LEVEL to make this.")
        return@on_item_on_item
    }
    if (!player.inventory.contains(Items.BUCKET_OF_MILK)) {
        player.message("You need a bucket of milk to make this.")
        return@on_item_on_item
    }
    if (!player.inventory.contains(Items.CAKE_TIN)) {
        player.message("You need a cake tin to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.POT_OF_FLOUR)
    player.inventory.remove(Items.EGG)
    player.inventory.remove(Items.BUCKET_OF_MILK)
    player.inventory.remove(Items.CAKE_TIN)
    player.inventory.add(Items.UNCOOKED_CAKE)
    player.inventory.add(Items.EMPTY_POT)
    player.inventory.add(Items.BUCKET)
    player.filterableMessage("You mix the milk, flour and egg together to make a raw cake mix.")
}
