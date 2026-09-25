package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.magic.RunePouch

/**
 * Items taken out of the game (owner 2026-09-24: "Verwijder Armadyl runes volledig (dus elke trace ervan grand exchange shops spells)",
 * "Verwijder aether rune volledig!"). Their cache definitions stay, because item ids must stay contiguous, but nothing may offer,
 * trade or keep them: the Grand Exchange refuses them ([gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.exchangeable]),
 * the spells, rune pouch and runecrafting no longer know them, and [purge] takes any copy out of a player's inventory, worn items,
 * bank and rune pouches at login. One list, so every route asks the same question.
 */
object RemovedItems {
    val IDS: Set<Int> = setOf(Items.ARMADYL_RUNE, Items.AETHER_RUNE, Items.AETHER_CATALYST)

    /** True for a removed item or its noted form. */
    fun isRemoved(def: ItemDef): Boolean = def.id in IDS || (def.noted && def.noteLinkId in IDS)

    fun isRemoved(
        player: Player,
        itemId: Int,
    ): Boolean = itemId in IDS || isRemoved(player.world.definitions.get(ItemDef::class.java, itemId))

    /** Removes every removed item the player holds; returns how many item stacks were taken. */
    fun purge(player: Player): Int {
        var taken = 0
        for (container in listOf(player.inventory, player.equipment, player.bank)) {
            taken += purge(player, container)
        }
        return taken
    }

    private fun purge(
        player: Player,
        container: ItemContainer,
    ): Int {
        var taken = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            if (isRemoved(player, item.id)) {
                container[slot] = null
                taken++
                continue
            }
            if (RunePouch.isPouch(item.id)) {
                val runes = RunePouch.contents(item)
                val kept = runes.filterNot { it.id in IDS }
                if (kept.size != runes.size) {
                    container[slot] = RunePouch.withContents(item, kept)
                    taken += runes.size - kept.size
                }
            }
        }
        if (taken > 0) container.dirty = true
        return taken
    }
}
