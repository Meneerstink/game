package gg.rsmod.plugins.content.areas.grandexchange

/*
 * The Grand Exchange home hall: the Royal Hall (owner 2026-09-25: "create me a building in the grand exchange and put all
 * the npcs in the building make the building beautifull make sure our building isnt noclipped on anything in grand
 * exchange ... use the highest quality materials"; chosen: royal marble & gold, one big floor, in place of the north-east
 * booth, cutting into the ring colonnade). It replaces the 2026-09-22 open-air hall on the south-west lawn.
 *
 * WHERE. Floor x 3176-3188, z 3503-3514 (GeHomeHall.X/Z/WIDTH/DEPTH). The north-east bank booth (47173) and the ring
 * section 47451 (paving, colonnade roof and pillars) are removed; the neighbouring colonnade roofs end half a tile short of
 * the walls, which stand one tile inside the cut (checked in rendered previews of the real cache models). The spirit tree
 * moved into the garden just east of the hall. Doorways: south (x 3181-3183, onto the ring), west (z 3508-3510, towards
 * the casino) and east (z 3508-3510, to the spirit tree).
 *
 * WHAT. Legends' Guild walls (41388, arched windows 41390): white marble inside, grey stone outside like the exchange.
 * Varrock Palace gold-and-black carpet over the whole floor, its gold throne with gold standards and 78 banners on the north
 * wall, a three-tier fountain (47747) in the middle with gold candelabras, black-and-gold suits of armour in the corners
 * and candle sconces on every wall. Every id is cache-native and carries no menu option.
 *
 * WHO. The 20 service stalls below stand along the west, east and north walls facing in; the south row beside the entrance
 * holds the bank staff of the old north-east booth (spawns_12598), the 78 Store keepers (StoreNpcs) and the Breach Trader.
 * Penguin/Ping/Pong stay removed; Lucien uses the non-combat variant 273. Skully and the casino croupiers keep their posts.
 */

// Walls, carpet, throne, fountain and the other furniture live in data/cfg/home_decor.txt (home_decor_live.plugin.kts).

val HALL_X = GeHomeHall.X
val HALL_Z = GeHomeHall.Z

// ------------------------------------------------------------------ the npcs

/** One stall: an npc on the inside of a wall, facing into the hall. */
GeHomeHall.SERVICE_POSTS.forEach { post ->
    spawn_npc(
        npc = post.npc,
        x = HALL_X + post.dx,
        z = HALL_Z + post.dz,
        walkRadius = 0,
        direction = post.facing,
    )
}
