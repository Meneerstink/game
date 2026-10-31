package gg.rsmod.plugins.content.skills.hunter

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/** OSRS Wiki Black warlock (item), Red chinchompa + talk page; Void Butterfly.kt visuals. */
object HunterRelease {
    val options = mapOf(Items.BLACK_WARLOCK to 4, Items.RED_CHINCHOMPA_10034 to 5)

    fun release(player: Player, slot: Int, held: Item, amount: Int): Boolean {
        if (held.id !in options || player.inventory[slot] !== held || held.amount != amount || amount < 1) return false
        if (held.id == Items.BLACK_WARLOCK) {
            if (amount != 1) return false
            // Same slot replacement also works when the inventory is full; no item can be lost.
            player.inventory[slot] = Item(Items.BUTTERFLY_JAR)
            val boost = 4 + player.skills.getMaxLevel(Skills.STRENGTH) * 15 / 100
            player.skills.alterCurrentLevel(Skills.STRENGTH, boost, boost)
            player.animate(5213)
            player.graphic(914)
            player.message("You release the black warlock butterfly from the jar.")
        } else {
            player.inventory[slot] = null
            player.message("You release the chinchompa and it bounds away.")
        }
        return true
    }
}
