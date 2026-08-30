package gg.rsmod.plugins.content.skills.construction

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

object Construction {
    val BUILT_ATTR = AttributeKey<MutableList<String>>(persistenceKey = "construction_built")

    suspend fun openMenu(it: QueueTask) {
        val player = it.player
        if (!player.hasItem(Items.HAMMER)) {
            player.filterableMessage("You need a hammer to do this.")
            return
        }
        if (!player.hasItem(Items.SAW)) {
            player.filterableMessage("You need a saw to do this.")
            return
        }
        while (true) {
            val built = player.attr[BUILT_ATTR] ?: emptyList()
            val level = player.skills.getMaxLevel(Skills.CONSTRUCTION)
            val available = ConstructionData.PIECES.filter { level >= it.level && it.id !in built }
            if (available.isEmpty()) {
                it.messageBox("You have nothing left to build at your current Construction level.")
                return
            }
            val page = available.take(4)
            val labels = (page.map { "${it.name} (${it.room}, lvl ${it.level})" } + "Exit").toTypedArray()
            val choice = it.options(*labels, title = "What would you like to build?")
            if (choice < 0 || choice !in page.indices) {
                return
            }
            val piece = page[choice]
            if (!hasMaterials(player, piece)) {
                it.messageBox(
                    "You need ${describeMaterials(player.world.definitions, piece)} to build the ${piece.name}.",
                )
                continue
            }
            piece.materials.forEach { (item, qty) -> player.inventory.remove(item, qty) }
            val list = player.attr[BUILT_ATTR] ?: mutableListOf<String>().also { player.attr[BUILT_ATTR] = it }
            list.add(piece.id)
            player.addXp(Skills.CONSTRUCTION, piece.xp, checkBrawlingGloves = true)
            it.messageBox("You build a ${piece.name}.")
        }
    }

    private fun hasMaterials(
        player: Player,
        piece: ConstructionPiece,
    ): Boolean = piece.materials.all { (item, qty) -> player.inventory.getItemCount(item) >= qty }

    private fun describeMaterials(
        definitions: gg.rsmod.game.fs.DefinitionSet,
        piece: ConstructionPiece,
    ): String =
        piece.materials.joinToString(", ") { (item, qty) ->
            val name = definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).name
            "$qty x $name"
        }
}
