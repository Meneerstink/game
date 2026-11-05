package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.cfg.Npcs
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

    /**
     * The Archaeologist's offer, for players who finished Desert Treasure II. OSRS Wiki "Transcript:Mysterious Bandit" (current option
     * "I need another ring." / "Very well, but it will cost you...", then the purchase as the historical dialogue words it, which is the
     * shop's whole content here: "Pay 75,000 for a Ring of Shadows?", "Seems I don't have enough coins..." / "Well off you go then.",
     * "You will need some space to hold it first.", "The bandit hands you a Ring of Shadows." + "Take good care of that. It's very poweful.").
     * The Archaeologist speaks the bandit's lines; the item box names him instead of the bandit.
     */
    suspend fun offer(task: QueueTask) {
        val player = task.player
        val npc = Npcs.ARCHAEOLOGIST
        task.chatPlayer("I need another ring.")
        task.chatNpc("Very well, but it will cost you...", npc = npc)
        if (task.options("Yes.", "No.", title = "Pay 75,000 for a Ring of Shadows?") != 1) return
        if (player.inventory.getItemCount(Items.COINS_995) < PRICE) {
            task.chatPlayer("Seems I don't have enough coins...")
            task.chatNpc("Well off you go then.", npc = npc)
            return
        }
        // The coins leave the slot they fill only when the stack is exactly 75,000; any other stack needs a free slot.
        if (player.inventory.isFull && player.inventory.getItemCount(Items.COINS_995) > PRICE) {
            task.chatNpc("You will need some space to hold it first.", npc = npc)
            return
        }
        if (!player.inventory.remove(Items.COINS_995, PRICE).hasSucceeded()) return
        player.inventory.add(Items.RING_OF_SHADOWS_UNCHARGED)
        task.itemMessageBox("The archaeologist hands you a Ring of Shadows.", Items.RING_OF_SHADOWS_UNCHARGED)
        task.chatNpc("Take good care of that. It's very poweful.", npc = npc)
    }
}
