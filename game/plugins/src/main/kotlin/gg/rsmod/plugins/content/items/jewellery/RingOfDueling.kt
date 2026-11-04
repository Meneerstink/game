package gg.rsmod.plugins.content.items.jewellery

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/** N530: Wiki Ring of dueling oldid=15386835. Keep existing arenas; replace retired minigames with Ferox. */
object RingOfDueling {
    val ITEMS = (Items.RING_OF_DUELLING_8..Items.RING_OF_DUELLING_1 step 2).toList()
    val FEROX = Tile(3151, 3635)
    val DESTINATIONS = linkedMapOf("Duel Arena" to Tile(3308, 3234), "Castle Wars" to Tile(2440, 3089), "Ferox Enclave" to FEROX)

    fun replacement(id: Int): Int? = ITEMS.indexOf(id).takeIf { it >= 0 }?.let { ITEMS.getOrNull(it + 1) }

    /** Capture slot/item before dialogue/countdown; moving/replacing a ring never buys a free teleport. */
    fun spend(player: Player, used: Item, slot: Int, equipped: Boolean): Boolean {
        if (used.id !in ITEMS) return false
        val container = if (equipped) player.equipment else player.inventory
        if (container[slot] !== used) return false
        container[slot] = replacement(used.id)?.let { Item(it) }
        return true
    }

    fun use(player: Player, destination: Tile, used: Item, slot: Int, equipped: Boolean) {
        player.canTeleport(TeleportType.FEROCIOUS_RING) { // jewellery visuals, OSRS dueling limit: level 20
            if (!spend(player, used, slot, equipped)) return@canTeleport
            player.teleport(destination, TeleportType.FEROCIOUS_RING)
            val remaining = ITEMS.size - ITEMS.indexOf(used.id) - 1
            player.message(if (remaining == 0) "Your ring of duelling crumbles to dust." else "Your ring of duelling has $remaining uses left.")
        }
    }
}
