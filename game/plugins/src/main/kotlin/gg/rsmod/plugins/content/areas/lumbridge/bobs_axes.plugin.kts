package gg.rsmod.plugins.content.areas.lumbridge

import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import gg.rsmod.plugins.content.items.armor.BarrowsRepair
import gg.rsmod.plugins.content.areas.godwars.FrozenKey

create_shop("Bob's Brilliant Axes", currency = CoinCurrency(), purchasePolicy = PurchasePolicy.BUY_STOCK) {
    sampleItems[0] = ShopItem(Items.BRONZE_PICKAXE, 1, resupplyCycles = 1000)
    sampleItems[1] = ShopItem(Items.BRONZE_HATCHET, 1, resupplyCycles = 1000)
    items[0] = ShopItem(Items.BRONZE_PICKAXE, 10)
    items[1] = ShopItem(Items.BRONZE_HATCHET, 10)
    items[2] = ShopItem(Items.IRON_HATCHET, 10)
    items[3] = ShopItem(Items.STEEL_HATCHET, 10)
    items[4] = ShopItem(Items.IRON_BATTLEAXE, 10)
    items[5] = ShopItem(Items.STEEL_BATTLEAXE, 10)
    items[6] = ShopItem(Items.MITHRIL_BATTLEAXE, 10)
}

on_npc_option(Npcs.BOB, "trade") {
    player.openShop("Bob's Brilliant Axes")
}

on_npc_option(Npcs.BOB, option = "talk-to") {
    player.queue { optionsDialogue(this) }
}

// N560 (owner 2026-11-02: a broken Eclipse moon piece used on Bob said "Nothing interesting happens"): OSRS Wiki "Eclipse moon
// chestplate" / Barrows equipment - "Having an NPC such as Bob ... repair it" - using a Barrows or Moon piece (any stage, broken
// included) on Bob opens the same repair as "Can you repair my items for me?"; the frozen key is recharged the same way.
gg.rsmod.plugins.content.items.armor.CombatPoints.allIds().forEach { id ->
    on_item_on_npc(item = id, npc = Npcs.BOB) {
        player.queue { BarrowsRepair.repair(this) }
    }
}
on_item_on_npc(item = Items.FROZEN_KEY_20120, npc = Npcs.BOB) {
    player.queue { if (FrozenKey.rechargeSlot(player) != null) FrozenKey.recharge(this) else BarrowsRepair.repair(this) }
}

suspend fun quest(task: QueueTask) {
    task.chatNpc("Get yer own!")
}

suspend fun shop(task: QueueTask) {
    task.chatPlayer("Have you anything to sell?")
    task.chatNpc("Yes! I buy and sell axes! Take your pick (or axe)!")
    task.player.openShop("Bob's Brilliant Axes")
}

suspend fun optionsDialogue(task: QueueTask) {
    when (task.options("Give me a quest!", "Have you anything to sell?", "Can you repair my items for me?")) {
        1 -> {
            quest(task)
        }
        2 -> {
            shop(task)
        }
        3 -> {
            // Bob (smith), 2011 wiki: repairs Barrows equipment and recharges the frozen key (FrozenKey).
            // PvpDeathBreakables repair (Avernic defender, Infernal cape, imbued/assembler/Masori/Dizana's
            // capes) moved to Perdu at the Grand Exchange - she is a real import (RCV-012 "ferox" batch,
            // local npc 14394), not absent, so this no longer needs to be fronted through Bob.
            if (FrozenKey.rechargeSlot(task.player) != null) {
                FrozenKey.recharge(task)
            } else {
                BarrowsRepair.repair(task)
            }
        }
    }
}
