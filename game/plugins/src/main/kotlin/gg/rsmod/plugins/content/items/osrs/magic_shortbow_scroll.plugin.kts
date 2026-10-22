package gg.rsmod.plugins.content.items.osrs

/**
 * N197 (owner 2026-10-22: "Magic shortbow scroll op een Magic shortbow gebruiken geeft 'Nothing interesting happens' - moet 'Are you
 * sure?' vragen en daarna een geluid spelen"). OSRS Wiki "Magic shortbow scroll": used on a magic shortbow it is consumed and the bow
 * becomes a Magic shortbow (i). Question and lines ADAPTED (the wiki gives no dialogue text).
 */
on_item_on_item(item1 = Items.MAGIC_SHORTBOW_SCROLL, item2 = Items.MAGIC_SHORTBOW) {
    player.queue {
        val sure = options("Yes, imbue the bow.", "No.", title = "Are you sure? The scroll is used up.") == 1
        if (!sure) return@queue
        if (!player.inventory.contains(Items.MAGIC_SHORTBOW) || !player.inventory.contains(Items.MAGIC_SHORTBOW_SCROLL)) return@queue
        if (player.inventory.remove(Items.MAGIC_SHORTBOW_SCROLL, 1).hasFailed()) return@queue
        if (player.inventory.remove(Items.MAGIC_SHORTBOW, 1).hasFailed()) {
            player.inventory.add(Items.MAGIC_SHORTBOW_SCROLL, 1)
            return@queue
        }
        player.inventory.add(Items.MAGIC_SHORTBOW_I, 1)
        player.playSound(Sfx.ENCHANT_ONYX_RING)
        player.message("You use the scroll to imbue your magic shortbow.")
    }
}
