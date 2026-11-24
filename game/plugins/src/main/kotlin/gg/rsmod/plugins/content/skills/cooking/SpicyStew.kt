package gg.rsmod.plugins.content.skills.cooking

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items

/** OSRS Wiki Spicy stew rev15352045; Empty spice shaker rev15185887; Red spice rev15241472.
 * The recipe belongs to the item, so copying/banking/reconnecting cannot assign another stew's recipe.
 * OSRS varbits1879..1882 independently confirm four two-bit counts; no OSRS varbit is sent to the 667 client.
 */
object SpicyStew {
    enum class Colour(val label: String, val fourDose: Int) {
        RED("Red", Items.RED_SPICE_4),
        ORANGE("Orange", Items.ORANGE_SPICE_4),
        BROWN("Brown", Items.BROWN_SPICE_4),
        YELLOW("Yellow", Items.YELLOW_SPICE_4),
    }

    fun doses(stew: Item, colour: Colour): Int =
        if (stew.id == Items.SPICY_STEW) ((stew.getAttr(ItemAttribute.SPICY_STEW_DOSES) ?: 0) ushr (colour.ordinal * 2)) and 3 else 0

    /** Only the Wiki's proven dose labels, no invented full Smell sentence or zero-dose text. */
    fun smell(stew: Item): List<String> = Colour.values().mapNotNull { colour ->
        val word = when (doses(stew, colour)) {
            1 -> "A little"
            2 -> "Some"
            3 -> "A lot"
            else -> return@mapNotNull null
        }
        "${colour.label}: $word"
    }

    /** One dose per use; both replacements fit their original slots even in a full inventory. */
    fun addDose(inventory: ItemContainer, stewSlot: Int, shakerSlot: Int): Boolean {
        if (stewSlot !in 0 until inventory.capacity || shakerSlot !in 0 until inventory.capacity || stewSlot == shakerSlot) return false
        val stew = inventory[stewSlot] ?: return false
        val shaker = inventory[shakerSlot] ?: return false
        if (stew.id !in setOf(Items.STEW, Items.SPICY_STEW) || stew.amount != 1 || shaker.amount != 1) return false
        val colour = Colour.values().firstOrNull { shaker.id in it.fourDose..it.fourDose + 3 } ?: return false
        if (stew.id == Items.STEW && inventory.any { it?.id == Items.SPICY_STEW }) return false
        val count = doses(stew, colour)
        if (count == 3) return false
        val next = Item(Items.SPICY_STEW).copyAttr(stew)
        val shift = colour.ordinal * 2
        val bits = stew.getAttr(ItemAttribute.SPICY_STEW_DOSES) ?: 0
        next.putAttr(ItemAttribute.SPICY_STEW_DOSES, (bits and (3 shl shift).inv()) or ((count + 1) shl shift))
        val nextShaker = if (shaker.id == colour.fourDose + 3) Items.EMPTY_SPICE_SHAKER else shaker.id + 1
        inventory[stewSlot] = next
        inventory[shakerSlot] = Item(nextShaker)
        return true
    }
}
