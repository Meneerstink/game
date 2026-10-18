package gg.rsmod.game.model.attr

import gg.rsmod.game.model.Trace

/**
 * N106 (owner 2026-10-17): the ONE way to change a persistent point balance - the three 78 Store currencies (Donator, Deadman,
 * Loyalty = [LOYALTY_POINTS]), Breach, PK, Slayer ([SLAYER_POINTS]), Pest Control and Penguin points. They are signed 32-bit
 * attributes: a balance never goes below 0 and never wraps past [MAX] (2,147,483,647); every add stops at the maximum.
 *
 * Donor: Void `VariableStore.inc(key, amount, max = Int.MAX_VALUE)` / `dec(key, amount, min = 0)`
 * (Donors/void/engine/src/main/kotlin/world/gregs/voidps/engine/client/variable/VariableStore.kt:24-34): one inc/dec pair for every
 * counter, content never does its own + or -. Unlike Void the sum is made in Long BEFORE the clamp - Void's `value + amount` in Int
 * still wraps at the edge (2,147,457,397 + 26,251 is negative and coerceAtMost does nothing).
 *
 * Source guard: PointBalanceEdgeTests (plugins) - no balance key is assigned anywhere else.
 */
object PointBalance {
    const val MAX: Int = Int.MAX_VALUE

    /** The balance; an absent key is 0. */
    fun of(
        attrs: AttributeMap,
        key: AttributeKey<Int>,
    ): Int = attrs[key] ?: 0

    /**
     * Adds [amount] points and returns the new balance: summed in Long and clamped to 0..[MAX], so it stops at the maximum and
     * never wraps. A balance that was already below 0 (wrapped before N106) counts as 0. A negative [amount] is refused (balance
     * unchanged, PROBLEM trace) - taking goes through [take].
     */
    fun add(
        attrs: AttributeMap,
        key: AttributeKey<Int>,
        amount: Int,
    ): Int {
        val current = of(attrs, key)
        if (amount < 0) {
            Trace.problem(Trace.Cat.PLUGIN) { "point balance ${key.persistenceKey}: refused add of $amount (negative) on $current" }
            return current
        }
        val sum = current.coerceAtLeast(0).toLong() + amount
        val total = sum.coerceAtMost(MAX.toLong()).toInt()
        if (sum > MAX) Trace.log(Trace.Cat.PLUGIN) { "point balance ${key.persistenceKey}: $current + $amount stops at the maximum $MAX" }
        attrs[key] = total
        return total
    }

    /**
     * Takes [amount] points when the balance holds them: returns true and lowers the balance, never below 0. Otherwise (too few
     * points, or a negative [amount]) returns false and the balance is unchanged.
     */
    fun take(
        attrs: AttributeMap,
        key: AttributeKey<Int>,
        amount: Int,
    ): Boolean {
        val current = of(attrs, key)
        if (amount < 0) {
            Trace.problem(Trace.Cat.PLUGIN) { "point balance ${key.persistenceKey}: refused take of $amount (negative) from $current" }
            return false
        }
        if (current < amount) return false
        attrs[key] = current - amount
        return true
    }

    /** Admin adjustments (`givepoints` with a negative amount): balance + [delta], clamped to 0..[MAX]. Returns the new balance. */
    fun adjust(
        attrs: AttributeMap,
        key: AttributeKey<Int>,
        delta: Long,
    ): Int = set(attrs, key, of(attrs, key).coerceAtLeast(0).toLong() + delta)

    /** Sets the balance to [value] clamped to 0..[MAX] (`maxpoints`, dev setters, scenario setup). Returns the new balance. */
    fun set(
        attrs: AttributeMap,
        key: AttributeKey<Int>,
        value: Long,
    ): Int {
        val total = value.coerceIn(0L, MAX.toLong()).toInt()
        attrs[key] = total
        return total
    }

    // ------------------------------------------------------------------------------------------ one-time login repair

    /**
     * Persistence keys of every point balance (the keys of the 78 Store currencies, Slayer, PK, Breach, Pest Control, Penguin).
     * The save loader repairs by key name, before any login hook runs; PointBalanceEdgeTests checks that every balance key used in
     * the sources is listed here.
     */
    val PERSISTENCE_KEYS: List<String> =
        listOf(
            "donator_points",
            "deadman_points",
            "loyalty_points",
            "slayer_points",
            "pk_points",
            "breach_points",
            "pest_points",
            "penguin_points",
        )

    /** Saved on the account once the repair ran, so it never runs a second time. */
    val REPAIR_MARKER = AttributeKey<Int>(persistenceKey = "point_balance_repair")

    /** A balance below this can only come from a wrap at the top (2,147,483,647 + n): it was a maximum balance. */
    const val WRAPPED_BELOW: Int = -2_000_000_000

    /** One repaired balance: the persistence key with the old and the new value (logged by the save loader). */
    data class Repair(
        val key: String,
        val old: Int,
        val new: Int,
    )

    /**
     * Owner decision 2026-10-18 (VRAGEN 32 = A): a balance that already wrapped negative before N106 is repaired ONCE at login.
     * Below [WRAPPED_BELOW] it was a wrap past the maximum -> [MAX]; any other negative -> 0 (small negatives get no gift). Runs
     * once per account ([REPAIR_MARKER]), so it is idempotent. Returns what was changed (empty when the marker is set or nothing
     * is negative).
     */
    fun repairWrapped(attrs: AttributeMap): List<Repair> {
        if (attrs.has(REPAIR_MARKER)) return emptyList()
        val repairs = mutableListOf<Repair>()
        PERSISTENCE_KEYS.forEach { name ->
            val key = AttributeKey<Int>(persistenceKey = name)
            val old = (attrs.getOrNull(key) as? Number)?.toInt() ?: return@forEach
            if (old >= 0) return@forEach
            val repaired = if (old < WRAPPED_BELOW) MAX else 0
            attrs[key] = repaired
            repairs += Repair(name, old, repaired)
        }
        attrs[REPAIR_MARKER] = 1
        return repairs
    }
}
