package gg.rsmod.plugins.content.mechanics.deadman.items

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanInventory
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids

/** Wiki Ascension bolts rev15402817: one shard + chisel -> ten bolts, 86 Fletching, 140 XP, two ticks. */
object AscensionCrafting {
    fun carve(player: Player): Boolean {
        val shard = Dmm2Ids.item(34669) ?: return false
        val bolts = Dmm2Ids.item(34684) ?: return false
        if (player.skills.getCurrentLevel(Skills.FLETCHING) < 86 || !player.inventory.contains(Items.CHISEL)) return false
        val inventory = ItemContainer(player.inventory)
        if (!inventory.remove(shard, 1, assureFullRemoval = true).hasSucceeded() || !DeadmanInventory.add(inventory, Item(bolts, 10))) return false
        DeadmanInventory.publish(inventory, player.inventory)
        player.addXp(Skills.FLETCHING, 140.0)
        return true
    }
}
