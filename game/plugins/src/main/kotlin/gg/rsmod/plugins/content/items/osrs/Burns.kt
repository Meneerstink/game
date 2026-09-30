package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.hit
import java.lang.ref.WeakReference

/**
 * OSRS burn damage-over-time (OSRS Wiki "Burn" / "Eclipse moon armour" / "Eclipse atlatl"): "While burning, the target takes 10 burn
 * damage over the course of 40 ticks or 24 seconds (Seen as 1 damage every 4 ticks or 2.4 seconds). Each burn has an individual duration
 * and up to five burns can be applied to a target at a time. At maximum stacks, existing burns must expire before a new burn can be
 * applied." The Eclipse special "will consume the remainder of the burn damage it would have dealt to the target".
 *
 * Owner 2026-09-30: the stacks never land as separate hitsplats. One driver per target runs the 4-tick burn cycle and deals ONE burn
 * hitsplat per cycle whose damage is the number of burns still active (1 stack = 1, ..., 5 stacks = 5); every burn keeps its own
 * lifetime (its remaining damage) and drops out when that reaches zero. Before this every [apply] started its own world queue, so
 * five stacks were five independent loops and five simultaneous 1-damage splats.
 *
 * Burn hits use the OSRS burn hitsplat (HitType.BURN; the client ships OSRS sprite 4767). Each point of burn damage is credited to the
 * pawn whose attack started that burn (kill attribution / drop ownership).
 *
 * Immunity (OSRS Wiki "Burn"): "Certain monsters are immune to burns at or below a certain severity" - strong (Eclipse set, Arkan blade),
 * normal (burning claws, scorching bow), weak; [STRONG_IMMUNE] / [NORMAL_IMMUNE] are the wiki's named monsters (no npc metadata carries
 * it). "Burn damage can be cleared ... by entering or leaving a player-owned house": the driver ends every burn when the target's
 * house state changes.
 */
object Burns {
    const val DAMAGE = 10
    const val INTERVAL_TICKS = 4
    const val MAX_STACKS = 5

    enum class Severity { WEAK, NORMAL, STRONG, INCENDIARY }

    /** OSRS Wiki "Burn": "the only NPCs currently known to be immune to strong burns" (and so to every weaker one). */
    private val STRONG_IMMUNE = setOf("tzkal-zuk", "slug prince", "gemstone crab", "mad angel", "blood moon", "blue moon", "eclipse moon")

    /** "Cerberus and the red phase of Alchemical Hydra are immune to normal burns." (the red phase is not a separate npc here) */
    private val NORMAL_IMMUNE = setOf("cerberus")

    fun isImmune(
        target: Pawn,
        severity: Severity,
    ): Boolean {
        if (target !is Npc || severity == Severity.INCENDIARY) return false
        val name = target.def.name.lowercase()
        return when {
            name in STRONG_IMMUNE -> true
            name in NORMAL_IMMUNE -> severity <= Severity.NORMAL
            else -> false
        }
    }

    private fun inHouse(target: Pawn): Boolean = target is Player && gg.rsmod.plugins.content.areas.poh.PlayerHouse.isSafeTile(target.tile)

    /** One burn: [remaining] damage left, dealt [perCycle] per cycle (1 for every player weapon; 5 for the breach pyrelord). */
    class Burn(
        var remaining: Int,
        val source: WeakReference<Pawn>? = null,
        val perCycle: Int = 1,
    )

    val ACTIVE = AttributeKey<MutableList<Burn>>()

    /** The running cycle driver of a target: a token object, so a cleared-then-reburnt target never ends up with two drivers. */
    private val DRIVER = AttributeKey<Any>()

    /** The cycle at which the driver deals its next combined hit (client Timers & Buffs countdown). */
    val NEXT_HIT_CYCLE = AttributeKey<Int>()

    fun active(target: Pawn): List<Burn> = target.attr[ACTIVE]?.filter { it.remaining > 0 } ?: emptyList()

