package gg.rsmod.plugins.content.scrolls

on_world_init {
    TreasureTrailStashWorld.post(world)
    world.plugins.bindObjectOverride { player, obj, option -> TreasureTrailStashWorld.interact(player, obj, option) }
}
on_login { TreasureTrailStashWorld.sync(player) }
