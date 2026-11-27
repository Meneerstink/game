package gg.rsmod.plugins.content.items.food

Food.values.forEach { food ->
    on_item_option(item = food.item, option = "eat") {
        if (!Foods.canEat(player, food)) {
            return@on_item_option
        }
        if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.EAT,
            )
        ) {
            return@on_item_option
        }

        val inventorySlot = player.getInteractingItemSlot()
        if (player.inventory[inventorySlot]?.id != food.item) return@on_item_option
        if (player.inventory.remove(item = food.item, beginSlot = inventorySlot).hasSucceeded()) {
            Foods.eat(player, food)
            gg.rsmod.plugins.content.diaries.Diaries.event(player, "eat:${world.definitions.get(ItemDef::class.java, food.item).name.lowercase()}") // DIARIES-OSRS Karamja hard 3
            if (food == Food.CABBAGE) gg.rsmod.plugins.content.scrolls.TreasureTrailGuardianDialogue.ateCabbage(player)
            if (food.replacement != -1) {
                player.inventory.add(item = food.replacement, beginSlot = inventorySlot)
            }
            // PROJECT48 S13 Food Master: a saved food stays in its slot.
            else if (gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilConsumables.keepsFood(player)) player.inventory.add(item = food.item, beginSlot = inventorySlot)
        }
    }
}

InedibleFoods.refusals.forEach { (id, message) ->
    on_item_option(id, "Eat") {
        if (player.inventory[player.getInteractingItemSlot()]?.id == id) player.message(message)
    }
}
