package gg.rsmod.plugins.content.scrolls

// No binding or invented component when Claude has not applied the cache batch.
TreasureTrailStashSetting.layout?.let { row ->
    gg.rsmod.plugins.api.ext.PaneMountHooks.register(TreasureTrailStashSetting.GROUP) { p -> TreasureTrailStashSetting.sync(p) }
    on_login { TreasureTrailStashSetting.sync(player) }
    on_button(TreasureTrailStashSetting.GROUP, row.button) { TreasureTrailStashSetting.toggle(player) }
}
