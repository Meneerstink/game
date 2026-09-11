package gg.rsmod.plugins.content.skills.cooking

/**
 * Cake + chocolate_dust/chocolate_bar -> chocolate_cake. Ported from Donors/void
 * data/skill/cooking/cooking.recipes.toml (chocolate_cake_dust, chocolate_cake_bar entries).
 */
private val LEVEL = 50
private val XP = 30.0

private val chocolateSources = listOf(Items.CHOCOLATE_DUST, Items.CHOCOLATE_BAR)

for (chocolate in chocolateSources) {
    on_item_on_item(item1 = Items.CAKE, item2 = chocolate) {
        if (player.skills.getCurrentLevel(Skills.COOKING) < LEVEL) {
            player.message("You need a Cooking level of $LEVEL to make this.")
            return@on_item_on_item
        }
        player.inventory.remove(Items.CAKE)
        player.inventory.remove(chocolate)
        player.inventory.add(Items.CHOCOLATE_CAKE)
        player.addXp(Skills.COOKING, XP)
        player.filterableMessage("You make a chocolate cake.")
    }
}
