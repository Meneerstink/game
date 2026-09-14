package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.format
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player

/**
 * Q-043-e God Wars Frozen key charges and kill-count drain (RuneScape Wiki revisions before EoC, fetched 2026-09-14):
 * - Frozen key (rev 2011-12-12): "Once created, the key has five charges. A charge will be used each time the frozen door is
 *   opened." Repair by Bob: "The cost of repairing one charge varies from 49,200 coins at 1 Smithing to 10,000 coins at 99
 *   Smithing, with 400 coins saved per Smithing level." "The God Wars frozen key will no longer get destroyed when running out of
 *   charges. It will need recharging before it will unlock the frozen door again" (Patch Notes 19 April 2011, same sentence).
 * - Bob (smith) (rev 2011-12-20): "Bob can recharge the frozen key".
 * - God Wars Dungeon (rev 2011-12-30): "Players who die or leave the dungeon receive the message "The power of all those you slew
 *   in the dungeon drains from your body," meaning their kill count has been reset."
 * ADAPTED: a key with no charges is refused with the existing key-hole text (no 2011 refusal text found); Bob's recharge dialogue
 * lines follow the Barrows repair dialogue (no 2011 transcript). SOURCE_GAP: base or boosted Smithing (base used). OWNER QUESTIONS:
 * 22 destruction at the last charge (owner addendum vs the April 2011 patch), 23 The Dig Site gate on assembly (quest not built).
 */
object FrozenKey {
    const val MAX_CHARGES = 5
    const val MSG_KILLCOUNT_DRAINED = "The power of all those you slew in the dungeon drains from your body."

    /** A key assembled before charges existed has never opened the door: it counts as full. */
    fun charges(item: Item): Int = if (item.id == Items.FROZEN_KEY_20120) item.attr[ItemAttribute.CHARGES] ?: MAX_CHARGES else 0

    fun withCharges(
        item: Item,
        charges: Int,
    ): Item = Item(item.id, item.amount).copyAttr(item).also { it.attr[ItemAttribute.CHARGES] = charges.coerceIn(0, MAX_CHARGES) }

    fun costPerCharge(smithingLevel: Int): Int = 49_600 - 400 * smithingLevel.coerceIn(1, 99)

    fun rechargeCost(
        charges: Int,
        smithingLevel: Int,
    ): Int = (MAX_CHARGES - charges).coerceAtLeast(0) * costPerCharge(smithingLevel)

    fun chargedSlot(player: Player): Int? =
        (0 until player.inventory.capacity).firstOrNull { slot -> player.inventory[slot]?.let { charges(it) > 0 } == true }

    fun rechargeSlot(player: Player): Int? =
        (0 until player.inventory.capacity).firstOrNull { slot ->
            player.inventory[slot]?.let { it.id == Items.FROZEN_KEY_20120 && charges(it) < MAX_CHARGES } == true
        }

    suspend fun recharge(task: QueueTask) {
        val player = task.player
        val slot = rechargeSlot(player) ?: return
        val key = player.inventory[slot] ?: return
        val cost = rechargeCost(charges(key), player.skills.getMaxLevel(Skills.SMITHING))
        task.chatNpc("I can recharge your frozen key for ${cost.format()} coins. Shall I go ahead?")
        if (task.options("Yes, please.", "No, thanks.") != 1) {
            return
        }
        if (player.inventory[slot]?.id != Items.FROZEN_KEY_20120) {
            return
        }
        if (player.inventory.getItemCount(Items.COINS_995) < cost) {
            task.chatNpc("You don't have enough coins for that.")
            return
        }
        player.inventory.remove(Items.COINS_995, cost)
        player.inventory[slot] = withCharges(player.inventory[slot]!!, MAX_CHARGES)
        task.chatNpc("There you go - good as new!")
    }
}
