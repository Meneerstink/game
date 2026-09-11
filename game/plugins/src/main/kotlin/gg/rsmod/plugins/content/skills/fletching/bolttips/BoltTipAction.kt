package gg.rsmod.plugins.content.skills.fletching.bolttips

import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.ext.player
import kotlin.math.min

object BoltTipAction {
    suspend fun cut(
        task: QueueTask,
        data: BoltTipData,
        amount: Int,
    ) {
        val player = task.player
        val inventory = player.inventory
        val maxCount = min(amount, inventory.getItemCount(data.gem))

        repeat(maxCount) {
            if (!canCut(task, data)) {
                player.animate(Anims.RESET)
                return
            }
            player.animate(data.animation)
            task.wait(2)
            if (!inventory.remove(data.gem, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            inventory.add(data.tip)
            player.addXp(Skills.FLETCHING, data.experience)
            task.wait(1)
        }
    }

    private suspend fun canCut(
        task: QueueTask,
        data: BoltTipData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory

        if (!inventory.contains(Items.CHISEL) || !inventory.contains(data.gem)) {
            return false
        }

        if (player.skills.getCurrentLevel(Skills.FLETCHING) < data.levelRequirement) {
            task.itemMessageBox(
                "You need a Fletching level of ${data.levelRequirement} to cut this into bolt tips.",
                item = data.gem,
            )
            return false
        }

        return true
    }
}
