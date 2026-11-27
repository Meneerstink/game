package gg.rsmod.plugins.content.diaries

/**
 * DIARIES-OSRS: world content that some diary tasks need and that this server did not have yet.
 *  - Seers' church organ (loc 25818 @2691,3461): 'Play' (OSRS Wiki "Organ": interactable with 'Play') - Kandarin easy 4.
 *  - Muddy chest in the Lava Maze (loc 170 'Closed chest' @3089,3859; open form 171): OSRS Wiki "Muddy chest": opened with a
 *    muddy key that is used up, guaranteed loot Uncut ruby 1, Mithril bar 2 (noted), Law rune 5, Death rune 5, Chaos rune 15.
 *    The mobile-only/blighted rolls and Larran's key are not ported. Wilderness medium 11.
 */

on_obj_option(obj = 25818, option = "Play") {
    player.animate(3675) // same organ animation as the Lumbridge church organ (Void play_organ)
    player.playJingle(72)
    Diaries.event(player, "organ:seers")
}

on_obj_option(obj = 170, option = "Open") {
    val chest = player.getInteractingGameObj()
    if (chest.tile.x != 3089 || chest.tile.z != 3859) {
        player.message("The chest is locked.")
        return@on_obj_option
    }
    if (!player.inventory.contains(Items.MUDDY_KEY)) {
        player.message("The chest is locked.")
        return@on_obj_option
    }
    val loot = listOf(
        Item(Items.UNCUT_RUBY, 1), Item(Items.MITHRIL_BAR_NOTED, 2), Item(Items.LAW_RUNE, 5), Item(Items.DEATH_RUNE, 5), Item(Items.CHAOS_RUNE, 15),
    )
    val slotsNeeded = loot.count { player.inventory.getItemCount(it.id) == 0 } - 1 // the key's slot frees up
    if (player.inventory.freeSlotCount < slotsNeeded) {
        player.message("You don't have enough inventory space to take the treasure.")
        return@on_obj_option
    }
    player.lock()
    player.queue {
        if (player.inventory.remove(Items.MUDDY_KEY, 1).hasSucceeded()) {
            player.message("You unlock the chest with your key.")
            player.animate(536)
            world.spawnTemporaryObject(DynamicObject(171, chest.type, chest.rot, chest.tile), 4, DynamicObject(chest))
            wait(1)
            loot.forEach { player.inventory.add(it) }
            player.message("You find some treasure in the chest!")
            Diaries.event(player, "chest:muddy")
        }
        player.unlock()
    }
}
