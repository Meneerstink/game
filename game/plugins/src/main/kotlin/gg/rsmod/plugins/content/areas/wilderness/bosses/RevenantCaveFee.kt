package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * N522 (owner 2026-11-02 07:4x: "entree fee bij osrs moet" - as OSRS): the Revenant Caves entrance fee.
 *
 * OSRS Wiki "Revenant Caves" / "Cavern (Revenant Caves)" / "Crevice (Revenant Caves)" and the Jagex Halloween 2020 newspost:
 * - "Before entering the caves, players must pay a fee of 100,000 coins. The fee can be taken from the bank and does not need to be
 *   brought along in the inventory." ("taken from the player's bank if they are not currently carrying enough")
 * - "As long as the player does not die inside the cave or get killed by another player, the fee does not need to be repaid. Upon
 *   dying to anything inside the caves (including monsters) or to another player anywhere in the Wilderness, the fee will be lost and
 *   must be repaid before re-entering the caves. If the killer was another player, the 100,000 coins will be dropped as loot."
 * - Leaving by the stairs costs nothing ("Stairs (Revenant Caves)").
 * The prompt: an item box with a coin stack, then "Yes, don't ask again" among the options (Microbot Rs2Walker / DreamBot GoToRevs
 * click exactly that; OSRS varbits WARN_REVENANT_FEE + PAID_REVENANT_ENTRY_FEE). The prompt's own wording is in no source: its text
 * here is the Jagex newspost's ("This fee can be paid from your bank. The payment will allow you to enter the caves as many times as
 * you like until you die within the caves").
 */
object RevenantCaveFee {
    const val FEE = 100_000

    /** OSRS varbit PAID_REVENANT_ENTRY_FEE. */
    val PAID = AttributeKey<Boolean>(persistenceKey = "revenant_cave_fee_paid")

    /** OSRS varbit WARN_REVENANT_FEE ("Yes, don't ask again"). */
    val NO_WARNING = AttributeKey<Boolean>(persistenceKey = "revenant_cave_fee_no_warning")

    const val PROMPT =
        "There is an entrance fee of 100,000 coins. This fee can be paid from your bank. The payment will allow you to enter " +
            "the caves as many times as you like until you die within the caves."

    /** The OSRS Revenant Caves map squares 12701-12703 / 12957-12959 (x 3136-3263, z 10048-10239, plane 0). */
    fun inCaves(tile: Tile): Boolean = tile.height == 0 && tile.x in 3136..3263 && tile.z in 10048..10239

    fun paid(player: Player): Boolean = player.attr[PAID] == true

    fun available(player: Player): Long =
        player.inventory.getItemCount(Items.COINS_995).toLong() + player.bank.getItemCount(Items.COINS_995)

    /** Carried coins first, then the bank; nothing is taken unless the whole fee can be paid. */
    fun pay(player: Player): Boolean {
        if (available(player) < FEE) return false
        val fromInventory = minOf(FEE, player.inventory.getItemCount(Items.COINS_995))
        if (fromInventory > 0) player.inventory.remove(Item(Items.COINS_995, fromInventory), assureFullRemoval = true)
        val left = FEE - fromInventory
        if (left > 0) player.bank.remove(Item(Items.COINS_995, left), assureFullRemoval = true)
        player.attr[PAID] = true
        player.message("You pay the entrance fee of 100,000 coins.")
        return true
    }

    /** A cavern or the crevice: enters when paid, otherwise asks (or pays at once after "Yes, don't ask again"). */
    fun enter(player: Player, enterAction: () -> Unit) {
        if (paid(player)) return enterAction()
        if (player.attr[NO_WARNING] == true) {
            if (pay(player)) enterAction() else notEnough(player)
            return
        }
        player.queue {
            itemMessageBox(PROMPT, item = Items.COINS_995, amountOrZoom = FEE)
            val choice = options("Yes", "Yes, don't ask again", "No", title = "Pay the entrance fee?")
            if (choice != 1 && choice != 2) return@queue
            if (!pay(player)) {
                notEnough(player)
                return@queue
            }
            if (choice == 2) player.attr[NO_WARNING] = true
            enterAction()
        }
    }

    private fun notEnough(player: Player) {
        player.message("You need 100,000 coins in your inventory or bank to pay the entrance fee.")
    }

    /**
     * The fee lost by this death: died inside the caves (to anything), or killed by a player anywhere in the Wilderness.
     * @return the coins for the player killer's loot (empty without a player killer or without a paid fee). Clears the fee.
     */
    fun onDeath(victim: Player, playerKiller: Player?, deathTile: Tile = victim.tile): List<Item> {
        if (!paid(victim)) return emptyList()
        val wilderness = runCatching { deathTile.getWildernessLevel() > 0 }.getOrDefault(false)
        val lost = inCaves(deathTile) || (playerKiller != null && (wilderness || inCaves(deathTile)))
        if (!lost) return emptyList()
        victim.attr[PAID] = false
        return if (playerKiller != null) listOf(Item(Items.COINS_995, FEE)) else emptyList()
    }
}
