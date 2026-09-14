package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.ext.hit

/**
 * OSRS burn damage-over-time (OSRS Wiki "Eclipse moon armour" / "Eclipse atlatl", 2026-09-14): "While burning, the target takes 10 burn
 * damage over the course of 40 ticks or 24 seconds (Seen as 1 damage every 4 ticks or 2.4 seconds). Each burn has an individual duration
 * and up to five burns can be applied to a target at a time. At maximum stacks, existing burns must expire before a new burn can be
 * applied." The Eclipse special "will consume the remainder of the burn damage it would have dealt to the target".
 * ADAPTED_TO_667: the burn hits use the regular hitsplat (667 has no burn hitsplat). SOURCE_GAP: the list of burn-immune targets.
 */
object Burns {
    const val DAMAGE = 10
    const val INTERVAL_TICKS = 4
    const val MAX_STACKS = 5

    class Burn(
        var remaining: Int = DAMAGE,
    )

    val ACTIVE = AttributeKey<MutableList<Burn>>()

    fun active(target: Pawn): List<Burn> = target.attr[ACTIVE]?.filter { it.remaining > 0 } ?: emptyList()

    /** Starts a burn on [target] unless five are already active; returns whether it started. */
    fun apply(target: Pawn): Boolean {
        val burns = target.attr[ACTIVE] ?: mutableListOf<Burn>().also { target.attr[ACTIVE] = it }
        burns.removeIf { it.remaining <= 0 }
        if (burns.size >= MAX_STACKS) return false
        val burn = Burn()
        burns.add(burn)
        target.world.queue {
            while (burn.remaining > 0) {
                wait(INTERVAL_TICKS)
                if (burn.remaining <= 0 || target.isDead()) break
                burn.remaining--
                target.hit(damage = 1)
            }
        }
        return true
    }

    /** Ends every burn on [target] and returns the damage they had left to deal. */
    fun consumeRemaining(target: Pawn): Int {
        val burns = target.attr[ACTIVE] ?: return 0
        val total = burns.sumOf { it.remaining.coerceAtLeast(0) }
        burns.forEach { it.remaining = 0 }
        burns.clear()
        return total
    }
}