    /** The damage the next burn hitsplat on [target] will deal (0 = not burning). */
    fun nextHitDamage(target: Pawn): Int = active(target).sumOf { minOf(it.perCycle, it.remaining) }

    /**
     * Starts a burn of [damage] on [target] unless five are already active; returns whether it started. Every burning weapon goes
     * through here (Burning claws, Eclipse set, Scorching bow's 5-damage shackles burn), so the stack cap applies across them and
     * the Eclipse special can consume any of them ("a burn, such as from the armour's set effect or the special attack of the Burning
     * claws, Arkan blade, or Scorching bow").
     */
    fun apply(
        target: Pawn,
        damage: Int = DAMAGE,
        source: Pawn? = null,
        perCycle: Int = 1,
        severity: Severity = Severity.NORMAL,
    ): Boolean {
        if (isImmune(target, severity)) return false
        val burns = target.attr[ACTIVE] ?: mutableListOf<Burn>().also { target.attr[ACTIVE] = it }
        burns.removeIf { it.remaining <= 0 }
        if (burns.size >= MAX_STACKS) return false
        burns.add(Burn(damage, source?.let { WeakReference(it) }, perCycle))
        if (target.attr[DRIVER] == null) startDriver(target)
        return true
    }

    private fun startDriver(target: Pawn) {
        val token = Any()
        val houseAtStart = inHouse(target)
        target.attr[DRIVER] = token
        target.attr[NEXT_HIT_CYCLE] = target.world.currentCycle + INTERVAL_TICKS
        target.world.queue {
            try {
                while (true) {
                    wait(INTERVAL_TICKS)
                    if (target.attr[DRIVER] !== token) return@queue
                    val burns = target.attr[ACTIVE] ?: return@queue
                    burns.removeIf { it.remaining <= 0 }
                    // A despawned npc (breach end, instance closed) or a logged-out player stops burning too.
                    if (burns.isEmpty() || target.isDead() || (target is Player && !target.isOnline) ||
                        (target is Npc && !target.isSpawned()) || inHouse(target) != houseAtStart
                    ) {
                        burns.forEach { it.remaining = 0 }
                        burns.clear()
                        return@queue
                    }
                    // One combined hitsplat: every active burn contributes its damage of this cycle (1 per player-weapon burn).
                    val shares = burns.map { it to minOf(it.perCycle, it.remaining) }
                    shares.forEach { (burn, share) -> burn.remaining -= share }
                    burns.removeIf { it.remaining <= 0 }
                    val damage = shares.sumOf { it.second }
                    val hit = target.hit(damage = damage, type = HitType.BURN)
                    hit.addAction {
                        shares.forEach { (burn, share) -> burn.source?.get()?.let { src -> if (src !== target) target.damageMap.add(src, share) } }
                    }
                    if (target is Player) gg.rsmod.plugins.content.combat.Combat.holdLogout(target)
                    target.attr[NEXT_HIT_CYCLE] = target.world.currentCycle + INTERVAL_TICKS
                    if (burns.isEmpty()) return@queue
                }
            } finally {
                if (target.attr[DRIVER] === token) {
                    target.attr.remove(DRIVER)
                    target.attr.remove(NEXT_HIT_CYCLE)
                    // Audit C-07: a burn that stops (death, logout, despawn) leaves no stack behind for MAX_STACKS or the Eclipse special.
                    target.attr[ACTIVE]?.let { list ->
                        list.removeIf { it.remaining <= 0 }
                        if (list.isEmpty()) target.attr.remove(ACTIVE)
                    }
                }
            }
        }
    }

    /** Audit C-07: drops every burn on [target] without paying out its damage (death). */
    fun clear(target: Pawn) {
        target.attr[ACTIVE]?.forEach { it.remaining = 0 }
        target.attr.remove(ACTIVE)
        target.attr.remove(DRIVER)
        target.attr.remove(NEXT_HIT_CYCLE)
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
