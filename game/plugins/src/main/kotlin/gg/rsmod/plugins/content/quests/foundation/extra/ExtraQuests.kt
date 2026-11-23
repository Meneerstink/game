package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.*

/** Extra short quests, deliberately outside the Armageddon unlock tables. */
object ExtraQuests {
    val ALL: List<ShortQuest> by lazy { listOf(FishingContest, ScorpionCatcher, HauntedMine, TheLostTribe) }
    val OPTIONS: List<Pair<Int, String>> = listOf(232 to "Talk-to", 3679 to "Talk-to", 225 to "Talk-to", 225 to "Pay",
        389 to "Talk-to", 388 to "Talk-to", 385 to "Pick-up", 386 to "Pick-up", 387 to "Pick-up",
        1528 to "Talk-to", 1528 to "Pickpocket", 1543 to "Take",
        2082 to "Talk-to", 2082 to "Pickpocket", 2084 to "Talk-to", 2084 to "Follow", 2086 to "Talk-to", 2086 to "Follow")
    fun bySlot(slot: Int): ShortQuest? = ALL.firstOrNull { it.slot == slot }

    fun held(player: Player, item: Int): Boolean =
        player.inventory.contains(item) || player.bank.contains(item) || player.equipment.contains(item)

    fun reclaim(player: Player, item: Int) {
        if (!held(player, item)) FoundationRewards.grant(player, item, 1)
        else if (!player.inventory.contains(item)) player.message("Your quest item is in your bank or equipment.")
    }

    /** No suspending dialogue between checking and removing an item. */
    fun take(player: Player, item: Int, amount: Int = 1): Boolean {
        if (player.inventory.getItemCount(item) < amount) {
            val name = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).name
            if (player.bank.contains(item)) player.message("Your $name is in your bank. Bring it with you.")
            else player.message("You need to bring $amount $name.")
            return false
        }
        return player.inventory.remove(item, amount).completed == amount
    }

    suspend fun advance(task: QueueTask, quest: ShortQuest, stage: Int) {
        FoundationQuests.completeStep(task.player, quest, stage)
        FoundationQuests.offerNextStep(task, quest, afterKill = false)
    }
}
