package gg.rsmod.plugins.content.magic

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.message

/**
 * PvP zone/timers further-foundations pass (2026-09-02, see `RSPS_DECISIONS.md`): the master
 * plan's "Combat restrictions" list requires "10 sec niet teleporteren" after a recent hit,
 * mirroring the pre-existing logout-button block (`logout_tab.plugin.kts`) that already gates
 * on the same [ACTIVE_COMBAT_TIMER]. This function is the single choke point essentially every
 * teleport method (spellbook teleports, Home Teleport, teleport tabs, and every piece of
 * teleport jewellery) already routes through via its own local `Player.teleport(tile, ...)`
 * wrapper, so one check here covers the whole teleport surface without touching each item.
 */
fun Player.canTeleport(type: TeleportType): Boolean {
    val currWildLvl = tile.getWildernessLevel()
    val wildLvlRestriction = type.wildLvlRestriction
    val randomEvent = tile.regionId

    if (!lock.canTeleport()) {
        return false
    }

    if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
            this, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.TELEPORT,
        )
    ) {
        return false
    }

    // RCV-005 owner retest 2026-09-13 ("je kunt nog steeds niet wegteleporteren als je in combat
    // bent"): OSRS lets a player teleport out of NPC combat. The 10-second rule stays only for PvP
    // (the original "PJ" restriction): it applies when the last hit came from another player.
    if (timers.has(ACTIVE_COMBAT_TIMER) && attr[LAST_HIT_BY_ATTR]?.get() is Player) {
        message("You can't teleport until 10 seconds after the end of combat.")
        return false
    }

    if (timers.has(TELEBLOCK_TIMER)) {
        message("A magical force has stopped you from teleporting.")
        return false
    }

    if (currWildLvl > wildLvlRestriction) {
        message("A mysterious force blocks your teleport spell!")
        message("You can't use this teleport after level $wildLvlRestriction wilderness.")
        return false
    }

    if (randomEvent == 12619) {
        message("You can't use this teleport here.")
        return false
    }

    return true
}

fun Pawn.prepareForTeleport() {
    stopMovement()
    resetInteractions()
    clearHits()
}

fun Pawn.teleport(
    endTile: Tile,
    type: TeleportType,
) {
    lock = LockState.FULL_WITH_DAMAGE_IMMUNITY

    queue(TaskPriority.STRONG) {
        prepareForTeleport()

        animate(type.animation)
        type.graphic?.let {
            graphic(it)
        }

        wait(type.teleportDelay)

        teleportTo(endTile)

        type.endAnimation?.let {
            animate(it)
        }

        type.endGraphic?.let {
            graphic(it)
        }

        type.endAnimation?.let {
            val def = world.definitions.get(AnimDef::class.java, it)
            wait(def.cycleLength)
        }

        animate(Anims.RESET)
        unlock()
    }
}
