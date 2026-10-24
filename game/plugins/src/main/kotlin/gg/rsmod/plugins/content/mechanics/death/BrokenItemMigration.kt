package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry

/**
 * Owner 2026-10-24 ("oude broken items omzetten"): before bundle 4 (N247) an untradeable without an imported broken id broke as a
 * hidden [ItemAttribute.BROKEN] mark on the whole item - same picture and name, so the player could not see it was broken. Now that
 * the OSRS "(broken)" / "(mangled)" items exist, such an item becomes that item: same attributes (charges, ammo) without the mark,
 * so Perdu repairs it back to the same item at the same price. Items without an imported damaged form keep the mark (still shown
 * and repaired as before). Runs at login over every container the player owns.
 */
object BrokenItemMigration {
    /** The imported damaged item [item] stands for, or null when it has no hidden mark or no imported form. */
    fun converted(item: Item): Item? {
        val state = UntradeableDeathProtection.state(item)
        if (state == 0) return null
        val lockable = TrouverRegistry.entryForLocked(item.id)
        val target =
            when (state) {
                UntradeableDeathProtection.STATE_MANGLED -> lockable?.mangledItemId
                else -> lockable?.brokenItemId ?: PvpDeathBreakables.breakableFor(item.id)?.brokenId
            } ?: return null
        return Item(target, item.amount).copyAttr(item).also { it.attr.remove(ItemAttribute.BROKEN) }
    }

    /** Converts every marked item of [player] in place; returns how many stacks changed. */
    fun migrate(player: Player): Int {
        var changed = 0
        listOf(player.inventory, player.bank, player.gravestone, player.deathRecovery).forEach { changed += migrate(it) }
        // A damaged item cannot be worn: a marked worn item (left over from the old rules) goes to the inventory, else the bank.
        var unequipped = false
        for (slot in 0 until player.equipment.capacity) {
            val item = player.equipment[slot] ?: continue
            val replacement = converted(item) ?: continue
            if (player.inventory.freeSlotCount > 0) {
                player.inventory.add(replacement)
            } else if (!player.bank.add(replacement).hasSucceeded()) {
                continue
            }
            player.equipment[slot] = null
            unequipped = true
            changed++
        }
        if (unequipped) player.refreshBonuses()
        return changed
    }

    private fun migrate(container: ItemContainer): Int {
        var changed = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            val replacement = converted(item) ?: continue
            container[slot] = replacement
            changed++
        }
        return changed
    }
}
