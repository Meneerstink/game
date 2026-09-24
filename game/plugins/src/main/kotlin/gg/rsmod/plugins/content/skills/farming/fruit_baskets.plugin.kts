package gg.rsmod.plugins.content.skills.farming

import gg.rsmod.plugins.content.skills.farming.data.itemCreation.BasketItemCreation

/*
 * Fruit baskets holding 1-5 fruit ("Apples (1)" .. "Apples (5)", every second id): "Fill" tops a basket up to 5 from the inventory and
 * "Remove-one" takes one fruit out - both advertised by the 667 cache on all 25 baskets and unbound (farmingItemCreation only fills an
 * empty basket with 5 at once). Rules and messages from the 667 Void donor (content/skill/farming/Baskets.kt).
 */
data class FruitBasket(val fruit: Int, val first: Int, val name: String, val description: String)

val FRUIT_BASKETS =
    listOf(
        FruitBasket(Items.COOKING_APPLE, 5378, "apple", "an apple"),
        FruitBasket(Items.ORANGE, 5388, "orange", "an orange"),
        FruitBasket(Items.STRAWBERRY, 5398, "strawberry", "a strawberry"),
        FruitBasket(Items.BANANA, 5408, "banana", "a banana"),
        FruitBasket(Items.TOMATO, 5960, "tomato", "a tomato"),
    )

fun FruitBasket.id(count: Int): Int = first + (count - 1) * 2

FRUIT_BASKETS.forEach { basket ->
    check(BasketItemCreation.values().any { it.produce == basket.fruit && it.product == basket.id(5) }) { "basket table out of step: ${basket.name}" }
    (1..5).forEach { count ->
        val id = basket.id(count)
        on_item_option(item = id, option = "fill") {
            if (count == 5) {
                player.message("The ${basket.name} basket is already full.")
                return@on_item_option
            }
            val slot = player.getInteractingSlot()
            if (player.inventory[slot]?.id != id) return@on_item_option
            val removed = player.inventory.remove(basket.fruit, 5 - count).completed
            if (removed > 0) {
                player.inventory.remove(id, 1, beginSlot = slot)
                player.inventory.add(basket.id(count + removed), 1, beginSlot = slot)
            }
        }
        on_item_option(item = id, option = "remove-one") {
            val slot = player.getInteractingSlot()
            if (player.inventory[slot]?.id != id) return@on_item_option
            if (player.inventory.isFull) {
                player.message("You don't have enough inventory space.")
                return@on_item_option
            }
            player.inventory.remove(id, 1, beginSlot = slot)
            player.inventory.add(if (count == 1) Items.BASKET else basket.id(count - 1), 1, beginSlot = slot)
            player.inventory.add(basket.fruit)
            player.message("You take ${basket.description} out of the ${basket.name} basket.")
        }
    }
}
