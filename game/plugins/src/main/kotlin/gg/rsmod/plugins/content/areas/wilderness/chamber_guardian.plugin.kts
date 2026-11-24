package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.prayedAtStatue
import gg.rsmod.game.model.attr.receivedStaff
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency


val chamberGuardian = Npcs.CHAMBER_GUARDIAN

create_shop(
    "Mage Arena Staves",
    currency = CoinCurrency(),
    purchasePolicy = PurchasePolicy.BUY_NONE,
    containsSamples = false,
) {
    // OSRS Wiki Mage Arena Staffs.: five each, 100-tick restock, no purchases from players.
    items[0] = ShopItem(Items.SARADOMIN_STAFF, amount = 5, sellPrice = 80000, resupplyCycles = 100)
    items[1] = ShopItem(Items.GUTHIX_STAFF, amount = 5, sellPrice = 80000, resupplyCycles = 100)
    items[2] = ShopItem(Items.ZAMORAK_STAFF, amount = 5, sellPrice = 80000, resupplyCycles = 100)
}

on_npc_option(chamberGuardian, option = "talk-to") {
    player.queue {
        if (MageArena.completed(player) && player.attr[prayedAtStatue] == true) {
            if (player.attr[receivedStaff] == true) {
                afterClaimingFreeStaffDialogue(this)
            } else {
                afterPrayingAtStatueDialogue(this)
            }
        } else {
            beforePrayingAtStatueDialogue(this)
        }
    }
}

on_npc_option(chamberGuardian, option = "trade") {
    if (MageArena.completed(player)) player.openShop("Mage Arena Staves")
}

suspend fun beforePrayingAtStatueDialogue(it: QueueTask) {
    it.chatPlayer("Hello my friend, Kolodion sent me down.")
    it.chatNpc(
        "Sssshhh... the gods are talking. I can hear their whispers.",
        "Can you hear them adventurer, they're calling you.",
    )
    it.chatPlayer("Erm... ok!")
    it.chatNpc(
        "Go chant at the statue of the god you most wish to",
        "represent in this world, you will be rewarded. Once",
        "you are done, come back to me. I shall supply you",
        "with a mage staff ready for battle.",
    )
}

suspend fun afterPrayingAtStatueDialogue(it: QueueTask) {
    it.chatPlayer("Hi.")
    it.chatNpc("Hello adventurer, have you made your choice?")
    it.chatPlayer("I have.")
    it.chatNpc(
        "Good, good, I hope you have chosen well. I will now",
        "present you with a magic staff. This, along with the",
        "cape awarded to you by your chosen god, are all the",
        "weapons and armour you will need here.",
    )
    when (MageArena.claimFirstStaff(it.player)) {
        MageArena.StaffResult.GRANTED -> it.messageBox("The guardian hands you an ornate magic staff.")
        MageArena.StaffResult.NO_CAPE -> it.chatNpc("Once you have your cape come back to me,", "and I shall supply you with a mage staff ready for battle.")
        MageArena.StaffResult.INVENTORY_FULL -> it.player.message("Not enough space in your inventory.")
        MageArena.StaffResult.ALREADY_CLAIMED -> afterClaimingFreeStaffDialogue(it)
        MageArena.StaffResult.NOT_COMPLETED -> beforePrayingAtStatueDialogue(it)
    }
}

suspend fun afterClaimingFreeStaffDialogue(it: QueueTask) {
    it.chatPlayer("Hello again.")
    it.chatNpc("Hello adventurer, are you looking for another staff?")
    val option = it.options("What do you have to offer?", "No thanks.")
    when (option) {
        1 -> {
            it.player.openShop("Mage Arena Staves")
        }
        2 -> {
            it.chatPlayer("No thanks.")
            it.chatNpc("Well let me know if you need one.")
        }
    }
}
