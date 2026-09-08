package gg.rsmod.plugins.content.areas.home

/**
 * Ferox Enclave 'Pool of Refreshment' (imported LocType [FeroxObjects.POOL_OF_REFRESHMENT], two
 * 2x2 placements at 3128,3633 and 3128,3638, real option "Drink").
 *
 * Restores Hitpoints, Prayer, run energy and every drained/boosted stat - and, per the standing
 * owner rule "herstelpool zonder spec" (RSPS_VOLLEDIGE_AUDIT_2026-08-31.md), NEVER special attack
 * energy, a deliberate deviation from the modern pool.
 *
 * Abuse guard: the same item-interaction lock check the game's other consumable effects use, so the
 * pool cannot be triggered mid-combat-lock or from an interrupted queue; entering the enclave already
 * ends combat (`bounty_hunter_home.plugin.kts`).
 */
on_obj_option(obj = FeroxObjects.POOL_OF_REFRESHMENT, option = "drink") {
    if (!player.lock.canItemInteract()) {
        return@on_obj_option
    }
    player.heal(9999)
    player.restorePrayer(9999)
    player.runEnergy = 100.0
    player.skills.restoreAll()
    player.message("You drink from the pool and feel refreshed.")
}
