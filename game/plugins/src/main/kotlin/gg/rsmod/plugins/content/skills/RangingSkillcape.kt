package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.MaxCapes

/** OSRS Wiki Ranging cape: one Vorkath's head permanently upgrades all current and future ranging/plain max capes. */
object RangingSkillcape {
    val UPGRADED = AttributeKey<Boolean>(persistenceKey = "ranging_cape_vorkath_head")
    val CAPES = setOf(Items.RANGING_CAPE, Items.RANGING_CAPE_T, MaxCapes.MAX_CAPE)

    fun upgraded(player: Player): Boolean = player.attr[UPGRADED] == true

    /** The actual clicked objects must still be present; no dialogue yield or search for a different copy. */
    fun upgrade(player: Player, first: Item, second: Item): Boolean {
        val head = if (first.id == Items.VORKATHS_HEAD) first else second
        val cape = if (head === first) second else first
        if (upgraded(player) || head.id != Items.VORKATHS_HEAD || head.amount < 1 || cape.amount < 1 || cape.id !in CAPES) return false
        val inventory = player.inventory
        val headSlot = inventory.indexOfFirst { it === head }
        if (headSlot < 0 || inventory.none { it === cape }) return false
        inventory[headSlot] = if (head.amount == 1) null else Item(head, head.amount - 1)
        player.attr[UPGRADED] = true
        return true
    }

    fun attractionDevice(player: Player, cape: Int): Int =
        if (cape in CAPES) {
            if (upgraded(player)) Items.AVAS_ASSEMBLER else Items.AVAS_ACCUMULATOR
        } else cape
}
