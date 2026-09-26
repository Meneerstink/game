package gg.rsmod.game.model.container.key

import gg.rsmod.game.model.container.ContainerStackType

/**
 * A decoupled file that holds [ContainerKey]s that are pre-defined in our core
 * game-module.
 *
 * @author Tom <rspsmods@gmail.com>
 */

val INVENTORY_KEY = ContainerKey("inventory", capacity = 28, stackType = ContainerStackType.NORMAL)
val EQUIPMENT_KEY = ContainerKey("equipment", capacity = 14, stackType = ContainerStackType.NORMAL)
val RANDOM_EVENT_GIFT_KEY = ContainerKey("random_event_gift", capacity = 28, stackType = ContainerStackType.STACK)
val BANK_KEY = ContainerKey("bank", capacity = 800, stackType = ContainerStackType.STACK)

/**
 * Death's Office: the items Death holds for a player (OSRS "Death's Office Item Retrieval", inventory 636, 120 slots). Items
 * arrive here when a gravestone collapses, when a familiar's cargo is rescued (owner override) or when an old grave hands its
 * resources over on a repeat death. Death keeps them without a time limit (OSRS Wiki "Death's Office").
 */
val DEATH_RECOVERY_KEY = ContainerKey("death_recovery", capacity = 120, stackType = ContainerStackType.NORMAL)

/**
 * The player's gravestone (OSRS "Grave", inventory 525, 120 slots): the items lost on a PvM death, waiting at the grave for
 * 15 minutes of play (OSRS Wiki "Grave": "a gravestone functions similarly to a bank with 120 slots").
 */
val GRAVESTONE_KEY = ContainerKey("gravestone", capacity = 120, stackType = ContainerStackType.NORMAL)
