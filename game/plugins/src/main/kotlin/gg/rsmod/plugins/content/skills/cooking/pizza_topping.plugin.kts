package gg.rsmod.plugins.content.skills.cooking

/**
 * Pizza topping chain: pizza_base+tomato -> incomplete_pizza -> (+cheese) -> uncooked_pizza.
 * uncooked_pizza is cooked into plain_pizza via the existing CookingData.PLAIN_PIZZA row/
 * CookingAction, then plain_pizza + meat/chicken/anchovies/pineapple -> meat/anchovy/pineapple_pizza.
 * Ported from Donors/void data/skill/cooking/cooking.recipes.toml (incomplete_pizza, uncooked_pizza,
 * meat_pizza, chicken_meat_pizza, anchovy_pizza, pineapple_ring_pizza, pineapple_chunk_pizza
 * entries). Assembly steps (incomplete_pizza, uncooked_pizza) grant 0 xp, matching the
 * dough_mixing/stew_curry_mixing precedent; topping steps grant the donor's explicit xp values.
 */
private val ASSEMBLY_LEVEL = 35
private val TOPPING_LEVEL = 45
private val ANCHOVY_LEVEL = 55
private val PINEAPPLE_LEVEL = 65
private val TOPPING_XP = 26.0
private val ANCHOVY_XP = 39.0
private val PINEAPPLE_XP = 45.0

on_item_on_item(item1 = Items.PIZZA_BASE, item2 = Items.TOMATO) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < ASSEMBLY_LEVEL) {
        player.message("You need a Cooking level of $ASSEMBLY_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.PIZZA_BASE)
    player.inventory.remove(Items.TOMATO)
    player.inventory.add(Items.INCOMPLETE_PIZZA)
    player.filterableMessage("You add the tomato to the pizza.")
}

on_item_on_item(item1 = Items.INCOMPLETE_PIZZA, item2 = Items.CHEESE) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < ASSEMBLY_LEVEL) {
        player.message("You need a Cooking level of $ASSEMBLY_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.INCOMPLETE_PIZZA)
    player.inventory.remove(Items.CHEESE)
    player.inventory.add(Items.UNCOOKED_PIZZA)
    player.filterableMessage("You add the cheese to the pizza.")
}

on_item_on_item(itemUsed = Items.PLAIN_PIZZA, itemsList = intArrayOf(Items.COOKED_MEAT, Items.COOKED_CHICKEN)) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < TOPPING_LEVEL) {
        player.message("You need a Cooking level of $TOPPING_LEVEL to make this.")
        return@on_item_on_item
    }
    val meat = if (player.inventory.contains(Items.COOKED_MEAT)) Items.COOKED_MEAT else Items.COOKED_CHICKEN
    player.inventory.remove(Items.PLAIN_PIZZA)
    player.inventory.remove(meat)
    player.inventory.add(Items.MEAT_PIZZA)
    player.addXp(Skills.COOKING, TOPPING_XP)
    player.filterableMessage("You add the meat to the pizza.")
}

on_item_on_item(item1 = Items.PLAIN_PIZZA, item2 = Items.ANCHOVIES) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < ANCHOVY_LEVEL) {
        player.message("You need a Cooking level of $ANCHOVY_LEVEL to make this.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.PLAIN_PIZZA)
    player.inventory.remove(Items.ANCHOVIES)
    player.inventory.add(Items.ANCHOVY_PIZZA)
    player.addXp(Skills.COOKING, ANCHOVY_XP)
    player.filterableMessage("You add the anchovies to the pizza.")
}

on_item_on_item(itemUsed = Items.PLAIN_PIZZA, itemsList = intArrayOf(Items.PINEAPPLE_RING, Items.PINEAPPLE_CHUNKS)) {
    if (player.skills.getCurrentLevel(Skills.COOKING) < PINEAPPLE_LEVEL) {
        player.message("You need a Cooking level of $PINEAPPLE_LEVEL to make this.")
        return@on_item_on_item
    }
    val pineapple = if (player.inventory.contains(Items.PINEAPPLE_RING)) Items.PINEAPPLE_RING else Items.PINEAPPLE_CHUNKS
    player.inventory.remove(Items.PLAIN_PIZZA)
    player.inventory.remove(pineapple)
    player.inventory.add(Items.PINEAPPLE_PIZZA)
    player.addXp(Skills.COOKING, PINEAPPLE_XP)
    player.filterableMessage("You add the pineapple to the pizza. Why?")
}
