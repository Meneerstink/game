package gg.rsmod.plugins.content.scrolls

TreasureTrailStepCatalogue.current.rows.filter { row ->
    TreasureTrailPuzzleBoxes.available(world, row) && TreasureTrailPuzzleDialogue.npcs(row).isNotEmpty()
}.forEach { ClueScrollManager.registerStep(TreasureTrailSourceStep(it)) }

ClueNpcs.local(306)?.let { guide ->
    // Wiki Lumbridge Guide map x3238/y3220/r3, with the source306 ordinary appearance.
    spawn_npc(npc = guide, x = 3238, z = 3220, height = 0, walkRadius = 3)
}
on_world_init {
    world.plugins.bindNpcOverride { player, npc, option -> TreasureTrailPuzzleDialogue.talk(player, npc, option) }
}
TreasureTrailPuzzleBoxes.sources.forEach { source ->
    ClueItems.local(source.item)?.let { id ->
        on_item_option(id, "open") { TreasureTrailPuzzleBoxes.open(player, player.getInteractingItem()) }
    }
}
ClueInterfaces.of(306)?.let { iface ->
    repeat(25) { cell -> on_button(iface, TreasureTrailPuzzleBoxes.CLICK_FIRST + cell) { TreasureTrailPuzzleBoxes.click(player, cell) } }
    on_button(iface, TreasureTrailPuzzleBoxes.CLOSE) { TreasureTrailPuzzleBoxes.close(player); player.closeInterface(iface) }
    on_interface_close(iface) { TreasureTrailPuzzleBoxes.close(player) }
}
on_logout { TreasureTrailPuzzleBoxes.remove(player) }
on_player_death { TreasureTrailPuzzleBoxes.remove(player) }
