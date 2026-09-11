package gg.rsmod.plugins.content.skills.cooking

/**
 * Pie shell and filling recipes, ported from Donors/void data/skill/cooking/cooking.recipes.toml
 * `[uncooked_apple_pie]`, `[uncooked_meat_pie]`. Same on_item_on_item DSL as the
 * other cooking mixing plugins; no xp here (xp only comes from the actual bake step in
 * CookingData/CookingAction, same precedent as the batch-21/22/27 mixing chains). The target's
 * CookingData already has cook rows for mud/garden/fish/admiral/wild/summer pie, but neither
 * donor's cooking.recipes.toml has mixing recipes for those, so they are left as a pre-existing
 * gap outside this donor-porting task.
 *
 * The `[pie_shell]` recipe (pastry dough on pie dish) is deliberately NOT bound here: the target
 * already registers that pair through CombinationData.PIE_SHELL (item_combination.plugin.kts),
 * and PluginRepository.bindItemOnItem throws on a second binding of the same pair at boot.
 */
on_item_on_item(item1 = Items.COOKING_APPLE, item2 = Items.PIE_SHELL) {
    player.inventory.remove(Items.COOKING_APPLE)
    player.inventory.remove(Items.PIE_SHELL)
    player.inventory.add(Items.UNCOOKED_APPLE_PIE)
    player.filterableMessage("You fill the pie shell with apple.")
}

on_item_on_item(item1 = Items.REDBERRIES, item2 = Items.PIE_SHELL) {
    player.inventory.remove(Items.REDBERRIES)
    player.inventory.remove(Items.PIE_SHELL)
    player.inventory.add(Items.UNCOOKED_BERRY_PIE)
    player.filterableMessage("You fill the pie shell with redberries.")
}
