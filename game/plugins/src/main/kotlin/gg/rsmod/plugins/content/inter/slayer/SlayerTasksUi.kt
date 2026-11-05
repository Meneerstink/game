package gg.rsmod.plugins.content.inter.slayer

import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.SlayerRewardsInterfaceImportTool as T
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.slayer.getSlayerAssignment
import gg.rsmod.plugins.content.skills.slayer.removeSlayerAssignment

/** Server contract for the existing 426 Tasks layer. Claude must append these components in the cache. */
object SlayerTasksUi {
    val CANCEL get() = T.COMPONENT_COUNT
    val BLOCK get() = CANCEL + 1
    val SLOT_TEXT_FIRST get() = CANCEL + 2
    val UNBLOCK_FIRST get() = CANCEL + 7
    val COMPONENT_COUNT get() = CANCEL + 12
    const val SLOTS = 5 // Existing 78 assignment policy; no new diary/quest requirements.

    fun enable(player: Player) {
        (listOf(CANCEL, BLOCK) + (0 until SLOTS).map { UNBLOCK_FIRST + it }).forEach {
            player.setInterfaceEvents(T.INTERFACE_ID, it, -1..-1, 0x2)
        }
    }

    fun draw(player: Player) {
        val assignment = player.getSlayerAssignment()?.identifier
        val remaining = player.attr[SLAYER_AMOUNT] ?: 0
        player.setComponentText(T.INTERFACE_ID, T.TASKS_TEXT,
            if (assignment != null && remaining > 0) "Current assignment: $assignment ($remaining remaining)" else "You have no current assignment.")
        player.setComponentText(T.INTERFACE_ID, CANCEL, "Cancel task (30 points)")
        player.setComponentText(T.INTERFACE_ID, BLOCK, "Block task (100 points)")
        val blocked = player.attr[BLOCKED_TASKS].orEmpty()
        repeat(SLOTS) { i ->
            player.setComponentText(T.INTERFACE_ID, SLOT_TEXT_FIRST + i, blocked.getOrNull(i) ?: "Empty")
            player.setComponentText(T.INTERFACE_ID, UNBLOCK_FIRST + i, "Unblock")
            player.setComponentHidden(T.INTERFACE_ID, UNBLOCK_FIRST + i, i >= blocked.size)
        }
    }

    fun button(player: Player, component: Int) {
        val assignment = player.getSlayerAssignment()
        when (component) {
            CANCEL -> {
                if (assignment == null || (player.attr[SLAYER_AMOUNT] ?: 0) <= 0) return
                if (!player.subtractSlayerPoints(30)) { player.message("You need 30 points to cancel your current mission."); return }
                player.removeSlayerAssignment()
            }
            BLOCK -> {
                if (assignment == null || (player.attr[SLAYER_AMOUNT] ?: 0) <= 0) return
                val blocked = player.attr[BLOCKED_TASKS].orEmpty().toMutableList()
                if (blocked.size >= SLOTS || assignment.identifier in blocked) return
                if (!player.subtractSlayerPoints(100)) { player.message("You need 100 slayer points to block a task."); return }
                blocked += assignment.identifier
                player.attr[BLOCKED_TASKS] = blocked
                player.removeSlayerAssignment()
            }
            in UNBLOCK_FIRST until UNBLOCK_FIRST + SLOTS -> {
                val blocked = player.attr[BLOCKED_TASKS].orEmpty().toMutableList()
                val index = component - UNBLOCK_FIRST
                if (index !in blocked.indices) return
                blocked.removeAt(index)
                player.attr[BLOCKED_TASKS] = blocked
            }
            else -> return
        }
        SlayerRewardsUi.refresh(player)
        draw(player)
    }
}
