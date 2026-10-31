package gg.rsmod.plugins.content.skills.herblore

val grimyHerbDefinitions = HerbData.grimyHerbDefinitions

grimyHerbDefinitions.values.forEach { unf ->
    on_item_option(item = unf.grimy, option = "Clean") {
        cleanHerb(player, unf.grimy)
    }
}

/**
 * N11 (2026-10-31, item-option census): two quest herbs a player can get here had a dead "Clean". They make no unfinished potion, so they
 * stay out of [HerbData] (its unf table); OSRS Wiki Grimy snake weed / Grimy rogue's purse: Herblore 3, 2.5 XP.
 */
val QUEST_HERBS =
    listOf(
        HerbData.Cleaning(Items.GRIMY_SNAKE_WEED, Items.CLEAN_SNAKE_WEED, 3, 2.5),
        HerbData.Cleaning(Items.GRIMY_ROGUES_PURSE, Items.CLEAN_ROGUES_PURSE, 3, 2.5),
    ).associateBy { it.grimy }

QUEST_HERBS.keys.forEach { grimy ->
    on_item_option(item = grimy, option = "Clean") {
        cleanHerb(player, grimy)
    }
}

fun cleanHerb(
    player: Player,
    herb: Int,
) {
    val def = grimyHerbDefinitions[herb]?.let { HerbData.Cleaning(it.grimy, it.clean, it.levelRequirement, it.experience) } ?: QUEST_HERBS[herb] ?: return
    if (player.skills.getCurrentLevel(Skills.HERBLORE) < def.levelRequirement) {
        player.message(
            "You need level ${def.levelRequirement} Herblore to clean the ${player.world.definitions.get(
                ItemDef::class.java,
                def.grimy,
            ).name.lowercase()}.",
        )
        return
    }
    val slot = player.getInteractingItemSlot()
    if (player.inventory.remove(herb, beginSlot = slot, assureFullRemoval = true).hasSucceeded()) {
        player.inventory.add(def.clean, 1, beginSlot = slot, assureFullInsertion = true)
        player.addXp(Skills.HERBLORE, def.experience)
        player.filterableMessage(
            "You clean the dirt from the ${player.world.definitions.get(
                ItemDef::class.java,
                def.grimy,
            ).name.removePrefix("Grimy ")}.",
        )
    }
}
