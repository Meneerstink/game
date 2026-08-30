package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.plugins.api.ext.*

/**
 * Rotating Wilderness hotspot: a Wilderness level band that carries a temporary bonus,
 * separate from (and additive on top of) the baseline ~20% Wilderness-vs-safe direction in
 * PROJECT_PLAN SS12. Current direction (SS13): roughly +15% reward/XP, rotating periodically.
 *
 * ponytail: [bonusMultiplier] is a helper other systems opt into (wired into
 * [gg.rsmod.plugins.content.areas.wilderness.WildernessBreach] rewards and PK points here) -
 * it is not wired into every wilderness skilling action's XP grant, since that would mean
 * touching every skilling file's `addXp` call in this pass. See IMPLEMENTATION_STATUS.md.
 */
object WildernessHotspot {
    data class Zone(
        val low: Int,
        val high: Int,
        val label: String,
    )

    private val ZONES =
        listOf(
            Zone(1, 10, "low Wilderness (levels 1-10)"),
            Zone(11, 25, "mid Wilderness (levels 11-25)"),
            Zone(26, 40, "deep Wilderness (levels 26-40)"),
            Zone(41, 56, "far Wilderness (levels 41-56)"),
        )

    const val BONUS_MULTIPLIER = 1.15
    private const val ROTATION_CYCLES = 3000 // ~30 minutes

    @Volatile var current: Zone = ZONES[0]
        private set

    fun isActive(tile: Tile): Boolean {
        val level = tile.getWildernessLevel()
        return level in current.low..current.high
    }

    fun bonusMultiplier(tile: Tile): Double = if (isActive(tile)) BONUS_MULTIPLIER else 1.0

    fun start(world: World) {
        world.queue {
            while (true) {
                current = ZONES.random()
                broadcast(world, "The Wilderness hotspot has moved to ${current.label}! (+15% reward/XP there)")
                wait(ROTATION_CYCLES)
            }
        }
    }

    private fun broadcast(
        world: World,
        message: String,
    ) {
        world.players.forEach { it.filterableMessage(message) }
    }
}
