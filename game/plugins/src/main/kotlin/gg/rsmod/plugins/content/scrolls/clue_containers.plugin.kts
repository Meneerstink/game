package gg.rsmod.plugins.content.scrolls

ClueContainers.boxes.forEach { (tier, osrs) ->
    ClueItems.local(osrs)?.let { id ->
        set_ground_item_condition(id) { ClueContainers.mayTakeBox(player, tier) }
        set_ground_item_pickup_amount(id) { player, amount -> ClueContainers.pickupAmount(player, tier, amount) }
        on_item_option(id, "open") {
            ClueContainers.openBox(player, player.getInteractingItemSlot(), player.getInteractingItem(), tier)
        }
    }
}
ClueContainers.cases.forEach { case ->
    ClueItems.local(case.osrs)?.let { id ->
        set_ground_item_condition(id) {
            val allowed = !ClueContainers.opened(player, case) && !ClueContainers.held(player, id)
            if (!allowed) player.message("You've already obtained this scroll case!")
            allowed
        }
        on_item_option(id, "open") { ClueContainers.openCase(player, player.getInteractingItemSlot(), player.getInteractingItem(), case) }
    }
}
