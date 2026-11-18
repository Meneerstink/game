package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * Tekton. Wiki (Tekton; Chambers of Xeric/Strategies): immune to Ranged, Magic does a fifth; walks to a player, then stands in a combat
 * stance and attacks the side in front of him (a slash also the side to his right); max hit 52, 59 enraged, attack speed 3; after 10-14
 * attacks or nobody in melee range he returns to the anvil, repairing ~1.33% every few ticks while two sparks fly at each player (3x3,
 * 10-20); anyone under him as he walks is stomped; done repairing he is enraged for a short while.
 * tsps rsprox (820, 3389): woken 7474 (7487 back from the anvil), walking 2 ticks later (form 7541) and the fire 30021 shuts the way in;
 * step sound 2720 every 4 ticks; in reach: fighting form 7542, stance 7478, blocker 30023 under him, first swing 3 ticks later (2 enraged),
 * then every 3; stab 7482 / crush 7484 land the tick after, slash 7483 two ticks after; leaves on his 9th swing the first time, 16th after a
 * repair; enraged (7543/7544, stance 7488, swings 7493/7494/7492) until his 6th swing; anvil 7475 + form 7545, sparks every 4 ticks
 * (projectile 660, gfx 659, sounds 155/156), heal every 3 ticks (1% + 1), 12/15/18/21 ticks; dying 7495, gone 3 ticks later, fire too.
 * ONZEKER (tsps): tiles, spark spots, stomp 1-3, protect from melee letting half through, exit crystal 30017; wakes within 3 tiles.
 */
class TektonRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private enum class State { IDLE, RISING, ENGAGING, FIGHTING, RETURNING, ANVIL, DEAD }
    private var state = State.IDLE
    private var tekton: Npc? = null
    private lateinit var home: Tile
    private lateinit var safe: Tile
    private val sparkFrom = mutableListOf<Tile>()
    private var fire: GameObject? = null
    private var block: GameObject? = null
    private var enraged = false
    private var repaired = false
    private var wait = 0
    private var swings = 0
    private var swingLimit = 9
    private var target: Player? = null
    private var searching = 0
    private var steps = 0
    private var anvilTicks = 0
    private var anvilFor = 12

    override fun load() {
        val a = ANVIL[variant]
        tekton = spawn(7540, tile(a.x, a.z, 6), speed = 3, deathSeq = 7495, meleeStyle = gg.rsmod.game.model.combat.StyleType.CRUSH)
        home = Tile(tekton?.tile ?: return)
        safe = tile(SAFE[variant].x, SAFE[variant].z)
        SPARKS[variant].forEach { sparkFrom += tile(it.x, it.z) }
        blockExit(CRYSTAL, 30017)
    }

    private fun form(normal: Int, angry: Int) { CoxIds.npc(if (enraged) angry else normal)?.let { tekton?.setTransmogId(it) } }
    private fun anim(normal: Int, angry: Int = normal) { tekton?.let { animate(it, if (enraged) angry else normal) } }

    private fun reachable(): List<Player> {
        val npc = tekton ?: return emptyList()
        val s = npc.getSize()
        return raiders().filter { p ->
            val x = p.tile.x - npc.tile.x; val z = p.tile.z - npc.tile.z
            val inX = x in 0 until s; val inZ = z in 0 until s
            !(inX && inZ) && ((inX && (z == -1 || z == s)) || (inZ && (x == -1 || x == s)))
        }
    }

    override fun onTick() {
        val npc = tekton ?: return
        if (!alive(npc)) return
        if (npc.hasMoveDestination()) {
            stompUnder(npc)
            if (steps++ % 4 == 0) areaSound(npc.tile, 2720, radius = 7)
        } else steps = 0
        when (state) {
            State.IDLE -> watch(npc)
            State.RISING -> if (--wait <= 0) rise()
            State.ENGAGING -> approach(npc)
            State.FIGHTING -> fightTick(npc)
            State.RETURNING -> returnHome(npc)
            State.ANVIL -> smith(npc)
            State.DEAD -> {}
        }
    }

    private fun watch(npc: Npc) {
        val s = npc.getSize()
        if (raiders().any { it.tile.x in npc.tile.x - 3 until npc.tile.x + s + 3 && it.tile.z in npc.tile.z - 3 until npc.tile.z + s + 3 }) wake()
    }

    private fun wake() { state = State.RISING; wait = 2; anim(7474, 7487) }

    private fun rise() {
        form(7541, 7543)
        if (fire == null) { val f = FIRE[variant]; fire = placeLoc(30021, tile(f.x, f.z, 2)) }
        state = State.ENGAGING; target = null; searching = 0
        tekton?.let { approach(it) }
    }

    private fun approach(npc: Npc) {
        val t = target?.takeIf { it.isOnline && !it.isDead() && contains(it.tile) } ?: run {
            val options = raiders().filter { it.tile != safe }
            if (options.isEmpty()) { retreat(npc); return }
            options.random().also { target = it }
        }
        npc.faceTile(t.tile)
        if (t in reachable()) { plant(npc); return }
        if (!npc.hasMoveDestination()) {
            npc.walkTo(t.tile)
            if (!npc.hasMoveDestination() && ++searching > 3) retreat(npc)
        }
    }

    private fun plant(npc: Npc) {
        npc.stopMovement()
        state = State.FIGHTING
        swings = 0
        swingLimit = if (repaired) 16 else 9
        wait = if (enraged) 2 else 3
        form(7542, 7544)
        anim(7478, 7488)
        if (block == null) block = placeLoc(30023, Tile(npc.tile), rotation = 0)
    }

    private fun fightTick(npc: Npc) {
        if (--wait > 0) return
        val reach = reachable()
        if (reach.isEmpty()) { retreat(npc); return }
        val t = target?.takeIf { it in reach } ?: reach.random().also { target = it }
        val style = world.random(2)
        anim(listOf(7482, 7483, 7484)[style], listOf(7493, 7494, 7492)[style])
        val facing = facing(npc, t.tile)
        npc.faceTile(t.tile)
        val slash = style == 1
        later(if (slash) 2 else 1) { swing(facing, slash) }
        swings++
        wait = 3
        if (enraged && swings == 6) { enraged = false; CoxIds.npc(7542)?.let { npc.setTransmogId(it) }; configure(npc, 7540, 3, 7495, keepHp = true) }
        if (swings >= swingLimit) retreat(npc)
    }

    /** 0 south, 1 west, 2 north, 3 east (the donor's FACINGS; the side to his right is the next one). */
    private fun facing(npc: Npc, t: Tile): Int {
        val s = npc.getSize()
        val dx = t.x * 2 - (npc.tile.x * 2 + s - 1); val dz = t.z * 2 - (npc.tile.z * 2 + s - 1)
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dz)) (if (dx > 0) 3 else 1) else (if (dz > 0) 2 else 0)
    }

    private fun swing(facing: Int, slash: Boolean) {
        val npc = tekton ?: return
        if (!alive(npc)) return
        val sides = if (slash) setOf(facing, (facing + 1) % 4) else setOf(facing)
        val s = npc.getSize()
        val sc = scaled(npc) ?: return
        for (p in reachable()) {
            val x = p.tile.x - npc.tile.x; val z = p.tile.z - npc.tile.z
            val side = when { z == -1 -> 0; z == s -> 2; x == -1 -> 1; else -> 3 }
            if (side in sides) attack(npc, p, sc.maxHit, CombatClass.MELEE, protectedPercent = 50)
        }
    }

    private fun stompUnder(npc: Npc) {
        val s = npc.getSize()
        raiders().filter { it.tile.x - npc.tile.x in 0 until s && it.tile.z - npc.tile.z in 0 until s }.forEach { damage(npc, it, world.random(1..3)) }
    }

    private fun retreat(npc: Npc) {
        state = State.RETURNING
        removeLoc(block); block = null
        enraged = false
        CoxIds.npc(7541)?.let { npc.setTransmogId(it) }
        npc.walkTo(home)
    }

    private fun returnHome(npc: Npc) {
        if (npc.hasMoveDestination()) return
        if (npc.tile.x != home.x || npc.tile.z != home.z) { npc.walkTo(home, detectCollision = false); return }
        state = State.ANVIL
        configure(npc, 7540, 3, 7495, keepHp = true)
        animate(npc, 7475)
        CoxIds.npc(7545)?.let { npc.setTransmogId(it) }
        anvilTicks = 0
        anvilFor = listOf(12, 15, 18, 21).random()
    }

    private fun smith(npc: Npc) {
        val t = ++anvilTicks
        val targets = raiders().filter { it.tile != safe && it.tile.getDistance(safe) > 1 }
        if (targets.isEmpty()) { state = State.IDLE; CoxIds.npc(7540)?.let { npc.setTransmogId(it) }; return }
        if ((t - 1) % 4 == 0) targets.forEach { p -> repeat(2) { spark(p) } }
        if (t % 3 == 0) npc.setCurrentLifepoints(minOf(npc.getMaximumLifepoints(), npc.getCurrentLifepoints() + npc.getMaximumLifepoints() / 100 + 1))
        if (t >= anvilFor) {
            enraged = true; repaired = true
            configure(npc, 7543, 3, 7495, keepHp = true)
            wake()
        }
    }

    private fun spark(p: Player) {
        val to = p.tile.transform(world.random(-1..1), world.random(-1..1))
        val from = sparkFrom.randomOrNull() ?: return
        val start = world.random(3..27)
        val end = start + world.random(70..120)
        projectile(from, to, 660, start, end - start, 0, 0, angle = world.random(20..25), steepness = 0)
        areaSound(from, 155, radius = 8, delay = start)
        val lands = end / 30
        later(lands) {
            if (!alive(tekton)) return@later
            gfxAt(659, to, delay = end - lands * 30)
            areaSound(to, 156, radius = 8)
            raiders().filter { it.tile.getDistance(to) <= 1 }.forEach { damage(null, it, world.random(10..20)) }
        }
    }

    /** Wiki: immune to Ranged, a fifth from Magic; nothing at the anvil (tsps message). */
    override fun incoming(npc: Npc, hit: Hit) {
        if (npc !== tekton) return
        val style = hit.sourceCombatClass
        var pct = when (style) { CombatClass.RANGED -> 0; CombatClass.MAGIC -> 20; else -> 100 }
        if (state == State.ANVIL) { pct = 0; (hit.source as? Player)?.message("Tekton's anvil protects him from your attacks.") }
        if (pct < 100) hit.hitmarks.forEach { it.damage = it.damage * pct / 100 }
    }

    override fun onNpcDeath(npc: Npc) {
        if (npc !== tekton) return
        state = State.DEAD
        removeLoc(block); block = null
        // Wiki 'Tekton' drops: stinkhorn mushroom x5, overload (+)(4) x2, prayer enhance (+)(4), revitalisation (+)(4); onyx 1/450 (solo rate).
        val at = Tile(npc.tile)
        dropBook(at, 20890)
        drop(at, 20910, 5); drop(at, 20996, 2); drop(at, 20972); drop(at, 20960)
        if (world.random(449) == 0) drop(at, 6573, classic = true)
        clearExit()
        val f = fire; fire = null
        later(3) { removeLoc(f) }
    }

    /** The fire at the way in (op 'Cross'): through to its other side. */
    fun crossFire(p: Player, obj: GameObject) {
        p.moveTo(gg.rsmod.plugins.content.activity.cox.CoxRaids.passageExit(p.tile, obj.tile, 2, 2))
    }

    companion object {
        val ANVIL = listOf(Tile(3279, 5298), Tile(3307, 5294), Tile(3341, 5298))
        val CRYSTAL = listOf(Tile(3270, 5294), Tile(3310, 5304), Tile(3352, 5296))
        val FIRE = listOf(Tile(3279, 5281), Tile(3311, 5281), Tile(3343, 5281))
        val SAFE = listOf(Tile(3280, 5280), Tile(3312, 5280), Tile(3344, 5280))
        val SPARKS = listOf(
            listOf(Tile(3273, 5288), Tile(3273, 5302), Tile(3280, 5305), Tile(3289, 5303), Tile(3288, 5289)),
            listOf(Tile(3320, 5289), Tile(3321, 5303), Tile(3317, 5302), Tile(3303, 5295), Tile(3305, 5288), Tile(3305, 5301)),
            listOf(Tile(3350, 5289), Tile(3334, 5288), Tile(3335, 5302), Tile(3343, 5304), Tile(3350, 5303)),
        )
    }
}
