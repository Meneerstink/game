package gg.rsmod.plugins.content.areas.home

import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * Ferox Enclave 'Pool of Refreshment' (imported LocType [FeroxObjects.POOL_OF_REFRESHMENT], two
 * 2x2 placements at 3128,3633 and 3128,3638, real option "Drink").
 *
 * RCV-011, owner "make Ferox Enclave function like OSRS" - OSRS Wiki "Pool of Refreshment": fully restores Hitpoints,
 * Prayer and run energy, cures poison and venom (this server has no player disease), resets every boosted and drained
 * skill to its base level, switches off all active prayers/curses, and says
 * "You feel reinvigorated after drinking from the pool." It never restores special attack energy - the OSRS Ferox pool
 * and the standing owner rule "herstelpool zonder spec" (RSPS_VOLLEDIGE_AUDIT_2026-08-31.md) agree.
 * SOURCE_BLOCKED: the pool's own animation/sound (OSRS anim 7304) are not in the 667 cache.
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
    Poison.cure(player)
    Venom.cure(player, immunityTicks = 0, announce = false)
    Prayers.deactivateAll(player)
    AncientCurses.deactivateAllCurses(player)
    player.message(BountyHunterHome.POOL_MESSAGE)
}
