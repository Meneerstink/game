package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxMap
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * Scavenger rooms (small runt / large beast). Wiki (Chambers of Xeric; Scavenger beast): the number of scavengers and (mildly) their
 * strength follow the party size; they drop raid supplies. tsps rsprox captures (820, 843, 3282, 3285, 3389): they bite with 255 and die
 * with 258; one is gone 2 ticks after dying and another takes its place 2 ticks after that, so the room never empties.
 * ONZEKER (tsps, not on the Wiki): counts (1 solo, 2 up to 4, 3 to 7, 4 to 15 ...), spawned around the room's middle 2 tiles apart.
 * Shortcut (large room): rocks to mine / rotten sapling to chop / boulder to push (Wiki: clearing gives 5 x its level requirement in points).
 * ONZEKER (tsps): the shortcut's tile, its level random between the party's lowest and highest level + 2 (max 99), a roll every 4 ticks with
 * a 1 in max(5, min(20, raiders/3)) chance, rubble 29739 / stump 29737 crossed with anim 839, the boulder rolls 3 tiles (anim 6131) for 4-6.
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
        if (laid.room == gg.rsmod.plugins.content.activity.cox.CoxRoom.LARGE_SCAVENGER_BEAST) placeShortcut()
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

    /** rsprox: another takes a dead one's place 4 ticks after it dies. Drops (Wiki Scavenger beast): bones + two rolls, public. */
    override fun onNpcDeath(npc: Npc) {
        val home = homes.remove(npc) ?: return
        drops(Tile(npc.tile))
        later(4) { beast(home) }
    }

    /**
     * OSRS Wiki 'Scavenger beast' drops: Bones always; two rolls of 18: fishing rod / iron pickaxe / iron axe / net / hammer / tinderbox 1 each,
     * lockpick 2, cave worms 30-50 2, endarkened juice / stinkhorn / cicely 2 each but 'whenever any secondary ingredient is rolled, all three
     * are dropped together' (= 6 of 18: endarkened 5-14 + stinkhorn 3-11 + cicely 3-6), mallignum root plank x2 2 (18 of 18).
     * Net = butterfly net 10010 (the Wiki's 'Insect net').
     */
    private fun drops(at: Tile) {
        fun drop(id: Int?, amount: Int = 1) { id?.let { world.spawn(gg.rsmod.game.model.entity.GroundItem(it, amount, Tile(at))) } }
        drop(526)
        repeat(2) {
            when (val r = world.random(17)) {
                in 0..5 -> drop(listOf(307, 1267, 1349, 10010, 2347, 590)[r])
                6, 7 -> drop(1523)
                8, 9 -> drop(gg.rsmod.plugins.content.activity.cox.CoxIds.item(20853), world.random(30..50))
                in 10..15 -> {
                    drop(gg.rsmod.plugins.content.activity.cox.CoxIds.item(20911), world.random(5..14))
                    drop(gg.rsmod.plugins.content.activity.cox.CoxIds.item(20910), world.random(3..11))
                    drop(gg.rsmod.plugins.content.activity.cox.CoxIds.item(20912), world.random(3..6))
                }
                16, 17 -> drop(gg.rsmod.plugins.content.activity.cox.CoxIds.item(21036), 2)
            }
        }
    }

    // ------------------------------------------------------------------ the shortcut

    private class Shortcut(val loc: Int, val skill: Int, val cleared: Int?, val level: Int, var obj: GameObject?, var done: Boolean = false,
                           val workers: MutableSet<Player> = HashSet())
    private var shortcut: Shortcut? = null

    private fun placeShortcut() {
        val kinds = listOf(Triple(29738, Skills.MINING, 29739), Triple(29736, Skills.WOODCUTTING, 29737), Triple(29740, Skills.STRENGTH, null))
        val (loc, skill, cleared) = kinds.random()
        val members = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
        val levels = members.map { it.skills.getMaxLevel(skill) }.ifEmpty { listOf(99) }
        val low = levels.minOrNull() ?: 99; val high = levels.maxOrNull() ?: 1
        val level = minOf(99, world.random(minOf(low, high + 2)..(high + 2)))
        val t = SHORTCUT[variant]
        shortcut = Shortcut(loc, skill, cleared, level, placeLoc(loc, tile(t.x, t.z)))
    }

    /** One swing / push; returns true to keep going. */
    fun work(p: Player, verb: String): Boolean {
        val sc = shortcut ?: return false
        if (sc.done) return false
        if (p.skills.getCurrentLevel(sc.skill) < sc.level) {
            p.message("You need a ${Skills.getSkillName(world, sc.skill)} level of at least ${sc.level} to $verb this.")
            return false
        }
        sc.workers += p
        if (sc.skill == Skills.MINING) p.addXp(Skills.MINING, 4.5)
        val odds = maxOf(5, minOf(20, scaling().size / 3))
        if (world.random(odds - 1) != 0) return true
        sc.done = true
        sc.workers.filter { it.isOnline }.forEach { raid.addPoints(it.username, sc.level * 5) }
        val o = sc.obj
        if (sc.cleared != null && o != null) {
            val t = gg.rsmod.game.model.Tile(o.tile); val rot = o.rot
            removeLoc(o); sc.obj = placeLoc(sc.cleared, t, rotation = rot)
            areaSound(t, if (sc.skill == Skills.MINING) 360 else 2734, radius = 4)
        } else if (o != null) {
            val dx = Integer.signum(o.tile.x - p.tile.x); val dz = if (dx == 0) Integer.signum(o.tile.z - p.tile.z) else 0
            val to = o.tile.transform(dx * 3, dz * 3)
            animatePlayer(p, 6131)
            removeLoc(o)
            later(3) { sc.obj = placeLoc(29740, to, rotation = o.rot); raiders().filter { it.tile.x == to.x && it.tile.z == to.z }.forEach { damage(null, it, world.random(4..6)) } }
        }
        return false
    }

    fun mine(p: Player) {
        val pick = gg.rsmod.plugins.content.skills.mining.PickaxeType.values().lastOrNull { (p.inventory.contains(it.item) || p.equipment.contains(it.item)) && p.skills.getCurrentLevel(Skills.MINING) >= it.level }
            ?: run { p.message("You do not have a pickaxe which you have the Mining level to use."); return }
        p.message("You swing your pickaxe at the rocks...")
        p.queue { while (true) { p.animate(pick.animation); wait(4); play(p, 3600); if (!work(p, "mine")) break }; p.animate(-1) }
    }

    fun chopSapling(p: Player) {
        val axe = gg.rsmod.plugins.content.skills.woodcutting.AxeType.values().lastOrNull { (p.inventory.contains(it.item) || p.equipment.contains(it.item)) && p.skills.getCurrentLevel(Skills.WOODCUTTING) >= it.level }
            ?: run { p.message("You do not have an axe which you have the woodcutting level to use."); return }
        p.message("You swing your axe...")
        p.queue { while (true) { p.animate(axe.animation); wait(4); if (!work(p, "chop")) break }; p.animate(-1) }
    }

    fun push(p: Player) { work(p, "push") }

    fun cross(p: Player, o: GameObject) {
        animatePlayer(p, 839)
        p.walkTo(gg.rsmod.game.model.Tile(o.tile.x * 2 - p.tile.x, o.tile.z * 2 - p.tile.z, p.tile.height), detectCollision = false)
    }

    companion object {
        val SHORTCUT = listOf(Tile(3272, 5222), Tile(3314, 5238), Tile(3353, 5224))
    }
}
