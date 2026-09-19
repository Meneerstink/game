package gg.rsmod.plugins.content.areas.home

import gg.rsmod.plugins.content.areas.wilderness.WildernessBreach
import gg.rsmod.plugins.content.areas.wilderness.WildernessHotspot
import gg.rsmod.plugins.content.daily.DailyObjectives
import gg.rsmod.plugins.content.mechanics.lfg.Lfg

/**
 * R14.9: home activity/info board. Shows the current Wilderness hotspot and next Breach status
 * from the real, already-running systems ([WildernessHotspot], [WildernessBreach]) - never a
 * fake countdown when nothing is scheduled, per R14.9's own explicit rule.
 *
 * Uses [Objs.JOB_BOARD] (46296), verified live this session to be the only real board-type
 * candidate with a usable option ("Look-at") - BANK_NOTICE_BOARD/BLACKBOARD/WORK_BOARD/
 * OPERATIONS_BOARD/DISPLAY_BOARD/MAP_BOARD all have zero real cache options and were rejected.
 * Its other real options ("Take-job...") belong to an unrelated cache feature and are not
 * bound here.
 *
 * R14.18: low-population location recommendation. Reuses the existing [WildernessHotspot]
 * (already a rotating "go here for +15%" rally point) and the busiest open [Lfg] group as the
 * "recommended locations" - only surfaced when population is low, and never hides/disables
 * anything else on the board or elsewhere. ponytail: LOW_POP_THRESHOLD is an arbitrary headcount
 * heuristic, not derived from real population data (none exists yet) - tune if it feels off.
 *
 * R14.19: voluntary group-finding for PvM/Wilderness/minigames, list/join/leave/cleanup, backed
 * by [Lfg] (in-memory, session-scoped). Reached via the same "Look-at" option rather than an
 * invented right-click entry (this object's only other real options are the unrelated
 * "Take-job..." ones noted above).
 */
private val LOW_POP_THRESHOLD = 10
// R14.9/HOME_DESIGN_2.png: on the main south path between the bank and the arrival point/south
// gate - the open plaza area, not a labelled quadrant of its own in the confirmed design, and
// clear of the corrected Shops (NE)/Vervoer (SE) placements this pass moved away from here.
val boardTile = HomeLayout.board.tile(world.gameContext.home)

spawn_obj(obj = Objs.JOB_BOARD, x = boardTile.x, z = boardTile.z, height = boardTile.height, type = 10, rot = 0)

on_obj_option(obj = Objs.JOB_BOARD, option = "look-at") {
    val lines = mutableListOf("Server activities:")

    lines += "- Wilderness hotspot: ${WildernessHotspot.current.label} (+15% reward/XP there)."

    lines += "- Deadman breaches: " + gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.statusLine()

    lines += "Daily objectives:"
    DailyObjectives.OBJECTIVES.forEach { lines += DailyObjectives.progressLine(player, it) }

    val population = world.players.count()
    if (population < LOW_POP_THRESHOLD) {
        lines += "Population is low ($population online) - recommended gathering spots:"
        lines += "- Wilderness hotspot: ${WildernessHotspot.current.label}."
        val busiest = Lfg.open().maxByOrNull { it.members.size }
        if (busiest != null) {
            lines += "- LFG group '${busiest.activity}' (${busiest.members.size} member(s), led by ${busiest.leader})."
        }
    }

    lines += "Voluntary group-finding (PvM/Wilderness/minigames):"
    val groups = Lfg.open()
    if (groups.isEmpty()) {
        lines += "- No open groups right now."
    } else {
        groups.forEach { lines += "- #${it.id} '${it.activity}': ${it.members.size} member(s), led by ${it.leader}." }
    }

    lines.forEach { player.message(it, type = ChatMessageType.CONSOLE) }

    player.queue {
        val myGroup = Lfg.myGroup(player)
        // Fixed action ids per branch (not string-matched, so relabeling text above can't desync
        // dispatch): 1=leave-mine, or 1=start/2=join when no group yet (2=start/3=join dropped
        // to the no-groups-open case below skips "Join").
        val menuOptions =
            when {
                myGroup != null -> arrayOf("Leave my group ('${myGroup.activity}')", "Nothing, just looking")
                groups.isNotEmpty() -> arrayOf("Start a new group", "Join a group", "Nothing, just looking")
                else -> arrayOf("Start a new group", "Nothing, just looking")
            }
        val choice = options(*menuOptions, title = "Group-finding")
        if (choice !in 1..menuOptions.size) {
            return@queue
        }

        if (myGroup != null) {
            if (choice == 1) Lfg.leave(player)
            return@queue
        }
        if (choice == 1) {
            val activity = inputString("Enter what you're looking for (e.g. 'Bandos', 'Wilderness pking')")
            if (activity.isNotBlank()) {
                Lfg.create(player, activity)
            }
            return@queue
        }
        if (choice == 2 && groups.isNotEmpty()) {
            val joinChoice =
                options(
                    *groups.map { "#${it.id} '${it.activity}' (${it.members.size})" }.toTypedArray(),
                    title = "Join which group?",
                )
            if (joinChoice in 1..groups.size) {
                Lfg.join(player, groups[joinChoice - 1])
            }
        }
    }
}

on_logout {
    Lfg.leave(player)
}
