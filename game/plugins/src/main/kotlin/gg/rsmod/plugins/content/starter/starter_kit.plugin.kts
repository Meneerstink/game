package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.attr.CREATION_DATE
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.plugins.content.inter.bank.Bank
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.util.Misc

load_metadata {
    propertyFileName = "starter_kit"

    author = "Tomm"
    name = "Starter Kit"
    description = "Give items to new accounts."

    // Owner 2026-10-08: a new player starts with an empty inventory - no bronze tools, food or runes. His only item is the
    // Deadman starter pack, handed out when the introduction is over (NewPlayerStart.grantStarterPack, TutorIntro.finish).
    properties()
}

on_item_option(item = NewPlayerConfig.current.starterChestItem, option = "open") {
    NewPlayerStart.openStarterPack(player)
}

on_login {
    val newAccount = player.attr[NEW_ACCOUNT_ATTR] ?: return@on_login
    if (newAccount) {
        val inventory = player.getInventoryStarterItems()
        val bank = player.getBankStarterItems()

        inventory.forEach { slotItem ->
            player.inventory.add(item = slotItem.item, beginSlot = slotItem.slot)
        }

        bank.forEach { slotItem ->
            player.bank.add(item = slotItem.item, beginSlot = slotItem.slot)
        }
        check(NewPlayerStart.grantStarterCash(player)) { "Unable to grant new-player starter cash." }
        BeginnerProtection.grantOnFirstLogin(player)

        // 1:1 prayer points (runetek5.plugin.kts): a full bar at his Prayer level. The old 10 here was the 667 x10 unit and
        // gave every new account 10 points at Prayer 1 (owner 2026-10-10: "wrm heeft onze character 10 prayer").
        player.setCurrentPrayerPoints(player.getMaximumPrayerPoints())
        player.setVarp(Bank.LAST_X_INPUT, 50)
        player.attr[CREATION_DATE] = System.currentTimeMillis()

        // Owner 2026-10-07: the old three-line welcome is replaced by the Quest Tutor's camera tour (TutorIntro) - after the
        // starter kit, cash and beginner protection above. It opens the Dangerous-area warning's intro window
        // (DangerWarning.startIntro, owner 2026-09-23) and closes it when the tour ends, is skipped or is interrupted.
        gg.rsmod.plugins.content.quests.foundation.TutorIntro.start(player)

        world.players.entries.filterNotNull().filter { it != player }.forEach {
            val color =
                when (it.interfaces.displayMode) {
                    DisplayMode.FIXED -> "0000ff"
                    DisplayMode.RESIZABLE_NORMAL -> "7fa9ff"
                    else -> "#fa9ff"
                }
            it.message(
                "[<col=d45b5b>Global</col>]: <col=$color>${Misc.formatForDisplay(
                    player.username,
                )} has just logged into 78 for the first time.</col>",
            )
        }
    }
}

fun Player.getInventoryStarterItems() = getStarterItems(inventory.capacity, { getItem }, { getItemAmount })

fun Player.getBankStarterItems() = getStarterItems(bank.capacity, { getBankItem }, { getBankItemAmount })

fun getStarterItems(
    containerCapacity: Int,
    itemProperty: (Int).() -> String,
    amountProperty: (Int).() -> String,
): List<SlotItem> {
    val items = mutableListOf<SlotItem>()
    for (i in 0 until containerCapacity) {
        val item = getProperty<Int>(itemProperty(i)) ?: continue
        val amt = getProperty<Int>(amountProperty(i)) ?: 1
        items.add(SlotItem(i, Item(item, amt)))
    }
    return items
}

val Int.getItem: String
    get() = "item[$this]"

val Int.getItemAmount: String
    get() = "amount[$this]"

val Int.getBankItem: String
    get() = "bank_item[$this]"

val Int.getBankItemAmount: String
    get() = "bank_amount[$this]"
