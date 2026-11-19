package gg.rsmod.plugins.content.scrolls

// No binding or invented component when Claude has not applied the cache batch.
TreasureTrailStashSetting.layout?.let { row ->
    gg.rsmod.plugins.api.ext.PaneMountHooks.register(TreasureTrailStashSetting.GROUP) { p -> TreasureTrailStashSetting.sync(p) }
    on_login { TreasureTrailStashSetting.sync(player) }
    on_button(TreasureTrailStashSetting.GROUP, row.button) { TreasureTrailStashSetting.toggle(player) }
} ?: run {
    // TOA-1911 V3 (owner 19-11 19:4x 'staat niet goed dus weghalen'): the Options-tab checkbox is gone (cache tx-20261117-163200 rolled
    // back, mapping removed) -> nobody can switch it any more, so a flag saved while it was live goes back to the OSRS default (off).
    on_login { player.attr.remove(TreasureTrailStashes.TAKE_EQUIPPED) }
}
