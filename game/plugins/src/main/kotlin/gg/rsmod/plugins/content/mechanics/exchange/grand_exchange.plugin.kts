package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp
import java.text.DecimalFormat

/**
 * Minimal, immediately-usable player-facing Grand Exchange flow: chat
 * commands rather than the retail interface. This ships the full backend
 * loop (offer, match, partial fill, cancel, collect, persistence, system
 * liquidity) end to end now; wiring an actual GE interface/interface-buttons
 * is tracked as a follow-up in IMPLEMENTATION_STATUS.md rather than guessed
 * at blind against this cache's (unverified) component layout.
 */

fun geService(player: Player) = player.world.getService(GrandExchangeService::class.java)

/** Stable account key (lowercase login name), unaffected by display-name changes. */
fun geUsername(player: Player): String = (player as Client).loginUsername

fun geMsg(
    player: Player,
    text: String,
) = player.message(text, type = ChatMessageType.CONSOLE)

fun geItemName(
    player: Player,
    itemId: Int,
): String? = player.world.definitions.getNullable(ItemDef::class.java, itemId)?.name

on_command("ge_sell") {
    if (PracticePvp.isHoldingTempGear(player)) {
        geMsg(player, "You can't use the Grand Exchange while wearing free Practice PvP gear.")
        return@on_command
    }
    val args = player.getCommandArgs()
    if (args.size < 3) {
        geMsg(player, "Usage: ::ge_sell item_id quantity price_per_item")
        return@on_command
    }
    val service = geService(player) ?: return@on_command
    val itemId = args[0].toIntOrNull()
    val quantity = args[1].toIntOrNull()
    val price = args[2].toIntOrNull()
    if (itemId == null || quantity == null || price == null || quantity <= 0 || price <= 0) {
        geMsg(player, "Invalid arguments. Usage: ::ge_sell item_id quantity price_per_item")
        return@on_command
    }
    val name = geItemName(player, itemId)
    if (name == null) {
        geMsg(player, "Item $itemId does not exist.")
        return@on_command
    }
    if (!player.world.definitions.get(ItemDef::class.java, itemId).tradeable) {
        geMsg(player, "$name can't be sold on the Grand Exchange.")
        return@on_command
    }
    // Debit the stock up front so it can never be sold twice - the offer's
    // escrow is now the only place these units exist.
    val removed = player.inventory.remove(item = itemId, amount = quantity, assureFullRemoval = true)
    if (removed.completed < quantity) {
        geMsg(player, "You don't have $quantity x $name to sell.")
        return@on_command
    }
    val (offer, _) = service.submit(geUsername(player), OfferType.SELL, itemId, price, quantity)
    geMsg(
        player,
        "Placed sell offer #${offer.id}: $quantity x $name @ ${DecimalFormat().format(price)} gp each. " +
            "Use ::ge_collect to withdraw proceeds as they fill.",
    )
}

on_command("ge_buy") {
    if (PracticePvp.isHoldingTempGear(player)) {
        geMsg(player, "You can't use the Grand Exchange while wearing free Practice PvP gear.")
        return@on_command
    }
    val args = player.getCommandArgs()
    if (args.size < 3) {
        geMsg(player, "Usage: ::ge_buy item_id quantity price_per_item")
        return@on_command
    }
    val service = geService(player) ?: return@on_command
    val itemId = args[0].toIntOrNull()
    val quantity = args[1].toIntOrNull()
    val price = args[2].toIntOrNull()
    if (itemId == null || quantity == null || price == null || quantity <= 0 || price <= 0) {
        geMsg(player, "Invalid arguments. Usage: ::ge_buy item_id quantity price_per_item")
        return@on_command
    }
    val name = geItemName(player, itemId)
    if (name == null) {
        geMsg(player, "Item $itemId does not exist.")
        return@on_command
    }
    if (!player.world.definitions.get(ItemDef::class.java, itemId).tradeable) {
        geMsg(player, "$name can't be bought on the Grand Exchange.")
        return@on_command
    }
    val totalCost = price.toLong() * quantity
    if (totalCost > Int.MAX_VALUE) {
        geMsg(player, "That offer's total cost is too large.")
        return@on_command
    }
    // Escrow the full cost up front at the buyer's own listed price; any
    // price-improvement difference is refunded automatically on fill.
    val removed = player.inventory.remove(item = Items.COINS_995, amount = totalCost.toInt(), assureFullRemoval = true)
    if (removed.completed < totalCost) {
        geMsg(player, "You don't have ${DecimalFormat().format(totalCost)} gp to place that offer.")
        return@on_command
    }
    val (offer, _) = service.submit(geUsername(player), OfferType.BUY, itemId, price, quantity)
    geMsg(
        player,
        "Placed buy offer #${offer.id}: $quantity x $name @ ${DecimalFormat().format(price)} gp each. " +
            "Use ::ge_collect to withdraw items as they fill.",
    )
}

on_command("ge_cancel") {
    val args = player.getCommandArgs()
    val offerId = args.getOrNull(0)?.toLongOrNull()
    if (offerId == null) {
        geMsg(player, "Usage: ::ge_cancel offer_id")
        return@on_command
    }
    val service = geService(player) ?: return@on_command
    val offer = service.cancel(geUsername(player), offerId)
    if (offer == null) {
        geMsg(player, "No active offer #$offerId found.")
    } else {
        geMsg(player, "Cancelled offer #$offerId. Use ::ge_collect to withdraw anything owed.")
    }
}

on_command("ge_offers") {
    val service = geService(player) ?: return@on_command
    val offers = service.offersFor(geUsername(player))
    if (offers.isEmpty()) {
        geMsg(player, "You have no Grand Exchange offers.")
        return@on_command
    }
    offers.forEach { offer ->
        val name = geItemName(player, offer.itemId) ?: "item ${offer.itemId}"
        geMsg(
            player,
            "#${offer.id} ${offer.type} $name: ${offer.quantityFilled}/${offer.totalQuantity} @ " +
                "${offer.pricePerItem} gp [${offer.status}] " +
                "(owed: ${offer.collectableCoins} gp, ${offer.collectableItems} x $name)",
        )
    }
}

on_command("ge_collect") {
    val args = player.getCommandArgs()
    val service = geService(player) ?: return@on_command
    val targets =
        args.getOrNull(0)?.toLongOrNull()?.let { listOf(it) }
            ?: service.offersFor(geUsername(player)).map { it.id }
    var collectedAny = false
    for (offerId in targets) {
        val owed = service.takeCollectable(geUsername(player), offerId) ?: continue
        val (coins, items) = owed
        var leftoverCoins = coins
        var leftoverItems = items

        if (coins > 0) {
            val amount = minOf(coins, Int.MAX_VALUE.toLong()).toInt()
            val result = player.inventory.add(item = Items.COINS_995, amount = amount)
            leftoverCoins = coins - result.completed
        }
        if (items > 0) {
            val offer = service.offersFor(geUsername(player)).find { it.id == offerId }
            val itemId = offer?.itemId
            if (itemId != null) {
                val result = player.inventory.add(item = itemId, amount = items)
                leftoverItems = items - result.completed
            }
        }
        if (leftoverCoins > 0 || leftoverItems > 0) {
            service.restoreCollectable(offerId, leftoverCoins, leftoverItems)
            geMsg(player, "Not enough inventory space to collect everything from offer #$offerId - try again with space free.")
        } else {
            collectedAny = true
        }
    }
    if (collectedAny) {
        geMsg(player, "Collected your Grand Exchange proceeds.")
    } else if (targets.isEmpty()) {
        geMsg(player, "You have no Grand Exchange offers.")
    }
}
