import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getInteractingItemId
import gg.rsmod.plugins.api.ext.getInteractingOtherItemId
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag

register_container_key(LootingBag.KEY)

on_item_option(Items.LOOTING_BAG, "Open") {
    LootingBag.setOpen(player, true)
    LootingBag.check(player)
}

listOf(Items.LOOTING_BAG, Items.LOOTING_BAG_OPEN).forEach { bagId ->
    if (bagId == Items.LOOTING_BAG_OPEN) {
        on_item_option(bagId, "Close") {
            LootingBag.setOpen(player, false)
        }
    }
    on_item_option(bagId, "Check") {
        LootingBag.check(player)
    }
    on_item_option(bagId, "Deposit") {
        LootingBag.depositAtBank(player)
    }
    on_item_option(bagId, "Settings") {
        player.queue {
            when (options("Store 1", "Store 5", "Store 10", "Store all", title = "Looting bag settings")) {
                1 -> LootingBag.setMode(player, 1)
                2 -> LootingBag.setMode(player, 5)
                3 -> LootingBag.setMode(player, 10)
                4 -> LootingBag.setMode(player, 0)
            }
        }
    }
    on_item_option(bagId, "Destroy") {
        player.queue {
            if (options("Yes, destroy it.", "No, keep it.", title = "Destroy your looting bag?") == 1) {
                LootingBag.destroy(player)
            }
        }
    }
}

// Bind the bag to every currently tradeable cache item so imported OSRS supplies, future cache
// items, and normal gear all follow one shared route.
val tradeableItems = world.definitions.getAllKeys(ItemDef::class.java)
    .filter { id -> !LootingBag.isBag(id) && world.definitions.get(ItemDef::class.java, id).tradeable }
    .toIntArray()

listOf(Items.LOOTING_BAG, Items.LOOTING_BAG_OPEN).forEach { bagId ->
    on_item_on_item(itemUsed = bagId, itemsList = tradeableItems) {
        val first = player.getInteractingItemId()
        val second = player.getInteractingOtherItemId()
        val itemId = when {
            LootingBag.isBag(first) -> second
            LootingBag.isBag(second) -> first
            else -> return@on_item_on_item
        }
        val available = player.inventory.getItemCount(itemId)
        if (available <= 0) return@on_item_on_item
        val amount = LootingBag.mode(player).let { if (it == 0) available else it.coerceAtMost(available) }
        LootingBag.store(player, itemId, amount)
    }
}
