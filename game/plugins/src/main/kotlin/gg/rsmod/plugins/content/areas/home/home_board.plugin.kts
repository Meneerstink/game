package gg.rsmod.plugins.content.areas.home

import gg.rsmod.plugins.content.areas.wilderness.WildernessBreach
import gg.rsmod.plugins.content.areas.wilderness.WildernessHotspot
import gg.rsmod.plugins.content.daily.DailyObjectives

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
 */
// R14.9/HOME_DESIGN_2.png: on the main south path between the bank and the arrival point/south
// gate - the open plaza area, not a labelled quadrant of its own in the confirmed design, and
// clear of the corrected Shops (NE)/Vervoer (SE) placements this pass moved away from here.
val boardTile = world.gameContext.home.transform(1, -2)

spawn_obj(obj = Objs.JOB_BOARD, x = boardTile.x, z = boardTile.z, height = boardTile.height, type = 10, rot = 0)

on_obj_option(obj = Objs.JOB_BOARD, option = "look-at") {
    val lines = mutableListOf("Server activities:")

    lines += "- Wilderness hotspot: ${WildernessHotspot.current.label} (+15% reward/XP there)."

    val warningCycle = WildernessBreach.warningIssuedAtCycle
    lines +=
        if (warningCycle != null) {
            val cyclesLeft = (WildernessBreach.WARNING_CYCLES - (world.currentCycle - warningCycle)).coerceAtLeast(0)
            val minutesLeft = (cyclesLeft / 100).coerceAtLeast(0)
            "- Wilderness Breach: incoming in about $minutesLeft minute(s)."
        } else {
            "- Wilderness Breach: not currently announced."
        }

    lines += "Daily objectives:"
    DailyObjectives.OBJECTIVES.forEach { lines += DailyObjectives.progressLine(player, it) }

    lines += "- Full activity list and voluntary grouping: coming soon."

    lines.forEach { player.message(it, type = ChatMessageType.CONSOLE) }
}
