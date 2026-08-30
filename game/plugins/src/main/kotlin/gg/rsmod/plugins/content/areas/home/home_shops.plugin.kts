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

spawn_npc(npc = Npcs.SHOPKEEPER_526, x = home.x + 2, z = home.z + 1, height = home.height) // General Store
spawn_npc(npc = Npcs.AUBURY, x = home.x + 2, z = home.z + 2, height = home.height) // Rune Shop
spawn_npc(npc = Npcs.HORVIK, x = home.x + 3, z = home.z + 2, height = home.height) // Armour Shop
spawn_npc(npc = Npcs.HICKTON, x = home.x + 3, z = home.z + 3, height = home.height) // Archery Emporium
spawn_npc(npc = Npcs.ZAFF, x = home.x + 4, z = home.z + 2, height = home.height) // Superior Staffs
