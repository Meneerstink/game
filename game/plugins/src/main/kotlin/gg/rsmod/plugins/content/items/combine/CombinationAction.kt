package gg.rsmod.plugins.content.items.combine

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
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

        data.items.forEach {
            inventory.remove(item = it, assureFullRemoval = true)
        }

        if (!player.grantOrRefund(Item(data.resultItem, 1), data.items.map { Item(it, 1) })) {
            return
        }
        player.addXp(data.skill, data.experience)

        // Owner 2026-09-18: every ornament kit (and kit-like paint / mix / upgrade kit) put on an item says so in the item GUI.
        val kitName = data.items.map { player.world.definitions.get(ItemDef::class.java, it).name.lowercase() }
        if (data.resultItem in gg.rsmod.plugins.content.items.osrs.OsrsOrnamentKits.ORNAMENTED_RESULTS ||
            kitName.any { it.contains("ornament kit") }
        ) {
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
