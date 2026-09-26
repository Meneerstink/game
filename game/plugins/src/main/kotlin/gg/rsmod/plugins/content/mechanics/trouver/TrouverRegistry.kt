package gg.rsmod.plugins.content.mechanics.trouver

/**
 * One base<->locked item pair the [Trouver] locking engine knows how to convert between.
 *
 * @param baseItemId the normal, usable item.
 * @param lockedItemId the "(l)" variant produced by [Trouver.lock] and consumed by [Trouver.unlock].
 * @param brokenItemId optional degraded-but-still-locked variant a locked item should become
 * instead of being destroyed on an unprotected Wilderness death, matching the real mechanic's
 * "broken"/"mangled" states. Left `null` until a real broken-item variant exists for this pair -
 * Unset: the locked item keeps its id and carries [gg.rsmod.game.model.item.ItemAttribute.BROKEN] = 1 instead.
 * @param mangledItemId the imported "mangled" variant (a locked item lost above level 20 Wilderness). Unset: the locked item
 * carries [gg.rsmod.game.model.item.ItemAttribute.BROKEN] = 2 instead. Both states keep the (l) status (OSRS Wiki
 * "Trouver parchment": "No matter what, the locked item will never lose its locked status").
 */
data class TrouverLockable(
    val baseItemId: Int,
    val lockedItemId: Int,
    val brokenItemId: Int? = null,
    val mangledItemId: Int? = null,
)

/**
 * Registry of every item pair [Trouver] can lock/unlock. Deliberately data-driven rather than
 * hard-coded so a future lockable item (once its real ids exist in this cache) is added by
 * registering one entry here - no engine code changes required, per the standing authorization's
 * "future lockable items integrate automatically without needing to exist yet" requirement.
 */
object TrouverRegistry {
    private val byBaseId = mutableMapOf<Int, TrouverLockable>()
    private val byLockedId = mutableMapOf<Int, TrouverLockable>()
    private val byDamagedId = mutableMapOf<Int, TrouverLockable>()

    /** Registers [entry]. Throws if either id is already registered (to a different pair) or the
     * two ids are the same - both indicate a configuration mistake, not a runtime condition to
     * handle gracefully. */
    fun register(entry: TrouverLockable) {
        require(entry.baseItemId != entry.lockedItemId) {
            "TrouverLockable base and locked item ids must differ (got ${entry.baseItemId} for both)."
        }
        require(byBaseId[entry.baseItemId] == null) {
            "Item ${entry.baseItemId} is already registered as a Trouver-lockable base item."
        }
        require(byLockedId[entry.lockedItemId] == null) {
            "Item ${entry.lockedItemId} is already registered as a Trouver locked-item id."
        }
        byBaseId[entry.baseItemId] = entry
        byLockedId[entry.lockedItemId] = entry
        entry.brokenItemId?.let { byDamagedId[it] = entry }
        entry.mangledItemId?.let { byDamagedId[it] = entry }
    }

    /** Clears every registration. Test-only. */
    fun clear() {
        byBaseId.clear()
        byLockedId.clear()
        byDamagedId.clear()
    }

    fun lockableFor(baseItemId: Int): TrouverLockable? = byBaseId[baseItemId]

    fun entryForLocked(lockedItemId: Int): TrouverLockable? = byLockedId[lockedItemId]

    fun isLocked(itemId: Int): Boolean = byLockedId.containsKey(itemId)

    /** The pair whose imported broken or mangled id is [itemId]. */
    fun entryForDamaged(itemId: Int): TrouverLockable? = byDamagedId[itemId]

    /** A locked item in any state (whole, broken or mangled). */
    fun isLockedAnyState(itemId: Int): Boolean = byLockedId.containsKey(itemId) || byDamagedId.containsKey(itemId)

    fun all(): Collection<TrouverLockable> = byBaseId.values
}
