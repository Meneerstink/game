package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Currency
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Shop

/**
 * The three monetization shops (owner night run 2026-09-19): one window, one catalogue ([StoreCatalogue]), three currencies.
 * Commands (owner types them without "::"): `store`, `donatorstore`, `deadmanstore`, `loyaltystore`, `points`;
 * staff: `givepoints <player> <donator|deadman|loyalty> <amount>` (the webshop hand-off for Donator Points).
 */

on_command("store") { StoreUi.open(player, Shop.DONATOR) }
on_command("donatorstore") { StoreUi.open(player, Shop.DONATOR) }
on_command("deadmanstore") { StoreUi.open(player, Shop.DEADMAN) }
on_command("loyaltystore") { StoreUi.open(player, Shop.LOYALTY) }

on_command("points") {
    Currency.values().forEach { player.message("${it.plural}: ${(player.attr[it.attr] ?: 0).format()}") }
}

on_command("givepoints", Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    if (args.size < 3) {
        player.message("Usage: givepoints <player> <donator|deadman|loyalty> <amount>")
        return@on_command
    }
    val amount = args[args.size - 1].toIntOrNull()
    val currency = Currency.values().firstOrNull { it.name.equals(args[args.size - 2], ignoreCase = true) }
    val name = args.dropLast(2).joinToString(" ")
    val target = world.getPlayerForName(name)
    if (amount == null || currency == null || target == null) {
        player.message("Usage: givepoints <player> <donator|deadman|loyalty> <amount> (the player must be online).")
        return@on_command
    }
    val total = ((target.attr[currency.attr] ?: 0).toLong() + amount).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    target.attr[currency.attr] = total
    target.message("You have received ${amount.format()} ${currency.plural}. You now have ${total.format()}.")
    player.message("${target.username} now has ${total.format()} ${currency.plural}.")
    if (StoreUi.isOpen(target)) StoreUi.renderPreview(target)
}

on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.CLOSE) {
    player.closeInterface(StoreUi.INTERFACE_ID)
}

Shop.values().indices.forEach { tab ->
    on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.TAB_FIRST + tab * StoreUi.TAB_STRIDE) {
        StoreUi.switchShop(player, tab)
    }
}

(0 until StoreUi.SLOT_COUNT).forEach { slot ->
    on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.SLOT_FIRST + slot) {
        if (player.getInteractingOpcode() == 25) {
            StoreUi.slotEntry(player, slot)?.let { world.sendExamine(player, it.purchaseItem, gg.rsmod.game.model.ExamineEntityType.ITEM) }
        } else {
            StoreUi.select(player, slot)
        }
    }
}

on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.PAGE_PREVIOUS) { StoreUi.turnPage(player, -1) }
on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.PAGE_NEXT) { StoreUi.turnPage(player, 1) }
on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.CAROUSEL_PREVIOUS) { StoreUi.turnCarousel(player, -1) }
on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.CAROUSEL_NEXT) { StoreUi.turnCarousel(player, 1) }
on_button(interfaceId = StoreUi.INTERFACE_ID, component = StoreUi.BUY_LAYER) { StoreUi.buy(player) }

/*
 * Loyalty Points from play time (owner: "speeltijd"): PROVISIONAL 50 points per 30 minutes online, on top of the existing daily
 * 500 (daily.plugin.kts). The timer pauses while offline, so idle logins cannot farm it faster.
 */
val LOYALTY_PLAYTIME_TIMER = TimerKey(persistenceKey = "loyalty_playtime", tickOffline = false)
val LOYALTY_PLAYTIME_CYCLES = 3000
val LOYALTY_PLAYTIME_POINTS = 50

on_login {
    if (!player.timers.has(LOYALTY_PLAYTIME_TIMER)) player.timers[LOYALTY_PLAYTIME_TIMER] = LOYALTY_PLAYTIME_CYCLES
}

on_timer(LOYALTY_PLAYTIME_TIMER) {
    player.attr[Currency.LOYALTY.attr] = (player.attr[Currency.LOYALTY.attr] ?: 0) + LOYALTY_PLAYTIME_POINTS
    player.filterableMessage("You have been awarded $LOYALTY_PLAYTIME_POINTS Loyalty Points for your time online.")
    player.timers[LOYALTY_PLAYTIME_TIMER] = LOYALTY_PLAYTIME_CYCLES
}
