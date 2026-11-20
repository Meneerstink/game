package gg.rsmod.plugins.content.mechanics.pvp.breach

/**
 * One breach monster's damage ledger, keyed by lower-case account name (stable across relogs) in first-damage order.
 *
 * DMM-ARMAGEDDON D3 (owner 20-11-2026, OSRS Wiki Deadman: Armageddon "Breach boss rewards"): "The MvP (player with the most damage
 * to the boss) will have a chance to get a unique drop. The non-MvPs (top 15 players that have dealt damage to the boss) will receive
 * regular breach loot ... Players who did damage to the breach monsters but did not do enough damage to be in the top 15 damage
 * dealers will not receive loot or points for that kill." Owner 20-11: the MvP gets the regular loot too, so the rewarded group is
 * the MvP + the next 15 = [BreachLoot.ELIGIBLE] by DAMAGE ([ranking]); loot and Deadman Points use the same group.
 * (Before: Deadman: Annihilation - the first 16 by ORDER got loot, the top 100 by damage got Breach Points.)
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

    /**
     * The rewarded accounts with their damage, biggest first, at most [BreachLoot.ELIGIBLE]; ties keep first-damage order (stable
     * sort). Index 0 is the MvP.
     */
    fun ranking(): List<Pair<String, Int>> =
        damage.entries.sortedByDescending { it.value }.take(BreachLoot.ELIGIBLE).map { it.key to it.value }

    /** The MvP: most damage (first to deal it on a tie), or null when nobody hit. */
    fun mvp(): String? = ranking().firstOrNull()?.first
}
