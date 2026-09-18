package gg.rsmod.plugins.content.items

/*
 * Installs the shared item-option guard (ItemActionGuard): an "Are you sure" item GUI before every Uncharge / Dismantle /
 * Disassemble / Revert, and the item dialogue for every Check / Check-charges / Inspect (owner 2026-09-18).
 */
ItemActionGuard.install(world.plugins)

// A charged weapon created without charges (spawn, shop, Grand Exchange, reward) is its uncharged OSRS item (ChargedItems).
gg.rsmod.game.model.container.ItemContainer.creationRedirect = { id -> gg.rsmod.plugins.content.items.osrs.ChargedItems.unchargedFor(id) }
