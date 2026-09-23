package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.items.osrs.DizanasQuiver

/**
 * OSRS Wiki "Dizana's quiver" Death (item page raw wikitext, fetched 2026-09-16): an unprotected
 * Wilderness PvP death drops "any ammo that the quiver was holding, as well as all the charges" -
 * whether the quiver itself ends up lost to the killer or kept because it is Trouver-locked. Applies
 * to every [DizanasQuiver.AMMO_HOLDERS] stack (all six quivers and Dizana's max cape/(l)), since they
 * share the same second-ammunition-slot attributes.
 *
 * SOURCE_GAP (blocking, recorded rather than guessed): the same page also describes the quiver staying
 * with the victim in a broken, unusable state requiring 270,000 coins to repair below level 20
 * Wilderness (270,000 dropped for the killer on an unprotected PvP death there - "everywhere" per the
 * owner's 2026-09-16 deadman-mode note), or being fully destroyed for 9,600 coins above level 20. No
 * "Dizana's quiver (broken)" item exists in this cache - only the six quiver variants 22660-22665 were
 * imported in batch tx-20260913-232047 (`OSRS_IMPORT_MASTER.yml`) - so inventing an id, or dropping
 * those coin amounts without the matching broken/destroyed state, would misrepresent the mechanic
 * rather than reproduce it. That part is intentionally not built here; see `OSRS_IMPORT_MASTER.yml`.
 */
object QuiverDeathRules {
    /**
     * Strips stored ammo/charges from every lost quiver/max-cape stack in [result] before it becomes
     * killer ground loot. Pure: returns the (possibly unchanged) result plus the ammo stacks to drop.
     */
    fun stripLost(result: DeathResolutionResult): Pair<DeathResolutionResult, List<Item>> {
        if (result.context != DeathContext.WILDERNESS_PVP) return result to emptyList()
        val dropped = mutableListOf<Item>()
        var changed = false
        val lost =
            result.itemRisk.lost.map { slotItem ->
                val stripped = strip(slotItem.item, dropped) ?: return@map slotItem
                changed = true
                slotItem.copy(item = stripped)
            }
        if (!changed) return result to emptyList()
        return result.copy(itemRisk = result.itemRisk.copy(lost = lost)) to dropped
    }

    /**
     * Physically strips ammo/charges from every protected (kept - within the keep count, or
     * Trouver-locked) quiver/max-cape stack still sitting in [victim]'s inventory/equipment after a
     * Wilderness PvP death; returns the ammo stacks removed. Unlike [stripLost], a protected stack is
     * never touched by [DeathExecutor], so this mutates the live container directly.
     */
    fun stripProtected(
        victim: Player,
        result: DeathResolutionResult,
    ): List<Item> {
        if (result.context != DeathContext.WILDERNESS_PVP) return emptyList()
        val dropped = mutableListOf<Item>()
        for (slotItem in result.itemRisk.protected) {
            val container =
                when (slotItem.source) {
                    DeathContainerSource.INVENTORY -> victim.inventory
                    DeathContainerSource.EQUIPMENT -> victim.equipment
                }
            val current = container[slotItem.slot] ?: continue
            if (current.id != slotItem.item.id) continue
            val stripped = strip(current, dropped) ?: continue
            container[slotItem.slot] = stripped
        }
        return dropped
    }

    /** Spawns [ammo] as killer-owned ground loot at [victim]'s death tile (a no-op for an empty list). */
    fun dropAmmo(
        world: World,
        victim: Player,
        killer: Player?,
        ammo: List<Item>,
    ) {
        ammo.forEach { world.spawn(GroundItem(it, victim.tile, killer)) }
    }

    private fun strip(
        item: Item,
        dropped: MutableList<Item>,
    ): Item? {
        val stored = DizanasQuiver.storedAmmo(item)
        val result = DizanasQuiver.strippedForDeath(item) ?: return null
        stored?.let { dropped.add(it) }
        return result
    }
}
