package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
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
        player.message("Usage: givepoints <player> <donator|deadman|loyalty|breach> <amount>")
        return@on_command
    }
    val amount = args[args.size - 1].toIntOrNull()
    val currencyName = args[args.size - 2]
    val currency = Currency.values().firstOrNull { it.name.equals(currencyName, ignoreCase = true) }
    val breach = currencyName.equals("breach", ignoreCase = true)
    val name = args.dropLast(2).joinToString(" ")
    val target = world.getPlayerForName(name)
    if (amount == null || (currency == null && !breach) || target == null) {
        player.message("Usage: givepoints <player> <donator|deadman|loyalty|breach> <amount> (the player must be online).")
        return@on_command
    }
    if (breach) {
        BreachPoints.addBalance(target, amount)
        val total = BreachPoints.balance(target)
        target.message("You have received ${amount.format()} Breach Points. You now have ${total.format()}.")
        player.message("${target.username} now has ${total.format()} Breach Points.")
        return@on_command
    }
    val total = PointBalance.adjust(target.attr, currency!!.attr, amount.toLong())
    target.message("You have received ${amount.format()} ${currency.plural}. You now have ${total.format()}.")
    player.message("${target.username} now has ${total.format()} ${currency.plural}.")
    if (StoreUi.isOpen(target)) StoreUi.renderPreview(target)
}

/** Owner 2026-09-29: "geef me unlimited deadmanpoints breachpoints loyalty points en donator" - every currency to its maximum. */
on_command("maxpoints", Privilege.ADMIN_POWER) {
    val name = player.getCommandArgs().joinToString(" ")
    val target = if (name.isBlank()) player else world.getPlayerForName(name)
    if (target == null) {
        player.message("Usage: maxpoints [player] (the player must be online).")
        return@on_command
    }
    Currency.values().forEach { PointBalance.set(target.attr, it.attr, PointBalance.MAX.toLong()) }
    PointBalance.set(target.attr, BreachPoints.BALANCE, PointBalance.MAX.toLong())
    target.message("Donator, Deadman, Loyalty and Breach Points set to ${Int.MAX_VALUE.format()}.")
    if (target != player) player.message("${target.username} now has maximum points in every currency.")
    if (StoreUi.isOpen(target)) StoreUi.renderPreview(target)
}

// Every clickable component of either window layout (6x5 legacy, 7x6 since 2026-10-03); StoreUi.click routes by the layout the
// cache holds, so these bindings never point at the wrong component whichever cache is live.
StoreUi.CLICKABLE.forEach { component ->
    on_button(interfaceId = StoreUi.INTERFACE_ID, component = component) {
        StoreUi.click(player, component, player.getInteractingOpcode())
    }
}

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
    // N106: stops at 2,147,483,647, never wraps (the owner's maxpoints account crossed it after ~263 h online).
    PointBalance.add(player.attr, Currency.LOYALTY.attr, LOYALTY_PLAYTIME_POINTS)
    // A reward is not a filterable effect line (lint #msg): it must reach the player with the Game filter on too.
    player.message("You have been awarded $LOYALTY_PLAYTIME_POINTS Loyalty Points for your time online.")
    player.timers[LOYALTY_PLAYTIME_TIMER] = LOYALTY_PLAYTIME_CYCLES
}
