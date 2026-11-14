package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import kotlin.math.abs
import kotlin.math.max

/**
 * N415 Mage Arena II: the enchanted symbol's tracking (Activate).
 *
 * Sources (docs/n415/RESEARCH.md, dossier N415 14-11 20:2x):
 * - OSRS Wiki Enchanted_symbol / Mage_Arena_II: above-ground Wilderness only (underground message verbatim), hot/cold
 *   readings, "visibly shaking" = the follower appears and attacks, up to 16 damage that can kill, the god prompt is skipped
 *   when one god is left, followers rotate about every 45 minutes of Wilderness time and the player is told on the next use,
 *   one follower per spawn place at a time, a defeated follower is not tracked again until the miniquest is complete.
 * - Wiki Map:God_followers_spawn_locations: the 17 centres below, radius 5.
 * - Temperature bands: RuneLite Quest Helper MageArenaTemperature (b9b9370: shaking 0-14 ... very cold 200+) and the
 *   Wiki Strange_device table (ice cold 500+), Chebyshev steps; both agree with the recorded OSRS readings quoted by tsps
 *   (RSProx 235/03344). Message wording and the 8-damage minimum: that recording. The ice-cold band cannot occur inside
 *   the Wilderness's 448-tile width; it is kept for completeness.
 */
object MageArenaIITracking {
    val POINTS = listOf(
        Tile(3172, 3898), Tile(3304, 3942), Tile(3296, 3876), Tile(3260, 3886), Tile(3233, 3909), Tile(3215, 3883),
        Tile(3231, 3873), Tile(3276, 3842), Tile(3248, 3831), Tile(3261, 3800), Tile(3168, 3796), Tile(3158, 3842),
        Tile(3022, 3831), Tile(3150, 3878), Tile(3334, 3903), Tile(3172, 3866), Tile(3282, 3821),
    )
    const val RADIUS = 5
    const val FOLLOWER_SIZE = 3
    /** Wiki ~45 minutes: 250 steps of 18 Wilderness ticks (the recorded varbit 6062 starts at 250). */
    const val ROTATION_STEPS = 250
    const val STEP_TICKS = 18
    const val MIN_DAMAGE = 8
    const val MAX_DAMAGE = 16
    const val SHAKING_BELOW = 15

    const val NOT_HERE = "The symbol doesn't seem to work here, I should try using it in the Wilderness."
    const val MOVED = "It appears the creatures have moved locations, the symbol glows slightly as it locates their new positions."
    const val HURTS = "The power of the enchanted symbol hurts you in the process."

    /** [below this many tiles] to temperature, hottest first. */
    private val BANDS = listOf(
        15 to "visibly shaking and burns to the touch", 30 to "incredibly hot", 70 to "very hot", 100 to "hot",
        150 to "warm", 200 to "cold", 500 to "very cold",
    )

    val STEPS_LEFT = AttributeKey<Int>(persistenceKey = "mage_arena_ii_rotation_steps")
    /** Per god (ordinal) the chosen point index and south-west spawn tile: "point,x,z;point,x,z;point,x,z". */
    val LOCATIONS = AttributeKey<String>(persistenceKey = "mage_arena_ii_locations")
    private val LAST_READING = AttributeKey<IntArray>()
    val STEP_TIMER = TimerKey(persistenceKey = "mage_arena_ii_rotation_step", tickOffline = false)

    data class Location(val point: Int, val tile: Tile) {
        val centre: Tile get() = tile.transform(FOLLOWER_SIZE / 2, FOLLOWER_SIZE / 2)
    }

    fun temperature(distance: Int): String = BANDS.firstOrNull { distance < it.first }?.second ?: "ice cold"

    /** The above-ground Wilderness (the underground caves sit at z >= 6400). */
    fun usableAt(tile: Tile): Boolean = tile.z < 6400 && tile.getWildernessLevel() > 0

    fun inWilderness(player: Player): Boolean = player.tile.getWildernessLevel() > 0

