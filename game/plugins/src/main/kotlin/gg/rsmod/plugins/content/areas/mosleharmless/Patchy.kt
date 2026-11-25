package gg.rsmod.plugins.content.areas.mosleharmless

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
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
    // Paired dry-run patchy-products, 25-11: candidates, guarded by the actual cache name and note state.
    // Claude must update these if fresh allocation differs when applying the batch.
    const val PARTYHAT_AND_SPECS = 25684
    const val PIRATE_HAT_AND_PATCH = 25686
    const val TOP_HAT_AND_MONOCLE = 25688
    private val imported = listOf(
        Recipe(Items.BLUE_PARTYHAT, 23582, PARTYHAT_AND_SPECS) to "Partyhat & specs",
        Recipe(23446, Items.EYE_PATCH, PIRATE_HAT_AND_PATCH) to "Pirate hat & patch",
        Recipe(Items.TOP_HAT, 23538, TOP_HAT_AND_MONOCLE) to "Top hat & monocle",
    )
    fun catalogue(definitions: DefinitionSet): List<Recipe> = RECIPES + imported.mapNotNull { (recipe, name) ->
        val definition = definitions.getNullable(ItemDef::class.java, recipe.product)
        recipe.takeIf { definition != null && !definition.noted && definition.name == name }
    }
    private fun known(player: Player, recipe: Recipe) = recipe in RECIPES || recipe in catalogue(player.world.definitions)
    data class Selection(val recipe: Recipe, val separate: Boolean, val originals: List<Pair<Int, Item>>)

    fun select(player: Player, recipe: Recipe, separate: Boolean): Selection? {
        if (!known(player, recipe)) return null
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
        if (!known(player, recipe) || selection.originals.any { (slot, item) -> player.inventory.rawItems.getOrNull(slot) !== item }) return false
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
