package gg.rsmod.plugins.content.areas.wilderness

on_world_init {
    WildernessHotspot.start(world)
    WildernessBreach.start(world)
}

on_command("hotspot") {
    player.filterableMessage("Current Wilderness hotspot: ${WildernessHotspot.current.label} (+15% reward/XP there).")
}
