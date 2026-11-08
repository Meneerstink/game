package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.plugins.content.activity.toa.core.ToaMode

/**
 * N462 the room start/complete game messages and the completion count.
 *
 * Sources (team/dossiers/N462-notities/toa/llemonduck, RuneLite plugin 'Tombs of Amascut' by LlemonDuck, reads the real OSRS chat):
 * - BeamTimerTracker: "Challenge started: Path of Het." (GAMEMESSAGE) - the first beam fires 10 ticks later.
 * - RaidCompletionTrackerTest: "Challenge complete: Path of Crondis. Duration: 12", "Challenge complete: Zebak. Duration: 12".
 * - SplitsTracker: "Challenge complete: (?:Path of )?([A-Za-z-]+).*Total:.*?([0-9]+:[.0-9]+)" and for the Wardens
 *   "Challenge complete: The (Wardens).*?completion time:.*?([0-9]+:[.0-9]+)".
 * - RuneLite ChatCommandsPlugin: the kill-count names "Tombs of Amascut", "Tombs of Amascut: Entry Mode", "Tombs of Amascut: Expert Mode";
 *   the count is red (<col=ff0000>) like every OSRS kill count.
 * - Wiki Tombs Speed Runner: the chat's "Total Time" after each challenge "does not account for the down-time in between rooms"
 *   (= the sum of the room durations).
 * - RuneLite 27fcc1da ChatCommandsPluginTest:1359-1427: every mode's count; the Wardens' Duration and challenge completion time
 *   use ef1020, separated by <br>; overall time is a separate game message. Owner: omit personal best records/suffixes.
 * - Red Rookie TombsOfAmascutStats 2dc8a8eb:108-121 observes all five boss start messages.
 * ONZEKER T27: colour/interpunctuation of ordinary room completion times; do not extrapolate other chat colours.
 */
object ToaChallengeMessages {
    fun name(key: ToaRoomKey): String = when {
        key.wardens -> "The Wardens"
        key.boss -> when (key.path!!) {
            ToaPath.SCABARAS -> "Kephri"
            ToaPath.HET -> "Akkha"
            ToaPath.APMEKEN -> "Ba-Ba"
            ToaPath.CRONDIS -> "Zebak"
        }
        else -> "Path of " + key.path!!.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun started(key: ToaRoomKey): String = "Challenge started: ${name(key)}."

    /** [duration]/[total] in game ticks: this room and the sum of all completed rooms, excluding travel/supplies. */
    fun complete(key: ToaRoomKey, duration: Int, total: Int, raidLevel: Int): String =
        if (key.wardens) "Challenge complete: The Wardens. Duration: <col=ef1020>${time(duration)}</col><br>" +
            "${raidName(raidLevel)} challenge completion time: <col=ef1020>${time(total)}</col>."
        else "Challenge complete: ${name(key)}. Duration: ${time(duration)}. Total: ${time(total)}."

    fun overall(raidLevel: Int, ticks: Int): String =
        "${raidName(raidLevel)} total completion time: <col=ef1020>${time(ticks)}</col>."

    fun raidName(raidLevel: Int): String = when (ToaMode.of(raidLevel)) {
        ToaMode.ENTRY -> "Tombs of Amascut: Entry Mode"
        ToaMode.NORMAL -> "Tombs of Amascut"
        ToaMode.EXPERT -> "Tombs of Amascut: Expert Mode"
    }

    fun count(raidLevel: Int, count: Int): String =
        "Your completed ${raidName(raidLevel)} count is: <col=ff0000>${String.format(java.util.Locale.US, "%,d", count)}</col>."

    /** m:ss.cc (a tick = 0.6 s), the format the RuneLite splits pattern reads ([0-9]+:[.0-9]+). */
    fun time(ticks: Int): String {
        val centiseconds = ticks.coerceAtLeast(0).toLong() * 60
        return "%d:%02d.%02d".format(java.util.Locale.ROOT, centiseconds / 6000, centiseconds / 100 % 60, centiseconds % 100)
    }
}
