package gg.rsmod.plugins.content.areas.home

/**
 * R14.6/HOME_DESIGN_2.png: a Summoning specialist at home, in the NW quadrant ("SUMMONING" in
 * the confirmed design, now vacated by the shops/vervoer quadrant corrections this pass).
 *
 * Reuses [Npcs.PIKKUPSTIX], a real, already-implemented and already-verified Summoning shopkeeper
 * (`skills/summoning/summoning_supply_shops.plugin.kts`, real `"trade"` binding to "Pikkupstix's
 * Summoning Shop" - pouches, spirit shards, charms) - same zero-duplicated-logic reuse pattern
 * as `home_shops.plugin.kts`.
 *
 * The design's pen/beast-totem imagery (a bull-like and a golem-like creature) is decorative -
 * live familiars are temporary, player-owned summons, not a permanent world fixture, so nothing
 * equivalent is placed here; the real Summoning-training obelisk/pouch loop itself
 * (`skills/summoning/familiar.plugin.kts`) already works from anywhere via the pouch items this
 * shop sells.
 */
val summoningTile = HomeLayout.summoning.tile(world.gameContext.home)

spawn_npc(npc = Npcs.PIKKUPSTIX, x = summoningTile.x, z = summoningTile.z, height = summoningTile.height)
