package gg.rsmod.plugins.content.areas.mosleharmless

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items

/** OSRS Wiki Patchy and individual creation recipes; native 667 item ids (not OSRS ids). */
object Patchy {
    const val SEW_PRICE = 500
    const val SEPARATE_PRICE = 600
    data class Recipe(val first: Int, val second: Int, val product: Int)
    val RECIPES = listOf(
        Recipe(Items.PIRATE_BANDANA, Items.EYE_PATCH, Items.BANDANA_AND_EYEPATCH),
        Recipe(Items.PIRATE_BANDANA_7124, Items.EYE_PATCH, Items.BANDANA_AND_EYEPATCH_8925),
        Recipe(Items.PIRATE_BANDANA_7130, Items.EYE_PATCH, Items.BANDANA_AND_EYEPATCH_8926),
        Recipe(Items.PIRATE_BANDANA_7136, Items.EYE_PATCH, Items.BANDANA_AND_EYEPATCH_8927),
        Recipe(Items.PIRATES_HAT, Items.EYE_PATCH, Items.HAT_AND_EYEPATCH),
        Recipe(Items.CRAB_CLAW, Items.PIRATES_HOOK, Items.CRABCLAW_AND_HOOK),
        Recipe(Items.BLACK_CAVALIER, Items.HIGHWAYMAN_MASK, Items.CAVALIER_AND_MASK),
        Recipe(Items.BLACK_BERET, Items.MIME_MASK, Items.BERET_AND_MASK),
        Recipe(Items.LEFT_EYEPATCH, Items.EYE_PATCH, Items.DOUBLE_EYEPATCHES),
    )
    // Missing products need real paired-cache imports before they can be offered.
    const val OSRS_PARTYHAT_AND_SPECS = 12399
    const val OSRS_PIRATE_HAT_AND_PATCH = 12412
    const val OSRS_TOP_HAT_AND_MONOCLE = 12434
    const val OSRS_DARK_FLIPPERS = 25557 // Kourend dependency remains parked; not offered.
    data class Selection(val recipe: Recipe, val separate: Boolean, val originals: List<Pair<Int, Item>>)

    fun select(player: Player, recipe: Recipe, separate: Boolean): Selection? {
        if (recipe !in RECIPES) return null
        val ids = if (separate) listOf(recipe.product) else listOf(recipe.first, recipe.second)
        val originals = ids.map { id ->
            val slot = player.inventory.rawItems.indexOfFirst { it?.id == id }
            if (slot < 0) return null
            slot to player.inventory[slot]!!
        }
        return Selection(recipe, separate, originals)
    }

    /** Check capacity and every original reference before changing anything; no suspension during payment. */
    fun exchange(player: Player, selection: Selection): Boolean {
        val recipe = selection.recipe
        if (recipe !in RECIPES || selection.originals.any { (slot, item) -> player.inventory.rawItems.getOrNull(slot) !== item }) return false
        val required = if (selection.separate) listOf(recipe.product) else listOf(recipe.first, recipe.second)
        if (selection.originals.map { it.second.id } != required) return false
        val price = if (selection.separate) SEPARATE_PRICE else SEW_PRICE
        val outputs = if (selection.separate) listOf(recipe.first, recipe.second) else listOf(recipe.product)
        val check = ItemContainer(player.inventory)
        if (!check.remove(Items.COINS_995, price).hasSucceeded()) return false
        for ((slot, item) in selection.originals) if (!check.remove(item.id, 1, beginSlot = slot).hasSucceeded()) return false
        for (id in outputs) if (!check.add(id).hasSucceeded()) return false
        player.inventory.remove(Items.COINS_995, price)
        for ((slot, item) in selection.originals) player.inventory.remove(item.id, 1, beginSlot = slot)
        for (id in outputs) player.inventory.add(id)
        return true
    }
}
