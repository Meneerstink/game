package gg.rsmod.plugins.content.skills.hunter

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*

/**
 * Simplified trap-and-wait Hunter loop: lay a trap item on the ground, it rolls a catch
 * chance every [HunterCreature.ticksBetweenRolls] cycles while it stands, and either springs
 * (collect the catch) or - on a miss roll past its patience window - is abandoned.
 *
 * ponytail: trap ownership/state lives in an in-memory map (not persisted) since traps are
 * meant to be transient; only XP/level/inventory (already save-backed) need to survive a
 * restart. Global lock via a single map, per-region sharding if this becomes a bottleneck.
 */
object Hunter {
    private const val MAX_TRAP_ROLLS = 6

    private class ActiveTrap(
        val creature: HunterCreature,
        val owner: Player,
        var obj: GameObject,
        var sprung: Boolean = false,
    )

    private val traps = HashMap<GameObject, ActiveTrap>()

    fun layTrap(
        player: Player,
        trapItem: Int,
    ) {
        val level = player.skills.getMaxLevel(Skills.HUNTER)
        val creature = HunterCreatures.bestFor(trapItem, level)
        if (creature == null) {
            player.filterableMessage("You don't have the Hunter level to use this here.")
            return
        }
        if (!player.inventory.remove(trapItem, 1).hasSucceeded()) {
            return
        }
        val world = player.world
        val tile = Tile(player.tile)
        val obj = DynamicObject(creature.emptyTrapObj, 10, 0, tile)
        world.spawn(obj)
        val trap = ActiveTrap(creature, player, obj)
        traps[obj] = trap
        player.filterableMessage("You lay a trap.")

        world.queue {
            var rolls = 0
            while (rolls < MAX_TRAP_ROLLS && world.isSpawned(trap.obj) && !trap.sprung) {
                wait(creature.ticksBetweenRolls)
                if (!world.isSpawned(trap.obj) || trap.sprung) break
                rolls++
                val chance = interpolate(creature.lowChance, creature.highChance, player.skills.getCurrentLevel(Skills.HUNTER))
                if (chance > RANDOM.nextInt(255)) {
                    spring(world, trap)
                    break
                }
            }
            // Trap timed out without a catch - remove it rather than leave it dangling forever.
            if (world.isSpawned(trap.obj) && !trap.sprung) {
                removeTrap(world, trap)
                if (player.isOnline && !player.isDead() && player.tile.getDistance(tile) < 16) {
                    player.filterableMessage("Your trap failed to catch anything and was abandoned.")
                }
            }
        }
    }

    private fun spring(
        world: gg.rsmod.game.model.World,
        trap: ActiveTrap,
    ) {
        if (!world.isSpawned(trap.obj)) return
        val tile = Tile(trap.obj.tile)
        world.remove(trap.obj)
        traps.remove(trap.obj)
        val fullObj = DynamicObject(trap.creature.fullTrapObj, 10, 0, tile)
        world.spawn(fullObj)
        trap.obj = fullObj
        trap.sprung = true
        traps[fullObj] = trap
    }

    private fun removeTrap(
        world: gg.rsmod.game.model.World,
        trap: ActiveTrap,
    ) {
        if (world.isSpawned(trap.obj)) {
            world.remove(trap.obj)
        }
        traps.remove(trap.obj)
    }

    fun collect(
        player: Player,
        obj: GameObject,
    ) {
        val trap = traps[obj] ?: return
        if (trap.owner != player) {
            player.filterableMessage("This trap belongs to someone else.")
            return
        }
        val world = player.world
        if (trap.sprung) {
            if (player.inventory.isFull) {
                player.filterableMessage("Your inventory is too full to collect this.")
                return
            }
            player.inventory.add(trap.creature.catchItem, trap.creature.catchAmount)
            player.addXp(Skills.HUNTER, trap.creature.xp, checkBrawlingGloves = true)
            player.filterableMessage("You collect your catch: ${trap.creature.name}.")
        } else {
            player.filterableMessage("You pick up your trap.")
            player.inventory.add(trap.creature.trapItem, 1)
        }
        removeTrap(world, trap)
    }
}
