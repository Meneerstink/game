package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID

/** Calculation/logging hooks, separate from room implementations and native interfaces. World-thread callbacks only. */
object ToaRaidEvents {
    var roomCleared: (ToaRaid, ToaRoomRun) -> Unit = { _, _ -> }
    var playerDeath: (ToaRaid, PlayerUID) -> Unit = { _, _ -> }
    var completed: (ToaRaid, ToaCompletion) -> Unit = { _, _ -> }
}

/** Created once at Wardens 0HP, before the death animation (01-route.tsv timer boundary). */
data class ToaCompletion(val raidToken: String, val roster: List<PlayerUID>, val settings: ToaRaidSettings,
    val elapsedCycles: Int, val failedAttempts: Int)
