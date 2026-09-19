package gg.rsmod.plugins.content.items.combine

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.confirmItemAction
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.grantOrRefund
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.util.Misc

object CombinationAction {
    suspend fun combine(
        task: QueueTask,
        data: CombinationData,
    ) {
        val player = task.player
        val inventory = player.inventory

        if (!canCombine(task, data)) {
            return
        }

        // Owner 2026-09-18 / 2026-09-19: an ornament kit (or kit-like paint / mix / upgrade kit) says so in the item GUI, and attaching
        // one first asks "Are you sure" in the same item GUI as detaching (Dismantle) does - on the item the kit goes on.
        val kitName = data.items.map { player.world.definitions.get(ItemDef::class.java, it).name.lowercase() }
        val ornament =
            data.resultItem in gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ORNAMENTED_RESULTS ||
                kitName.any { it.contains("ornament kit") }
        if (ornament) {
            val target =
                data.items.firstOrNull { !player.world.definitions.get(ItemDef::class.java, it).name.lowercase().contains("kit") }
                    ?: data.resultItem
            if (!task.confirmItemAction(target, "Are you sure you want to attach the ornament kit to this item?", "The ornament kit will be attached to this item.")) {
                return
            }
            // The items must still be there after the GUI (they may have been moved or dropped meanwhile).
            if (!canCombine(task, data)) {
                return
            }
        }

        data.items.forEach {
            inventory.remove(item = it, assureFullRemoval = true)
        }

        if (!player.grantOrRefund(Item(data.resultItem, 1), data.items.map { Item(it, 1) })) {
            return
        }
        player.addXp(data.skill, data.experience)

        if (ornament) {
            task.itemMessageBox(gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ATTACH_MESSAGE, item = data.resultItem)
            return
        }

        if (data.tool != CombinationTool.NONE) {
            player.filterableMessage(
                "You use your ${player.world.definitions.get(
                    ItemDef::class.java,
                    data.tool.item,
                ).name.lowercase()} to make ${Misc.formatWithIndefiniteArticle(
                    player.world.definitions
                        .get(ItemDef::class.java, data.resultItem)
                        .name
                        .lowercase(),
                )}.",
            )
        } else {
            if (data.message == null) {
                player.filterableMessage(
                    "You combine the items and make ${Misc.formatWithIndefiniteArticle(
                        player.world.definitions
                            .get(ItemDef::class.java, data.resultItem)
                            .name
                            .lowercase(),
                    )}.",
                )
            } else {
                player.filterableMessage(data.message)
            }
        }
    }

    private suspend fun canCombine(
        task: QueueTask,
        data: CombinationData,
    ): Boolean {
        val player = task.player
        val inventory = player.inventory
        val resultName =
            player.world.definitions
                .get(ItemDef::class.java, data.resultItem)
                .name

        if (data.tool != CombinationTool.NONE && !inventory.contains(data.tool.item)) {
            player.message(
                "You need ${Misc.formatWithIndefiniteArticle(
                    player.world.definitions
                        .get(ItemDef::class.java, data.tool.item)
                        .name
                        .lowercase(),
                )} to combine this.",
            )
            return false
        }

        if (!inventory.hasItems(data.items)) {
            return false
        }

        if (player.skills.getCurrentLevel(data.skill) < data.levelRequired) {
            task.itemMessageBox(
                "You need a ${Skills.getSkillName(
                    player.world,
                    data.skill,
                )} level of ${data.levelRequired} to<br>craft ${Misc.formatWithIndefiniteArticle(
                    resultName,
                )}.",
                item = data.resultItem,
            )
            return false
        }
        return true
    }
}
