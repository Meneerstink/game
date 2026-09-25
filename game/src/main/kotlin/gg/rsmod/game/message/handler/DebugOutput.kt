package gg.rsmod.game.message.handler

import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.priv.Privilege

/**
 * Audit S-12: the dev-settings debug flags (debug-buttons/items/spells/objects) used to send their
 * console lines - interface, component, item and object ids - to every player, not only to staff.
 * Debug output from the packet handlers now goes only to players with the `dev` power.
 */
internal fun Client.seesDebugOutput(): Boolean = world.privileges.isEligible(privilege, Privilege.DEV_POWER)
