package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.tools.importer.N108InterfaceImportTool
import gg.rsmod.game.tools.importer.N108InterfaceImportTool.W
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setInterfaceEvents

/**
 * The Deadman Tasks window (N104, owner design GOEDGEKEURD 06): left All tasks + the four tiers with their progress, the next tier
 * reward and the DP earned from tasks; right the filters To do (default) / Done / All and the type, the tasks with their progress
 * and reward. Data: [DeadmanTasks] (provisional list, real progress).
 */
object DeadmanTasksUi {
    const val INTERFACE = N108InterfaceImportTool.TASK_WINDOW

    private val TIER = AttributeKey<Int>() // 0 = all, 1..4 = the tiers
    private val FILTER = AttributeKey<Int>() // 0 to do, 1 done, 2 all
    private val TYPE = AttributeKey<Int>() // -1 all, else DeadmanTasks.Type ordinal

    private const val EVENTS_OP1 = 0x2

    fun isOpen(player: Player) = player.interfaces.isVisible(INTERFACE)

    fun open(player: Player, tier: Int = 0) {
        player.openInterface(INTERFACE, InterfaceDestination.MAIN_SCREEN)
        N108Ui.reset(player, INTERFACE)
        player.setInterfaceEvents(INTERFACE, W.CLOSE, -1..-1, EVENTS_OP1)
        for (t in 0 until 5) player.setInterfaceEvents(INTERFACE, W.tier(t) + 8, -1..-1, EVENTS_OP1)
        for (f in 0 until 3) player.setInterfaceEvents(INTERFACE, W.filter(f) + 2, -1..-1, EVENTS_OP1)
        player.setInterfaceEvents(INTERFACE, W.TYPE_HIT, -1..-1, EVENTS_OP1)
        player.attr[TIER] = tier
        player.attr[FILTER] = 0
        player.attr[TYPE] = -1
        draw(player)
    }

    fun tier(player: Player, tier: Int) {
        player.attr[TIER] = tier
        draw(player)
    }

    fun filter(player: Player, filter: Int) {
        player.attr[FILTER] = filter
        draw(player)
    }

    fun nextType(player: Player) {
        val now = player.attr[TYPE] ?: -1
        player.attr[TYPE] = if (now + 1 >= DeadmanTasks.Type.values().size) -1 else now + 1
        draw(player)
    }

    fun draw(player: Player) {
        if (!isOpen(player)) return
        val i = INTERFACE
        val tierSel = player.attr[TIER] ?: 0
        val filter = player.attr[FILTER] ?: 0
        val type = player.attr[TYPE] ?: -1
        val done = DeadmanTasks.done(player)
        // Left: All + tiers.
        for (t in 0 until 5) {
            val base = W.tier(t)
            val list = if (t == 0) DeadmanTasks.TASKS else DeadmanTasks.tierTasks(DeadmanTasks.Tier.values()[t - 1])
            val have = list.count { it.id in done }
            N108Ui.hidden(player, i, base, t != tierSel)
            N108Ui.hidden(player, i, base + 1, t == tierSel)
            N108Ui.text(player, i, base + 4, "$have/${list.size}")
            N108Ui.pos(player, i, base + 7, if (list.isEmpty()) -110 else 110 * have / list.size - 110, 0)
        }
        val next = DeadmanTasks.Tier.values().firstOrNull { tier -> DeadmanTasks.tierTasks(tier).any { it.id !in done } }
        if (next != null) {
            val left = DeadmanTasks.tierTasks(next).count { it.id !in done }
            N108Ui.text(player, i, W.REWARD_TEXT, "<col=${next.colour}>${next.title}</col> · $left task${if (left == 1) "" else "s"} to go<br><col=ffffff>+${DeadmanTasks.TIER_BONUS.getValue(next)} DP</col> bonus")
        } else {
            N108Ui.text(player, i, W.REWARD_TEXT, "<col=0dc10d>Every tier is done!</col>")
        }
        N108Ui.text(player, i, W.TOTAL, "From tasks: <col=ffffff>${N108Ui.number(DeadmanTasks.earned(player))} DP</col>")
        // Filters.
        for (f in 0 until 3) {
            N108Ui.hidden(player, i, W.filter(f), f != filter)
            N108Ui.hidden(player, i, W.filter(f) + 1, f == filter)
        }
        N108Ui.text(player, i, W.TYPE_TEXT, "Type: ${if (type < 0) "All" else DeadmanTasks.Type.values()[type].label} <col=a29680>v</col>")
        // Rows.
        val rows = DeadmanTasks.TASKS.filter { task ->
            (tierSel == 0 || task.tier.ordinal == tierSel - 1) &&
                (type < 0 || task.type.ordinal == type) &&
                when (filter) {
                    0 -> task.id !in done
                    1 -> task.id in done
                    else -> true
                }
        }
        val world = player.world
        for (r in 0 until W.ROW_SLOTS) {
            val base = W.row(r)
            val task = rows.getOrNull(r)
            if (task == null) {
                for (c in 0 until W.ROW_STRIDE) if (c != 9) N108Ui.hidden(player, i, base + c, true)
                continue
            }
            val finished = task.id in done
            N108Ui.sprite(player, i, base, "tw_row_${task.tier.name.lowercase()}")
            N108Ui.hidden(player, i, base, false)
            N108Ui.hidden(player, i, base + 1, false)
            N108Ui.item(player, i, base + 2, task.icon)
            N108Ui.hidden(player, i, base + 2, false)
            N108Ui.text(player, i, base + 3, if (finished) "<col=8f8473>${task.name}</col>" else task.name)
            N108Ui.hidden(player, i, base + 3, false)
            val tag = when (task.label) {
                DeadmanTasks.Label.PVP -> "tw_tag_pvp"
                DeadmanTasks.Label.DANGER -> "tw_tag_dng"
                else -> null
            }
            N108Ui.hidden(player, i, base + 4, tag == null)
            if (tag != null) {
                N108Ui.sprite(player, i, base + 4, tag)
                N108Ui.pos(player, i, base + 4, 30 + N108Ui.p11(world, task.name) + 5, r * W.ROW_H + 2)
            }
            N108Ui.text(player, i, base + 5, if (finished) "<col=5f574c>${task.description}</col>" else task.description)
            N108Ui.hidden(player, i, base + 5, false)
            val have = DeadmanTasks.progress(player, task)
            N108Ui.text(player, i, base + 6, "${N108Ui.number(have)}/${N108Ui.number(task.target)}")
            N108Ui.hidden(player, i, base + 6, finished)
            N108Ui.hidden(player, i, base + 7, finished)
            N108Ui.hidden(player, i, base + 8, finished)
            N108Ui.pos(player, i, base + 9, 54 * have / task.target - 54, 0)
            N108Ui.text(player, i, base + 10, if (finished) "<col=6f8f6a>+${task.dp} DP</col>" else "+${task.dp} DP")
            N108Ui.hidden(player, i, base + 10, false)
            N108Ui.hidden(player, i, base + 11, !finished)
            if (finished) N108Ui.pos(player, i, base + 11, 230, r * W.ROW_H + 9)
        }
        N108Ui.hidden(player, i, W.EMPTY, rows.isNotEmpty())
        if (rows.isEmpty()) N108Ui.text(player, i, W.EMPTY, if (filter == 1) "No task done here yet." else "Nothing left to do here!")
        N108Ui.scroll(player, i, W.ROWS, W.ROWS_SCROLLBAR, maxOf(rows.size * W.ROW_H, W.ROWS_H))
    }
}
