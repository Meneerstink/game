package gg.rsmod.plugins.content.scrolls

on_obj_option(TreasureTrailStatistics.STATISTICS_OBJECT,"read") { TreasureTrailStatistics.open(player) }
on_obj_option(TreasureTrailStatistics.PINBOARD_OBJECT,"read") { TreasureTrailStatistics.openPinboard(player) }
ClueInterfaces.of(221)?.let { iface -> on_button(iface,0) { player.closeInterface(iface) } }
ClueInterfaces.of(625)?.let { iface -> on_button(iface,2) { player.closeInterface(iface) } }
on_world_init {
    world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java,TreasureTrailStatistics.STATISTICS_OBJECT).examine="ALL of the numbers."
    world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java,TreasureTrailStatistics.PINBOARD_OBJECT).examine="Looks like Watson's ideas board."
}
