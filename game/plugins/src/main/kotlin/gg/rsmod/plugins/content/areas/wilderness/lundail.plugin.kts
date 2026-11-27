import gg.rsmod.plugins.content.mechanics.shops.CoinCurrency

val lundail = Npcs.LUNDAIL

create_shop(
    "Lundail's Arena-side Rune Shop",
    currency = CoinCurrency(),
    purchasePolicy = PurchasePolicy.BUY_STOCK,
    containsSamples = false,
) {
    items[0] = ShopItem(Items.FIRE_RUNE, amount = 1000, sellPrice = 17, buyPrice = 5)
    items[1] = ShopItem(Items.WATER_RUNE, amount = 1000, sellPrice = 17, buyPrice = 5)
    items[2] = ShopItem(Items.AIR_RUNE, amount = 100, sellPrice = 17, buyPrice = 5)
    items[3] = ShopItem(Items.EARTH_RUNE, amount = 100, sellPrice = 17, buyPrice = 5)
    items[4] = ShopItem(Items.MIND_RUNE, amount = 100, sellPrice = 17, buyPrice = 5)
    items[5] = ShopItem(Items.BODY_RUNE, amount = 100, sellPrice = 16, buyPrice = 4)
    items[6] = ShopItem(Items.NATURE_RUNE, amount = 300, sellPrice = 372, buyPrice = 111)
    items[7] = ShopItem(Items.CHAOS_RUNE, amount = 300, sellPrice = 140, buyPrice = 42)
    items[8] = ShopItem(Items.LAW_RUNE, amount = 100, sellPrice = 378, buyPrice = 113)
    items[9] = ShopItem(Items.COSMIC_RUNE, amount = 100, sellPrice = 232, buyPrice = 69)
    items[10] = ShopItem(Items.DEATH_RUNE, amount = 300, sellPrice = 310, buyPrice = 93)
}

on_npc_option(lundail, option = "talk-to") {
    player.queue {
        lundailDialogue(this)
    }
}

on_npc_option(lundail, option = "trade") {
    player.openShop("Lundail's Arena-side Rune Shop")
}

suspend fun lundailDialogue(it: QueueTask) {
    it.chatNpc("How can I help you, brave adventurer?")
    val option = it.options("What are you selling?", "What's that big old building above us?", "Claim free runes.")
    when (option) {
        1 -> whatAreYouSelling(it)
        2 -> whatsThatBigOldBuilding(it)
        // OSRS Wiki Transcript:Lundail + Wilderness Diary rewards (DIARIES-OSRS D6): 40 / 80 / 120 / 200 random runes a day for the
        // claimed easy / medium / hard / elite tier, from body, mind, nature, chaos, law, cosmic and death runes; once per UTC day.
        3 -> claimRunes(it)
    }
}

val LUNDAIL_DAY = gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "lundail_free_runes_day")
val FREE_RUNES = intArrayOf(559, 558, 561, 562, 563, 564, 560)

suspend fun claimRunes(it: QueueTask) {
    val player = it.player
    val tiers = gg.rsmod.plugins.content.diaries.Diaries.claimed(player, gg.rsmod.plugins.content.diaries.Diaries.Diary.WILDERNESS)
    if (tiers == 0) {
        it.chatNpc("You'll need to have completed the Easy Wilderness Achievement Diary to qualify for free runes.")
        return
    }
    val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay().toInt()
    if (player.attr[LUNDAIL_DAY] == today) {
        it.chatNpc("I've already given you your runes for today. Come back tomorrow.")
        return
    }
    val amount = intArrayOf(40, 80, 120, 200)[tiers - 1]
    val counts = IntArray(FREE_RUNES.size)
    repeat(amount) { counts[player.world.random(FREE_RUNES.size - 1)]++ }
    val kinds = counts.count { c -> c > 0 }
    if (player.inventory.freeSlotCount < kinds && FREE_RUNES.indices.any { i -> counts[i] > 0 && !player.inventory.contains(FREE_RUNES[i]) }) {
        it.chatNpc("You'll need some more free space in your pack for these.")
        return
    }
    player.attr[LUNDAIL_DAY] = today
    FREE_RUNES.forEachIndexed { i, rune -> if (counts[i] > 0) player.inventory.add(rune, counts[i]) }
    it.chatNpc("Here you go: $amount runes, as promised to those who know the Wilderness.")
}

suspend fun whatAreYouSelling(it: QueueTask) {
    it.chatPlayer("What are you selling?")
    it.chatNpc(
        "I sell rune stones. I've got some good stuff,",
        "some really powerful little rocks. Take a look.",
    )
    it.player.openShop("Lundail's Arena-side Rune Shop")
}

suspend fun whatsThatBigOldBuilding(it: QueueTask) {
    it.chatPlayer("What's that big old building above us?")
    it.chatNpc(
        "That, my friend, is the mage battle arena.",
        "Top mages come from all over Gielinor to compete",
        "in the arena.",
    )
    it.chatPlayer("Wow.")
    it.chatNpc("Few return, most get fried, hence the smell.")
    it.chatPlayer("Hmmm.. I did notice.")
    // End of dialogue
}
