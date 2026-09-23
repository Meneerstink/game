package gg.rsmod.plugins.content.areas.draynor

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Requirement
import gg.rsmod.plugins.api.cfg.SkillRequirement
import gg.rsmod.plugins.content.items.osrs.DizanasQuiver
import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards

/**
 * Contains the requirements needed to interact with Ava, as well as her shop inventory and dialogue options.
 *
 * @author Alycia <https://github.com/alycii>
 */
val requirements =
    listOf<Requirement>(
        SkillRequirement(skill = Skills.RANGED, level = 30),
        SkillRequirement(skill = Skills.WOODCUTTING, level = 35),
        SkillRequirement(skill = Skills.CRAFTING, level = 19),
        SkillRequirement(skill = Skills.SLAYER, level = 18),
    )

/**
 * Ava's Odds and Ends (Trade). OSRS Wiki "Ava's Odds and Ends" base stock: feather 1000, iron arrow 40, steel arrow 10,
 * iron arrowtips 30, steel arrowtips 20 (the feather pack is not in the 667 cache).
 */
create_shop(
    "Ava's Odds and Ends",
    currency = CoinCurrency(),
    purchasePolicy = PurchasePolicy.BUY_STOCK,
    containsSamples = false,
) {
    items[0] = ShopItem(Items.FEATHER, 1000)
    items[1] = ShopItem(Items.IRON_ARROW, 40)
    items[2] = ShopItem(Items.STEEL_ARROW, 10)
    items[3] = ShopItem(Items.IRON_ARROWTIPS, 30)
    items[4] = ShopItem(Items.STEEL_ARROWTIPS, 20)
}

/**
 * Ava's devices (Devices / "Buy device"). OSRS Wiki "Ava": a replacement device costs 999 coins; the accumulator also
 * takes 75 steel arrows and needs 50 Ranged. Sold through a real shop interface instead of the old chat purchase.
 */
val AVAS_DEVICES = "Ava's Devices"

create_shop(
    AVAS_DEVICES,
    currency =
        gg.rsmod.plugins.content.mechanics.shops.RequirementCoinCurrency(
            mapOf(
                Items.AVAS_ACCUMULATOR to
                    gg.rsmod.plugins.content.mechanics.shops.PurchaseRule(
                        check = { p ->
                            if (p.skills.getCurrentLevel(Skills.RANGED) < 50) "You need a Ranged level of 50 to use the accumulator." else null
                        },
                        materials = listOf(Items.STEEL_ARROW to 75),
                    ),
            ),
        ),
    purchasePolicy = PurchasePolicy.BUY_NONE,
    containsSamples = false,
) {
    items[0] = ShopItem(Items.AVAS_ATTRACTOR, 10, sellPrice = 999)
    items[1] = ShopItem(Items.AVAS_ACCUMULATOR, 10, sellPrice = 999)
}

on_npc_option(npc = Npcs.AVA, option = "trade") {
    if (!checkRequirements(player)) {
        return@on_npc_option
    }
    player.openShop("Ava's Odds and Ends")
}

on_npc_option(npc = Npcs.AVA, option = "talk-to") {
    if (!checkRequirements(player)) {
        return@on_npc_option
    }
    player.queue {
        chatNpc(
            *"Hello there and welcome to my humble abode. It's sadly rather more humble than I'd like, to be honest, although perhaps you can help with that?"
                .splitForDialogue(),
        )
        chatPlayer(
            *"I would be happy to make your home a better place."
                .splitForDialogue(),
        )
        chatNpc(
            *"Yay, I didn't even have to talk about a reward; you're more gullible than most adventurers, that's for sure."
                .splitForDialogue(),
        )
        when (options("Could I buy one of your devices?", "Can I see your odds and ends?", "Never mind.")) {
            FIRST_OPTION -> player.openShop(AVAS_DEVICES)
            SECOND_OPTION -> player.openShop("Ava's Odds and Ends")
        }
    }
}

on_npc_option(npc = Npcs.AVA, option = "buy device") {
    if (!checkRequirements(player)) {
        return@on_npc_option
    }
    player.openShop(AVAS_DEVICES)
}

// Dragon Slayer II unlocks Ava's real assembler upgrade route. The exact materials are the
// cache-backed OSRS route: Vorkath's head, 75 mithril arrows, and either an accumulator or 4,999 coins.
on_item_on_npc(item = Items.VORKATHS_HEAD, npc = Npcs.AVA) {
    if (player.attr[UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] != true) {
        player.message("You must complete Dragon Slayer II before Ava can create an assembler for you.")
        return@on_item_on_npc
    }
    if (player.skills.getCurrentLevel(Skills.RANGED) < 70) {
        player.message("You need at least 70 Ranged to use Ava's assembler.")
        return@on_item_on_npc
    }
    val hasAccumulator = player.inventory.contains(Items.AVAS_ACCUMULATOR)
    val hasCoins = player.inventory.getItemCount(Items.COINS_995) >= 4_999
    if (player.inventory.getItemCount(Items.MITHRIL_ARROW) < 75 || (!hasAccumulator && !hasCoins)) {
        player.message("You need Vorkath's head, 75 mithril arrows and an Ava's accumulator or 4,999 coins.")
        return@on_item_on_npc
    }
    player.inventory.remove(Items.VORKATHS_HEAD)
    player.inventory.remove(Items.MITHRIL_ARROW, 75)
    if (hasAccumulator) player.inventory.remove(Items.AVAS_ACCUMULATOR) else player.inventory.remove(Items.COINS_995, 4_999)
    player.inventory.add(Items.AVAS_ASSEMBLER)
    player.message("Ava upgrades your device into an Ava's assembler.")
}

/**
 * OSRS Wiki "Dizana's quiver" (re-read 2026-09-17c, quoted in `DizanasQuiver`'s class doc): bringing Ava's assembler, Ava's
 * accumulator or a max cape equivalent to Ava applies that device's ammunition-saving effect to every quiver the player owns, now
 * and in the future, without consuming the device; only an assembler gives the assembler effect. Animal Magnetism is not enforced
 * (owner decision; no quest system in this cache). SOURCE_GAP: Ava's dialogue is not on the page - a plain game message is used.
 */
DizanasQuiver.AVA_UPGRADE_DEVICES.forEach { deviceId ->
    on_item_on_npc(item = deviceId, npc = Npcs.AVA) {
        val offered = DizanasQuiver.effectOf(deviceId)
        val current = DizanasQuiver.avaEffect(player)
        if (current != null && current.ordinal >= offered.ordinal) {
            player.message("Your Dizana's quivers already have this ammunition-saving effect.")
            return@on_item_on_npc
        }
        player.attr[DizanasQuiver.AVA_EFFECT] = offered.name
        player.message("Ava applies the ammunition-saving effect of her device to every Dizana's quiver you own.")
    }
}

/**
 * Checks if the player meets the requirements to purchase an item from the shop.
 *
 * @param player The player whose requirements need to be checked.
 * @return {@code true} if the player meets all the requirements, {@code false} otherwise.
 */
fun checkRequirements(player: Player): Boolean {
    val meetsRequirement = requirements.all { it.hasRequirement(player) }
    if (!meetsRequirement) {
        player.message("She doesn't seem interested in talking to you.")
        player.message("You'll need 30 Ranged, 35 Woodcutting, 19 Crafting and 18 Slayer to get her attention.")
        return false
    }
    return true
}
