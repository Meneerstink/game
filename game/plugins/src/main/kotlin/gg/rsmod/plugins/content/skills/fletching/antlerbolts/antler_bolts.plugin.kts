package gg.rsmod.plugins.content.skills.fletching.antlerbolts

/**
 * OSRS-IMPORT antler bolts (OSRS Wiki "Sunlight antler bolts" / "Moonlight antler bolts", 2026-09-14): a chisel on an
 * antler makes 12 bolts; Sunlight antler 62 Fletching and 10 XP, Moonlight antler 72 Fletching and 12.1 XP.
 * SOURCE_GAP: the per-action animation and tick timing are not stated on those pages; each selected antler is
 * processed without an invented animation or delay (recorded in OSRS_IMPORT_STATUS.md).
 */

data class AntlerRecipe(val antler: Int, val bolts: Int, val level: Int, val experience: Double)

val BOLTS_PER_ANTLER = 12

val recipes =
    listOf(
        AntlerRecipe(Items.SUNLIGHT_ANTLER, Items.SUNLIGHT_ANTLER_BOLTS, level = 62, experience = 10.0),
        AntlerRecipe(Items.MOONLIGHT_ANTLER, Items.MOONLIGHT_ANTLER_BOLTS, level = 72, experience = 12.1),
    )

recipes.forEach { recipe ->
    on_item_on_item(item1 = Items.CHISEL, item2 = recipe.antler) {
        player.queue {
            produceItemBox(
                recipe.bolts,
                option = SkillDialogueOption.MAKE,
                title = "Choose how many you wish to make, then<br>click on the chosen item to begin.",
                logic = ::carveAntlers,
            )
        }
    }
}

fun carveAntlers(
    player: Player,
    item: Int,
    amount: Int,
) {
    val recipe = recipes.firstOrNull { it.bolts == item } ?: return
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < recipe.level) {
        player.message("You need a Fletching level of ${recipe.level} to make these bolts.")
        return
    }
    repeat(amount) {
        if (!player.inventory.contains(Items.CHISEL)) return
        if (!player.inventory.remove(recipe.antler, 1).hasSucceeded()) return
        player.inventory.add(recipe.bolts, BOLTS_PER_ANTLER)
        player.addXp(Skills.FLETCHING, recipe.experience)
    }
}
