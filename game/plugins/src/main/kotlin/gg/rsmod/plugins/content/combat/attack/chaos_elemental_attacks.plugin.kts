package gg.rsmod.plugins.content.combat.attack

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.timer.TimerKey

/**
 * Chaos Elemental mechanics on top of its data-driven attacks (NpcAttacks, Void chaos_elemental.combat.toml:
 * discord, confusion, madness with their cast/travel/impact gfx and sounds). Ported from Void
 * `content/area/wilderness/ChaosElemental.kt`.
 *
 * SOURCE_CONFLICT: Void registers "free_inventory_spaces" twice - the second registration (a confusion
 * cooldown check) overwrites the first, and "confusion_cooldown" is never registered. Both checks are ported
 * under the names the data uses: confusion needs no confusion cooldown, madness needs free inventory space.
 */
val CONFUSION_COOLDOWN = TimerKey()

NpcAttacks.condition("confusion_cooldown") { _, target ->
    target is Player && !target.timers.has(CONFUSION_COOLDOWN)
}

NpcAttacks.condition("free_inventory_spaces") { _, target ->
    target is Player && target.inventory.freeSlotCount > 0
}

/** Void: teleport the target to a random tile within 10 of the elemental, then a 10-tick cooldown. */
NpcAttacks.onImpact("chaos_elemental", "confusion") { npc, target ->
    val tile = npc.world.findRandomTileAround(npc.tile, radius = 10) ?: return@onImpact false
    if (target is Player) {
        target.stopMovement()
        target.moveTo(tile.x, tile.z, tile.height)
        target.timers[CONFUSION_COOLDOWN] = 10
    }
    true
}

/** Void: move one to three random worn items into the inventory while it has space. */
NpcAttacks.onImpact("chaos_elemental", "madness") { npc, target ->
    if (target is Player) {
        repeat(npc.world.random(1..3)) {
            if (target.inventory.freeSlotCount == 0) return@onImpact true
            val worn = (0 until target.equipment.capacity).filter { target.equipment[it] != null }
            if (worn.isEmpty()) return@onImpact true
            EquipAction.unequip(target, worn.random())
        }
    }
    true
}
