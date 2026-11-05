package gg.rsmod.plugins.content.skills.slayer

/**
 * N522 nachtrun deel 3: Larran's small and big chest ([LarransChests]). Placed by the server on their OSRS Wiki tiles (the 667 map has
 * no Larran's chests). Unlock takes one Larran's key; the loot goes to the inventory, what does not fit lands at the player's feet.
 */

// OSRS cache 2499: 34831 (small, multiloc 33343/34828) at 3281,3659 rot 1; 34832 (big, multiloc 34829/34830) at 3018,3955,1 rot 2.
if (LarransChests.ENABLED) spawn_obj(obj = LarransChests.SMALL, x = LarransChests.SMALL_TILE.x, z = LarransChests.SMALL_TILE.z, height = LarransChests.SMALL_TILE.height, type = 10, rot = 1)
if (LarransChests.BIG_PLACED) spawn_obj(obj = LarransChests.BIG, x = LarransChests.BIG_TILE.x, z = LarransChests.BIG_TILE.z, height = LarransChests.BIG_TILE.height, type = 10, rot = 2)

// The OSRS ship's ladders (OsrsRegionImportTool 'larran-ship'): Zaryx PirateShipLadders - Climb-up = the player's tile one plane up,
// Climb-down one plane down (272/273 deck, 245/246 quarterdeck).
listOf(LarransChests.SHIP_LADDER_UP, LarransChests.SHIP_LADDER_UP_2).forEach { id ->
    on_obj_option(obj = id, option = "climb-up") { player.handleLadder(height = player.tile.height + 1) }
}
listOf(LarransChests.SHIP_LADDER_DOWN, LarransChests.SHIP_LADDER_DOWN_2).forEach { id ->
    on_obj_option(obj = id, option = "climb-down") { player.handleLadder(height = player.tile.height - 1) }
}

fun unlock(player: Player, big: Boolean) {
    if (!player.inventory.contains(LarransChests.KEY)) {
        // OSRS Wiki: "The chest is locked and can only be opened with Larran's key."
        player.message("The chest is locked.")
        return
    }
    if (player.inventory.remove(LarransChests.KEY, 1).hasFailed()) return
    LarransChests.roll(player, big).forEach { item ->
        val added = player.inventory.add(item.id, item.amount)
        if (added.hasFailed() || added.completed < item.amount) {
            player.world.spawn(gg.rsmod.game.model.entity.GroundItem(item.id, item.amount - added.completed, player.tile, player))
        }
    }
}

on_obj_option(obj = LarransChests.SMALL, option = "unlock") { unlock(player, big = false) }
on_obj_option(obj = LarransChests.BIG, option = "unlock") { unlock(player, big = true) }
