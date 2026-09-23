package gg.rsmod.plugins.content.skills.fletching.javelins

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.doubleItemMessageBox
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.grantOrRefund
import gg.rsmod.plugins.api.ext.player
import kotlin.math.min

object JavelinAction {
    suspend fun attach(
        task: QueueTask,
        data: JavelinData,
        amount: Int,
    ) {
        val player = task.player
        val inventory = player.inventory
        val maxCount = min(amount, min(inventory.getItemCount(data.shaft), inventory.getItemCount(data.tip)))

        repeat(maxCount) {
            if (!canAttach(task, data)) {
                return
            }
            if (!inventory.remove(data.shaft, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            if (!inventory.remove(data.tip, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            if (!player.grantOrRefund(Item(data.product, 1), listOf(Item(data.shaft, 1), Item(data.tip, 1)))) {
                return
            }
            player.addXp(Skills.FLETCHING, data.experience)
            player.filterableMessage("You attach the javelin tips to the shafts.")
            task.wait(1)
        }
    }

    private suspend fun canAttach(
        task: QueueTask,
        data: JavelinData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory

        if (!inventory.contains(data.shaft) || !inventory.contains(data.tip)) {
            return false
        }

        if (player.skills.getCurrentLevel(Skills.FLETCHING) < data.levelRequirement) {
            task.doubleItemMessageBox(
                "You need a Fletching level of ${data.levelRequirement} to attach these tips.",
                item1 = data.shaft,
                item2 = data.tip,
            )
            return false
        }

        return true
    }
}
