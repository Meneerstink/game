package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.combat.dealHit

/**
 * The Tightrope room (DEATHLY_ROOM). Wiki (Chambers of Xeric/Strategies, Tightrope): groups of 2-4 deathly rangers and mages, their attacks
 * pass through prayer at reduced damage; they tolerate players, but whoever crosses the rope while they aren't fighting becomes their target;
 * crossing needs an Agility level between 80% and 100% of the party's average, at random; using the keystone on the barrier kills any of them
 * still standing.
 * tsps rsprox (820, 843, 3282, 3285, 3389): crossing = face the rope, walk across a tile a tick from 2 ticks later, sound 2495 each tick, XP
 * the tick after arriving, no messages; mages 1162 + gfx 129 + sound 155, projectile 130, impact 131 (splash 85) + sound 156; rangers 426 + gfx
 * 24 + sound 2693, projectile 15 landing the tick after; shouts one a tick once fighting; dying 836; dispelling gives 2,000 points.
 * ONZEKER (tsps): tiles, counts, walk anim 762, 7.5 Agility XP, prayers letting a third through, keystone anims/sounds and the messages.
 */
class TightropeRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val guards = mutableListOf<Npc>()
    private var barrier: GameObject? = null
    private var requirement = 1
    private val targetOf = HashMap<Npc, Player>()
    private val next = HashMap<Npc, Int>()
    private val shouts = ArrayDeque<Pair<Npc, String>>()

    override fun load() {
        val n = scaling().size
        val rangers = if (n >= 7) 4 else if (n >= 4) 3 else 2
        val mages = if (n >= 10) 4 else if (n >= 4) 3 else 2
        val taken = HashSet<Pair<Int, Int>>()
        guardTiles(MAGES[variant], mages, taken).forEach { addGuard(7560, it) }
        guardTiles(RANGERS[variant], rangers, taken).forEach { addGuard(7559, it) }
        val b = BARRIER[variant]
        val (sx, sz) = if (b.third % 2 == 0) 1 to 2 else 2 to 1
        barrier = placeLoc(29749, tile(b.first.x, b.first.z, sx, sz), rotation = (b.third + laid.rotation) and 3)
        val members = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }.ifEmpty { raiders() }
        val avg = if (members.isEmpty()) 99.0 else members.map { it.skills.getMaxLevel(Skills.AGILITY) }.average()
        requirement = maxOf(1, (avg * (0.8 + world.randomDouble() * 0.2)).toInt())
    }

    private fun guardTiles(spots: List<Tile>, count: Int, taken: HashSet<Pair<Int, Int>>): List<Tile> {
        val candidates = spots + spots.flatMap { s -> listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1).map { (dx, dz) -> Tile(s.x + dx, s.z + dz) } }
        val out = mutableListOf<Tile>()
        for (c in candidates) {
            if (out.size >= count) break
            val key = c.x to c.z
            val t = tile(c.x, c.z)
            if (key in taken || gg.rsmod.game.model.Direction.NESW.none { d -> world.collision.canTraverse(t, d, projectile = false, water = false) }) continue
            taken += key; out += c
        }
        return out
    }

    private fun addGuard(osrs: Int, s: Tile) { spawn(osrs, tile(s.x, s.z), speed = 4, deathSeq = 836)?.let { guards += it } }

    override fun onTick() {
        shouts.removeFirstOrNull()?.let { (npc, text) -> if (alive(npc)) npc.forceChat(text) }
        for (g in guards) {
            if (!alive(g)) continue
            val t = targetOf[g]?.takeIf { it.isOnline && !it.isDead() && contains(it.tile) } ?: continue
            g.facePawn(t)
            if (tick < (next[g] ?: 0)) continue
            next[g] = tick + 4
            swing(g, t)
        }
    }

    private fun swing(g: Npc, p: Player) {
        val sc = scaled(g) ?: return
        if (osrs(g) == 7560) {
            animate(g, 1162); gfxOn(g, 129, height = 92); areaSound(g.tile, 155, radius = 10)
            val end = 116 + 5 * g.tile.getDistance(p.tile).coerceAtMost(4)
            projectile(g.tile, p, 130, 51, end - 51, 172, 124, angle = 16)
            attack(g, p, sc.maxHit, CombatClass.MAGIC, delay = maxOf(1, end / 30), protectedPercent = 33) { dealt ->
                gfxOn(p, if (dealt > 0) 131 else 85, height = 124); play(p, 156)
            }
        } else {
            animate(g, 426); gfxOn(g, 24, height = 96); play(p, 2693)
            projectile(g.tile, p, 15, 41, 18, 163, 146, angle = 15, steepness = 11)
            attack(g, p, sc.maxHit, CombatClass.RANGED, delay = 1, protectedPercent = 33)
        }
    }

    override fun passRefusal(p: Player, passage: Tile): String? = blockedNear(passage, listOfNotNull(barrier?.tile))

    /** Guards answer whoever attacks them. */
    override fun incoming(npc: Npc, hit: gg.rsmod.game.model.Hit) {
        val p = hit.source as? Player ?: return
        if (npc in guards && targetOf[npc] == null) targetOf[npc] = p
    }

    /** Op on the tightrope 29750 (template loc): Agility check, then across to the rope's other end. */
    fun cross(p: Player, rope: GameObject) {
        if (p.skills.getCurrentLevel(Skills.AGILITY) < requirement) {
            p.queue { messageBox("You need an Agility level of $requirement to cross this tightrope.") }
            return
        }
        val ropeId = rope.id
        val end = (0 until 32).flatMap { dx -> (0 until 32).map { dz -> dx to dz } }.asSequence()
            .map { (dx, dz) -> Tile(raid.map.area.bottomLeftX + laid.gridX * 32 + dx, raid.map.area.bottomLeftZ + laid.gridZ * 32 + dz, rope.tile.height) }
            .mapNotNull { t -> world.getObject(t, gg.rsmod.game.model.collision.ObjectType.INTERACTABLE)?.takeIf { it.id == ropeId && (it.tile.x != rope.tile.x || it.tile.z != rope.tile.z) } }
            .filter { it.tile.x == rope.tile.x || it.tile.z == rope.tile.z }
            .minByOrNull { it.tile.getDistance(rope.tile) } ?: return
        val dest = end.tile.transform(Integer.signum(end.tile.x - rope.tile.x), Integer.signum(end.tile.z - rope.tile.z))
        val idle = guards.filter { alive(it) && targetOf[it] == null }
        idle.forEach { targetOf[it] = p }
        idle.shuffled().take(world.random(1..4)).forEach { shouts.addLast(it to SHOUTS.random()) }
        p.stopMovement()
        p.faceTile(dest)
        p.queue {
            wait(2)
            val steps = maxOf(kotlin.math.abs(dest.x - p.tile.x), kotlin.math.abs(dest.z - p.tile.z))
            p.walkTo(dest, detectCollision = false)
            repeat(steps) { p.playSound(sound(2495), loops = 2); wait(1) }
            wait(1)
            p.addXp(Skills.AGILITY, 7.5)
        }
    }

    /** Keystone 20884 (op 'Dispel' or item on the barrier): the barrier goes, every guard still standing dies, 2,000 points (tsps). */
    fun dispel(p: Player) {
        val b = barrier ?: return
        val keystone = CoxIds.item(20884) ?: return
        if (!p.inventory.contains(keystone)) { p.message("You're going to need a magical keystone to dispel this barrier."); return }
        p.inventory.remove(keystone, 1)
        animatePlayer(p, 832); play(p, 1657)
        p.message("Your keystone glows as it is absorbed into the barrier, which disperses.")
        removeLoc(b); barrier = null
        completed = true
        raid.addPoints(p.username, 2000)
        guards.filter { alive(it) }.forEach { g ->
            val hp = g.getCurrentLifepoints().toDouble()
            p.dealHitTo(g, hp)
        }
    }

    private fun Player.dealHitTo(g: Npc, hp: Double) {
        dealHit(g, minHit = hp, maxHit = hp, landHit = true, delay = 0, hitType = gg.rsmod.plugins.api.HitType.REGULAR_HIT)
    }

    /** The keystone on its pedestal 29751: take one (tsps messages). */
    fun take(p: Player) {
        val keystone = CoxIds.item(20884) ?: return
        if (p.inventory.freeSlotCount <= 0) { p.message("You need some more free inventory space to pick it up."); return }
        if (p.inventory.contains(keystone)) { p.message("You already have a keystone, and see no need to take another."); return }
        animatePlayer(p, 832); play(p, 2582)
        p.inventory.add(keystone)
    }

    companion object {
        val SHOUTS = listOf("Intruder!", "Stop them at all costs!", "Protect the keystone!")
        val MAGES = listOf(listOf(Tile(3285, 5363), Tile(3287, 5363)), listOf(Tile(3317, 5363), Tile(3319, 5363)), listOf(Tile(3340, 5365), Tile(3340, 5367)))
        val RANGERS = listOf(listOf(Tile(3284, 5370), Tile(3286, 5370)), listOf(Tile(3316, 5370), Tile(3318, 5370)), listOf(Tile(3333, 5364), Tile(3333, 5366)))
        val BARRIER = listOf(Triple(Tile(3270, 5360), 0, 0), Triple(Tile(3309, 5368), 0, 1), Triple(Tile(3355, 5360), 0, 2))
            .map { (t, _, face) -> Triple(t, 0, face) }
    }
}
