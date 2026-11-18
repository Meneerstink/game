package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.message.impl.LocAnimMessage
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxChest
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxMap
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.combat.poison
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * The Great Olm. Wiki (Great Olm): basic magic/ranged attacks with the same damage, prayer takes off ~75-80%, 1 in 5 to switch style; spheres
 * (red/green/purple) take half the target's hitpoints unless praying melee/ranged/magic, praying anything at the launch turns prayers off and
 * halves Prayer; phase attacks 1 in 3 instead of a standard attack (1 in 2 in the last phase), avoiding those used this phase: acid (Acid
 * Spray pools 3-6 a tick + poison, Acid Drip), flame (Deep Burn: 5 and -2 to two stats six times, spreading; Fire Wall: 50-65 if still between
 * the walls, water/ice spells, Humidify or a watering can douse it), crystal (Falling Crystals 15-20 on one player, Crystal Bombs 15 at 4 tiles
 * +15 each tile closer); claw specials on the left claw: Crystal Burst, Lightning (zaps, overheads off, bind), Swap (pairs swap places, 5 a tile
 * apart); the rotation standard, empty, standard, Crystal Burst, standard, empty, standard, Lightning, standard, empty, standard, Swap; it
 * attacks the zone with most players; before the last phase the head heals whatever is done to it; the left claw clenches when hit for at least
 * 1/20 of its health; phases: 3 + raiders/8 claw phases; in the last one the claws must fall within 34 ticks of each other, then the final
 * stand (Life Siphon); mitigation: head a third from Magic, left claw a third from Magic and Ranged, right claw a third from Ranged.
 * tsps rsprox (820, 843, 3282, 3285, 3389): rises 7 ticks after the barrier is passed ('The Great Olm rises with the power of <col>...'),
 * rising locs + anims a tick later + sound 3405, risen at 6, first attack the tick after; head projectiles 1339 magic (sound 3749), 1340
 * ranged, spheres 1341/1343/1345 (sound 1784) landing at 180 cycles, acid 1354, burn 1349 (sound 585), fire walls 1347/1348 (sound 3750),
 * bombs 1357 exploding 11 ticks after the throw (gfx 40, sound 1021), falling crystals 1352/1353 every 2 ticks; claws fall 7352/7370, cave loc
 * 2 ticks later; the last claw down: head falls 4 ticks later, cave at 6, rises across the chamber at 38; messages word for word (donor).
 * ONZEKER (tsps, not on the Wiki): the chamber tiles, attacks every 4 ticks, transition crystals, acid pools 16 ticks, clusters, lightning 18
 * ticks for 15-25 + 5-tick bind, Swap 8 ticks, deep burn every 7 ticks, the clench 45 ticks, Life Siphon 1 in 16 in the final stand.
 * Not like OSRS (yet): dousing = the fire's 'Douse' op with a watering can or a water/ice autocast (casting on the npc has no hook here).
 */
class OlmRoom(raid: CoxRaid, laid: CoxLaidRoom) : CoxRoom(raid, raid.layout.floors.lastIndex, laid) {
    private class Side(val head: Triple<Int, Int, Int>, val left: Triple<Int, Int, Int>, val right: Triple<Int, Int, Int>, val npcOffset: Int, val headShift: Int)
    private val sides = listOf(
        Side(Triple(3220, 5738, 3), Triple(3220, 5743, 3), Triple(3220, 5733, 3), 3, -1),
        Side(Triple(3238, 5738, 1), Triple(3238, 5733, 1), Triple(3238, 5743, 1), 0, 1),
    )
    private var side = world.random(1)
    private var phase = 1
    private var totalPhases = 3
    private var power = -1
    private var risen = false
    private var dead = false
    private var transitioning = false
    private var finalStandStarted = false
    private var head: Npc? = null
    private var leftClaw: Npc? = null
    private var rightClaw: Npc? = null
    private var rightGone = false
    private val locs = HashMap<String, GameObject?>()
    private var direction = MIDDLE
    private var counter = 0
    private var skippedBasic = false
    private var ranging = false
    private var lastAttack = ""
    private var usedCycleScript = false
    private var cycleScriptAt = 0
    private var attackWait = 0
    private val usedThisPhase = HashSet<String>()
    private val busy = HashSet<String>()
    private var clench = 0
    private var clenched = false
    private val downAt = HashMap<Npc, Int>()
    private class Pool(val obj: GameObject?, var ticks: Int)
    private val pools = HashMap<Pair<Int, Int>, Pool>()
    private val acidUntil = HashMap<Player, Int>()
    private class Cluster(var obj: GameObject?, var ticks: Int, val tile: Tile)
    private val clusters = HashMap<Pair<Int, Int>, Cluster>()
    private var crystalsUntil = -1
    private val falling = HashMap<Pair<Int, Int>, Pair<Tile, IntArray>>()
    private var onPlayerIn = 5
    private val fires = mutableListOf<Npc>()

    private fun t(x: Int, z: Int) = CoxMap.olmTile(raid.map, Tile(x, z, 0))
    private val low by lazy { t(3227, 5730) }
    private val high by lazy { t(3237, 5748) }
    private fun y(cacheZ: Int) = t(3227, cacheZ).z
    private fun x(cacheX: Int) = t(cacheX, 5730).x

    override fun load() {
        totalPhases = 3 + scaling().size / 8
    }

