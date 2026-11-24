package gg.rsmod.plugins.content.skills.cooking

on_item_option(Items.COCKTAIL_SHAKER, "Mix-cocktail") {
    val slot = player.getInteractingItemSlot()
    val held = player.inventory[slot] ?: return@on_item_option
    CocktailMaking.open(player, slot, held)
}
CocktailMaking.MIXES.forEach { (button, recipe) ->
    on_button(436, button) {
        if (!player.interfaces.isVisible(436)) return@on_button
        val selection = player.attr[CocktailMaking.SELECTION] ?: return@on_button
        val held = selection.held.get() ?: return@on_button
        player.attr.remove(CocktailMaking.SELECTION)
        player.closeMainInterface()
        player.queue {
            wait(1) // Wiki mixed-cocktail recipes: one tick.
            CocktailMaking.make(player, recipe, selection.slot, held)
        }
    }
}
on_button(436, 95) { player.closeMainInterface(); player.attr.remove(CocktailMaking.SELECTION) }

CocktailMaking.POURS.forEach { recipe ->
    on_item_option(recipe.input, "Pour") {
        val slot = player.getInteractingItemSlot()
        val held = player.inventory[slot] ?: return@on_item_option
        // Blizzard and Blurberry take one tick; the other pours are immediate (OSRS Wiki recipes).
        if (recipe.input in listOf(Items.MIXED_BLIZZARD, Items.MIXED_BLURBERRY_SPECIAL)) {
            player.queue { wait(1); CocktailMaking.make(player, recipe, slot, held) }
        } else CocktailMaking.make(player, recipe, slot, held)
    }
    on_item_on_item(item1 = recipe.input, item2 = Items.COCKTAIL_GLASS) {
        val slot = player.inventory.getItemIndex(recipe.input, skipAttrItems = false)
        val held = player.inventory.rawItems.getOrNull(slot) ?: return@on_item_on_item
        if (recipe.input in listOf(Items.MIXED_BLIZZARD, Items.MIXED_BLURBERRY_SPECIAL)) {
            player.queue { wait(1); CocktailMaking.make(player, recipe, slot, held) }
        } else CocktailMaking.make(player, recipe, slot, held)
    }
}
CocktailMaking.GARNISHES.forEach { recipe ->
    on_item_option(recipe.input, "Add-ingreds") {
        val slot = player.getInteractingItemSlot()
        val held = player.inventory[slot] ?: return@on_item_option
        CocktailMaking.make(player, recipe, slot, held)
    }
    recipe.ingredients.keys.forEach { ingredient ->
        on_item_on_item(item1 = recipe.input, item2 = ingredient) {
            val slot = player.inventory.getItemIndex(recipe.input, skipAttrItems = false)
            val held = player.inventory.rawItems.getOrNull(slot) ?: return@on_item_on_item
            CocktailMaking.make(player, recipe, slot, held)
        }
    }
}
CocktailMaking.CUTS.forEach { cut ->
    on_item_on_item(item1 = Items.KNIFE, item2 = cut.fruit) {
        val slot = player.inventory.getItemIndex(cut.fruit, skipAttrItems = false)
        val held = player.inventory.rawItems.getOrNull(slot) ?: return@on_item_on_item
        player.queue {
            produceItemBox(cut.chunks, cut.slices, maxItems = 1, logic = { output, _ ->
                queue { wait(2); CocktailMaking.cut(this.player, cut, output, slot, held) }
            })
        }
    }
}
