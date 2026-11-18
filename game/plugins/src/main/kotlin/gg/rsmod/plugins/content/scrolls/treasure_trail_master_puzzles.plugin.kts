package gg.rsmod.plugins.content.scrolls

TreasureTrailStepCatalogue.current.rows.filter { TreasureTrailMasterPuzzles.available(world, it) }.forEach {
    ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
}
on_world_init {
    world.plugins.bindNpcOverride { player, npc, option -> TreasureTrailMasterPuzzles.talk(player, npc, option) }
}
TreasureTrailPuzzleBoxes.masterSources.forEach { source ->
    ClueItems.local(source.item)?.let { id ->
        on_item_option(id, "open") { TreasureTrailPuzzleBoxes.open(player, player.getInteractingItem()) }
    }
}
ClueItems.local(TreasureTrailLightBoxes.SOURCE_ITEM)?.let { id ->
    on_item_option(id, "open") { TreasureTrailLightBoxes.open(player, player.getInteractingItem()) }
}
TreasureTrailLightBoxes.iface()?.let { iface ->
    TreasureTrailLightBoxes.buttons.forEach { component ->
        on_button(iface, component) { TreasureTrailLightBoxes.click(player, component) }
    }
    on_button(iface, TreasureTrailLightBoxes.CLOSE) { TreasureTrailLightBoxes.remove(player); player.closeInterface(iface) }
    on_interface_close(iface) { TreasureTrailLightBoxes.remove(player) }
}
on_logout { TreasureTrailLightBoxes.remove(player) }
on_player_death { TreasureTrailLightBoxes.remove(player) }
