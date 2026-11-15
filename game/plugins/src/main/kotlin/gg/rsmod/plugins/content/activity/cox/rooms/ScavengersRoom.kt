package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxMap
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * Scavenger rooms (small runt / large beast). Wiki (Chambers of Xeric; Scavenger beast): the number of scavengers and (mildly) their
 * strength follow the party size; they drop raid supplies. tsps rsprox captures (820, 843, 3282, 3285, 3389): they bite with 255 and die
 * with 258; one is gone 2 ticks after dying and another takes its place 2 ticks after that, so the room never empties.
 * ONZEKER (tsps, not on the Wiki): counts (1 solo, 2 up to 4, 3 to 7, 4 to 15 ...), spawned around the room's middle 2 tiles apart.
 * LATER: the large room's shortcut (rocks / sapling / boulder, Wiki: 5 x the level in points) and the scavengers' drop table.
 */
class ScavengersRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val homes = HashMap<Npc, Tile>()

    override fun load() {
        val n = scaling().size
        val count = listOf(1 to 1, 4 to 2, 7 to 3, 15 to 4, 23 to 5, 31 to 6, 39 to 7).firstOrNull { n <= it.first }?.second ?: 8
        val base = raid.map.area
        val middle = Tile(base.bottomLeftX + laid.gridX * CoxMap.ROOM_TILES + 16, base.bottomLeftZ + laid.gridZ * CoxMap.ROOM_TILES + 16,
            gg.rsmod.plugins.content.activity.cox.CoxLayouts.FLOOR_PLANES[floor])
        var tries = 0
        while (homes.size < count && tries++ < 200) {
            val t = middle.transform(world.random(-5..5), world.random(-5..5))
            if (!free(t) || homes.values.any { it.getDistance(t) <= 2 }) continue
            beast(t)
        }
    }

    private fun free(t: Tile): Boolean {
        for (dx in 0..1) for (dz in 0..1) {
            val c = t.transform(dx, dz)
            if (!contains(c) || gg.rsmod.game.model.Direction.NESW.none { d -> world.collision.canTraverse(c, d, projectile = false, water = false) }) return false
        }
        return true
    }

    private fun beast(t: Tile) {
        spawn(listOf(7548, 7549).random(), t, speed = 4, deathSeq = 258)?.let { homes[it] = t; fighters[it] = Fighter(4, 1) }
    }

    override fun onTick() = fight(::bite)

    private fun bite(npc: Npc, p: Player) {
        animate(npc, 255)
        val sc = scaled(npc) ?: return
        attack(npc, p, sc.maxHit, CombatClass.MELEE)
    }

    /** rsprox: another takes a dead one's place 4 ticks after it dies. */
    override fun onNpcDeath(npc: Npc) {
        val home = homes.remove(npc) ?: return
        later(4) { beast(home) }
    }
}
