package gg.rsmod.plugins.content.scrolls

// The imported source Close components/hover hooks retain their actual identities.
TreasureTrailMaps.imported().forEach { (map,local) ->
    on_button(local,map.close) { player.closeInterface(local) }
}
