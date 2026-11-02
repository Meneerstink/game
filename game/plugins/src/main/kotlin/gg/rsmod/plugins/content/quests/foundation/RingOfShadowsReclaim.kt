package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.mechanics.ownership.Possessions

/**
 * N501 Ring of shadows (owner 2026-11-01 14:1x): accounts that finished Desert Treasure II before the ring was part of the reward
 * get it once at login, and a lost ring can be had again ("ja en ook een plek waar je de ring kunt terughalen").
 *
 * OSRS Wiki "Ring of shadows": "If lost, another can be obtained from the Mysterious Bandit in the Ancient Vault for 75,000 coins."
 * The Ancient Vault does not exist in this world, so the Archaeologist in the Grand Exchange hall, who starts and ends the quest
 * here, sells the replacement on the same terms (75,000 coins, the uncharged ring, more than one allowed like the bandit).
 */
object RingOfShadowsReclaim {
    const val PRICE = 75_000
    val RINGS = setOf(Items.RING_OF_SHADOWS, Items.RING_OF_SHADOWS_UNCHARGED)

    /** Set once the login grant has run for this account (the quest reward sets it too, so a finished quest never pays twice). */
    val GRANTED = AttributeKey<Boolean>(persistenceKey = "ring_of_shadows_granted")

    /** Login: Desert Treasure II finished, never granted, and no ring anywhere -> one uncharged ring (inventory, else bank). */
    fun onLogin(player: Player) {
        if (player.attr[GRANTED] == true || !DesertTreasureII.isFinished(player)) return
        player.attr[GRANTED] = true
        if (Possessions.owns(player, RINGS)) return
        val where = give(player) ?: return
        player.message("You have been given a Ring of shadows for completing Desert Treasure II ($where).")
    }

    /** Puts an uncharged ring in the inventory or, when that is full, the bank; returns where, or null when neither had room. */
    fun give(player: Player): String? {
        if (player.inventory.add(Items.RING_OF_SHADOWS_UNCHARGED).hasSucceeded()) return "in your inventory"
        if (player.bank.add(Item(Items.RING_OF_SHADOWS_UNCHARGED)).hasSucceeded()) return "in your bank"
        return null
    }

    /** The Archaeologist's offer, for players who finished Desert Treasure II. */
    suspend fun offer(task: QueueTask) {
        val player = task.player
        if (task.options("Yes, pay 75,000 coins.", "No.", title = "Buy a Ring of shadows for 75,000 coins?") != 1) return
        if (player.inventory.getItemCount(Items.COINS_995) < PRICE) {
            player.message("You need 75,000 coins to buy a Ring of shadows.")
            return
        }
        if (player.inventory.isFull && player.inventory.getItemCount(Items.COINS_995) > PRICE) {
            player.message("You don't have enough inventory space.")
            return
        }
        player.inventory.remove(Items.COINS_995, PRICE)
        player.inventory.add(Items.RING_OF_SHADOWS_UNCHARGED)
        player.message("You buy a Ring of shadows for 75,000 coins.")
    }
}
