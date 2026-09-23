package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item

/**
 * Grants [product] to this player; if it doesn't fit, refunds every stack in [consumed] (materials
 * already removed for this crafting attempt) instead of silently destroying them, and tells the
 * player why. Returns whether [product] was actually granted.
 *
 * Bug fix (owner-reported, 2026-09-16): every "remove materials, then add the crafted product"
 * action in this codebase (javelin/bolt tipping, crossbow assembly/stringing, the generic
 * multi-item combine engine, log whittling) called `inventory.add(product, ...)` without checking
 * whether it actually succeeded. With a full inventory, the materials were removed and the product
 * then silently failed to be added - the stack just vanished, with no message and no refund. This
 * helper is the one shared fix for that whole class of site, not a per-item patch.
 */
fun Player.grantOrRefund(
    product: Item,
    consumed: List<Item>,
): Boolean {
    val grant = inventory.add(product, assureFullInsertion = true)
    if (grant.hasSucceeded()) {
        return true
    }
    consumed.forEach { inventory.add(it, assureFullInsertion = true) }
    filterableMessage("You don't have enough inventory space to do that.")
    return false
}

fun Player.grantOrRefund(
    productId: Int,
    amount: Int,
    consumed: List<Item>,
): Boolean = grantOrRefund(Item(productId, amount), consumed)
