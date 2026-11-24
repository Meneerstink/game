package gg.rsmod.plugins.content.skills.cooking

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import java.lang.ref.WeakReference

/** OSRS Wiki Cocktail shaker, seven cocktail/ingredient pages; native 667 interface 436 and item definitions. */
object CocktailMaking {
    data class Recipe(val input: Int, val output: Int, val level: Int, val xp: Double,
        val ingredients: Map<Int, Int>, val returnsShaker: Boolean = false)
    data class Selection(val slot: Int, val held: WeakReference<Item>)
    val SELECTION = AttributeKey<Selection>()

    // Native interface buttons and their onInvTransmit ingredient lists (not a replacement cooking window).
    val MIXES = linkedMapOf(
        3 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_BLIZZARD, 18, 30.0,
            mapOf(Items.VODKA to 2, Items.GIN to 1, Items.ORANGE to 1, Items.LIME to 1, Items.LEMON to 1)),
        16 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_SGG, 20, 30.0,
            mapOf(Items.VODKA to 1, Items.LIME to 3)),
        23 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_BLAST, 6, 30.0,
            mapOf(Items.PINEAPPLE to 1, Items.LEMON to 1, Items.ORANGE to 1)),
        32 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_PUNCH, 8, 30.0,
            mapOf(Items.PINEAPPLE to 2, Items.ORANGE to 1, Items.LEMON to 1)),
        41 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_DRAGON, 32, 30.0,
            mapOf(Items.VODKA to 1, Items.GIN to 1, Items.DWELLBERRIES to 1)),
        50 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_SATURDAY, 33, 30.0,
            mapOf(Items.WHISKY to 1, Items.EQUA_LEAVES to 1, Items.BUCKET_OF_MILK to 1, Items.CHOCOLATE_BAR to 1)),
        61 to Recipe(Items.COCKTAIL_SHAKER, Items.MIXED_BLURBERRY_SPECIAL, 37, 30.0,
            mapOf(Items.VODKA to 1, Items.BRANDY to 1, Items.GIN to 1, Items.ORANGE to 1, Items.LEMON to 2)),
    )

    val POURS = listOf(
        Recipe(Items.MIXED_BLIZZARD, Items.WIZARD_BLIZZARD, 18, 80.0,
            mapOf(Items.COCKTAIL_GLASS to 1, Items.PINEAPPLE_CHUNKS to 1, Items.LIME_SLICES to 1), true),
        Recipe(Items.MIXED_SGG, Items.SHORT_GREEN_GUY, 20, 90.0,
            mapOf(Items.COCKTAIL_GLASS to 1, Items.LIME_SLICES to 1, Items.EQUA_LEAVES to 1), true),
        Recipe(Items.MIXED_BLAST, Items.FRUIT_BLAST, 6, 20.0,
            mapOf(Items.COCKTAIL_GLASS to 1, Items.LEMON_SLICES to 1), true),
        Recipe(Items.MIXED_PUNCH, Items.PINEAPPLE_PUNCH, 8, 40.0,
            mapOf(Items.COCKTAIL_GLASS to 1, Items.PINEAPPLE_CHUNKS to 1, Items.LIME_CHUNKS to 1, Items.ORANGE_SLICES to 1), true),
        Recipe(Items.MIXED_BLURBERRY_SPECIAL, Items.BLURBERRY_SPECIAL, 37, 150.0,
            mapOf(Items.COCKTAIL_GLASS to 1, Items.ORANGE_CHUNKS to 1, Items.LEMON_CHUNKS to 1,
                Items.LIME_SLICES to 1, Items.EQUA_LEAVES to 1), true),
        Recipe(Items.MIXED_SATURDAY, Items.MIXED_SATURDAY_9572, 33, 0.0,
            mapOf(Items.COCKTAIL_GLASS to 1), true),
        Recipe(Items.MIXED_DRAGON, Items.MIXED_DRAGON_9575, 32, 0.0,
            mapOf(Items.COCKTAIL_GLASS to 1), true),
    )
    val GARNISHES = listOf(
        Recipe(Items.MIXED_SATURDAY_9573, Items.CHOC_SATURDAY, 33, 140.0,
            mapOf(Items.CHOCOLATE_DUST to 1, Items.POT_OF_CREAM to 1)),
        Recipe(Items.MIXED_DRAGON_9575, Items.MIXED_DRAGON_9576, 32, 0.0,
            mapOf(Items.PINEAPPLE_CHUNKS to 1, Items.POT_OF_CREAM to 1)),
    )

    data class Cut(val fruit: Int, val chunks: Int, val slices: Int, val slicesAmount: Int = 1)
    val CUTS = listOf(
        Cut(Items.LEMON, Items.LEMON_CHUNKS, Items.LEMON_SLICES),
        Cut(Items.ORANGE, Items.ORANGE_CHUNKS, Items.ORANGE_SLICES),
        Cut(Items.PINEAPPLE, Items.PINEAPPLE_CHUNKS, Items.PINEAPPLE_RING, 4),
        Cut(Items.TENTI_PINEAPPLE, Items.PINEAPPLE_CHUNKS, Items.PINEAPPLE_RING, 4),
        Cut(Items.LIME, Items.LIME_CHUNKS, Items.LIME_SLICES),
    )

    fun open(player: Player, slot: Int, held: Item) {
        if (held.id != Items.COCKTAIL_SHAKER || player.inventory.rawItems.getOrNull(slot) !== held) return
        player.attr[SELECTION] = Selection(slot, WeakReference(held))
        player.openInterface(436, InterfaceDestination.MAIN_SCREEN)
    }

    /** Preflight the complete transaction, then commit without yielding; stale items and duplicate buttons cannot pay twice. */
    fun make(player: Player, recipe: Recipe, slot: Int, held: Item): Boolean {
        if (held.id != recipe.input || player.inventory.rawItems.getOrNull(slot) !== held ||
            player.skills.getCurrentLevel(Skills.COOKING) < recipe.level) return false
        val inventory = player.inventory
        val check = ItemContainer(inventory)
        if (!check.remove(held.id, 1, beginSlot = slot).hasSucceeded()) return false
        for ((id, count) in recipe.ingredients) if (!check.remove(id, count).hasSucceeded()) return false
        if (!check.add(recipe.output).hasSucceeded()) return false
        if (recipe.returnsShaker && !check.add(Items.COCKTAIL_SHAKER).hasSucceeded()) return false
        inventory.remove(held.id, 1, beginSlot = slot)
        recipe.ingredients.forEach { (id, count) -> inventory.remove(id, count) }
        inventory.add(recipe.output)
        if (recipe.returnsShaker) inventory.add(Items.COCKTAIL_SHAKER)
        if (recipe.xp > 0.0) player.addXp(Skills.COOKING, recipe.xp)
        return true
    }

    fun cut(player: Player, definition: Cut, output: Int, slot: Int, held: Item): Boolean {
        if (held.id != definition.fruit || player.inventory.rawItems.getOrNull(slot) !== held || !player.inventory.contains(Items.KNIFE) ||
            output !in listOf(definition.chunks, definition.slices)) return false
        val amount = if (output == definition.slices) definition.slicesAmount else 1
        val check = ItemContainer(player.inventory)
        if (!check.remove(held.id, 1, beginSlot = slot).hasSucceeded() || !check.add(output, amount).hasSucceeded()) return false
        player.inventory.remove(held.id, 1, beginSlot = slot)
        player.inventory.add(output, amount)
        return true
    }
}
