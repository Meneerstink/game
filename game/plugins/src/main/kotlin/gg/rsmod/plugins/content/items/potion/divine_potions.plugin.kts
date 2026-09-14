package gg.rsmod.plugins.content.items.potion

/**
 * OSRS-IMPORT potions-combat: the divine potion five-minute expiry and the Super combat potion recipe (rules and sources in
 * [DivinePotions]). ADAPTED: the recipe message.
 */

DivinePotions.TIMERS.forEach { (skill, timer) ->
    on_timer(timer) {
        DivinePotions.expire(player, skill)
    }
}

// "A super combat potion is a potion made by using either a torstol or a torstol potion (unf) on any 4-dose of: super attack, super strength,
// or super defence, while having one 4-dose of each in the player's inventory. This requires 90 Herblore, and yields a super combat (4) and
// 150 Herblore experience."
val superFours = listOf(Items.SUPER_ATTACK_4, Items.SUPER_STRENGTH_4, Items.SUPER_DEFENCE_4)
listOf(Items.CLEAN_TORSTOL, Items.TORSTOL_POTION_UNF).forEach { torstol ->
    superFours.forEach { potion ->
        on_item_on_item(item1 = torstol, item2 = potion) {
            if (player.skills.getCurrentLevel(Skills.HERBLORE) < 90) {
                player.message("You need a Herblore level of 90 to make a super combat potion.")
                return@on_item_on_item
            }
            if (!player.inventory.contains(torstol) || superFours.any { !player.inventory.contains(it) }) {
                player.message("You need a four-dose super attack, super strength and super defence potion to do that.")
                return@on_item_on_item
            }
            player.inventory.remove(torstol, 1)
            superFours.forEach { player.inventory.remove(it, 1) }
            // SOURCE_GAP: the page does not say whether the two spare vials are returned; none are added.
            player.inventory.add(Items.SUPER_COMBAT_POTION_4, 1)
            player.addXp(Skills.HERBLORE, 150.0)
        }
    }
}
