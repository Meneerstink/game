package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getVarp

/**
 * N415: earned MA2 state and synchronous inventory transactions. Source: Mage Arena II and its transcript.
 * Owner decision2026-11-14 2B: preserve old auto-completed varp1918 and its rights through an explicit one-time migration.
 * Cape ownership and Breach kills do not independently grant completion.
 * Boss/tracking bindings remain a separate source/cache gate (docs/n415/CLAUDE-HANDOFF.md).
 */
object MageArenaII {
    enum class God(val title: String, val remainsSource: Int, val cape: Int, val imbued: Int, val creature: String) {
        SARADOMIN("Saradomin", 21797, Items.SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE, "Saradominist justiciar"),
        GUTHIX("Guthix", 21798, Items.GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE, "Guthixian Ent"),
        ZAMORAK("Zamorak", 21799, Items.ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE, "Zamorakian Demon");
        val bit: Int get() = 1 shl ordinal
    }

    val STARTED = AttributeKey<Boolean>(persistenceKey = "mage_arena_ii_started")
    val DELIVERED = AttributeKey<Int>(persistenceKey = "mage_arena_ii_delivered")
    val FIRST_IMBUE = AttributeKey<Boolean>(persistenceKey = "mage_arena_ii_first_imbue")
    val CREDITS = AttributeKey<Int>(persistenceKey = "mage_arena_ii_credits")
    val MIGRATED = AttributeKey<Boolean>(persistenceKey = "mage_arena_ii_migrated")
    val LEGACY_COMPLETED = AttributeKey<Boolean>(persistenceKey = "mage_arena_ii_legacy_completed")
    const val ALL_REMAINS = 7
    const val VARP = 1918
    const val SLOT = 193

    // Assets is passed explicitly in tests; production resolves only finalized CacheTransaction allocations.
    class Assets(val item: (Int) -> Int?) {
        val symbol: Int? get() = item(21800)
        fun remains(god: God): Int? = item(god.remainsSource)
        val ready: Boolean get() = symbol != null && God.values().all { remains(it) != null }
    }
    val assets = Assets(MageArenaAssets::item)

    fun legacyCompleted(player: Player): Boolean = player.attr[LEGACY_COMPLETED] == true

    /** Read the old complete value(1) BEFORE writing the new0/1/2 display. Never reinterpret a new started stage. */
    fun migrate(player: Player) {
        if (player.attr[MIGRATED] == true) return
        if (player.attr[STARTED] != true && player.getVarp(VARP) >= 1) {
            player.attr[LEGACY_COMPLETED] = true
            player.attr[STARTED] = true
            player.attr[DELIVERED] = ALL_REMAINS
            player.attr[CREDITS] = ALL_REMAINS
            // Preserve any existing first-reward claim; no item is removed or fabricated.
        }
        player.attr[MIGRATED] = true
    }

    fun isRemains(itemId: Int, assets: Assets = this.assets): Boolean = God.values().any { assets.remains(it) == itemId }

    /** Wiki item destroy text, including the original capitalisation. */
    fun destroyDetails(assets: Assets = this.assets): Map<Int, String> = buildMap {
        assets.symbol?.let { put(it, "Kolodion will offer you a replacement at the Mage Arena bank.") }
        assets.remains(God.SARADOMIN)?.let { put(it, "You will have to locate and defeat the Saradominist Justiciar again.") }
        assets.remains(God.GUTHIX)?.let { put(it, "You will have to locate and defeat the Guthixian Ent again.") }
        assets.remains(God.ZAMORAK)?.let { put(it, "You will have to locate and defeat the Zamorakian Demon again.") }
    }

    /** Confirmation can suspend: recheck the original slot and never consume another item or quest credit. */
    fun destroy(player: Player, itemId: Int, slot: Int, assets: Assets = this.assets): Boolean {
        if (itemId !in destroyDetails(assets) || slot !in player.inventory.rawItems.indices) return false
        if (player.inventory[slot]?.id != itemId) return false
        player.inventory[slot] = null
        return true
    }

    fun started(player: Player): Boolean = player.attr[STARTED] == true
    fun delivered(player: Player, god: God): Boolean = (player.attr[DELIVERED] ?: 0) and god.bit != 0
    fun completed(player: Player): Boolean = started(player) && (player.attr[DELIVERED] ?: 0) and ALL_REMAINS == ALL_REMAINS
    fun displayStage(player: Player): Int = if (completed(player)) 2 else if (started(player)) 1 else 0
    fun canStart(player: Player): Boolean = MageArena.completed(player) && player.skills.getMaxLevel(Skills.MAGIC) >= 75 && GodSpellTraining.mastered(player)

