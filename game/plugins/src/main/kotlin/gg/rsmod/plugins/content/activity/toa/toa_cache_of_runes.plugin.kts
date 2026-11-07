package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.api.cfg.Items

/*
 * N462 Cache of runes (ToA common, OSRS 27293): "Open" did nothing. OSRS Wiki Cache of runes (Rewards, after the 25 June 2025 Summer Sweep
 * Up: Combat change): one roll, raid level and points play no part -
 *   death / blood / soul rune 500-1000 at 6/30 each, 1000-1500 at 3/30 each, 3000 at 1/30 each.
 * ONZEKER: the open message is not in our sources; the runes appear without a message.
 */
private data class CacheLine(val rune: Int, val min: Int, val max: Int, val weight: Int)

private val CACHE_LINES = listOf(Items.DEATH_RUNE, Items.BLOOD_RUNE, Items.SOUL_RUNE).flatMap { rune ->
    listOf(CacheLine(rune, 500, 1000, 6), CacheLine(rune, 1000, 1500, 3), CacheLine(rune, 3000, 3000, 1))
}

ToaIds.item(27293)?.let { cache ->
    on_item_option(item = cache, option = "open") {
        val slot = player.getInteractingItemSlot()
        if (player.inventory[slot]?.id != cache) return@on_item_option
        var pick = world.random(CACHE_LINES.sumOf { it.weight } - 1)
        val line = CACHE_LINES.first { l -> (pick < l.weight).also { if (!it) pick -= l.weight } }
        val amount = world.random(line.min..line.max)
        // The cache's own slot frees for the runes, so they always fit.
        if (!player.inventory.remove(cache, 1, beginSlot = slot).hasSucceeded()) return@on_item_option
        player.inventory.add(line.rune, amount)
    }
}
