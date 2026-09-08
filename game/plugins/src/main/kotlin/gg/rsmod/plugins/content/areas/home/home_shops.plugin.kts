package gg.rsmod.plugins.content.areas.home

/**
 * R14.14/HOME_DESIGN_2.png: grouped specialty shops at home, in the NE quadrant of the
 * octagonal ruin - separate specialised shopkeepers within a short walk of each other, not one
 * all-in-one provisioning NPC. Placed clear of the board, the pool/altar, the central bank tile
 * and the wall/gate ring.
 *
 * Corrected this pass: an earlier version of this file placed these shops in the NW quadrant
 * before the confirmed design image was actually reviewed - HOME_DESIGN_2.png clearly labels
 * "SHOPS" in the NE, with "SUMMONING" in the NW instead (see `home_summoning.plugin.kts`).
 *
 * Reuses five real, already-implemented and already-verified town shopkeepers by spawning a
 * second instance of each at home - their `on_npc_option("trade"/"talk-to")` handlers are bound
 * globally by npc id (see e.g. `varrock/aubury.plugin.kts`, `varrock/horvik_armour_shop.plugin.kts`,
 * `catherby/hickton.plugin.kts`, `varrock/zaff.plugin.kts`, `falador/falador_general_store.plugin.kts`),
 * so the new home instances trade correctly with zero duplicated shop logic. This gives real,
 * distinct buy/sell categories (general supplies, runes, armour, ranged/archery, magic staffs)
 * instead of a fake or generic single home shop.
 */
val home = world.gameContext.home
val generalTile = HomeLayout.shopGeneral.tile(home)
val runesTile = HomeLayout.shopRunes.tile(home)
val armourTile = HomeLayout.shopArmour.tile(home)
val archeryTile = HomeLayout.shopArchery.tile(home)
val staffsTile = HomeLayout.shopStaffs.tile(home)
val faridTile = HomeLayout.faridMorrisane.tile(home)

spawn_npc(npc = Npcs.SHOPKEEPER_526, x = generalTile.x, z = generalTile.z, height = generalTile.height) // General Store
spawn_npc(npc = Npcs.AUBURY, x = runesTile.x, z = runesTile.z, height = runesTile.height) // Rune Shop
spawn_npc(npc = Npcs.HORVIK, x = armourTile.x, z = armourTile.z, height = armourTile.height) // Armour Shop
spawn_npc(npc = Npcs.HICKTON, x = archeryTile.x, z = archeryTile.z, height = archeryTile.height) // Archery Emporium
spawn_npc(npc = Npcs.ZAFF, x = staffsTile.x, z = staffsTile.z, height = staffsTile.height) // Superior Staffs

// 2026-09-06 owner human retest: relocated from the Grand Exchange (spawns_12598.plugin.kts) -
// his dialogue/options (`FaridMorrisane.plugin.kts`) are bound globally by npc id, so moving his
// spawn is the whole fix; no duplicated logic needed.
spawn_npc(npc = Npcs.FARID_MORRISANE_ORES, x = faridTile.x, z = faridTile.z, height = faridTile.height, direction = Direction.SOUTH)
