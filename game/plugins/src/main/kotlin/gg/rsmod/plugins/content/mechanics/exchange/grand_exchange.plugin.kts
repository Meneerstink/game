package gg.rsmod.plugins.content.mechanics.exchange

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.plugins.content.combat.isBeingAttacked
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
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

/**
 * Audit E-02: the chat commands used to work anywhere, so an item could be pulled into GE escrow
 * mid-fight (out of death risk) and collected after respawning. Like the retail GE they now only
 * work from a guarded (safe) zone, out of combat, while not locked or dying.
 */
fun geCommandBlocked(player: Player): Boolean {
    val reason =
        when {
            player.attr[DEATH_FLAG] == true || player.isLocked() -> "You can't do that right now."
            AreaState.isDangerous(player.zoneTile()) -> "You can only use the Grand Exchange from a safe zone."
            player.isBeingAttacked() -> "You can't use the Grand Exchange while in combat."
            else -> return false
        }
    geMsg(player, reason)
    return true
}

fun geItemName(
    player: Player,
    itemId: Int,
): String? = player.world.definitions.getNullable(ItemDef::class.java, itemId)?.name

on_command("ge_sell") {
    if (geCommandBlocked(player)) return@on_command
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
    if (service.freeSlot(geUsername(player)) == null) {
        geMsg(player, "All ${GrandExchangeService.SLOTS} of your Grand Exchange offer slots are in use.")
        return@on_command
    }
    // Debit the stock up front so it can never be sold twice - the offer's
    // escrow is now the only place these units exist.
    val removed = player.inventory.remove(item = itemId, amount = quantity, assureFullRemoval = true)
    if (removed.completed < quantity) {
        geMsg(player, "You don't have $quantity x $name to sell.")
        return@on_command
    }
    // Audit E-08: player save (with a pending marker) before the book write, see GeEscrow.
    val (offer, fills) =
        GeEscrow.place(player, listOf(itemId to quantity), restore = { player.inventory.add(itemId, quantity) }) { token ->
            service.submit(geUsername(player), OfferType.SELL, itemId, price, quantity, escrowToken = token)
        } ?: return@on_command
    GrandExchangeInterface.announceFills(player.world, service, fills)
    geMsg(
        player,
        "Placed sell offer #${offer.id}: $quantity x $name @ ${DecimalFormat().format(price)} gp each. " +
            "Use ::ge_collect to withdraw proceeds as they fill.",
    )
}

on_command("ge_buy") {
    if (geCommandBlocked(player)) return@on_command
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
    if (service.freeSlot(geUsername(player)) == null) {
        geMsg(player, "All ${GrandExchangeService.SLOTS} of your Grand Exchange offer slots are in use.")
        return@on_command
    }
    // Escrow the full cost up front at the buyer's own listed price; any
    // price-improvement difference is refunded automatically on fill.
    val removed = player.inventory.remove(item = Items.COINS_995, amount = totalCost.toInt(), assureFullRemoval = true)
    if (removed.completed < totalCost) {
        geMsg(player, "You don't have ${DecimalFormat().format(totalCost)} gp to place that offer.")
        return@on_command
    }
    // Audit E-08: player save (with a pending marker) before the book write, see GeEscrow.
    val (offer, fills) =
        GeEscrow.place(player, listOf(Items.COINS_995 to totalCost.toInt()), restore = { player.inventory.add(Items.COINS_995, totalCost.toInt()) }) { token ->
            service.submit(geUsername(player), OfferType.BUY, itemId, price, quantity, escrowToken = token)
        } ?: return@on_command
    GrandExchangeInterface.announceFills(player.world, service, fills)
    geMsg(
        player,
        "Placed buy offer #${offer.id}: $quantity x $name @ ${DecimalFormat().format(price)} gp each. " +
            "Use ::ge_collect to withdraw items as they fill.",
    )
}

on_command("ge_cancel") {
    if (geCommandBlocked(player)) return@on_command
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

/*
 * The payout itself lives in GrandExchangeCollection, because the bank booths' own `Collect`
 * option has to pay out exactly the same way this command does.
 */
on_command("ge_collect") {
    if (geCommandBlocked(player)) return@on_command
    val service = geService(player) ?: return@on_command
    val offerId = player.getCommandArgs().getOrNull(0)?.toLongOrNull()
    val outcome = GrandExchangeCollection.collect(player, service, offerId)
    GrandExchangeCollection.describe(outcome).forEach { geMsg(player, it) }
}
