package gg.rsmod.plugins.content.areas.daemonheim

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*

/**
 * A simplified Dungeoneering training loop: a single shared "floor" at the Daemonheim
 * dungeon entrance. Players who help clear a wave of monsters (and are still nearby when
 * the wave is cleared) get Dungeoneering XP and a small completion reward; the floor number
 * then increases, escalating the monster pool.
 *
 * ponytail: this is a *reusable* shared floor, not a real per-party generated dungeon
 * instance - this engine has no instance/zone manager (see also `Construction.kt`).
 * Monsters are picked from existing, already-combat-configured NPCs to avoid re-defining
 * combat stats that are shared globally with the same NPC id used elsewhere in the world.
 * Contribution is "present when the floor clears", not per-hit damage tracking - same
 * participation model used for Wilderness Breaches. Upgrade path: a real instance manager,
 * generated room layouts, and per-hit contribution once those exist.
 */
object Dungeoneering {
    val FLOOR_ATTR = AttributeKey<Int>(persistenceKey = "dungeoneering_floor")

    private val ENTRANCE = Tile(3452, 3721, 0)
    private const val CLEAR_RADIUS = 12

    @Volatile private var currentFloor = 1

    @Volatile private var floorActive = false

    private fun monsterFor(floor: Int): Int =
        when {
            floor <= 5 -> Npcs.ZOMBIE_75
            floor <= 10 -> Npcs.SKELETON
            floor <= 20 -> Npcs.HILL_GIANT
            floor <= 35 -> Npcs.ICE_GIANT
            floor <= 60 -> Npcs.GREATER_DEMON
            else -> Npcs.BLACK_DEMON
        }

    fun join(player: Player) {
        val progress = player.attr[FLOOR_ATTR] ?: 1
        if (progress > currentFloor) {
            currentFloor = progress
        }
        player.teleportTo(Tile(ENTRANCE))
        player.filterableMessage("You enter the Daemonheim dungeon floor (floor $currentFloor).")
        if (!floorActive) {
            startFloor(player.world)
        } else {
            player.filterableMessage("A wave is already in progress here - jump in!")
        }
    }

    private fun startFloor(world: World) {
        floorActive = true
        val floor = currentFloor
        val npcType = monsterFor(floor)
        val count = (2 + floor).coerceAtMost(8)
        world.queue {
            val spawned = ArrayList<Npc>(count)
            repeat(count) {
                val n =
                    Npc(
                        npcType,
                        Tile(ENTRANCE.x + RANDOM.nextInt(5) - 2, ENTRANCE.z + RANDOM.nextInt(5) - 2, ENTRANCE.height),
                        world,
                    )
                n.respawnOverride = false
                world.spawn(n)
                spawned.add(n)
            }
            while (spawned.any { world.npcs.contains(it) && !it.isDead() }) {
                wait(3)
            }
            val participants = ArrayList<Player>()
            world.players.forEach { p ->
                if (p.tile.getDistance(ENTRANCE) <= CLEAR_RADIUS) {
                    participants.add(p)
                }
            }
            participants.forEach { p ->
                p.addXp(Skills.DUNGEONEERING, 45.0 * floor, checkBrawlingGloves = true)
                val progress = p.attr[FLOOR_ATTR] ?: 1
                if (floor >= progress) {
                    p.attr[FLOOR_ATTR] = floor + 1
                }
                if (world.random(3) == 0) {
                    p.inventory.add(Items.COINS_995, 75 * floor)
                }
                p.filterableMessage("The floor is clear! You gain Dungeoneering experience.")
            }
            currentFloor = floor + 1
            floorActive = false
        }
    }
}
