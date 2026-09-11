package gg.rsmod.plugins.content.skills.fletching.crossbows

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.doubleItemMessageBox
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.ext.player
import kotlin.math.min

class CrossbowAction(
    val definitions: DefinitionSet,
) {
    suspend fun assemble(
        task: QueueTask,
        data: CrossbowData,
        amount: Int,
    ) {
        val player = task.player
        val inventory = player.inventory
        val maxCount = min(amount, min(inventory.getItemCount(data.stock), inventory.getItemCount(data.limbs)))

        repeat(maxCount) {
            if (!canAssemble(task, data)) {
                player.animate(Anims.RESET)
                return
            }
            task.wait(2)
            if (!inventory.remove(data.stock, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            if (!inventory.remove(data.limbs, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            inventory.add(data.unstrung, 1)
            player.filterableMessage("You attach the limbs to the stock.")
            player.addXp(Skills.FLETCHING, data.assembleExperience)
            task.wait(1)
        }
    }

    suspend fun string(
        task: QueueTask,
        data: CrossbowData,
        amount: Int,
    ) {
        val player = task.player
        val inventory = player.inventory
        val productName =
            player.world.definitions
                .get(ItemDef::class.java, data.strung)
                .name
                .trim()
                .lowercase()
        val maxCount = min(amount, inventory.getItemCount(data.unstrung))

        repeat(maxCount) {
            if (!canString(task, data)) {
                player.animate(Anims.RESET)
                return
            }
            player.animate(data.stringAnim)
            task.wait(2)
            if (!inventory.remove(data.unstrung, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            if (!inventory.remove(Items.CROSSBOW_STRING, assureFullRemoval = true).hasSucceeded()) {
                return
            }
            inventory.add(data.strung, 1)
            player.filterableMessage("You add a string to the $productName.")
            player.addXp(Skills.FLETCHING, data.stringExperience)
            task.wait(1)
        }
    }

    private suspend fun canAssemble(
        task: QueueTask,
        data: CrossbowData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory
        if (!inventory.contains(data.stock) || !inventory.contains(data.limbs)) {
            return false
        }
        if (player.skills.getCurrentLevel(Skills.FLETCHING) < data.assembleLevelRequirement) {
            task.doubleItemMessageBox(
                "You need a Fletching level of at least ${data.assembleLevelRequirement} to attach these limbs.",
                item1 = data.stock,
                item2 = data.limbs,
            )
            return false
        }
        return true
    }

    private suspend fun canString(
        task: QueueTask,
        data: CrossbowData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory
        if (!inventory.contains(Items.CROSSBOW_STRING)) {
            return false
        }
        if (player.skills.getCurrentLevel(Skills.FLETCHING) < data.stringLevelRequirement) {
            task.itemMessageBox(
                "You need a Fletching level of at least ${data.stringLevelRequirement} to string a ${data.itemName}.",
                item = data.unstrung,
            )
            return false
        }
        return true
    }
}
