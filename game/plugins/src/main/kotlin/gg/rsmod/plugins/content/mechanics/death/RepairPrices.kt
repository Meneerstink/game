package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry

/**
 * The one repair price of every untradeable (owner 2026-09-26): what Perdu charges to repair it after a PvP death, what the
 * killer receives when it breaks (or is destroyed without an OSRS coin amount) and what it is worth when the keep-1 ranking
 * picks the most valuable item.
 *
 * - OSRS amount where sourced: [PvpDeathBreakables] entries (Avernic defender, Infernal cape, capes ...), Fire cape 150,000
 *   (OSRS Wiki "Fire cape": "must be repaired by using it on Perdu and paying him 150,000 coins"), Dizana's quiver 270,000
 *   (item page, see [QuiverDeathRules]).
 * - Otherwise 25 % of the cache store price, at least 10,000.
 * - A Trouver-locked item costs what its unlocked item costs; a MANGLED locked item costs [MANGLED_REPAIR] (OSRS Wiki
 *   "Trouver parchment": the killer receives the repair fee; "Infernal cape": mangled - "The PKer receives 500,000").
 */
object RepairPrices {
    const val MIN_REPAIR = 10_000L
    const val STORE_PERCENT = 25L
    const val MANGLED_REPAIR = 500_000L

    /** The item definition, or null when the id is unknown (never throws on a partly loaded definition set). */
    fun itemDef(
        definitions: DefinitionSet,
        itemId: Int,
    ): ItemDef? = runCatching { definitions.getNullable(ItemDef::class.java, itemId) }.getOrNull()

    private val osrs: Map<Int, Long> by lazy {
        val map = HashMap<Int, Long>()
        PvpDeathBreakables.ALL.forEach { map[it.itemId] = it.repairCost.toLong() }
        map[Items.FIRE_CAPE] = 150_000L
        for (quiver in intArrayOf(Items.DIZANAS_QUIVER, Items.DIZANAS_QUIVER_UNCHARGED, Items.BLESSED_DIZANAS_QUIVER)) map[quiver] = 270_000L
        map
    }

    /**
     * OSRS coins for the killer when an unlocked untradeable is destroyed above level 20 (Dizana's quiver item page: "fully
     * destroyed for 9,600 coins"). Every other item pays its [repairPrice] (owner 2026-09-26).
     */
    private val destroyCoins: Map<Int, Long> =
        mapOf(Items.DIZANAS_QUIVER to 9_600L, Items.DIZANAS_QUIVER_UNCHARGED to 9_600L, Items.BLESSED_DIZANAS_QUIVER to 9_600L)

    /** The unlocked, unbroken item [itemId] stands for (a locked, broken or mangled id maps back to its base item). */
    fun baseItem(itemId: Int): Int {
        PvpDeathBreakables.forBroken(itemId)?.let { return it.itemId }
        val locked = TrouverRegistry.entryForDamaged(itemId)?.lockedItemId ?: itemId
        return TrouverRegistry.entryForLocked(locked)?.baseItemId ?: locked
    }

    fun repairPrice(
        definitions: DefinitionSet,
        itemId: Int,
    ): Long {
        val base = baseItem(itemId)
        osrs[base]?.let { return it }
        val cost = itemDef(definitions, base)?.cost?.toLong() ?: 0L
        return maxOf(MIN_REPAIR, cost * STORE_PERCENT / 100L)
    }

    /** Coins the killer receives when an unlocked untradeable is destroyed in deep Wilderness. */
    fun destroyCoins(
        definitions: DefinitionSet,
        itemId: Int,
    ): Long = destroyCoins[baseItem(itemId)] ?: repairPrice(definitions, itemId)
}
