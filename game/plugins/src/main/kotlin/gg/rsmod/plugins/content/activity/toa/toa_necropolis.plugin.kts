package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject

/*
 * N462: the way in and out of the Jaltevas Pyramid (Tombs of Amascut lobby).
 * OSRS: the pyramid entrance at the Necropolis is multiloc 44596 (cache 2686; placed at 3357,2711 plane 1, shape 10, rotation 0) switched by
 * varbit 13837 between "Blocked Entry" 44008 (Inspect) and "Entry" 44010 (Enter) - open once Beneath Cursed Sands is done. The 667 map import
 * keeps only the base definition (no varbit transform), so the server places the open entrance; every account that may enter has the quest
 * (owner E2: existing completion stays) - the short quest for new accounts later checks ToaLobby.canEnter here.
 * Inside, the lobby "Exit" (46087, 3358,9129) leads back out.
 * ONZEKER: the two landing tiles (no source gives them); checked walkable in the test client.
 */
val ENTRANCE = Tile(3357, 2711, 1)
val LOBBY_ARRIVAL = Tile(3358, 9127, 0)
val OUTSIDE = Tile(3358, 2709, 1)

on_world_init {
    val open = ToaIds.loc(44010) ?: return@on_world_init
    world.getObject(ENTRANCE, ObjectType.INTERACTABLE)?.let { world.remove(it) }
    world.spawn(DynamicObject(open, 10, 0, ENTRANCE))
}

ToaIds.loc(44010)?.let { entry ->
    on_obj_option(obj = entry, option = "enter") {
        // Without the quest OSRS shows the Blocked Entry (44008) instead - built with the short quest (card n462-toa-log-quest).
        if (!ToaLobby.canEnter(player)) return@on_obj_option
        player.moveTo(LOBBY_ARRIVAL)
    }
}

on_obj_option(obj = ToaLocs.LOBBY_EXIT, option = "leave") {
    player.moveTo(OUTSIDE)
}
