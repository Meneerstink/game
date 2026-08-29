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
 * Holds a player's non-protected items after a non-Wilderness (PvM/safe) death,
 * until they are reclaimed or the recovery expires. Capacity 42 covers the
 * worst case of every inventory (28) and equipment (14) slot being lost in a
 * single death.
 */
val DEATH_RECOVERY_KEY = ContainerKey("death_recovery", capacity = 42, stackType = ContainerStackType.NORMAL)
