package gg.rsmod.plugins.content.skills.slayer

/**
 * N522 nachtrun deel 3: Larran's small and big chest ([LarransChests]). Placed by the server on their OSRS Wiki tiles (the 667 map has
 * no Larran's chests). Unlock takes one Larran's key; the loot goes to the inventory, what does not fit lands at the player's feet.
 */

if (LarransChests.ENABLED) spawn_obj(obj = LarransChests.SMALL, x = LarransChests.SMALL_TILE.x, z = LarransChests.SMALL_TILE.z, height = LarransChests.SMALL_TILE.height, type = 10, rot = 0)
if (LarransChests.BIG_PLACED) spawn_obj(obj = LarransChests.BIG, x = LarransChests.BIG_TILE.x, z = LarransChests.BIG_TILE.z, height = LarransChests.BIG_TILE.height, type = 10, rot = 0)

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
