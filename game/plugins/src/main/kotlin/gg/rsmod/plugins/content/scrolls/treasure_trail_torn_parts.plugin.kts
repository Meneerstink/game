package gg.rsmod.plugins.content.scrolls

val parts = TreasureTrailTornParts.SOURCE_IDS.mapNotNull(ClueItems::local)
if (parts.size == 3) {
    val components = TreasureTrailStepCatalogue.current.rows.filter { it.tornPart }
    ClueScrollManager.registerStep(TreasureTrailThreePartStep(components))
    for (id in parts) on_item_option(id,"combine") {
        TreasureTrailTornParts.combine(player,player.getInteractingItem())
    }
    on_world_init {
        world.plugins.bindNpcOverride { player,npc,option -> TreasureTrailTornDialogue.talk(player,npc,option) }
    }
}
