package gg.rsmod.plugins.content.mechanics.pvp.breach

/**
 * One breach monster's damage ledger, keyed by lower-case account name (stable across relogs) in first-damage order.
 *
 * Two rankings are read from it and must never be confused (OSRS Wiki, Deadman: Annihilation):
 *  * loot: "Drops are received by the first 16 players to deal damage to a breach monster" - [lootEligible], by ORDER;
 *  * points: "The top 100 damage dealers to breach NPCs will earn 1 point per damage dealt" - [pointEarners], by AMOUNT.
 */
class BreachContribution {
    private val damage = LinkedHashMap<String, Int>()

    val size: Int get() = damage.size

    fun add(
        username: String,
        amount: Int,
    ) {
        if (amount <= 0) return
        val key = username.lowercase()
        damage[key] = (damage[key] ?: 0) + amount
    }

    fun damageOf(username: String): Int = damage[username.lowercase()] ?: 0

    /** The first [BreachLoot.ELIGIBLE] accounts to deal damage, in that order - not the biggest hitters. */
    fun lootEligible(): List<String> = damage.keys.take(BreachLoot.ELIGIBLE)

    /** The [BreachPoints.EARNERS] biggest damage dealers with their damage; ties keep first-damage order. */
    fun pointEarners(): List<Pair<String, Int>> =
        damage.entries.sortedByDescending { it.value }.take(BreachPoints.EARNERS).map { it.key to it.value }
}
