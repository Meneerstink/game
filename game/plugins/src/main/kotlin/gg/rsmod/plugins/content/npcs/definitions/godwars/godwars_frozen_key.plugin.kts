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

// Fallback entry point for the Nex encounter in case the real Frozen Door object isn't
// reachable in this map build - see nex.plugin.kts.
on_command("nex") {
    if (!player.inventory.remove(Items.FROZEN_KEY_20120, 1).hasSucceeded()) {
        player.filterableMessage("You need a completed Frozen key to do this.")
        return@on_command
    }
    player.filterableMessage("The Frozen Door grinds open. Nex awakens...")
    val tile = gg.rsmod.game.model.Tile(player.tile)
    val nex = gg.rsmod.game.model.entity.Npc(Npcs.NEX, tile, player.world)
    nex.respawnOverride = false
    nex.walkRadius = 0
    player.world.spawn(nex)
}
