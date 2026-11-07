package gg.rsmod.plugins.content.activity.toa

/** N462: the static-map multiloc variants ([ToaMultilocs]); the raid ones are placed per instance by [ToaScene]. */
on_world_init { ToaMultilocs.placeWorld(world) }
