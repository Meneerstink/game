package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*

/**
 * Wilderness Breach (PROJECT_PLAN SS13): a scheduled event, announced ~15 minutes ahead, that
 * spawns a wave of authentic existing Wilderness monsters (the revenant roster, already
 * implemented) around a live Wilderness player. Normal Wilderness PvP stays enabled - there
 * is no special safe zone. Every player who contributes at least [MIN_CONTRIBUTION] damage
 * across the wave gets one reward roll (no winner-takes-all).
 *
 * ponytail: the epicenter is a *live player's* current tile rather than a fixed Breach
 * location list (no verified set of Wilderness-wide coordinates to rotate through in this
 * environment) - always walkable since a real player is standing there. Contribution is a
 * damage-dealt snapshot taken from players near the epicenter when the wave starts, not a
 * dynamic join/leave feed - a late joiner can still contribute but a player who wanders off
 * after the snapshot won't be re-checked. See IMPLEMENTATION_STATUS.md.
 */
object WildernessBreach {
    private val MOB_POOL =
        listOf(
            Npcs.REVENANT_HOBGOBLIN,
            Npcs.REVENANT_HELLHOUND,
            Npcs.REVENANT_DEMON,
            Npcs.REVENANT_ORK,
            Npcs.REVENANT_DARK_BEAST,
            Npcs.REVENANT_KNIGHT,
        )

    private const val MIN_INTERVAL_CYCLES = 6000 // ~60 min
    private const val MAX_INTERVAL_CYCLES = 12000 // ~120 min
    private const val WARNING_CYCLES = 1500 // ~15 min
    private const val WAVE_TIMEOUT_CYCLES = 3000 // ~30 min cap
    private const val MIN_CONTRIBUTION = 20
    private const val SNAPSHOT_RADIUS = 20

    private val ANCIENT_WARRIOR_REWARDS =
        listOf(
            Items.VESTAS_LONGSWORD,
            Items.VESTAS_SPEAR,
            Items.STATIUSS_WARHAMMER,
            Items.ZURIELS_STAFF,
            Items.MORRIGANS_COIF,
            Items.MORRIGANS_LEATHER_BODY,
            Items.MORRIGANS_LEATHER_CHAPS,
        )

    fun start(world: World) {
        world.queue {
            while (true) {
                val interval = MIN_INTERVAL_CYCLES + RANDOM.nextInt(MAX_INTERVAL_CYCLES - MIN_INTERVAL_CYCLES)
                wait((interval - WARNING_CYCLES).coerceAtLeast(1))
                val epicentre = pickEpicentre(world)
                if (epicentre == null) {
                    // No one in the Wilderness right now - try again next cycle rather than
                    // firing a breach nobody can reach.
                    continue
                }
                broadcast(world, "A Wilderness Breach is forming and will erupt in about 15 minutes!")
                wait(WARNING_CYCLES)
                runBreach(world, epicentre)
            }
        }
    }

    private fun pickEpicentre(world: World): Tile? {
        val candidates = ArrayList<Player>()
        world.players.forEach { p -> if (p.tile.getWildernessLevel() > 0) candidates.add(p) }
        return candidates.randomOrNull()?.tile?.let { Tile(it) }
    }

    private fun runBreach(
        world: World,
        epicentre: Tile,
    ) {
        broadcast(world, "The Wilderness Breach erupts!")
        val participants = ArrayList<Player>()
        world.players.forEach { p -> if (p.tile.getDistance(epicentre) <= SNAPSHOT_RADIUS) participants.add(p) }

        val spawned = ArrayList<Npc>()
        repeat(6) {
            val type = MOB_POOL.random()
            val n =
                Npc(
                    type,
                    Tile(epicentre.x + RANDOM.nextInt(7) - 3, epicentre.z + RANDOM.nextInt(7) - 3, epicentre.height),
                    world,
                )
            n.respawns = false
            world.spawn(n)
            spawned.add(n)
        }

        world.queue {
            var elapsed = 0
            while (spawned.any { world.npcs.contains(it) && !it.isDead() } && elapsed < WAVE_TIMEOUT_CYCLES) {
                wait(5)
                elapsed += 5
            }
            spawned.forEach { if (world.npcs.contains(it)) world.remove(it) }

            val hotspotBonus = WildernessHotspot.bonusMultiplier(epicentre)
            participants.forEach { p ->
                val contribution = spawned.sumOf { it.damageMap.getDamageFrom(p) }
                if (contribution >= MIN_CONTRIBUTION) {
                    val coins = (500 + RANDOM.nextInt(1500) * hotspotBonus).toInt()
                    p.inventory.add(Items.COINS_995, coins)
                    p.filterableMessage("You take part in the Wilderness Breach and are rewarded ($coins coins).")
                    if (world.random(50) == 0) {
                        val reward = ANCIENT_WARRIOR_REWARDS.random()
                        p.inventory.add(reward)
                        p.filterableMessage("A rare Ancient Warriors' relic falls from the Breach!")
                    }
                }
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
