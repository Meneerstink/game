package gg.rsmod.plugins.content.skills.herblore

import gg.rsmod.plugins.content.quests.foundation.bracket.OneSmallFavour

/*
 * Guthix rest tea (One Small Favour reward), OSRS Wiki "Guthix rest" + "Herb tea mix" + "Ruined herb tea" + "Cup of hot water" (run 4):
 *  - 2 guam leaves, 1 harralander and 1 marrentill on a cup of hot water, in any order, make Guthix rest(3); a partial cup is one of the
 *    10 herb tea mixes (4464-4482). Using one herb while the others are in the inventory adds them all at once.
 *  - 14, 14.5, 15 and 15.5 Herblore experience for the first, second, third and fourth herb (59 in all); Herblore 18, boostable.
 *  - only "after a point during One Small Favour": once Sanfew has explained it ([OneSmallFavour.guthixRestTaught]).
 *  - a second harralander / marrentill or a third guam warns that the tea will be ruined; going on gives a ruined herb tea.
 *  - a bowl of hot water poured into an empty cup also gives a cup of hot water.
 * The refusal / warning lines have no OSRS source (BUGLIJST P48-TEKST).
 */

/** Herbs in a cup: harralander, marrentill, guam. */
data class Brew(val h: Int, val m: Int, val g: Int) {
    val herbs get() = h + m + g
}

val EMPTY_BREW = Brew(0, 0, 0)
val FULL_BREW = Brew(1, 1, 2)

val BREWS =
    mapOf(
        Items.CUP_OF_HOT_WATER to EMPTY_BREW,
        Items.HERB_TEA_MIX to Brew(1, 0, 0),
        Items.HERB_TEA_MIX_4466 to Brew(0, 0, 1),
        Items.HERB_TEA_MIX_4468 to Brew(0, 1, 0),
        Items.HERB_TEA_MIX_4470 to Brew(1, 1, 0),
        Items.HERB_TEA_MIX_4472 to Brew(1, 0, 1),
        Items.HERB_TEA_MIX_4474 to Brew(0, 0, 2),
        Items.HERB_TEA_MIX_4476 to Brew(0, 1, 1),
        Items.HERB_TEA_MIX_4478 to Brew(1, 1, 1),
        Items.HERB_TEA_MIX_4480 to Brew(0, 1, 2),
        Items.HERB_TEA_MIX_4482 to Brew(1, 0, 2),
    )

val ITEM_OF_BREW = BREWS.entries.associate { (item, brew) -> brew to item } + (FULL_BREW to Items.GUTHIX_REST_3)

val HERBS = listOf(Items.CLEAN_HARRALANDER, Items.CLEAN_MARRENTILL, Items.CLEAN_GUAM)

val HERB_XP = doubleArrayOf(14.0, 14.5, 15.0, 15.5)

val TEA_LEVEL = 18

fun Brew.with(herb: Int): Brew =
    when (herb) {
        Items.CLEAN_HARRALANDER -> copy(h = h + 1)
        Items.CLEAN_MARRENTILL -> copy(m = m + 1)
        else -> copy(g = g + 1)
    }

fun Brew.ruinedBy(herb: Int): Boolean = with(herb).let { it.h > FULL_BREW.h || it.m > FULL_BREW.m || it.g > FULL_BREW.g }

BREWS.keys.forEach { cup ->
    HERBS.forEach { herb ->
        on_item_on_item(item1 = cup, item2 = herb) { addHerb(player, cup, herb) }
    }
}

on_item_on_item(item1 = Items.BOWL_OF_HOT_WATER, item2 = Items.EMPTY_CUP) {
    val inv = player.inventory
    if (inv.remove(Items.BOWL_OF_HOT_WATER).hasFailed()) return@on_item_on_item
    inv.remove(Items.EMPTY_CUP)
    inv.add(Items.CUP_OF_HOT_WATER)
    inv.add(Items.BOWL)
    player.filterableMessage("You pour the hot water into the cup.")
}

fun addHerb(
    player: gg.rsmod.game.model.entity.Player,
    cup: Int,
    herb: Int,
) {
    if (!OneSmallFavour.guthixRestTaught(player)) {
        player.message("You need to progress further in One Small Favour before you can make Guthix rest.")
        return
    }
    if (player.skills.getCurrentLevel(Skills.HERBLORE) < TEA_LEVEL) {
        player.message("You need a Herblore level of $TEA_LEVEL to make Guthix rest.")
        return
    }
    val brew = BREWS[cup] ?: return
    if (!brew.ruinedBy(herb)) {
        brewAll(player, cup, brew, herb)
        return
    }
    player.queue {
        val choice = options("Add it and ruin the tea.", "Leave the tea as it is.", title = "Too much of that herb will ruin the tea.")
        if (choice != 1) return@queue
        val inv = player.inventory
        if (!inv.contains(cup) || inv.remove(herb).hasFailed()) return@queue
        inv.remove(cup)
        inv.add(Items.RUINED_HERB_TEA)
        player.filterableMessage("The herb tea is ruined.")
    }
}

/** Adds [first], then every other herb the cup still needs that is in the inventory (wiki: "automatically create a 3 dose Guthix Rest"). */
fun brewAll(
    player: gg.rsmod.game.model.entity.Player,
    cup: Int,
    start: Brew,
    first: Int,
) {
    val inv = player.inventory
    if (!inv.contains(cup) || inv.remove(first).hasFailed()) return
    inv.remove(cup)
    var brew = start.with(first)
    player.addXp(Skills.HERBLORE, HERB_XP[brew.herbs - 1])
    for (herb in HERBS) {
        while (!brew.ruinedBy(herb) && inv.contains(herb)) {
            inv.remove(herb)
            brew = brew.with(herb)
            player.addXp(Skills.HERBLORE, HERB_XP[brew.herbs - 1])
        }
    }
    inv.add(ITEM_OF_BREW.getValue(brew))
    player.filterableMessage(if (brew == FULL_BREW) "You make a Guthix rest tea." else "You add the herb to the tea.")
}
