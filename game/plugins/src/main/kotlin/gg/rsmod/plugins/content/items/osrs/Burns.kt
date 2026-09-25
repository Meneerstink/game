package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.hit

/**
 * OSRS burn damage-over-time (OSRS Wiki "Eclipse moon armour" / "Eclipse atlatl", 2026-09-14): "While burning, the target takes 10 burn
 * damage over the course of 40 ticks or 24 seconds (Seen as 1 damage every 4 ticks or 2.4 seconds). Each burn has an individual duration
 * and up to five burns can be applied to a target at a time. At maximum stacks, existing burns must expire before a new burn can be
 * applied." The Eclipse special "will consume the remainder of the burn damage it would have dealt to the target".
 * Burn hits use the OSRS burn hitsplat (HitType.BURN; the client ships OSRS sprite 4767). SOURCE_GAP: the list of burn-immune targets.
 */
object Burns {
    const val DAMAGE = 10
    const val INTERVAL_TICKS = 4
    const val MAX_STACKS = 5

    class Burn(
        var remaining: Int,
    )

    val ACTIVE = AttributeKey<MutableList<Burn>>()

    fun active(target: Pawn): List<Burn> = target.attr[ACTIVE]?.filter { it.remaining > 0 } ?: emptyList()

    /**
     * Starts a burn of [damage] on [target] unless five are already active; returns whether it started. Every burning weapon goes
     * through here (Burning claws, Eclipse set, Scorching bow's 5-damage shackles burn), so the stack cap applies across them and
     * the Eclipse special can consume any of them ("a burn, such as from the armour's set effect or the special attack of the Burning
     * claws, Arkan blade, or Scorching bow").
     */
    fun apply(
        target: Pawn,
        damage: Int = DAMAGE,
    ): Boolean {
        val burns = target.attr[ACTIVE] ?: mutableListOf<Burn>().also { target.attr[ACTIVE] = it }
        burns.removeIf { it.remaining <= 0 }
        if (burns.size >= MAX_STACKS) return false
        val burn = Burn(damage)
        burns.add(burn)
        target.world.queue {
            while (burn.remaining > 0) {
                wait(INTERVAL_TICKS)
                if (burn.remaining <= 0 || target.isDead() || (target is Player && !target.isOnline)) break
                burn.remaining--
                target.hit(damage = 1, type = gg.rsmod.plugins.api.HitType.BURN) // OSRS burn hitsplat (owner 2026-09-19)
            }
            // Audit C-07: a burn that stops (death, logout, consumed) leaves no stack behind; before this a stopped burn kept its
            // remaining damage, counted towards MAX_STACKS and was paid out again by the Eclipse special.
            burn.remaining = 0
            target.attr[ACTIVE]?.remove(burn)
        }
        return true
    }

    /** Audit C-07: drops every burn on [target] without paying out its damage (death). */
    fun clear(target: Pawn) {
        target.attr[ACTIVE]?.forEach { it.remaining = 0 }
        target.attr.remove(ACTIVE)
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
