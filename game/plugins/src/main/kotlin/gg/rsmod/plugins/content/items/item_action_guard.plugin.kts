package gg.rsmod.plugins.content.items

/*
 * Installs the shared item-option guard (ItemActionGuard): an "Are you sure" item GUI before every Uncharge / Dismantle /
 * Disassemble / Revert, and the item dialogue for every Check / Check-charges / Inspect (owner 2026-09-18).
 */
ItemActionGuard.install(world.plugins)

// A charged weapon created without charges (spawn, shop, Grand Exchange, reward) is its uncharged OSRS item (ChargedItems).
gg.rsmod.game.model.container.ItemContainer.creationRedirect = { id -> gg.rsmod.plugins.content.items.osrs.ChargedItems.unchargedFor(id) }

// Owner 2026-09-19 "Craw's bow now also has the uncharge option": items created BEFORE the redirect existed (saved inventory, worn
// equipment, bank) are still the charged id without any charge attribute - an uncharged bow wearing the charged bow's menu
// (Wield / Check / Uncharge). On login every such attribute-less charged item becomes its uncharged OSRS item, same rule as above.
on_login {
    listOf(player.inventory, player.equipment, player.bank).forEach { container ->
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            if (item.hasAnyAttr()) continue
            val uncharged = gg.rsmod.plugins.content.items.osrs.ChargedItems.unchargedFor(item.id) ?: continue
            container[slot] = Item(uncharged, item.amount)
        }
    }
}
