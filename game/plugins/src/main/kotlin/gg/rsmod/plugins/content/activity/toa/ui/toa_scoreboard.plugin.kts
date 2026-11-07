package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.activity.toa.ToaRaidEvents
import gg.rsmod.plugins.content.activity.toa.ToaRecordStore

// Both cache variants: lobby Scoreboard, OSRS 46071 / 46072 (local 65228 / 66097).
listOfNotNull(ToaIds.loc(46071), ToaIds.loc(46072)).distinct().forEach { id ->
    on_obj_option(id, "Read") { ToaScoreboardUi.open(player) }
}

ToaInterfaces.of(ToaInterfaces.STATISTICS)?.let { iface ->
    (listOf(ToaScoreboardUi.CLOSE) + ToaScoreboardUi.TABS.keys + (1..8).map(ToaScoreboardUi::overallComponent)).forEach { c ->
        on_button(iface, c) {
            val record = ToaScoreboardUi.click(player, c) ?: return@on_button
            player.queue {
                // Four names per page also fits an eight-player record in the existing chatbox message interface.
                record.names.chunked(4).forEach { names ->
                    messageBox("${record.mode.label} - ${record.size} - ${ToaScoreboardUi.time(record.ticks)}<br>" + names.joinToString("<br>"))
                }
            }
        }
    }
}

run {
    val previous = ToaRaidEvents.completed
    ToaRaidEvents.completed = { raid, completion ->
        previous(raid, completion)
        // Record persistence cannot abort a completion or lose its rewards.
        try { ToaRecordStore.completed(raid, completion) }
        catch (e: Exception) { ToaRecordStore.logger.error(e) { "PROBLEM ToA server record could not be saved" } }
    }
}
