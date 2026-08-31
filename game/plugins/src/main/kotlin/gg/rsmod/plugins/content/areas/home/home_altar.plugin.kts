package gg.rsmod.plugins.content.areas.home

/**
 * R02.2/R14.8: home prayer altar, placed beside the restoration pool in the Pool & Altar
 * quadrant (per HOME_DESIGN_2.png).
 *
 * Reuses [Objs.ALTAR_27661], a real cache altar already bound to a verified "pray-at" option
 * in `objs/prayeraltar/prayer_altar.plugin.kts` (recharges Prayer points via
 * [gg.rsmod.plugins.content.mechanics.prayer.Prayers.rechargePrayerPoints]) - spawning a second
 * instance here reuses that existing, already-correct handler with zero duplicated logic, same
 * pattern as `home_shops.plugin.kts`.
 *
 * Audit finding 2 fix: this object's real footprint is 2x1 (width=2, length=1), confirmed via a
 * boot-time [ObjectDef] dump. The previous tile (4,-2) let it run onto (5,-2), a perimeter wall
 * position. Moved one tile west to (3,-2), clear of the x=5 wall column and of the pool's
 * footprint (which occupies rows z=-1..0, one row south of here).
 */
val altarTile = world.gameContext.home.transform(3, -2)

spawn_obj(obj = Objs.ALTAR_27661, x = altarTile.x, z = altarTile.z, height = altarTile.height, type = 10, rot = 0)
