package gg.rsmod.plugins.content.skills.cooking

/**
 * Mixing water and a pot of flour into the four dough types used by the bread, pastry, pizza
 * and pitta cooking chains (CookingData.BREAD/PITTA_BREAD already consume BREAD_DOUGH/PITTA_DOUGH,
 * but nothing previously produced those dough items). Ported from Donors/void
 * data/skill/cooking/cooking.recipes.toml (bread_dough_*, pastry_dough_*, pizza_base_*,
 * pitta_dough_* entries); water can come from a bucket, bowl or jug, each returning that
 * container empty alongside the emptied flour pot.
 *
 * Each (water container, pot of flour) pair may only be bound once in the plugin repository, so
 * every water container gets a single binding which opens the produce-item dialogue listing all
 * four doughs; the chosen dough is then mixed for the requested amount.
 */
private data class DoughType(
    val dough: Int,
    val level: Int = 1,
    val xp: Double = 0.0,
)

private val doughTypes =
    listOf(
        DoughType(dough = Items.BREAD_DOUGH),
        DoughType(dough = Items.PASTRY_DOUGH, xp = 1.0),
        DoughType(dough = Items.PIZZA_BASE, level = 35),
        DoughType(dough = Items.PITTA_DOUGH, level = 35, xp = 1.0),
    )

private val waterContainers =
    mapOf(
        Items.BUCKET_OF_WATER to Items.BUCKET,
        Items.BOWL_OF_WATER to Items.BOWL,
        Items.JUG_OF_WATER to Items.JUG,
    )

for ((waterContainer, emptyContainer) in waterContainers) {
    on_item_on_item(item1 = waterContainer, item2 = Items.POT_OF_FLOUR) {
        val maxItems =
            minOf(
                player.inventory.getItemCount(waterContainer),
                player.inventory.getItemCount(Items.POT_OF_FLOUR),
            )
        player.queue {
            produceItemBox(
                *doughTypes.map { it.dough }.toIntArray(),
                maxItems = maxItems,
                logic = { dough, amount -> mixDough(this, waterContainer, emptyContainer, dough, amount) },
            )
        }
    }
}

fun mixDough(
    player: Player,
    waterContainer: Int,
    emptyContainer: Int,
    dough: Int,
    amount: Int,
) {
    val type = doughTypes.firstOrNull { it.dough == dough } ?: return
    if (player.skills.getCurrentLevel(Skills.COOKING) < type.level) {
        player.message("You need a Cooking level of ${type.level} to make this.")
        return
    }
    val inventory = player.inventory
    repeat(amount) {
        if (!inventory.contains(waterContainer) || !inventory.contains(Items.POT_OF_FLOUR)) {
            return
        }
        inventory.remove(waterContainer)
        inventory.remove(Items.POT_OF_FLOUR)
        inventory.add(dough)
        inventory.add(emptyContainer)
        inventory.add(Items.EMPTY_POT)
        if (type.xp > 0.0) {
            player.addXp(Skills.COOKING, type.xp)
        }
        player.filterableMessage("You mix the water and flour to make some dough.")
    }
}
