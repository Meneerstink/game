package gg.rsmod.plugins.content.activity.penguin_hide_and_seek

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.timer.TimerKey
import kotlin.random.Random

/**
 * Disclosed simplifications vs Void's `PenguinHideAndSeek.kt`:
 *  - Void resets on a configurable real-world weekday/hour boundary (`ticksUntil(DayOfWeek)`).
 *    This re-architects onto a flat epoch-week number (`currentTimeMillis / 1 week`) checked on a
 *    world timer, which rotates weekly but not necessarily on the exact same hour Void would - a
 *    deliberate scope reduction, not a guessed fact.
 *  - No polar bear well, seasonal disguises, Larry's points shop, spy notebook item, or mod/admin
 *    commands this batch - core spy-on-the-disguised-penguin mechanic only. Recorded as named
 *    remaining gaps in the progress register.
 */
object PenguinHideAndSeekHandler {
    val PenguinRotationTimer = TimerKey(persistenceKey = "penguin_rotation_check", tickOffline = false)
    private const val CHECK_INTERVAL_TICKS = 100 * 60 * 60 // hourly re-check, cheap no-op if the week hasn't changed

    private const val SPOTS_PER_POOL = 5
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    var currentWeek: Int = -1
        private set
    private val activeSpawns = mutableListOf<Npc>()
    val activeSpots = mutableListOf<PenguinSpot>()

    fun currentWeekNumber(): Int = (System.currentTimeMillis() / WEEK_MILLIS).toInt()

    fun ensureCurrentWeek(world: World) {
        val week = currentWeekNumber()
        if (week == currentWeek) {
            return
        }
        currentWeek = week
        respawn(world, week)
    }

    private fun respawn(
        world: World,
        week: Int,
    ) {
        activeSpawns.forEach { world.remove(it) }
        activeSpawns.clear()
        activeSpots.clear()

        val random = Random(week)
        val chosen = PenguinEasySpots.shuffled(random).take(SPOTS_PER_POOL) + PenguinHardSpots.shuffled(random).take(SPOTS_PER_POOL)
        chosen.forEach { spot ->
            val npc = Npc(spot.disguise.npcId, spot.tile, world)
            npc.respawnOverride = false
            world.spawn(npc)
            activeSpawns.add(npc)
            activeSpots.add(spot)
        }
    }

    fun indexOf(npc: Npc): Int = activeSpawns.indexOf(npc)
}
