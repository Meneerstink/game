package gg.rsmod.plugins.content.skills.cooking

/**
 * Nettle tea chain: bowl_of_water+nettles -> nettle_water (no xp), then nettle_water is boiled
 * on a fire/range via CookingData.NETTLE_TEA -> nettle_tea (guaranteed success, no burn). Ported
 * from Donors/void data/skill/cooking/cooking.recipes.toml [nettle_water] and cooking.tables.toml
 * [.nettle_water]. Milky nettle tea / cup variants are Ghosts Ahoy quest-only steps (not yet
 * implemented) and are deferred with that quest.
 */
private val NETTLE_TEA_LEVEL = 20

on_item_on_item(item1 = Items.BOWL_OF_WATER, item2 = Items.NETTLES) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < NETTLE_TEA_LEVEL) {
        player.message("You need a Cooking level of $NETTLE_TEA_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BOWL_OF_WATER)
    player.inventory.remove(Items.NETTLES)
    player.inventory.add(Items.NETTLEWATER)
    player.filterableMessage("You place the nettles into the bowl of water.")
}
