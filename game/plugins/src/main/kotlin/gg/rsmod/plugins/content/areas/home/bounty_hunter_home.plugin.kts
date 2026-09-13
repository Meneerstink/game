package gg.rsmod.plugins.content.areas.home

import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.LockState
import gg.rsmod.plugins.content.combat.Combat

/**
 * Ferox Enclave hub wiring on top of the imported world content.
 *
 *  * Entering the safe polygon ends any combat (same rule the previous hub applied).
 *  * The enclave's own 'Barrier' walls (imported LocTypes [FeroxObjects.BARRIER_A]/[FeroxObjects.BARRIER_B])
 *    are real collision walls; their "Pass-Through" option force-moves the player to the landing tile
 *    on the other side, the identical mechanism the previous gate ring used (and the Wilderness
 *    ditch uses). Ordinary pathing cannot cross them.
 *  * Modern minigame access points that exist physically in Ferox (Clan Wars, LMS, Castle Wars,
 *    Bounty Hunter portals, Death's domain, scoreboard, reward chest, coffer, Twisted crate, the
 *    upper-floor stairs) have no server-side counterpart on this revision-667 project and are
 *    disabled with a message rather than faked (DEFERRED).
 *
 * The bank chest and the altar are bound through the existing global handlers
 * (`objs/bank_locs/BankObjects.CHESTS_USE`, `objs/prayeraltar/prayer_altar.plugin.kts`); the pool is
 * bound in `home_pool.plugin.kts`.
 */
val home = world.gameContext.home
val safeArea = BountyHunterHome.safeArea(home)
val gates = BountyHunterHome.gates(home)

on_enter_simple_polygon_area(safeArea) {
    Combat.reset(player)
    player.resetFacePawn()
}

listOf(FeroxObjects.BARRIER_A, FeroxObjects.BARRIER_B, FeroxObjects.BARRIER_FIELD).forEach { barrier ->
    on_obj_option(obj = barrier, option = "pass-through") {
        val clickedTile = player.getInteractingGameObj().tile
        val gate = gates.find { it.tile == clickedTile }
        if (gate == null) {
            player.message("This barrier cannot be passed.")
            return@on_obj_option
        }
        // Standing on the inner side means the player is leaving the enclave.
        val goingOutward = player.tile.getDistance(gate.innerLanding) <= player.tile.getDistance(gate.outerLanding)
        if (BountyHunterHome.barrierRefusesEntry(goingOutward, gate, player.timers.has(gg.rsmod.game.model.timer.TELEBLOCK_TIMER))) {
            player.message(BountyHunterHome.BARRIER_TELEBLOCK_MESSAGE)
            return@on_obj_option
        }
        val endTile = if (goingOutward) gate.outerLanding else gate.innerLanding
        if (goingOutward && gate.exitsToWilderness) {
            player.message("You pass through the barrier into the Wilderness.")
        }
        player.faceTile(endTile)
        player.queue {
            player.stopMovement()
            // This is a walk-through field, not a jump. Normal movement updates select
            // the equipped player's walk animation; forced-movement packets do not.
            player.lock = LockState.FULL
            try {
                player.movementQueue.addStep(endTile, MovementQueue.StepType.FORCED_WALK, detectCollision = false)
                wait(player.tile.getDistance(endTile) + 1)
            } finally {
                player.lock = LockState.NONE
            }
        }
    }
}

/*
 * Ferox access-point audit (owner run 2026-09-05; OSRS Wiki "Ferox Enclave" + production-cache placements):
 *  A retained & wired  : barriers (pass-through), stairs 62431/62432 (mechanics/stairs stairs.json: rot-2
 *                        north-facing, 2-tile landings between each stair pair), bank chest, pools, altar.
 *  B removed           : Bounty Hunter portal 62576 @3140,3621 and the green Clan Wars free-for-all portal
 *                        62420 @3127,3626 - placements physically removed from both caches (FeroxFollowupTool).
 *  C dependency absent : Death's domain (OSRS-2020 Death's Office; this revision keeps gravestones), Clan Wars
 *                        challenge portal, Castle Wars portal, LMS doors/reward chest/scoreboard/coffer/crate,
 *                        and the "Walk-down" stairs 62564 into the Ferox Enclave Dungeon (map not imported).
 *                        They answer with an explicit refusal instead of a faked destination.
 */
private val disabledModernContent =
    mapOf(
        FeroxObjects.CLAN_WARS_CHALLENGE_PORTAL to "enter",
        FeroxObjects.CASTLE_WARS_PORTAL to "enter",
        FeroxObjects.LMS_CASUAL to "pass-through",
        FeroxObjects.LMS_COMPETITIVE to "pass-through",
        FeroxObjects.LMS_HIGH_STAKES to "pass-through",
        FeroxObjects.REWARD_CHEST to "claim",
        FeroxObjects.SCOREBOARD to "view",
        FeroxObjects.COFFER to "use",
        FeroxObjects.TWISTED_CRATE to "search",
        FeroxObjects.STAIRS_WALK_DOWN to "walk-down",
    )

disabledModernContent.forEach { (objectId, option) ->
    on_obj_option(obj = objectId, option = option) {
        player.message("That part of the Enclave is not in use on this world.")
    }
}

on_obj_option(obj = FeroxObjects.CLAN_WARS_CHALLENGE_PORTAL, option = "join") {
    player.message("That part of the Enclave is not in use on this world.")
}
