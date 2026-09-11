package gg.rsmod.plugins.content.skills.fletching.tippedbolts

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.doubleItemMessageBox
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.player
import kotlin.math.min

object TippedBoltAction {
    suspend fun attach(
        task: QueueTask,
        data: TippedBoltData,
        amount: Int,
    ) {
        val player = task.player
        val inventory = player.inventory
        val maxCount = min(amount, min(inventory.getItemCount(data.plainBolt), inventory.getItemCount(data.tip)))

        repeat(maxCount) {
            if (!canAttach(task, data)) {
                return
            }
            if (!inventory.remove(data.plainBolt, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            if (!inventory.remove(data.tip, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            inventory.add(data.product, 1)
            player.addXp(Skills.FLETCHING, data.experience)
            player.filterableMessage("You attach the bolt tips to the bolts.")
            task.wait(1)
        }
    }

    private suspend fun canAttach(
        task: QueueTask,
        data: TippedBoltData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory

        if (!inventory.contains(data.plainBolt) || !inventory.contains(data.tip)) {
            return false
        }

        if (player.skills.getCurrentLevel(Skills.FLETCHING) < data.levelRequirement) {
            task.doubleItemMessageBox(
                "You need a Fletching level of ${data.levelRequirement} to attach these tips.",
                item1 = data.plainBolt,
                item2 = data.tip,
            )
            return false
        }

        return true
    }
}
