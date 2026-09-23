package gg.rsmod.plugins.content.skills.thieving

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.Tile

/**
 * Thieving door-picking training: a small set of doors that are always safe to leave through, but
 * require Thieving to enter or re-enter. Ported from 2009scape's `content/global/skill/thieving/
 * PickableDoorHandler.java`, the only donor found with a curated location list for this cache's
 * `Door`/`Gate` ids that advertise `Pick-lock` - those ids alone (`DOORS` in that file) are
 * structurally identical across dozens of other in-world placements that are NOT thieving doors, so
 * the level gate only applies at these 12 specific tiles; everywhere else the object is left for the
 * general door mechanism (`doors.plugin.kts`) to skip, same as before this file existed.
 *
 * Simplified from the donor in two ways, both recorded as deliberate, not accidental:
 *  - The donor's `isInside`/`flipped` pair decides which side of the door is "locked" from a
 *    `Direction.getLogicalDirection` + `object.getDirection()` comparison this codebase has no
 *    verified equivalent conversion for (rotation-to-compass mapping was not confirmed against a
 *    live client this session). Rather than guess a mapping and risk silently blocking the "safe"
 *    side too, both `Open` and `Pick-lock` are gated identically here by Thieving level: meet it,
 *    walk through and gain XP; don't, and the message names the requirement. This does trade away
 *    the "walk out for free, pick the lock to get back in" loop the donor's asymmetry
 *    supports, but it cannot accidentally lock a player out of a side that should be free.
 *  - SOURCE_CONFLICT on failure: 2009scape gives an under-level attempt a chance to trigger a trap
 *    (1-3 damage), while the current OSRS Wiki's Lockpick page describes these training doors as
 *    having "no consequences" for failing. Since this server's identity is revision 667 (2011) and
 *    the modern wiki describes live 2026 mechanics, not 2011's, this keeps the donor's period-dated
 *    behaviour rather than the modern wiki's, and is recorded here in case that reading is wrong.
 */
data class PickableDoorSite(
    val tile: Tile,
    val level: Int,
    val xp: Double,
    val requiresLockpick: Boolean = false,
)

val PICKABLE_DOOR_SITES =
    listOf(
        PickableDoorSite(Tile(2672, 3308, 0), 1, 3.8),
        PickableDoorSite(Tile(2672, 3301, 0), 14, 15.0),
        PickableDoorSite(Tile(2610, 3316, 0), 15, 15.0),
        PickableDoorSite(Tile(3190, 3957, 0), 32, 25.0, requiresLockpick = true),
        PickableDoorSite(Tile(2565, 3356, 0), 46, 37.5),
        PickableDoorSite(Tile(2579, 3286, 1), 61, 50.0),
        PickableDoorSite(Tile(2579, 3307, 1), 61, 50.0),
        PickableDoorSite(Tile(3018, 3187, 0), 1, 0.0),
        PickableDoorSite(Tile(2601, 9482, 0), 82, 0.0, requiresLockpick = true),
        PickableDoorSite(Tile(3044, 3956, 0), 39, 35.0, requiresLockpick = true),
        PickableDoorSite(Tile(3041, 3959, 0), 39, 35.0, requiresLockpick = true),
        PickableDoorSite(Tile(3038, 3956, 0), 39, 35.0, requiresLockpick = true),
    )

/** 2009scape's `PickableDoorHandler.DOORS`: ids that carry `Pick-lock` somewhere in this cache. */
val PICKABLE_DOOR_IDS =
    intArrayOf(
        42028, 2550, 2551, 2554, 2555, 2556, 2557, 2558, 2559, 5501, 7246, 9565,
        13314, 13317, 13320, 13323, 13326, 13344, 13345, 13346, 13347, 13348, 13349,
        15759, 34005, 34805, 34806, 34812,
    )

fun attemptPickableDoor(player: Player) {
    val obj = player.getInteractingGameObj()
    val site = PICKABLE_DOOR_SITES.firstOrNull { it.tile == obj.tile } ?: return
    if (player.skills.getCurrentLevel(Skills.THIEVING) < site.level) {
        player.message("You need a Thieving level of ${site.level} to pick this lock.")
        return
    }
    if (site.requiresLockpick && !player.inventory.contains(Items.LOCKPICK)) {
        player.message("You need a lockpick in order to pick this lock.")
        return
    }
    player.message("You pick the lock.")
    player.addXp(Skills.THIEVING, site.xp, checkBrawlingGloves = true)
    player.queue {
        val horizontal = obj.rot == 1 || obj.rot == 3
        val destination =
            when {
                horizontal && player.tile.x - obj.tile.x >= 0 -> obj.tile.transform(-1, 0)
                horizontal -> obj.tile.transform(1, 0)
                player.tile.z - obj.tile.z >= 0 -> obj.tile.transform(0, -1)
                else -> obj.tile.transform(0, 1)
            }
        player.lock = LockState.FULL
        player.walkTo(this, destination, detectCollision = false)
        wait(1)
        player.lock = LockState.NONE
    }
}

on_world_init {
    PICKABLE_DOOR_IDS.forEach { id ->
        if (if_obj_has_option(id, "open")) {
            on_obj_option(id, "open") { attemptPickableDoor(player) }
        } else {
            logger.info("Pickable doors: object {} has no 'Open' option in this cache; skipped.", id)
        }
        if (if_obj_has_option(id, "pick-lock")) {
            on_obj_option(id, "pick-lock") { attemptPickableDoor(player) }
        }
    }
}
