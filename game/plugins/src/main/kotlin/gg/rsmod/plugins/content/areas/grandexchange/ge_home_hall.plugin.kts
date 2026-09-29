package gg.rsmod.plugins.content.areas.grandexchange

/*
 * The Grand Exchange home hall: the Royal Hall (owner 2026-09-25: "create me a building in the grand exchange and put all
 * the npcs in the building make the building beautifull make sure our building isnt noclipped on anything in grand
 * exchange ... use the highest quality materials"; chosen: royal marble & gold; then "i want the building to move in the
 * middel of grand exchange"). It first stood in the north-east corner, which is restored with its booth, clerks and bankers.
 *
 * WHERE. Floor x 3158-3171, z 3486-3497 (GeHomeHall.X/Z/WIDTH/DEPTH), centred on the exchange's fountain inside the inner
 * ring, clear of the ring colonnade. RoyalHallMapTool removed the centre's planters, fences, canopy and paving decals and
 * raised the floor above the paving models. Doorways: south (x 3163-3166, the path from the south gate), west and east
 * (z 3490-3493).
 *
 * WHAT. Whitewashed walls with gold stained-glass windows on two storeys, a cream parapet with 78 standards and a slate hip
 * roof (data/cfg/home_decor.txt); inside white marble, a Varrock Palace gold rug round the fountain (62758, a copy of the
 * exchange's own), the king's and queen's thrones beside the summoning obelisk, gold candelabras, benches, paintings and
 * black-and-gold armour in the corners. Every id is cache-native or a byte-exact copy and carries no menu option.
 *
 * WHO. Services on the ground floor, one theme per marble corner, the casino and lounge upstairs (GeHomeHall, owner
 * 2026-09-27); every npc is posted by data/world_edits.json.
 */

// Walls, carpet, throne, fountain and the other furniture live in data/cfg/home_decor.txt (home_decor_live.plugin.kts).

// ------------------------------------------------------------------ the npcs

// Every hall npc is posted once in data/world_edits.json (GeHomeHall: one source per npc); nothing is spawned here.

// ------------------------------------------------------------------ the spiral staircases to the upper floors

// The hall's two staircases (GeHomeHall.STAIRS) are dressing from home_decor.txt; this takes their climb options before
// the generic stairs: the bottom leads up to level 1, the middle piece on level 1 up to level 2 or down, the top on level 2
// down to level 1. Players land in front of the stairs on the ground floor and beside the stairwell upstairs.
on_world_init {
    world.plugins.bindObjectOverride { p, obj, opt ->
        // The middle piece and the top stand on the same x/z as the bottom's south-west tile, one and two levels up.
        val stairs = GeHomeHall.STAIRS.firstOrNull { it.base.x == obj.tile.x && it.base.z == obj.tile.z && obj.tile.height <= 2 } ?: return@bindObjectOverride false
        val ours = obj.id == GeHomeHall.STAIRS_BOTTOM || obj.id == GeHomeHall.STAIRS_MIDDLE || obj.id == GeHomeHall.STAIRS_TOP
        if (!ours) return@bindObjectOverride false
        val option = obj.getDef(world.definitions).options.getOrNull(opt - 1)?.lowercase() ?: return@bindObjectOverride false
        val up = option == "climb-up" || option == "climb" && obj.id == GeHomeHall.STAIRS_BOTTOM
        val down = option == "climb-down" || option == "climb" && obj.id == GeHomeHall.STAIRS_TOP
        val to =
            when {
                up && obj.id == GeHomeHall.STAIRS_BOTTOM -> stairs.gallery
                up && obj.id == GeHomeHall.STAIRS_MIDDLE -> stairs.landing
                down && obj.id == GeHomeHall.STAIRS_MIDDLE -> stairs.floor
                down && obj.id == GeHomeHall.STAIRS_TOP -> stairs.gallery
                else -> null
            }
        if (to == null) {
            p.message("These stairs only lead ${if (obj.id == GeHomeHall.STAIRS_BOTTOM) "up" else "down"}.")
            return@bindObjectOverride true
        }
        p.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
            wait(2)
            p.moveTo(to)
        }
        true
    }
}

// ------------------------------------------------------------------ the facade over the balconies, seen from inside

// Owner 2026-09-29: from inside a lower storey no balcony and no hole - GeHomeHallCurtains closes each hidden balcony row
// for that player. Checked every tick for the players near the hall (cheap: a tile test, packets only on a change).
val HALL_CURTAINS = gg.rsmod.game.model.timer.TimerKey()

on_world_init { world.timers[HALL_CURTAINS] = 1 }

on_timer(HALL_CURTAINS) {
    world.players.forEach { p ->
        if (p.tile.isWithinRadius(gg.rsmod.game.model.Tile(3164, 3491, p.tile.height), 40)) GeHomeHallCurtains.update(p)
    }
    world.timers[HALL_CURTAINS] = 1
}
