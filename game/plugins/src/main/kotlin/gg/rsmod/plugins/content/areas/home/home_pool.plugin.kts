package gg.rsmod.plugins.content.areas.home

/**
 * R02.2/R14.8/HOME_DESIGN_2.png: home restoration pool, in the E quadrant ("POOL & ALTAR" in
 * the confirmed design) - due east of the bank, clearly separated from Shops (NE) and Vervoer
 * (SE). Restores HP, prayer, run energy and any lowered stats - NEVER special attack energy,
 * which is a deliberate deviation from the real Construction "Ornate rejuvenation pool" (which
 * DOES restore special energy) per R14.8's explicit rule.
 *
 * Uses [Objs.POOL_CLASS_5] (39562), verified live this session to be a real cache pool object
 * whose only real option is "Collect" - matching real 2011 Construction pool terminology, not
 * guessed (candidates without a real functional option, e.g. Objs.WATER_POOL's "Look"/
 * "Investigate" or Objs.POOL's blank options, were checked and rejected first).
 *
 * Audit finding 2 fix: a boot-time [ObjectDef] dump confirmed this object's real footprint is
 * 2x2, not 1x1. BATCH 1: tile now comes from [HomeLayout.pool], which already declares this
 * 2x2 footprint for the boot-time no-overlap self-check in `home_verify.plugin.kts`.
 */
val poolTile = HomeLayout.pool.tile(world.gameContext.home)

spawn_obj(obj = Objs.POOL_CLASS_5, x = poolTile.x, z = poolTile.z, height = poolTile.height, type = 10, rot = 0)

on_obj_option(obj = Objs.POOL_CLASS_5, option = "collect") {
    // R14.8: "cannot be abused through boundaries during combat" - same lock check the game's
    // other consumable-effect actions use, so it can't be triggered while mid-combat-lock or
    // from an interrupted queue.
    if (!player.lock.canItemInteract()) {
        return@on_obj_option
    }
    player.heal(9999)
    player.restorePrayer(9999)
    player.runEnergy = 100.0
    player.skills.restoreAll()
    player.message("You feel refreshed.")
}
