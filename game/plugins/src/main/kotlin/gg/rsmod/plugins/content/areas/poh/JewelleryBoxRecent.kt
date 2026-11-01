package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool

/** OSRS jewellery boxes repeat one previous destination. Store a stable name, never a UI row number. */
object JewelleryBoxRecent {
    val LAST = AttributeKey<String>(persistenceKey = "poh_jewellery_last_destination")
    const val OPTION = "Last-destination"

    fun destinations(): List<PohTeleports.Leaf> =
        PohTeleportInterfaceImportTool.BOXES.flatMapIndexed { i, box ->
            box.destinations.indices.mapNotNull { d -> PohTeleportUi.jewelleryLeaf(PohTeleportInterfaceImportTool.Box.button(i, d)) }
        }

    fun remember(player: Player, leaf: PohTeleports.Leaf) {
        if (leaf in destinations()) player.attr[LAST] = leaf.name
    }

    /** A missing/removed destination must never become an arbitrary teleport. */
    fun last(player: Player, available: List<PohTeleports.Leaf> = destinations()): PohTeleports.Leaf? =
        player.attr[LAST]?.let { name -> available.firstOrNull { it.name == name } }

    fun repeat(player: Player) {
        val leaf = last(player)
        if (leaf == null) PohTeleportUi.openJewelleryBox(player)
        else PohTeleportUi.go(player, PohTeleportUi.JEWELLERY_BOX, leaf)
    }
}
