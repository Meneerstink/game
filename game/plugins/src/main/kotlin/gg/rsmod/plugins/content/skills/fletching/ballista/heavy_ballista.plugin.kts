package gg.rsmod.plugins.content.skills.fletching.ballista

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.grantOrRefund

/**
 * OSRS Wiki "Heavy ballista" (fetched 2026-09-16): a 3-step assembly, not a single combine - the
 * previous `CombinationData.HEAVY_BALLISTA` entry (removed) crafted all 4 parts in one click, which
 * the owner caught live-testing. Real steps, all requiring 72 Fletching:
 *  1. Ballista limbs + Heavy frame -> Incomplete heavy ballista (30 XP)
 *  2. Incomplete heavy ballista + Ballista spring -> Unstrung heavy ballista (30 XP)
 *  3. Unstrung heavy ballista + Monkey tail -> Heavy ballista (600 XP)
 * 30 + 30 + 600 = 660 XP total, matching the wiki's "72 Fletching and grants 660 experience."
 */
val LEVEL_REQUIRED = 72

on_item_on_item(item1 = Items.BALLISTA_LIMBS, item2 = Items.HEAVY_FRAME) {
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < LEVEL_REQUIRED) {
        player.message("You need a Fletching level of $LEVEL_REQUIRED to attach the limbs to the frame.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.BALLISTA_LIMBS, 1, assureFullRemoval = true)
    player.inventory.remove(Items.HEAVY_FRAME, 1, assureFullRemoval = true)
    if (!player.grantOrRefund(
            Item(Items.INCOMPLETE_HEAVY_BALLISTA, 1),
            listOf(Item(Items.BALLISTA_LIMBS, 1), Item(Items.HEAVY_FRAME, 1)),
        )
    ) {
        return@on_item_on_item
    }
    player.addXp(Skills.FLETCHING, 30.0)
    player.filterableMessage("You attach the limbs to the frame.")
}

on_item_on_item(item1 = Items.INCOMPLETE_HEAVY_BALLISTA, item2 = Items.BALLISTA_SPRING) {
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < LEVEL_REQUIRED) {
        player.message("You need a Fletching level of $LEVEL_REQUIRED to attach the spring.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.INCOMPLETE_HEAVY_BALLISTA, 1, assureFullRemoval = true)
    player.inventory.remove(Items.BALLISTA_SPRING, 1, assureFullRemoval = true)
    if (!player.grantOrRefund(
            Item(Items.UNSTRUNG_HEAVY_BALLISTA, 1),
            listOf(Item(Items.INCOMPLETE_HEAVY_BALLISTA, 1), Item(Items.BALLISTA_SPRING, 1)),
        )
    ) {
        return@on_item_on_item
    }
    player.addXp(Skills.FLETCHING, 30.0)
    player.filterableMessage("You attach the spring to the ballista.")
}

on_item_on_item(item1 = Items.UNSTRUNG_HEAVY_BALLISTA, item2 = Items.MONKEY_TAIL) {
    if (player.skills.getCurrentLevel(Skills.FLETCHING) < LEVEL_REQUIRED) {
        player.message("You need a Fletching level of $LEVEL_REQUIRED to attach the monkey tail.")
        return@on_item_on_item
    }
    player.inventory.remove(Items.UNSTRUNG_HEAVY_BALLISTA, 1, assureFullRemoval = true)
    player.inventory.remove(Items.MONKEY_TAIL, 1, assureFullRemoval = true)
    if (!player.grantOrRefund(
            Item(Items.HEAVY_BALLISTA, 1),
            listOf(Item(Items.UNSTRUNG_HEAVY_BALLISTA, 1), Item(Items.MONKEY_TAIL, 1)),
        )
    ) {
        return@on_item_on_item
    }
    player.addXp(Skills.FLETCHING, 600.0)
    player.filterableMessage("You attach the monkey tail, completing the heavy ballista.")
}
