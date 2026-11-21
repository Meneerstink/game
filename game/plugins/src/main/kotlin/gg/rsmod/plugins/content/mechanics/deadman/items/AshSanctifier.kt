package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.skills.prayer.scattering.AshData

/**
 * PROJECT48 DMM-ITEMS I-A09, Ash sanctifier (OSRS Wiki, read 21-11): charged with death runes (10 charges each); carried and charged, it purifies
 * the demonic ashes that drop from monsters at once for HALF the Prayer XP of scattering them (full XP needs the Elite Kourend diary, which this
 * server does not have), one charge per ash. The Activity option toggles it; the charges are kept on death. Only the infernal ashes are
 * "demonic ashes" on this server (the 667 impious/accursed ashes are not).
 */
object AshSanctifier {
    const val CHARGES_PER_RUNE = 10
    val OFF = AttributeKey<Boolean>(persistenceKey = "ash_sanctifier_off")

    private val DEMONIC = intArrayOf(Items.INFERNAL_ASHES, Items.INFERNAL_ASHES_22312)

    fun charges(item: Item?): Int = item?.attr?.get(ItemAttribute.CHARGES) ?: 0

    /** Returns true when the drop [item] was used up by the sanctifier. */
    fun purify(
        player: Player,
        item: Item,
    ): Boolean {
        if (item.id !in DEMONIC || player.attr[OFF] == true) return false
        val slot = player.inventory.indexOfFirst { it?.id == DmmItems.ASH_SANCTIFIER && charges(it) > 0 }
        if (slot < 0) return false
        val sanctifier = player.inventory[slot] ?: return false
        val used = minOf(item.amount, charges(sanctifier))
        val left = charges(sanctifier) - used
        if (left > 0) sanctifier.attr[ItemAttribute.CHARGES] = left else sanctifier.attr.remove(ItemAttribute.CHARGES)
        val xp = AshData.ashDefinitions[item.id]?.experience ?: return false
        player.addXp(Skills.PRAYER, xp * 0.5 * used)
        player.filterableMessage("Your ash sanctifier purifies the ashes.")
        return used >= item.amount
    }

    /** Using death runes on the sanctifier: all of them become charges. */
    fun charge(player: Player) {
        val slot = player.inventory.indexOfFirst { it?.id == DmmItems.ASH_SANCTIFIER }
        val runes = player.inventory.getItemIndex(Items.DEATH_RUNE, skipAttrItems = false)
        if (slot < 0 || runes < 0) return
        val amount = player.inventory[runes]?.amount ?: return
        if (!player.inventory.remove(Items.DEATH_RUNE, amount).hasSucceeded()) return
        val sanctifier = player.inventory[slot] ?: return
        val total = charges(sanctifier).toLong() + amount.toLong() * CHARGES_PER_RUNE
        sanctifier.attr[ItemAttribute.CHARGES] = total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        player.message("You add $amount death runes; the sanctifier now has ${charges(sanctifier)} charges.")
    }

    fun check(
        player: Player,
        item: Item?,
    ) {
        player.message("Your ash sanctifier has ${charges(item)} charges and is ${if (player.attr[OFF] == true) "switched off" else "switched on"}.")
    }

    fun toggle(player: Player) {
        val off = player.attr[OFF] != true
        player.attr[OFF] = off
        player.message("The ash sanctifier is now ${if (off) "off" else "on"}.")
    }

    fun uncharge(
        player: Player,
        slot: Int,
    ) {
        val item = player.inventory[slot] ?: return
        val runes = charges(item) / CHARGES_PER_RUNE
        // The runes must fit before the charges go: with a full inventory the charges were removed and the runes lost.
        if (runes > 0 && !player.inventory.add(Items.DEATH_RUNE, runes).hasSucceeded()) {
            player.message("You have no room in your inventory for the death runes.")
            return
        }
        item.attr.remove(ItemAttribute.CHARGES)
        player.message("You remove the charges and get $runes death runes back.")
    }
}