    enum class SymbolResult { GRANTED, OWNED, REQUIREMENTS, INVENTORY_FULL, ASSETS_MISSING }

    fun hasSymbol(player: Player, assets: Assets = this.assets): Boolean =
        assets.symbol?.let { id -> player.containers.values.any { it.contains(id) } } == true

    /** Failed start/replacement never consumes a quest step and cannot create a second owned symbol. */
    fun symbol(player: Player, assets: Assets = this.assets): SymbolResult {
        if (!started(player) && !canStart(player)) return SymbolResult.REQUIREMENTS
        if (!assets.ready) return SymbolResult.ASSETS_MISSING
        if (hasSymbol(player, assets)) {
            player.attr[STARTED] = true
            return SymbolResult.OWNED
        }
        if (!player.inventory.add(assets.symbol!!).hasSucceeded()) return SymbolResult.INVENTORY_FULL
        player.attr[MIGRATED] = true
        player.attr[STARTED] = true
        return SymbolResult.GRANTED
    }

    /** Duplicates are all taken for one credit (explicit Mage Arena II reward note); credits never stack. */
    fun handIn(player: Player, god: God, assets: Assets = this.assets): Int {
        if (!started(player)) return 0
        val remains = assets.remains(god) ?: return 0
        val count = player.inventory.getItemCount(remains)
        if (count == 0 || !player.inventory.remove(remains, count).hasSucceeded()) return 0
        player.attr[DELIVERED] = (player.attr[DELIVERED] ?: 0) or god.bit
        player.attr[CREDITS] = (player.attr[CREDITS] ?: 0) or god.bit
        return count
    }

    /** Ownership includes familiar storage; handing the remains in still requires the player's inventory. */
    fun holdsRemains(player: Player, god: God, assets: Assets = this.assets): Boolean =
        assets.remains(god)?.let { id -> player.containers.values.any { it.contains(id) } } == true

    /** Source symbol prompt: filter delivered remains during quest, owned remains after completion. */
    fun trackable(player: Player, assets: Assets = this.assets): List<God> {
        if (!started(player)) return emptyList()
        return God.values().filter { god ->
            if (!completed(player)) !delivered(player, god)
            else assets.remains(god) != null && !holdsRemains(player, god, assets)
        }
    }

    enum class ImbueResult { IMBUED, NOT_COMPLETED, NO_CREDIT, NO_CAPE }

    /**
     * The transcript's first reward uses all three remains for ONE cape of choice. Further capes use matching remains.
     * Replace the exact plain cape slot: works in a full inventory, and a stale dialogue cannot consume a different item.
     * Early per-god imbuing is a contradictory Wiki note: not enabled without reconciled evidence.
     */
    fun imbue(player: Player, god: God, slot: Int): ImbueResult {
        if (!completed(player)) return ImbueResult.NOT_COMPLETED
        if (slot !in player.inventory.rawItems.indices || player.inventory[slot]?.id != god.cape) return ImbueResult.NO_CAPE
        val first = player.attr[FIRST_IMBUE] != true
        val credits = player.attr[CREDITS] ?: 0
        if (!first && credits and god.bit == 0) return ImbueResult.NO_CREDIT
        player.inventory[slot] = Item(god.imbued)
        player.attr[FIRST_IMBUE] = true
        player.attr[CREDITS] = if (first) 0 else credits and god.bit.inv()
        return ImbueResult.IMBUED
    }

    /** Factual journal text assembled from the walkthrough; individual collected/handed-in state is never conflated. */
    fun journal(player: Player, assets: Assets = this.assets): List<String> = buildList {
        if (!started(player)) {
            add("Speak to Kolodion in the Mage Arena bank about additional challenges.")
            add("Requirements: Mage Arena I, 75 Magic and 100 casts of each god spell inside the arena.")
            return@buildList
        }
        God.values().forEach { god ->
            add(when {
                delivered(player, god) -> "I have given Kolodion the remains of the ${god.creature}."
                holdsRemains(player, god, assets) -> "I should take the remains of the ${god.creature} to Kolodion."
                else -> "I must use the enchanted symbol to find and defeat the ${god.creature}."
            })
        }
        if (completed(player)) add(if (player.attr[FIRST_IMBUE] == true) "Kolodion can imbue further god capes for the respective creature's remains." else "Kolodion can imbue one god cape of my choice.")
    }
}
