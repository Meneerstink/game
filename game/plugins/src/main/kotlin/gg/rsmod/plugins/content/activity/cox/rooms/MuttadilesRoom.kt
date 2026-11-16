package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * The Muttadiles. Wiki (Muttadile; Meat tree): the submerged large one is unattackable and fires erratic Magic (each player it sees 1 in 3);
 * the small one uses Ranged at a distance and Melee up close; the large one all three, up close a stomp on everyone in melee range for up to
 * 50% more than its melee max, at a distance one style for at least three attacks; at about half health they eat from the meat tree up to
 * half their health, at most three times, giving up after about 15 seconds; the meat tree has 5 x the party's total Woodcutting (at least
 * 100); a chop succeeds 20% at Woodcutting 1 to 78% at 99 and takes off level/2 -7..+3; cutting gives points.
 * tsps rsprox (820, 843, 3282, 3285, 3389): submerged it fires every 6 ticks (projectile 393, sound 162); small bites 7420 / spits 7421
 * (projectile 1291, sound 385) every 4 ticks; small dies 7426, the large one emerges (7423) crawling a tile a tick for 5 ticks, turns to 7563,
 * attacks 2 later (spit, magic 7422 + 393, slam 7424 sound 163, bites); eating = 3 bites on 3 ticks (7420), 15% each, at most three meals.
 * ONZEKER (tsps): tiles, waking within 6 tiles of the tendrils, bases (submerged/large magic 25, spits 30), prayers letting a third
 * through, 45 XP + 2 points a chop, felled tree 30013, exit crystal 30018.
 */
class MuttadilesRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private var small: Npc? = null
    private var large: Npc? = null
    private var tree: Npc? = null
    private var treeBase: GameObject? = null
    private lateinit var treeTile: Tile
    private val tendrils = mutableListOf<GameObject>()
    private val tendrilTiles = mutableListOf<Tile>()
    private var woken = false
    private var largeState = 0 // 0 submerged, 1 emerging, 2 out
    private val meals = HashMap<Npc, Int>()
    private class Meal(val since: Int, var bites: Int = 3, var bitten: Boolean = false)
    private val eating = HashMap<Npc, Meal>()
    private val next = HashMap<Npc, Int>()
    private var largeStyle = CombatClass.RANGED
    private var largeStyleLeft = 0

    override fun load() {
        val t = TREE[variant]
        treeTile = tile(t.x, t.z, 2)
        treeBase = placeLoc(30012, Tile(treeTile), rotation = 0)
        tree = spawn(7564, Tile(treeTile))?.also { tr ->
            val wc = raid.points.keys.mapNotNull { k -> world.players.entries.firstOrNull { it?.username?.lowercase() == k } }
                .sumOf { it.skills.getMaxLevel(Skills.WOODCUTTING) }.coerceAtLeast(raiders().sumOf { it.skills.getMaxLevel(Skills.WOODCUTTING) })
            val hp = maxOf(100, 5 * wc)
            tr.combatDef = tr.combatDef.copy(lifepoints = hp)
            tr.setCurrentLifepoints(hp)
        }
        blockExit(CRYSTAL)
        TENDRILS[variant].forEach { s -> val tt = tile(s.x, s.z); tendrilTiles += tt; placeLoc(29767, tt, rotation = 0)?.let { tendrils += it } }
        small = spawn(7562, tile(SMALL[variant].x, SMALL[variant].z, 3), speed = 4, deathSeq = 7426)
        large = spawn(7561, tile(LARGE[variant].x, LARGE[variant].z, 8), speed = 4, deathSeq = 7426)
    }

    override fun onTick() {
        if (!woken) { if (raiders().any { it.tile.getDistance(tendrilTiles.first()) <= 6 }) woken = true; return }
        if (completed) return
        val lg = large
        if (lg != null && largeState == 0 && alive(small) && tick % 6 == 0) erratic(lg)
        for (npc in listOfNotNull(small, large)) {
            if (!alive(npc)) continue
            if (eating.containsKey(npc)) { eat(npc); continue }
            if (npc === small || largeState == 2) {
                considerEating(npc)
                if (!eating.containsKey(npc)) fightOne(npc)
            }
        }
    }

    private fun erratic(lg: Npc) {
        val sc = scaled(lg) ?: return
        raiders().filter { world.random(1..3) == 1 }.forEach { p ->
            val lands = fly(lg, p, 393, 61, 15, 62, 36, 5)
            areaSound(lg.tile, 162, radius = 15, delay = 10)
            attack(lg, p, CoxMonsters.maxHitFor(7561, sc, 25, CoxMonsters.MAG), CombatClass.MAGIC, delay = lands, protectedPercent = 33)
        }
    }

    /** A flight from [npc] to [p]: ends 5 client cycles a tile further out (tsps). Returns the ticks until it lands. */
    private fun fly(npc: Npc, p: Player, id: Int, start: Int, extra: Int, startHeight: Int, endHeight: Int, angle: Int): Int {
        val end = start + extra + 5 * npc.tile.getDistance(p.tile)
        projectile(npc.tile, p, id, start, end - start, startHeight, endHeight, angle = angle)
        return maxOf(1, end / 30)
    }

    private fun considerEating(npc: Npc) {
        val tr = tree ?: return
        if (!alive(tr)) return
        if ((meals[npc] ?: 0) >= 3 || npc.getCurrentLifepoints() * 2 >= npc.getMaximumLifepoints() || world.random(2) != 0) return
        eating[npc] = Meal(tick)
        meals[npc] = (meals[npc] ?: 0) + 1
        npc.faceTile(treeTile)
        npc.walkTo(treeTile)
    }

    private fun eat(npc: Npc) {
        val meal = eating[npc] ?: return
        if (!alive(tree) || (tick - meal.since > 25 && !meal.bitten)) { eating.remove(npc); return }
        if (npc.tile.getDistance(treeTile) > npc.getSize() + 1) { if (!npc.hasMoveDestination()) npc.walkTo(treeTile); return }
        meal.bitten = true
        npc.stopMovement()
        animate(npc, 7420)
        npc.setCurrentLifepoints(minOf(npc.getMaximumLifepoints(), npc.getCurrentLifepoints() + npc.getMaximumLifepoints() * 15 / 100))
        if (--meal.bites <= 0) eating.remove(npc)
    }

    private fun fightOne(npc: Npc) {
        val p = nearest(npc) ?: return
        val isLarge = npc === large
        val melee = meleeReach(npc, p.tile)
        if (!melee && distance(p.tile, npc) > 6) { npc.walkTo(p.tile); return }
        npc.stopMovement(); npc.facePawn(p)
        if (tick < (next[npc] ?: 0)) return
        next[npc] = tick + 4
        val sc = scaled(npc) ?: return
        val o = osrs(npc)
        if (melee) {
            if (isLarge && world.random(1) == 0) {
                animate(npc, 7424)
                areaSound(p.tile, 163, radius = 6, delay = 30)
                raiders().filter { meleeReach(npc, it.tile) }.forEach { attack(npc, it, sc.maxHit, CombatClass.MELEE, delay = 1) }
            } else {
                animate(npc, 7420)
                attack(npc, p, if (isLarge) sc.maxHit * 2 / 3 else sc.maxHit, CombatClass.MELEE, delay = 1)
            }
            return
        }
        var style = CombatClass.RANGED
        if (isLarge) {
            if (largeStyleLeft <= 0) { largeStyle = if (world.random(1..5) <= 4) CombatClass.RANGED else CombatClass.MAGIC; largeStyleLeft = 3 }
            largeStyleLeft--
            style = largeStyle
        }
        if (style == CombatClass.RANGED) {
            animate(npc, 7421)
            val lands = fly(npc, p, 1291, 5, 40, if (isLarge) 46 else 25, 36, 5)
            areaSound(npc.tile, 385, radius = 10)
            attack(npc, p, CoxMonsters.maxHitFor(o, sc, 30, CoxMonsters.RNG), CombatClass.RANGED, delay = lands, protectedPercent = 33)
        } else {
            animate(npc, 7422)
            val lands = fly(npc, p, 393, 61, 15, 62, 36, 5)
            areaSound(npc.tile, 162, radius = 10)
            attack(npc, p, CoxMonsters.maxHitFor(o, sc, 25, CoxMonsters.MAG), CombatClass.MAGIC, delay = lands, protectedPercent = 33)
        }
    }

    private fun emerge() {
        val lg = large ?: return
        if (!alive(lg)) return
        largeState = 1
        animate(lg, 7423)
        val towards = small?.tile ?: tendrilTiles.first()
        // Wiki: 'the larger one emerges from the water and starts attacking' - onto the ground next to where the small one fell. The old
        // fixed 5-tile step left it in the water in variant 0 (16-11 test: stayed on 6406,4, out of reach): the nearest spot near the
        // small one's tile where its whole 5x5 body stands on open ground.
        val land = landFor(lg, towards)
        lg.walkTo(land, detectCollision = false)
        later(5) {
            if (!alive(lg)) return@later
            if (lg.tile.x != land.x || lg.tile.z != land.z) lg.moveTo(land)
            CoxIds.npc(7563)?.let { lg.setTransmogId(it) }; configure(lg, 7563, 4, 7426, keepHp = true)
        }
        later(7) { largeState = 2 }
    }

    private fun landFor(lg: Npc, near: Tile): Tile {
        val size = 5
        fun open(t: Tile) = (0 until size).all { x -> (0 until size).all { z ->
            val f = Tile(t.x + x, t.z + z, t.height); contains(f) && !world.collision.isClipped(f) } }
        return (near.x - 8..near.x + 8).flatMap { x -> (near.z - 8..near.z + 8).map { z -> Tile(x, z, near.height) } }
            .filter { open(it) }.minByOrNull { it.getDistance(near) } ?: Tile(lg.tile)
    }

    /** Submerged/emerging it can't be hurt (Wiki: unattackable). */
    override fun incoming(npc: Npc, hit: Hit) {
        if (npc === large && largeState != 2) hit.hitmarks.forEach { it.damage = 0 }
        if (npc === tree && hit.source is Player) hit.hitmarks.forEach { it.damage = 0 }
    }

    override fun onNpcDeath(npc: Npc) {
        when {
            npc === small -> {
                // Wiki 'Muttadile' (small): big bones, overload (+)(4), prayer enhance (+)(4), stinkhorn 1-3; long bone 1/400.
                val at = Tile(npc.tile)
                drop(at, 532, classic = true); drop(at, 20996); drop(at, 20972); drop(at, 20910, world.random(1..3))
                if (world.random(399) == 0) drop(at, 10976, classic = true)
                later(3) { emerge() }
            }
            npc === tree -> { val b = treeBase; treeBase = null; removeLoc(b); placeLoc(30013, Tile(treeTile), rotation = 0) }
            npc === large -> {
                // Wiki 'Muttadile' (large): big bones, overload (+)(4), prayer enhance (+)(4), Xeric's aid (+)(4), revitalisation (+)(4); long bone 1/400.
                val at = Tile(npc.tile)
                drop(at, 532, classic = true); drop(at, 20996); drop(at, 20972); drop(at, 20984); drop(at, 20960)
                dropBook(at, 20897)
                if (world.random(399) == 0) drop(at, 10976, classic = true)
                clearExit(); later(2) { tendrils.toList().forEach { removeLoc(it) }; tendrils.clear() } }
        }
    }

    /** Wiki (Mod Ash): chop chance 20% at Woodcutting 1 to 78% at 99; damage level/2 -7..+3; tsps: a roll every 5 ticks, 45 XP + 2 points. */
    fun chop(p: Player) {
        val tr = tree
        if (tr == null || !alive(tr)) { p.message("The tree has died."); return }
        val axe = gg.rsmod.plugins.content.skills.woodcutting.AxeType.values().reversed()
            .firstOrNull { (p.inventory.contains(it.item) || p.equipment.contains(it.item)) && p.skills.getCurrentLevel(Skills.WOODCUTTING) >= it.level }
        if (axe == null) { p.message("You do not have an axe which you have the woodcutting level to use."); return }
        p.message("You swing your axe...")
        p.queue {
            var ticks = 0
            while (alive(tr) && !closed) {
                if (ticks % 4 == 0) p.animate(axe.animation)
                wait(1)
                if (++ticks % 5 != 0) continue
                val level = p.skills.getCurrentLevel(Skills.WOODCUTTING).coerceIn(1, 99)
                if (world.randomDouble() >= 0.2 + (level - 1) * 0.58 / 98) continue
                p.addXp(Skills.WOODCUTTING, 45.0)
                raid.addPoints(p.username, 2)
                val dmg = maxOf(1, level / 2 + world.random(-7..3))
                if (tr.getCurrentLifepoints() - dmg <= 0) {
                    raiders().forEach { play(it, 2734) }
                    p.message("You hack away the final pieces of meat.")
                    remove(tr); onNpcDeath(tr)
                    break
                }
                tr.setCurrentLifepoints(tr.getCurrentLifepoints() - dmg)
                play(p, 2070)
                p.message("You hack away some of the meat.")
            }
            p.animate(-1)
        }
    }

    companion object {
        val TREE = listOf(Tile(3269, 5320), Tile(3301, 5320), Tile(3350, 5317))
        val TENDRILS = listOf(listOf(Tile(3279, 5314), Tile(3280, 5314)), listOf(Tile(3311, 5314), Tile(3312, 5314)), listOf(Tile(3343, 5313), Tile(3344, 5313)))
        val CRYSTAL = listOf(Tile(3270, 5328), Tile(3308, 5335), Tile(3352, 5327))
        val LARGE = listOf(Tile(3282, 5332), Tile(3314, 5330), Tile(3336, 5330))
        val SMALL = listOf(Tile(3276, 5328), Tile(3307, 5325), Tile(3344, 5323))
    }
}