    private fun inChamber(p: Tile) = p.height == 0 && p.x in low.x..high.x && p.z in low.z..high.z
    private fun inZone(p: Tile, zone: Int): Boolean {
        if (!inChamber(p)) return false
        return when (zone) {
            SOUTH -> p.z <= y(5740)
            NORTH -> p.z >= y(5740)
            MIDDLE -> p.z > y(5734) && p.z < y(5746)
            else -> true
        }
    }

    override fun contains(t: Tile): Boolean = inChamber(t)
    private fun olmRaiders() = world.players.entries.filterNotNull().filter { it.isOnline && !it.isDead() && inChamber(it.tile) }
    private fun viewers() = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) && it.tile.height == 0 &&
        CoxMap.roomAt(raid.map, raid.layout, it.tile) == null }
    private fun everyone(zone: Int) = olmRaiders().filter { inZone(it.tile, zone) }
    private fun lastPhase() = phase >= totalPhases
    private fun finalStand() = lastPhase() && !alive(leftClaw) && !alive(rightClaw)

    private fun randomTile(zone: Int): Tile? {
        repeat(50) {
            val c = Tile(world.random(low.x..high.x), world.random(low.z..high.z), 0)
            if (inZone(c, zone) && gg.rsmod.game.model.Direction.NESW.any { d -> world.collision.canTraverse(c, d, projectile = false, water = false) }) return c
        }
        return null
    }

    // ------------------------------------------------------------------ locs

    private fun locAnim(key: String, anim: Int) {
        val obj = locs[key] ?: return
        val id = seq(anim).takeIf { it >= 0 } ?: return
        viewers().forEach { it.write(LocAnimMessage(obj, id)) }
    }
    private fun headAnim(pair: IntArray) = locAnim("head", if (lastPhase()) pair[1] else pair[0])
    private fun leftAnim(pair: IntArray) = locAnim("left", if (rightGone) pair[1] else pair[0])

    private fun place(key: String, osrs: Int, at: Triple<Int, Int, Int>) {
        locs[key]?.let { removeLoc(it) }
        locs[key] = placeLoc(osrs, t(at.first, at.second), rotation = at.third)
    }

    // ------------------------------------------------------------------ the barrier and rising

    /** The mystical barrier 29879: 'Pass' asks, 'Quick-pass' doesn't; no way back (tsps). */
    fun passBarrier(p: Player, barrier: GameObject, confirm: Boolean) {
        val inside = p.tile.z > barrier.tile.z
        if (inside && !dead) { p.queue { messageBox("A strange magic prevents you from passing through.<br><br>There's no way back now.") }; return }
        val pass = {
            if (!dead) p.message("As you pass through the barrier, a sense of dread washes over you.")
            p.moveTo(p.tile.transform(0, if (inside) -2 else 2))
            if (!risen && !dead) { risen = true; raid.progress = maxOf(raid.progress, 4); later(7) { rise() } }
        }
        if (!confirm || dead) { pass(); return }
        p.queue {
            messageBox("You sense that strange magic will prevent you from coming back through. Make sure you are prepared for what is ahead.")
            if (options("Yes", "No", title = "Go through?") == 1) pass()
        }
    }

    private fun rise() {
        if (closed) return
        risen = true
        rightGone = false
        val s = sides[side]
        power = listOf(ACID, FLAME, CRYSTAL).filter { it != power }.random()
        if (!lastPhase()) olmRaiders().forEach { it.message("The Great Olm rises with the power of ${POWER_NAMES[power]}</col>.") }
        place("head", 29880, s.head); place("left", 29883, s.left); place("right", 29886, s.right)
        later(1) {
            locs["head"]?.let { areaSound(it.tile.transform(5, 2), 3405, radius = 15, delay = 15) }
            headAnim(intArrayOf(7335, 7383)); locAnim("left", 7354); locAnim("right", 7350)
        }
        later(6) { risenUp() }
    }

    private fun risenUp() {
        if (closed) return
        val s = sides[side]
        place("head", 29881, s.head); place("left", 29884, s.left); place("right", 29887, s.right)
        fun at(key: String, shift: Int = 0) = locs[key]!!.tile.let { Tile(it.x + s.npcOffset + shift, it.z, 0) }
        head = spawn(7554, at("head", s.headShift), speed = 4)
        leftClaw = spawn(7555, at("left"), speed = 4)
        rightClaw = spawn(7553, at("right"), speed = 4)
        listOfNotNull(head, leftClaw, rightClaw).forEach { fighters.remove(it) }
        usedThisPhase.clear(); counter = 0; attackWait = 1; direction = MIDDLE
        clench = 0; clenched = false
        headAnim(intArrayOf(7336, 7374)); leftAnim(intArrayOf(7355, 7365)); locAnim("right", 7351)
    }

    // ------------------------------------------------------------------ every tick

    override fun onTick() {
        tickPools(); tickClusters(); tickFalling()
        val h = head ?: return
        if (!alive(h) || dead) return
        if (!finalStand() && h.getCurrentLifepoints() < h.getMaximumLifepoints() && tick % 5 == 0) h.setCurrentLifepoints(h.getMaximumLifepoints())
        if (clench > 0 && --clench == 0) {
            clenched = false
            if (lastPhase()) leftAnim(intArrayOf(7355, 7365)) else { locAnim("left", 7362); later(1) { leftAnim(intArrayOf(7355, 7365)) } }
        }
        for ((claw, at) in downAt.toList()) if (tick >= at) regrow(claw)
        if (transitioning) return
        if (--attackWait > 0) return
        attackWait = 4
        if (olmRaiders().isEmpty()) return
        attack()
    }

    private fun zoneWithMostPlayers(): Int {
        var south = 0; var middle = 0; var north = 0
        val sep = y(5740)
        for (p in olmRaiders()) { if (inZone(p.tile, MIDDLE)) middle++; if (p.tile.z > sep) north++; if (p.tile.z < sep) south++ }
        if (middle >= south && middle >= north) return MIDDLE
        return if (north >= south) NORTH else SOUTH
    }

    private fun attack() {
        val seq = if (lastPhase()) LAST_SEQUENCE else SEQUENCE
        val step = seq[counter]
        counter = (counter + 1) % seq.size
        val zoneEmpty = everyone(direction).isEmpty()
        if (zoneEmpty || (step != "auto" && !skippedBasic)) {
            if (step == "auto") skippedBasic = true
            val wanted = zoneWithMostPlayers()
            if (wanted != direction) {
                if (step in SPECIALS && !zoneEmpty) { val c = skippedBasic; skippedBasic = false; special(step, c) }
                turnHead(wanted)
                return
            }
            if (step == "skip") return
        }
        val caught = skippedBasic
        skippedBasic = false
        if (step == "auto") autoAttack() else special(step, caught)
    }

    private fun turnHead(zone: Int) {
        val left = side == LEFT
        val from = direction
        val anim = when (zone) {
            SOUTH -> if (from == NORTH) (if (left) intArrayOf(7343, 7379) else intArrayOf(7344, 7380)) else (if (left) intArrayOf(7339, 7381) else intArrayOf(7341, 7377))
            MIDDLE -> if (from == SOUTH) (if (left) intArrayOf(7340, 7382) else intArrayOf(7342, 7378)) else (if (left) intArrayOf(7342, 7378) else intArrayOf(7340, 7382))
            else -> if (from == SOUTH) (if (left) intArrayOf(7344, 7380) else intArrayOf(7343, 7379)) else (if (left) intArrayOf(7341, 7377) else intArrayOf(7339, 7381))
        }
        headAnim(anim)
        direction = zone
        later(1) { headAnim(resting()) }
    }

    private fun resting(): IntArray {
        val left = side == LEFT
        return when (direction) {
            MIDDLE -> intArrayOf(7336, 7374)
            SOUTH -> if (left) intArrayOf(7337, 7376) else intArrayOf(7338, 7375)
            else -> if (left) intArrayOf(7338, 7375) else intArrayOf(7337, 7376)
        }
    }

    private fun headAttackAnim() {
        val left = side == LEFT
        headAnim(when (direction) {
            MIDDLE -> intArrayOf(7345, 7371)
            SOUTH -> if (left) intArrayOf(7346, 7373) else intArrayOf(7347, 7372)
            else -> if (left) intArrayOf(7347, 7372) else intArrayOf(7346, 7373)
        })
        later(1) { headAnim(resting()) }
    }

    private fun face(): Tile {
        val (fx, fz) = if (side == LEFT) 3227 to 5740 else 3238 to 5740
        val shift = when (direction) { SOUTH -> -1; NORTH -> 1; else -> 0 }
        return Tile(x(fx), y(fz) + shift, 0)
    }

    /** A shot from the head (tsps rsprox): 32 cycles in, angle 5, ends at [end] (default 26 + 5 a tile). Ticks until it lands. */
    private fun headShot(to: Player, id: Int, end: Int? = null): Int {
        val from = face()
        val finish = end ?: (26 + 5 * from.getDistance(to.tile))
        projectile(from, to, id, 32, finish - 32, 88, 40, angle = 5, steepness = 0)
        return maxOf(1, finish / 30)
    }
    private fun headShot(to: Tile, id: Int, end: Int? = null): Int {
        val from = face()
        val finish = end ?: (26 + 5 * from.getDistance(to))
        projectile(from, to, id, 32, finish - 32, 88, 0, angle = 5, steepness = 0)
        return maxOf(1, finish / 30)
    }
    private fun headSound(id: Int, delay: Int = 12, radius: Int = 15) = areaSound(face(), id, radius = radius, delay = delay)

    private fun leftUnavailable() = !alive(leftClaw) || clenched || downAt.containsKey(leftClaw)

    private fun autoAttack() {
        if (lastAttack != "sphere" && world.random(19) == 0) { sphere(); lastAttack = "sphere"; return }
        if (finalStand() && world.random(15) == 0) { lifeSiphon(); lastAttack = "siphon"; return }
        val chance = if (lastPhase()) 0.5 else 1.0 / 3
        if (!usedCycleScript && tick >= cycleScriptAt && world.randomDouble() < chance) {
            usedCycleScript = true; cycleScriptAt = tick + 17
            phaseAttack(); return
        }
        standard(); lastAttack = "standard"
    }

    private fun special(step: String, caughtUp: Boolean) {
        usedCycleScript = false
        if (caughtUp) autoAttack()
        if (leftUnavailable()) return
        when (step) { "burst" -> crystalBurst(); "lightning" -> lightning(); "swap" -> swap(); "protect" -> protectLeft() }
    }

    private fun phaseAttack() {
        val p = if (lastPhase()) world.random(2) else power
        val pair = listOf(listOf("acidDrip", "acidSpray"), listOf("deepBurn", "fireWall"), listOf("fallingCrystals", "crystalBombs"))[p]
        val fresh = pair.filter { it !in usedThisPhase && it !in busy }
        val name = fresh.randomOrNull() ?: pair.filter { it !in busy }.randomOrNull() ?: pair[1]
        usedThisPhase += name
        lastAttack = name
        when (name) {
            "acidDrip" -> acidDrip(); "acidSpray" -> acidSpray(); "deepBurn" -> deepBurn(); "fireWall" -> fireWall()
            "fallingCrystals" -> fallingCrystals(); else -> crystalBombs()
        }
    }

    // ------------------------------------------------------------------ standard attacks

    private fun maxHit(): Int {
        val h = head ?: return 27
        val sc = scaled(h) ?: return 27
        val base = if (finalStand()) 29 else if (lastPhase()) 28 else 27
        return CoxMonsters.maxHitFor(7554, sc, base, CoxMonsters.MAG)
    }

    private fun standard() {
        if (world.random(4) == 0) ranging = !ranging
        val targets = everyone(direction)
        val h = head ?: return
        if (targets.isEmpty()) return
        headAttackAnim()
        headSound(if (ranging) 1784 else 3749, radius = if (ranging) 15 else 13)
        targets.forEach { p ->
            val lands = headShot(p, if (ranging) 1340 else 1339)
            attack(h, p, maxHit(), if (ranging) CombatClass.RANGED else CombatClass.MAGIC, delay = lands, protectedPercent = 20)
        }
    }

    private fun sphere() {
        val targets = everyone(direction).shuffled().take(3)
        if (targets.isEmpty()) return
        headAttackAnim()
        headSound(1784, delay = 10)
        val types = listOf(0, 1, 2).shuffled()
        val styles = listOf(CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC)
        targets.forEachIndexed { i, p ->
            val type = types[i]
            val praying = styles.any { p.isProtectedFrom(it) }
            if (praying) {
                Prayers.deactivateAll(p)
                p.skills.setCurrentLevel(Skills.PRAYER, p.skills.getCurrentLevel(Skills.PRAYER) / 2)
            }
            p.message(SPHERE_TEXT[type] + if (praying) " Your prayers have been sapped." else "")
            val lands = headShot(p, listOf(1345, 1343, 1341)[type], 180)
            later(lands) {
                if (p.isDead()) return@later
                gfxOn(p, listOf(1346, 1344, 1342)[type], height = 96)
                if (!p.isProtectedFrom(styles[type])) damage(head, p, minOf(99, p.getCurrentLifepoints() / 2))
            }
        }
    }

    private fun lifeSiphon() {
        val first = randomTile(direction) ?: return
        var second: Tile? = null
        repeat(20) { if (second == null || (second!!.x == first.x && second!!.z == first.z)) second = randomTile(direction) }
        val s2 = second ?: return
        if (s2.x == first.x && s2.z == first.z) return
        headAttackAnim()
        olmRaiders().forEach { it.message("The Great Olm prepares to absorb energy from anyone unprotected...") }
        listOf(first, s2).forEach { headShot(it, 1355, 120) }
        later(3) { gfxAt(1363, first); gfxAt(1363, s2) }
        later(8) {
            val h = head ?: return@later
            if (!alive(h)) return@later
            var total = 0
            olmRaiders().filter { (it.tile.x != first.x || it.tile.z != first.z) && (it.tile.x != s2.x || it.tile.z != s2.z) }.forEach { p ->
                val d = world.random(15..25); total += d; damage(h, p, d)
            }
            if (total > 0) h.setCurrentLifepoints(minOf(h.getMaximumLifepoints(), h.getCurrentLifepoints() + total * 5))
        }
    }

    // ------------------------------------------------------------------ acid

    private fun addPool(t: Tile) {
        val key = t.x to t.z
        if (pools.containsKey(key)) return
        pools[key] = Pool(placeLoc(30032, Tile(t.x, t.z, 0), rotation = 0), 16)
    }

    private fun tickPools() {
        for ((k, p) in pools.toList()) if (--p.ticks <= 0) { removeLoc(p.obj); pools.remove(k) }
        for ((p, until) in acidUntil.toList()) {
            if (tick >= until || !p.isOnline) { acidUntil.remove(p); continue }
            if (inChamber(p.tile)) addPool(p.tile)
        }
        if (pools.isEmpty()) return
        for (p in olmRaiders()) {
            val pool = pools[p.tile.x to p.tile.z] ?: continue
            if (pool.ticks > 15) continue
            damage(head, p, world.random(3..6)); p.poison(2)
        }
    }

    private fun acidDrip() {
        val target = everyone(direction).randomOrNull() ?: return
        busy += "acidDrip"
        headAttackAnim(); headSound(1784)
        val lands = headShot(target, 1354)
        later(lands) {
            acidUntil[target] = tick + 21
            target.message("<col=ff0000>The Great Olm has smothered you in acid. It starts to drip off slowly.")
            addPool(target.tile)
        }
        later(lands + 21) { busy -= "acidDrip" }
    }

    private fun acidSpray() {
        headAttackAnim(); headSound(1784)
        var count = world.random(7..12)
        var tries = 100
        while (count > 0 && tries-- > 0) {
            val t = randomTile(direction) ?: continue
            if (pools.containsKey(t.x to t.z)) continue
            count--
            val lands = headShot(t, 1354, 26 + 7 * face().getDistance(t))
            later(lands) { addPool(t); areaSound(t, 3308, radius = 3) }
        }
    }

    // ------------------------------------------------------------------ flame

    private fun deepBurn() {
        val target = everyone(direction).randomOrNull() ?: return
        busy += "deepBurn"
        headAttackAnim(); headSound(585, delay = 0, radius = 13)
        val burning = HashMap<Player, Int>(); burning[target] = 6
        val lands = headShot(target, 1349)
        later(lands) { target.message("The Great Olm infects you with a burning overwhelming power.") }
        val skills = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)
        fun burn(p: Player, text: String) {
            p.forceChat(text); damage(head, p, 5); play(p, 740)
            repeat(2) { val s = skills.random(); p.skills.setCurrentLevel(s, maxOf(1, p.skills.getCurrentLevel(s) - 2)) }
        }
        fun pulse() {
            if (closed || dead) { burning.keys.forEach { it.message("You feel the deep burning inside dissipate.") }; busy -= "deepBurn"; return }
            val chamber = olmRaiders()
            val next = HashMap<Player, Int>()
            for ((p, left) in burning) {
                if (p !in chamber) continue
                burn(p, "Burn with me!")
                if (left > 1) next[p] = left - 1 else p.message("You feel the deep burning inside dissipate.")
                for (o in chamber) {
                    if (o in burning || o in next || o.tile.getDistance(p.tile) > 1) continue
                    next[o] = 6; burn(o, "I will burn with you.")
                }
            }
            burning.clear(); burning.putAll(next)
            if (burning.isEmpty()) { busy -= "deepBurn"; return }
            later(7) { pulse() }
        }
        later(lands) { pulse() }
    }

    private fun fireWall() {
        val target = everyone(direction).randomOrNull() ?: return
        headAttackAnim()
        val z = target.tile.z
        if (!(z > y(5731) + 1 && z < y(5748) - 1)) return
        busy += "fireWall"
        val left = side == LEFT
        val fromX = x(if (left) 3237 else 3228)
        val step = if (left) -1 else 1
        val north = Tile(fromX, z + 1, 0); val south = Tile(fromX, z - 1, 0)
        listOf(north, south).forEach { headShot(it, 1347, 60) }
        headSound(3750, radius = 13)
        val made = mutableListOf<Npc>()
        later(2) {
            if (dead || closed) return@later
            for (i in 0 until 10) for ((wall, dz) in listOf(north to 1, south to -1)) {
                val t = Tile(wall.x + step * i, wall.z, 0)
                if (!inChamber(t)) continue
                projectile(wall, t, 1348, 0, 30 - i * 3, 10, 10, angle = 0, steepness = 0)
                olmRaiders().filter { it.tile.x == t.x && it.tile.z == t.z }.forEach { p ->
                    damage(head, p, world.random(8..10))
                    p.message("You leap away from the flame, getting slightly scorched in the process.")
                    animatePlayer(p, 1114)
                    p.moveTo(Tile(t.x, t.z + dz, 0))
                }
                spawn(7558, t)?.let { made += it; fires += it }
            }
        }
        later(10) {
            made.forEach { fires.remove(it); remove(it) }
            olmRaiders().filter { it.tile.z == z }.forEach { damage(head, it, world.random(50..65)) }
            busy -= "fireWall"
        }
    }

    /** The fire's 'Douse' (tsps): a watering can or a water/ice autocast; 'Only water, ice spells or humidify can douse this flame!'. */
    fun douse(p: Player, fire: Npc) {
        if (fire !in fires) return
        val can = (5333..5340).firstOrNull { p.inventory.contains(it) }
        val spell = p.attr[gg.rsmod.plugins.content.combat.Combat.CASTING_SPELL]
        val water = spell?.name?.let { Regex("WATER|ICE_(RUSH|BURST|BLITZ|BARRAGE)").containsMatchIn(it) } == true
        if (can == null && !water) { p.message("Only water, ice spells or humidify can douse this flame!"); return }
        if (can != null) { p.inventory.remove(can, 1); p.inventory.add(if (can == 5333) 5331 else can - 1) }
        p.faceTile(fire.tile)
        later(1) { p.message("You douse the flame."); fires.remove(fire); remove(fire) }
    }

    // ------------------------------------------------------------------ crystal

    private fun fallingCrystals() {
        val target = everyone(direction).randomOrNull() ?: return
        busy += "fallingCrystals"
        headSound(324)
        olmRaiders().forEach { it.message("The Great Olm sounds a cry...") }
        target.message("<col=ff0000>The Great Olm has chosen you as its target - watch out!</col>")
        headAttackAnim()
        var left = 11
        fun drop() {
            if (closed || dead || left-- <= 0 || target.isDead() || !inChamber(target.tile)) { busy -= "fallingCrystals"; return }
            val t = Tile(target.tile)
            projectile(t, t, 1352, 0, 60, 250, 0, angle = 10, steepness = 64)
            later(2) {
                gfxAt(1353, t, height = 10); areaSound(t, 3821, radius = 3)
                olmRaiders().filter { it.tile.x == t.x && it.tile.z == t.z }.forEach { p -> damage(head, p, world.random(15..20)); p.message("The falling crystal shatters into you.") }
            }
            later(2) { drop() }
        }
        later(2) { drop() }
    }

    private fun crystalBombs() {
        busy += "crystalBombs"
        headAttackAnim()
        val count = if (scaling().size >= 8) 3 else 2
        val bombs = mutableListOf<Tile>()
        var tries = 0
        while (bombs.size < count && tries++ < 50) { val t = randomTile(ALL) ?: continue; if (bombs.none { it.getDistance(t) <= 2 }) bombs += t }
        headSound(1784)
        val objs = mutableListOf<GameObject>()
        bombs.forEach { t -> val lands = headShot(t, 1357, 26 + 7 * face().getDistance(t)); later(lands) { placeLoc(29766, t, rotation = 0)?.let { objs += it } } }
        later(11) {
            objs.forEach { o -> gfxAt(40, o.tile, height = 40); areaSound(o.tile, 1021, radius = 10); removeLoc(o) }
            bombs.forEach { b -> olmRaiders().forEach { p -> val d = p.tile.getDistance(b); if (d <= 4) damage(head, p, 15 * (5 - d)) } }
            busy -= "crystalBombs"
        }
    }

    // ------------------------------------------------------------------ claw specials

    private fun leftSign(anim: IntArray, infinity: Boolean = false) {
        viewers().forEach { play(it, 1551) }
        if (infinity) locAnim("left", 7357) else leftAnim(anim)
        if (!infinity) later(3) { leftAnim(intArrayOf(7355, 7365)) }
    }

    private fun crystalBurst() {
        leftSign(intArrayOf(7356, 7366))
        olmRaiders().forEach { p ->
            val key = p.tile.x to p.tile.z
            if (clusters.containsKey(key)) return@forEach
            clusters[key] = Cluster(placeLoc(30033, Tile(p.tile.x, p.tile.z, 0), rotation = 0), 6, Tile(p.tile))
            play(p, 1551)
        }
    }

    private fun tickClusters() {
        for ((k, c) in clusters.toList()) {
            val left = --c.ticks
            if (left == 3) {
                removeLoc(c.obj); c.obj = placeLoc(30034, c.tile, rotation = 0)
                olmRaiders().forEach { p ->
                    play(p, 3821)
                    if (p.tile.x != c.tile.x || p.tile.z != c.tile.z) return@forEach
                    val around = (-1..1).flatMap { dx -> (-1..1).map { dz -> c.tile.transform(dx, dz) } }.filter { it != c.tile &&
                        gg.rsmod.game.model.Direction.NESW.any { d -> world.collision.canTraverse(it, d, projectile = false, water = false) } }
                    damage(head, p, world.random(10..40))
                    p.message("The crystal beneath your feet grows rapidly and shunts you to the side.")
                    animatePlayer(p, 1114)
                    around.randomOrNull()?.let { p.moveTo(it) }
                }
            } else if (left <= 0) { gfxAt(1338, c.tile); removeLoc(c.obj); clusters.remove(k) }
        }
    }

    private fun lightning() {
        leftSign(intArrayOf(7358, 7368))
        val count = if (olmRaiders().size >= 6) 5 else 4
        val bolts = (0..8).shuffled().take(count).mapIndexed { i, col ->
            val north = i % 2 == 0
            val (bx, bz) = if (north) 3228 to 5731 else 3228 to 5748
            Pair(intArrayOf(x(bx) + col, y(bz)), north)
        }
        var ticks = 18
        fun pulse() {
            if (closed || --ticks <= 0) return
            for ((pos, north) in bolts) {
                val t = Tile(pos[0], pos[1], 0)
                gfxAt(1356, t)
                olmRaiders().filter { it.tile.x == t.x && it.tile.z == t.z }.forEach { p ->
                    damage(head, p, world.random(15..25))
                    p.message("<col=ff0000>You've been electrocuted to the spot!</col>")
                    listOf(gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_FROM_MAGIC, gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_FROM_MELEE,
                        gg.rsmod.plugins.content.mechanics.prayer.Prayer.PROTECT_FROM_MISSILES).forEach { Prayers.deactivate(p, it) }
                    CoxRooms.blockPrayers(p, world.currentCycle + 5, "You've been injured and can't use that prayer!")
                    animatePlayer(p, 3170)
                    p.freeze(5) {}
                }
                pos[1] += if (north) 1 else -1
            }
            later(1) { pulse() }
        }
        pulse()
    }

    private fun swap() {
        val all = olmRaiders().shuffled().toMutableList()
        if (all.isEmpty()) return
        val colours = listOf(1359, 1360, 1361, 1362)
        class Pair2(val a: Player, val b: Player?, val tile: Tile?, val colour: Int)
        val pairs = mutableListOf<Pair2>()
        var count = minOf(4, maxOf(1, all.size / 2))
        while (count-- > 0 && all.isNotEmpty()) {
            val a = all.removeAt(0)
            val b = all.removeFirstOrNull()
            var tile: Tile? = null
            if (b == null) {
                repeat(50) { if (tile == null) randomTile(ALL)?.takeIf { it.getDistance(a.tile) <= 5 }?.let { tile = it } }
                if (tile == null) continue
                a.message("The Great Olm had no one to pair you with! The magical power will enact soon...")
            } else {
                a.message("You have been paired with <col=ff0000>${b.username}</col>! The magical power will enact soon...")
                b.message("You have been paired with <col=ff0000>${a.username}</col>! The magical power will enact soon...")
            }
            pairs += Pair2(a, b, tile, colours[pairs.size])
        }
        if (pairs.isEmpty()) return
        leftSign(intArrayOf(7359, 7369))
        listOf(0, 2, 4, 6).forEach { d -> later(d) { pairs.forEach { pr -> gfxOn(pr.a, pr.colour); pr.b?.let { gfxOn(it, pr.colour) } ?: pr.tile?.let { gfxAt(pr.colour, it) } } } }
        later(8) {
            for (pr in pairs) {
                if (pr.a.isDead() || !inChamber(pr.a.tile)) continue
                if (pr.b != null && (pr.b.isDead() || !inChamber(pr.b.tile))) continue
                val dest = pr.b?.let { Tile(it.tile) } ?: pr.tile ?: continue
                val dist = pr.a.tile.getDistance(dest)
                if (dist == 0) { pr.a.message("The teleport attack has no effect!"); pr.b?.message("The teleport attack has no effect!"); continue }
                val dmg = minOf(95, 5 * dist)
                val from = Tile(pr.a.tile)
                gfxAt(436, from); gfxAt(436, dest)
                damage(head, pr.a, dmg); play(pr.a, 200); pr.a.moveTo(dest)
                pr.b?.let { b ->
                    damage(head, b, dmg); play(b, 200); b.moveTo(from)
                    pr.a.message("Yourself and ${b.username} have swapped places!")
                    b.message("Yourself and ${pr.a.username} have swapped places!")
                }
            }
        }
    }

    /** Last phase's infinity sign: the left claw heals from damage for 16 ticks. */
    private fun protectLeft() {
        if (!alive(leftClaw) || clench > 0 || !alive(rightClaw)) return
        clenched = true; clench = 16
        leftSign(intArrayOf(7357, 7357), infinity = true)
    }

    // ------------------------------------------------------------------ damage, claws, phases

    override fun incoming(npc: Npc, hit: Hit) {
        if (npc !== head && npc !== leftClaw && npc !== rightClaw) return
        val style = hit.sourceCombatClass
        val src = hit.source as? Player
        var pct = 100
        when (npc) {
            head -> if (style == CombatClass.MAGIC) pct = 33
            leftClaw -> if (clenched) {
                if (lastPhase()) { val healed = hit.hitmarks.sumOf { it.damage }; hit.hitmarks.forEach { it.damage = 0 }
                    npc.setCurrentLifepoints(minOf(npc.getMaximumLifepoints(), npc.getCurrentLifepoints() + healed)); return }
                pct = 0
                src?.message("The Great Olm is currently protecting its left claw! You cannot see a way to attack it successfully.")
            } else if (style == CombatClass.MAGIC || style == CombatClass.RANGED) pct = 33
            rightClaw -> if (style == CombatClass.RANGED) pct = 33
        }
        if (pct < 100) hit.hitmarks.forEach { it.damage = it.damage * pct / 100 }
        val dealt = hit.hitmarks.sumOf { it.damage }
        // Wiki (Mod Ash): a hit of at least 1/20 of its health makes the left claw clench (not in the last phase, not with the right claw down).
        if (npc === leftClaw && !clenched && !lastPhase() && alive(rightClaw) && dealt >= (npc.getMaximumLifepoints() + 19) / 20) {
            clenched = true; clench = 45
            olmRaiders().forEach { it.message("The Great Olm's left claw clenches to protect itself temporarily.") }
            later(1) { locAnim("left", 7360); later(1) { locAnim("left", 7361) } }
        }
        // The head can't die before the final stand; in the last phase a fallen claw comes back unless the other falls within 34 ticks.
        val lethal = dealt >= npc.getCurrentLifepoints()
        if (!lethal) return
        val keepAlive = (npc === head && !finalStand()) ||
            (lastPhase() && npc !== head && !downAt.containsKey(npc) && (if (npc === leftClaw) rightClaw else leftClaw)?.let { alive(it) && !downAt.containsKey(it) } == true)
        if (!keepAlive) return
        var left = npc.getCurrentLifepoints() - 1
        hit.hitmarks.forEach { m -> val d = minOf(m.damage, left.coerceAtLeast(0)); m.damage = d; left -= d }
        if (npc !== head) {
            downAt[npc] = tick + 34
            locAnim(if (npc === leftClaw) "left" else "right", if (npc === leftClaw) 7370 else 7352)
        }
    }

    private fun regrow(claw: Npc) {
        downAt.remove(claw)
        if (!alive(claw) || dead) return
        claw.setCurrentLifepoints(claw.getMaximumLifepoints())
        locAnim(if (claw === leftClaw) "left" else "right", if (claw === leftClaw) 7354 else 7350)
    }

    override fun onNpcDeath(npc: Npc) {
        if (npc === head) { defeated(); return }
        if (npc !== leftClaw && npc !== rightClaw) return
        val s = sides[side]
        val isLeft = npc === leftClaw
        locAnim(if (isLeft) "left" else "right", if (isLeft) 7370 else 7352)
        later(2) { place(if (isLeft) "left" else "right", if (isLeft) 29885 else 29888, if (isLeft) s.left else s.right) }
        if (isLeft) leftClaw = null else {
            rightClaw = null; rightGone = true
            later(4) { if (alive(leftClaw)) { locAnim("left", 7363); later(2) { if (alive(leftClaw)) leftAnim(intArrayOf(7355, 7365)) } } }
            if (clench > 2) clench = 2
        }
        val other = if (isLeft) rightClaw else leftClaw
        if (other != null && alive(other) && !downAt.containsKey(other)) return
        if (other != null && downAt.containsKey(other)) {
            downAt.remove(other)
            remove(other)
            if (isLeft) rightClaw = null else leftClaw = null
            later(2) { place(if (isLeft) "right" else "left", if (isLeft) 29888 else 29885, if (isLeft) s.right else s.left) }
        }
        if (lastPhase()) beginFinalStand() else switchSides()
    }

    private fun beginFinalStand() {
        if (finalStandStarted) return
        finalStandStarted = true
        olmRaiders().forEach { it.message("The Great Olm is giving its all. This is its final stand.") }
        crystalsUntil = Int.MAX_VALUE
    }

    private fun switchSides() {
        transitioning = true
        crystalsUntil = tick + 33
        later(4) { headAnim(intArrayOf(7348, 7348)) }
        later(6) {
            head?.let { remove(it) }; head = null
            place("head", 29882, sides[side].head)
            side = 1 - side
        }
        later(38) { if (!closed) { phase++; transitioning = false; rise() } }
    }

    /** Crystals fall around the chamber (one on a player every 5 ticks): 16-25 under, 10-16 beside (tsps). */
    private fun tickFalling() {
        for ((k, v) in falling.toList()) {
            val (tile, ticks) = v
            if (--ticks[0] == 1) gfxAt(1358, tile)
            if (ticks[0] > 0) continue
            falling.remove(k)
            olmRaiders().forEach { p -> when (p.tile.getDistance(tile)) { 0 -> damage(head, p, world.random(16..25)); 1 -> damage(head, p, world.random(10..16)) } }
            viewers().forEach { play(it, 1021) }
        }
        if (tick >= crystalsUntil || dead) return
        val cap = if (olmRaiders().size >= 6) 5 else 4
        var amount = minOf(cap - falling.size, world.random(0 until cap))
        var aimAtPlayer = --onPlayerIn <= 0
        while (amount-- > 0) {
            val t = if (aimAtPlayer) { aimAtPlayer = false; onPlayerIn = 5; olmRaiders().randomOrNull()?.tile?.let { Tile(it) } } else randomTile(ALL)
            t ?: continue
            if (falling.containsKey(t.x to t.z)) continue
            val from = t.transform(listOf(-1, 1).random(), listOf(-1, 0, 1).random())
            projectile(from, t, 1357, 0, 120, 255, 0, angle = 16, steepness = 0)
            gfxAt(1446, t, delay = 30)
            if (!finalStand()) viewers().forEach { play(it, 3834) }
            falling[t.x to t.z] = t to intArrayOf(3)
        }
    }

    private fun defeated() {
        if (dead) return
        dead = true
        cancelEvents()
        crystalsUntil = -1
        headAnim(intArrayOf(7348, 7348))
        val raiders = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
        val exit = roomLocsOlm(30018).firstOrNull()
        raiders.forEach { p ->
            p.message("As the Great Olm collapses, the crystal blocking your exit has been shattered.")
            exit?.let { e -> seq(7506).takeIf { it >= 0 }?.let { p.write(LocAnimMessage(e, it)) } }
        }
        exit?.let { e -> later(2) { if (e is DynamicObject) removeLoc(e) else world.remove(e) } }
        later(1) { place("head", 29882, sides[side].head) }
        CoxChest.complete(raid)
    }

    /** Locs in the Olm's chamber with this OSRS id. */
    private fun roomLocsOlm(osrs: Int): List<GameObject> {
        val id = CoxIds.loc(osrs) ?: return emptyList()
        val base = CoxMap.olmTile(raid.map, Tile(CoxMap.OLM_CHUNK_X * 8, CoxMap.OLM_CHUNK_Z * 8, 0))
        val out = mutableListOf<GameObject>()
        for (cx in 0 until CoxMap.OLM_WIDTH) for (cz in 0 until CoxMap.OLM_HEIGHT) {
            val chunk = world.chunks.get(Tile(base.x + cx * 8, base.z + cz * 8, 0), createIfNeeded = false) ?: continue
            chunk.getEntities<GameObject>(gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT).filterTo(out) { it.id == id && it.tile.height == 0 }
        }
        return out
    }

    /** ::coxclear in the chamber: rises at once if needed, then kills the Olm (tsps: 'in the Olm's chamber it kills the Olm'). */
    fun testKill() { if (!risen) { risen = true; rise() }; later(8) { finalStandStarted = true; phase = totalPhases; head?.let { h -> h.setCurrentLifepoints(0); onNpcDeath(h); remove(h) } } }

    companion object {
        const val LEFT = 0
        const val SOUTH = 0
        const val MIDDLE = 1
        const val NORTH = 2
        const val ALL = 3
        const val ACID = 0
        const val FLAME = 1
        const val CRYSTAL = 2
        val POWER_NAMES = listOf("<col=00ff00>acid", "<col=ff0000>flame", "<col=ff00ff>crystal")
        val SEQUENCE = listOf("auto", "skip", "auto", "burst", "auto", "skip", "auto", "lightning", "auto", "skip", "auto", "swap")
        val LAST_SEQUENCE = SEQUENCE + listOf("auto", "skip", "auto", "protect")
        val SPECIALS = setOf("burst", "lightning", "swap", "protect")
        val SPHERE_TEXT = listOf(
            "<col=ff3045>The Great Olm fires a sphere of aggression your way.</col>",
            "<col=229628>The Great Olm fires a sphere of accuracy and dexterity your way.</col>",
            "<col=3366ff>The Great Olm fires a sphere of magical power your way.</col>",
        )
    }
}
