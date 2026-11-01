package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.format
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.playSound

/**
 * Perdu (local npc 14394, Grand Exchange) repairs every item a PvP death damaged: imported broken ids ([PvpDeathBreakables]),
 * broken/mangled Trouver-locked ids and attribute-broken untradeables ([UntradeableDeathProtection]). OSRS Wiki: every
 * PvP-broken untradeable ("Fire cape", "Infernal cape", "Avernic defender", "Trouver parchment") is repaired "by using it on
 * Perdu"; Bob repairs degraded Barrows gear, not death damage, so this service is Perdu's only. The price is
 * [UntradeableDeathProtection.repairCost]: the item's repair price, or 500,000 for a mangled locked item.
 */
object BrokenItemRepair {
    /**
     * @param playerLine what the player says first; null when he already asked (N508: Perdu's Talk-to option he chose). Using a
     *   damaged item on Perdu asks with this line, as the player's own action.
     */
    suspend fun repair(
        task: QueueTask,
        playerLine: String? = "Can you repair this for me?",
    ) {
        val player = task.player
        val definitions = player.world.definitions
        val damaged = (0 until player.inventory.capacity).filter { slot -> player.inventory[slot]?.let(UntradeableDeathProtection::isDamaged) == true }
        if (damaged.isEmpty()) return
        playerLine?.let { task.chatPlayer(it) }
        val total =
            damaged.sumOf { UntradeableDeathProtection.repairCost(definitions, player.inventory[it]!!) }
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        task.chatNpc("I can repair that for a total of ${total.format()} coins. Shall I go ahead?")
        if (task.options("Yes, please.", "No, thanks.") != 1) return
        // The inventory may have changed while the dialogue was open: re-check every slot before charging.
        val still = damaged.filter { player.inventory[it]?.let(UntradeableDeathProtection::isDamaged) == true }
        val due = still.sumOf { UntradeableDeathProtection.repairCost(definitions, player.inventory[it]!!) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (player.inventory.getItemCount(Items.COINS_995) < due) {
            task.chatNpc("You don't have enough coins for that.")
            return
        }
        if (due > 0 && !player.inventory.remove(Items.COINS_995, due, assureFullRemoval = true).hasSucceeded()) return
        still.forEach { slot -> player.inventory[slot] = UntradeableDeathProtection.repaired(player.inventory[slot]!!) }
        // Owner 2026-10-24 ("wanneer je een item repairt bij perdu hoor je geen sound"): the anvil strike, as a smith's repair.
        player.playSound(gg.rsmod.plugins.api.cfg.Sfx.ANVIL)
        task.chatNpc("There you go - good as new!")
    }
}