package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.Box
import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool.Nexus
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentItem
import gg.rsmod.plugins.api.ext.setComponentSprite
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * The house teleport screens (owner bug 2026-10-06 183223 "scrying portal has no teleport interfaces"; owner 2026-10-02: house
 * options are interfaces, not chat menus): the OSRS Portal Nexus Teleport Menu ([NEXUS], OSRS 17) for the nexus and the
 * portal-chamber portals, and the OSRS jewellery box ([JEWELLERY_BOX], OSRS 590) for the chapel's jewellery box. Layout:
 * `PohTeleportInterfaceImportTool`. Every teleport still goes through [canTeleport] / [teleport], so wilderness and Deadman
 * rules stay those of the real spell or item.
 */
object PohTeleportUi {
    const val NEXUS = PohTeleportInterfaceImportTool.NEXUS
    const val JEWELLERY_BOX = PohTeleportInterfaceImportTool.JEWELLERY_BOX
    const val CLOSE = DeathsOfficeInterfaceImportTool.CLOSE

    /** IF_SETEVENTS: op 1 only. */
    private const val OP1 = 2

    /** IF_SETOBJECT count that draws no number (client ObjType: no count when invCount == -1) - OSRS `setobject_nonum`. */
    private const val NO_COUNT = -1

    /** The nexus rows, in directory order (spellbooks, jewellery, tablets and scrolls) - every teleport in the house. */
    val NEXUS_ROWS: List<PohTeleports.Leaf> by lazy { PohTeleports.leaves() }

    /** The row text: OSRS clientscript 2675 prefixes the first 35 rows with their key (1-9, A-Z). */
    fun rowText(index: Int, name: String): String {
        val key = PohTeleportInterfaceImportTool.KEYS.getOrNull(index) ?: return name
        return "<col=ffffff>$key</col> :  $name"
    }

    /** The row a nexus component belongs to, or -1. */
    fun nexusRowOf(component: Int): Int {
        val offset = component - Nexus.ROW_FIRST
        if (offset < 0 || offset % Nexus.ROW_STRIDE != 0) return -1
        val row = offset / Nexus.ROW_STRIDE
        return if (row < NEXUS_ROWS.size && row < Nexus.ROWS) row else -1
    }

    fun openNexus(player: Player) {
        player.openInterface(NEXUS, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(NEXUS, CLOSE, -1..-1, OP1)
        if (NEXUS_ROWS.size > Nexus.ROWS) {
            Trace.problem(Trace.Cat.INTERFACE) { "nexus for ${player.username}: ${NEXUS_ROWS.size} teleports but the screen has ${Nexus.ROWS} rows - the last ones are missing" }
        }
        val rows = NEXUS_ROWS.take(Nexus.ROWS)
        for (r in 0 until Nexus.ROWS) {
            val row = Nexus.row(r)
            val leaf = rows.getOrNull(r)
            player.setComponentHidden(NEXUS, row, leaf == null)
            if (leaf == null) continue
            when (val icon = leaf.icon) {
                is PohTeleports.Icon.Sprite -> {
                    player.setComponentSprite(NEXUS, row + Nexus.ROW_SPELL, icon.id)
                    player.setComponentHidden(NEXUS, row + Nexus.ROW_ITEM, true)
                    player.setComponentHidden(NEXUS, row + Nexus.ROW_SPELL, false)
                }
                is PohTeleports.Icon.Obj -> {
                    player.setComponentItem(NEXUS, row + Nexus.ROW_ITEM, icon.id, NO_COUNT)
                    player.setComponentHidden(NEXUS, row + Nexus.ROW_ITEM, false)
                    player.setComponentHidden(NEXUS, row + Nexus.ROW_SPELL, true)
                }
            }
            player.setComponentText(NEXUS, row + Nexus.ROW_NAME, rowText(r, leaf.name))
            player.setInterfaceEvents(NEXUS, row, -1..-1, OP1)
        }
        player.runClientScript(
            DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (NEXUS shl 16) or Nexus.SCROLL,
            (NEXUS shl 16) or Nexus.SCROLLBAR,
            maxOf(rows.size * Nexus.ROW_HEIGHT, Nexus.SCROLL_H),
        )
    }

    fun openJewelleryBox(player: Player) {
        player.openInterface(JEWELLERY_BOX, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(JEWELLERY_BOX, CLOSE, -1..-1, OP1)
        PohTeleportInterfaceImportTool.BOXES.forEachIndexed { i, box ->
            player.setComponentItem(JEWELLERY_BOX, Box.box(i) + Box.BOX_ITEM, box.item, NO_COUNT)
            box.destinations.indices.forEach { d -> player.setInterfaceEvents(JEWELLERY_BOX, Box.button(i, d), -1..-1, OP1) }
        }
    }

    /** The directory leaf behind a jewellery box button, or null. */
    fun jewelleryLeaf(component: Int): PohTeleports.Leaf? {
        PohTeleportInterfaceImportTool.BOXES.forEachIndexed { i, box ->
            box.destinations.forEachIndexed { d, name ->
                if (Box.button(i, d) == component) {
                    val leaf =
                        jewellery().firstOrNull { it.name == box.branch }?.children?.filterIsInstance<PohTeleports.Leaf>()?.firstOrNull { it.name == name }
                    if (leaf == null) Trace.problem(Trace.Cat.INTERFACE) { "jewellery box button ${box.title} / $name has no destination in PohTeleports" }
                    return leaf
                }
            }
        }
        return null
    }

    private fun jewellery(): List<PohTeleports.Branch> =
        PohTeleports.ROOT.children.filterIsInstance<PohTeleports.Branch>().firstOrNull { it.name == "Jewellery" }
            ?.children?.filterIsInstance<PohTeleports.Branch>() ?: emptyList()

    /** A click on a destination: close the screen and teleport like the real spell or item. */
    fun go(
        player: Player,
        interfaceId: Int,
        leaf: PohTeleports.Leaf,
    ) {
        player.closeInterface(interfaceId)
        val tile = leaf.tile(player)
        if (tile == null) {
            Trace.problem(Trace.Cat.INTERFACE) { "house teleport ${leaf.name} for ${player.username}: no landing tile (requirement message or no free tile)" }
            return
        }
        Trace.log(Trace.Cat.INTERFACE) { "house teleport ${leaf.name} for ${player.username} from $interfaceId -> $tile" }
        player.canTeleport(leaf.type) {
            if (interfaceId == JEWELLERY_BOX) JewelleryBoxRecent.remember(player, leaf)
            player.teleport(tile, leaf.type)
        }
    }
}
