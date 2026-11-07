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
 * Cache 13354 plane 1 carries the bridge flag (settings 10/11) over the entrance hall and its stairs, so the loader puts the
 * entrance on game plane 0 (DefinitionSet bridge shift) - placing it on plane 1 left it floating and unclickable above the hall
 * (07-11 live check: only 'Walk here' at the door, and the exit put players on the hall roof).
 * ONZEKER: the two landing tiles (no source gives them); OUTSIDE = the hall floor in front of the door (cache plane 1
 * settings 10 = walkable bridge, checked on live 07-11).
 */
val ENTRANCE = Tile(3357, 2711, 0)
val LOBBY_ARRIVAL = Tile(3358, 9127, 0)
val OUTSIDE = Tile(3356, 2712, 0)

/*
 * Fairy ring AKP (OSRS Wiki Fairy ring AKP: 3284,2706; cache 2686 square 13098 loc 29495 shape 10 rot 0). The region import kept only
 * the animation of its definition (local 66204: no model, no name, no options - an invisible ring nobody could Use, so the island had no
 * way back). Every other ring here is the rev-667 'Fairy ring' 14142 (Use -> dial, fairy_rings.plugin.kts); the same one stands here.
 */
val AKP_RING = Tile(3284, 2706, 0)

on_world_init {
    ToaIds.loc(29495)?.let { broken -> world.getObject(AKP_RING, ObjectType.INTERACTABLE)?.takeIf { it.id == broken }?.let { world.remove(it) } }
    world.spawn(DynamicObject(14142, 22, 0, AKP_RING))
}

on_world_init {
    val open = ToaIds.loc(44010) ?: return@on_world_init
    world.getObject(ENTRANCE, ObjectType.INTERACTABLE)?.let { world.remove(it) }
    world.spawn(DynamicObject(open, 10, 0, ENTRANCE))
}

ToaIds.loc(44010)?.let { entry ->
    on_obj_option(obj = entry, option = "enter") {
        // Without the quest OSRS shows the Blocked Entry (44008, Inspect): its box (Transcript:Beneath Cursed Sands, before the cutscene).
        if (!ToaLobby.canEnter(player)) {
            player.queue { messageBox("The entrance is completely blocked. There doesn't seem to be any way through.") }
            return@on_obj_option
        }
        player.moveTo(LOBBY_ARRIVAL)
    }
}

on_obj_option(obj = ToaLocs.LOBBY_EXIT, option = "leave") {
    player.moveTo(OUTSIDE)
}
