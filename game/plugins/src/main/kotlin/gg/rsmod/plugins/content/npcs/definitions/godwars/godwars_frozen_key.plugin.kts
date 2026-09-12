package gg.rsmod.plugins.content.npcs.definitions.godwars

val PIECES =
    intArrayOf(
        Items.FROZEN_KEY_PIECE_ARMADYL,
        Items.FROZEN_KEY_PIECE_BANDOS,
        Items.FROZEN_KEY_PIECE_ZAMORAK,
        Items.FROZEN_KEY_PIECE_SARADOMIN,
    )

fun tryCombine(player: gg.rsmod.game.model.entity.Player) {
    if (PIECES.all { player.inventory.getItemCount(it) >= 1 }) {
        PIECES.forEach { player.inventory.remove(it, 1) }
        player.inventory.add(Items.FROZEN_KEY_20120)
        player.filterableMessage("The four frozen key pieces bind together into a whole key.")
    } else {
        player.filterableMessage("You need all four frozen key pieces (Armadyl, Bandos, Zamorak, Saradomin) to do this.")
    }
}

PIECES.forEach { piece ->
    on_item_option(item = piece, option = 1) {
        tryCombine(player)
    }
}
