package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.combat.Combat

/** OSRS Wiki Slayer bell / Molanisk: level 39, within three tiles, lure a wall creature into combat. */
object Molanisks {
    private val WALL = AttributeKey<GameObject>()
    private val active = mutableSetOf<Pair<World, Tile>>()

    fun inRange(player: Tile, wall: Tile): Boolean = player.height == wall.height && player.getDistance(wall) <= 3

    fun ring(player: Player) {
        if (!player.inventory.contains(Items.SLAYER_BELL)) return
        player.animate(Anims.HUMANOID_6083) // OSRS gameval MOLANISK_PLAYER_RING_BELL; native 667 sequence.
        player.playSound(Sfx.SLAYER_MOLANISK_BELL)
        if (player.skills.getCurrentLevel(Skills.SLAYER) < 39) return
        val world = player.world
        val walls = mutableListOf<GameObject>()
        for (dx in -3..3) for (dz in -3..3) {
            val tile = player.tile.transform(dx, dz)
            val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
            chunk.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                .filterTo(walls) { it.id == Objs.MOLANISK_22545 && inRange(player.tile, it.tile) }
        }
        val wall = walls.filter { (world to it.tile) !in active }.minByOrNull { player.tile.getDistance(it.tile) } ?: return
        val key = world to wall.tile
        if (!active.add(key)) return
        val creature = Npc(Npcs.MOLANISK, wall.tile, world).apply {
            respawnOverride = false // returns to its wall, never a second roaming spawn
            attr[WALL] = wall
        }
        if (!world.spawn(creature)) {
            active.remove(key)
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.PLUGIN) {
                "Molanisk could not spawn at ${wall.tile}; wall retained"
            }
            return
        }
        world.remove(wall)
        // Wiki Molanisk: ringing while another monster attacks transfers its aggression to the Molanisk.
        // Only an NPC opponent is displaced. Player opponents, PvP reservations and Tele Block are untouched.
        val previous = player.attr[LAST_HIT_BY_ATTR]?.get() as? Npc
        if (previous != null && previous !== creature && previous.owner == null) {
            Combat.reset(previous)
            val oldCheck = previous.aggroCheck
            previous.aggroCheck = { npc, candidate -> candidate !== player && (oldCheck?.invoke(npc, candidate) != false) }
            player.attr.remove(LAST_HIT_BY_ATTR)
            player.timers.remove(Combat.PJ_TIMER)
        }
        creature.attack(player)
    }

    /** Called after the existing death animation/drop lifecycle; retain the existing combat definition's respawn delay. */
    fun died(npc: Npc) {
        val wall = npc.attr[WALL] ?: return
        npc.attr.remove(WALL)
        val world = npc.world
        world.queue {
            wait(npc.combatDef.respawnDelay)
            world.spawn(wall)
            active.remove(world to wall.tile)
        }
    }
}