    fun locations(player: Player): List<Location>? {
        val raw = player.attr[LOCATIONS] ?: return null
        val parts = raw.split(';').map { it.split(',').map(String::toInt) }
        if (parts.size != MageArenaII.God.values().size || parts.any { it.size != 3 }) return null
        return parts.map { (point, x, z) -> Location(point, Tile(x, z)) }
    }

    fun location(player: Player, god: MageArenaII.God): Location? = locations(player)?.getOrNull(god.ordinal)

    /** A free 3x3 footprint whose centre lies inside the map circle; falls back to the circle centre. */
    fun spawnTile(player: Player, point: Tile): Tile {
        val world = player.world
        val candidates = mutableListOf<Tile>()
        for (dx in -RADIUS..RADIUS) for (dz in -RADIUS..RADIUS) {
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue
            val sw = Tile(point.x + dx - 1, point.z + dz - 1)
            val free = (0 until FOLLOWER_SIZE).all { ox ->
                (0 until FOLLOWER_SIZE).all { oz -> !world.collision.isClipped(sw.transform(ox, oz)) }
            }
            if (free) candidates += sw
        }
        return if (candidates.isEmpty()) point.transform(-1, -1) else candidates[world.random(candidates.indices)]
    }

    /** New, distinct points for the three followers; their old actors go (Wiki: "the creatures have moved locations"). */
    fun rotate(player: Player) {
        // random(n) is inclusive: n + 1 must not overflow (so no Int.MAX_VALUE seed bound). Exactly one point per god.
        val points = POINTS.indices.shuffled(java.util.Random(player.world.random(1_000_000_000).toLong()))
            .take(MageArenaII.God.values().size)
        player.attr[LOCATIONS] = points.joinToString(";") { index ->
            val tile = spawnTile(player, POINTS[index])
            "$index,${tile.x},${tile.z}"
        }
        player.attr.remove(LAST_READING)
        player.attr[STEPS_LEFT] = ROTATION_STEPS
        player.timers[STEP_TIMER] = STEP_TICKS
        MageArenaIIEncounter.dismissAll(player)
    }

    /** Timer step: counts only while inside the Wilderness (Wiki), never below zero. */
    fun step(player: Player) {
        val left = player.attr[STEPS_LEFT] ?: return
        if (left <= 0) return
        if (inWilderness(player)) player.attr[STEPS_LEFT] = left - 1
        if ((player.attr[STEPS_LEFT] ?: 0) > 0) player.timers[STEP_TIMER] = STEP_TICKS
    }

    fun rotated(player: Player): Boolean = locations(player) != null && (player.attr[STEPS_LEFT] ?: 0) <= 0

    data class Reading(val distance: Int, val text: String)

    fun reading(player: Player, god: MageArenaII.God): Reading? {
        val target = location(player, god)?.centre ?: return null
        val distance = max(abs(player.tile.x - target.x), abs(player.tile.z - target.z))
        val last = player.attr[LAST_READING] ?: IntArray(MageArenaII.God.values().size) { -1 }
        val previous = last[god.ordinal]
        last[god.ordinal] = distance
        player.attr[LAST_READING] = last
        val change = when {
            previous < 0 -> ""
            distance < previous -> ", and warmer than last time"
            distance > previous -> ", but colder than last time"
            else -> ", and the same temperature as last time"
        }
        return Reading(distance, "The enchanted symbol is ${temperature(distance)}$change.")
    }

    /** One reading: the temperature, the blood sacrifice (may kill), and at "visibly shaking" the follower. */
    fun locate(player: Player, god: MageArenaII.God) {
        val reading = reading(player, god) ?: return
        player.message(reading.text)
        player.message(HURTS)
        player.hit(player.world.random(MIN_DAMAGE..MAX_DAMAGE))
        if (reading.distance < SHAKING_BELOW) {
            val location = location(player, god) ?: return
            MageArenaIIEncounter.summon(player, god, location)
        }
    }
}
