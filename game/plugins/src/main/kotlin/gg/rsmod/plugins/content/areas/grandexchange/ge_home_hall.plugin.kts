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

// ------------------------------------------------------------------ the spiral staircases to the gallery

// The hall's two staircases (GeHomeHall.STAIRS) are dressing from home_decor.txt; this takes their climb options before
// the generic stairs so players land on the rug in front of them or on the gallery beside the stairwell.
on_world_init {
    world.plugins.bindObjectOverride { p, obj, opt ->
        // The top (1740) stands on the same x/z as the bottom's south-west tile, one level up.
        val stairs = GeHomeHall.STAIRS.firstOrNull { it.base.x == obj.tile.x && it.base.z == obj.tile.z && obj.tile.height <= 1 } ?: return@bindObjectOverride false
        val option = obj.getDef(world.definitions).options.getOrNull(opt - 1)?.lowercase() ?: return@bindObjectOverride false
        val up = obj.id == GeHomeHall.STAIRS_BOTTOM && (option == "climb" || option == "climb-up")
        val down = obj.id == GeHomeHall.STAIRS_TOP && (option == "climb" || option == "climb-down")
        if (!up && !down) {
            if (obj.id == GeHomeHall.STAIRS_BOTTOM || obj.id == GeHomeHall.STAIRS_TOP) p.message("These stairs only lead ${if (obj.id == GeHomeHall.STAIRS_BOTTOM) "up" else "down"}.")
            return@bindObjectOverride obj.id == GeHomeHall.STAIRS_BOTTOM || obj.id == GeHomeHall.STAIRS_TOP
        }
        p.lockingQueue(lockState = gg.rsmod.game.model.LockState.FULL) {
            wait(2)
            p.moveTo(if (up) stairs.gallery else stairs.floor)
        }
        true
    }
}
