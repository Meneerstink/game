package gg.rsmod.plugins.content.areas.edgeville

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.api.cfg.Objs

/**
 * PROJECT48 D2 (owner choice 1, 22-11: "SOUL WARS WEGHALEN" - the 2010 Soul Wars island lies on Varlamore land, 1728-1919 x
 * 3072-3327, which the area import replaces). The minigame code is gone, so its only way in, the rev-667 Soul Wars portal in
 * Edgeville (loc 42219, shape 11 rot 3 at 3083,3473 - findloc on the test server 23-11), goes too: no dead "Enter" option.
 */
val SOUL_WARS_PORTAL_TILE = Tile(3083, 3473, 0)

on_world_init {
    world.getObject(SOUL_WARS_PORTAL_TILE, ObjectType.DIAGONAL_INTERACTABLE)
        ?.takeIf { it.id == Objs.SOUL_WARS_PORTAL }
        ?.let { world.remove(it) }
}
