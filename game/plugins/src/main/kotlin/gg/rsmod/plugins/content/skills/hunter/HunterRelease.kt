package gg.rsmod.plugins.content.skills.hunter

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/** OSRS Wiki Black warlock (item), Red chinchompa + talk page; Void Butterfly.kt visuals. */
object HunterRelease {
    val BUTTERFLY_DELAY = gg.rsmod.game.model.timer.TimerKey()
    private val butterflies = mapOf(
        Items.BLACK_WARLOCK to Triple(Skills.STRENGTH, 914, "black warlock"),
        10016 to Triple(Skills.CONSTITUTION, 915, "snowy knight"),
        10018 to Triple(Skills.DEFENCE, 916, "sapphire glacialis"),
        10020 to Triple(Skills.ATTACK, 917, "ruby harvest"),
    )
    val chinchompas = setOf(Items.RED_CHINCHOMPA_10034, Items.CHINCHOMPA_10033)
    private val salamanders = (10146..10149).toSet()
    val options = butterflies.keys.associateWith { 4 } + (chinchompas + salamanders).associateWith { 5 }

    fun release(player: Player, slot: Int, held: Item, amount: Int): Boolean {
        if (held.id !in options || player.inventory[slot] !== held || held.amount != amount || amount < 1) return false
        val butterfly = butterflies[held.id]
        if (butterfly != null) {
            if (amount != 1) return false
            // Same slot replacement also works when the inventory is full; no item can be lost.
            player.inventory[slot] = Item(Items.BUTTERFLY_JAR)
            if (butterfly.first == Skills.CONSTITUTION) {
                player.heal(15)
            } else {
                val boost = 4 + player.skills.getMaxLevel(butterfly.first) * 15 / 100
                player.skills.alterCurrentLevel(butterfly.first, boost, boost)
            }
            player.animate(5213)
            player.graphic(butterfly.second)
            player.message("You release the ${butterfly.third} butterfly from the jar.")
        } else {
            player.inventory[slot] = null
            player.message(if (held.id in chinchompas) "You release the chinchompa and it bounds away." else "You release the salamander and it darts away.")
        }
        return true
    }
}
