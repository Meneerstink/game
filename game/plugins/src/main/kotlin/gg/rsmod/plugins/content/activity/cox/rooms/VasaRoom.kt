package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * Vasa Nistirio. Wiki (Vasa Nistirio; Glowing crystal): waking, he raises a magical fire at the way in and opens with his special: everyone
 * is teleported, split evenly between beside him (always, solo) and the walls; those beside him are stunned and can't pray; he can't be
 * hurt while charging; the blast deals the total of the hitpoints of those beside him (each one's hitpoints - 5), shared among everyone then
 * next to him; those who ran in with Protect from Magic take nothing. Boulders: ranged on everyone within 8 tiles not under him; standing
 * under him is stomped. After the special he walks to a crystal, can't be hurt there, healing 1% every 2 ticks; a 66-67 tick timer runs
 * while a crystal is active, at zero he goes to the centre and his special; disabling the crystal first undoes the healing. Crystals are
 * immune to Ranged and take a third from Magic.
 * tsps rsprox (820, 3282): woken 7408 + sounds 1724/1595, the fire 4 ticks later, 7409 + walking form 7566 the tick after; special 7409:
 * teleport 3 ticks later (beside him: gfx 245, sound 2727), 'Vasa Nistirio has blocked your use of prayers.' (sound 2673) 2 ticks after,
 * explosion 7410 at 8 (sound 1781, eight projectiles 1327) landing at 12 as gfx 1328 round him (sound 3428); walking: stomps (sound 3834)
 * and boulders every 3 ticks (projectile 1329, sound 781) landing 2 ticks later (gfx 1330, sound 1021); at the crystal 7412 + form 7567,
 * sound 2885 every 2 ticks, then 7414; dying 7415, he and his crystal go 3 ticks later, the exits 30016 shatter, the fire goes.
 * ONZEKER (tsps): tiles, where the far half is sent, boulder base 18 on a 3x3, stomps 1-5, prayers blocked 8 ticks, the crystal message.
 */
class VasaRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private enum class State { ASLEEP, WAKING, SPECIAL, WALKING, SIPHONING, RETURNING, DEAD }
    private var state = State.ASLEEP
    private var vasa: Npc? = null
    private lateinit var home: Tile
    private val crystalTiles = mutableListOf<Tile>()
    private val crystalLocs = arrayOfNulls<GameObject>(4)
    private val crystals = arrayOfNulls<Npc>(4)
    private val exits = mutableListOf<GameObject>()
    private var fire: GameObject? = null
    private var stage = 0
    private var current = -1
    private var timer = -1
    private var siphonTicks = 0
    private var siphoned = 0
    private var boulderTicks = 0
    private var specialNext = false
    private var waited = 0
    private var near = listOf<Player>()
    private var far = listOf<Player>()
    private var blast = 0

    override fun load() {
        CRYSTALS[variant].forEach { s -> crystalTiles += tile(s.x, s.z, 6) }
        crystalTiles.forEachIndexed { i, t -> crystalLocs[i] = placeLoc(29775, Tile(t)) }
        home = tile(CENTRE[variant].x, CENTRE[variant].z, 6)
        vasa = spawn(7565, Tile(home), speed = 3, deathSeq = 7415)
        EXITS[variant].forEach { e -> placeLoc(30016, tile(e.x, e.z))?.let { exits += it } }
    }

    private fun centre(): Tile = vasa!!.tile.transform(2, 2)
    private fun under(p: Player): Boolean { val v = vasa!!.tile; return p.tile.x - v.x in 0..4 && p.tile.z - v.z in 0..4 }

    override fun onTick() {
        val v = vasa ?: return
        if (!alive(v)) return
        if (state != State.ASLEEP && state != State.WAKING) raiders().filter { under(it) }.forEach { damage(v, it, world.random(1..5)) }
        when (state) {
            State.ASLEEP -> if (raiders().any { it.tile.getDistance(centre()) <= 7 }) { state = State.WAKING; stage = 0 }
            State.WAKING -> wake(v)
            State.WALKING -> walking(v)
            State.SIPHONING -> siphon(v)
            State.RETURNING -> returning(v)
            else -> {}
        }
    }

    private fun wake(v: Npc) {
        when (stage++) {
            0 -> { animate(v, 7408); areaSound(centre(), 1724, radius = 15); areaSound(centre(), 1595, radius = 15, delay = 15) }
            4 -> { val f = FIRE[variant]; fire = placeLoc(30019, tile(f.x, f.z, 2)) }
            5 -> { CoxIds.npc(7566)?.let { v.setTransmogId(it) }; configure(v, 7566, 3, 7415, keepHp = true); special(v) }
        }
    }

    private fun special(v: Npc) {
        state = State.SPECIAL
        animate(v, 7409)
        later(3) { teleport(v) }
        later(5) { near.filter { contains(it.tile) }.forEach { it.message("Vasa Nistirio has blocked your use of prayers."); play(it, 2673) } }
        later(8) {
            if (!alive(v)) return@later
            animate(v, 7410)
            areaSound(centre(), 1781, radius = 15)
            ring(v).forEachIndexed { i, t -> projectile(centre(), t, 1327, i * 8, 125 - i * 8, 105, 2, angle = 0, steepness = 30) }
        }
        later(12) { explode(v) }
        later(13) { if (alive(v)) chooseCrystal(v) }
    }

    private fun teleport(v: Npc) {
        if (!alive(v)) return
        val raiders = raiders().shuffled()
        if (raiders.isEmpty()) { near = emptyList(); far = emptyList(); return }
        val n = mutableListOf(raiders.first()); val f = mutableListOf<Player>()
        raiders.drop(1).forEach { (if (f.size < n.size) f else n) += it }
        near = n; far = f; blast = 0
        val middle = centre()
        f.forEach { it.moveTo(around(middle, 10, 14)) }
        n.forEach { p ->
            p.moveTo(around(middle, 3, 4))
            p.stopMovement()
            p.lock = gg.rsmod.game.model.LockState.FULL
            later(6) { if (p.lock == gg.rsmod.game.model.LockState.FULL) p.unlock() }
            gg.rsmod.plugins.content.mechanics.prayer.Prayers.deactivateAll(p)
            CoxRooms.blockPrayers(p, world.currentCycle + 8, "Vasa Nistirio has blocked your use of prayers.")
            gfxOn(p, 245, height = 124)
            play(p, 2727)
            blast += maxOf(0, minOf(99, p.getCurrentLifepoints()) - 5)
        }
    }

    private fun ring(v: Npc): List<Tile> = listOf(-1 to -1, 2 to -1, 5 to -1, 5 to 2, 5 to 5, 2 to 5, -1 to 5, -1 to 2).map { (x, z) -> v.tile.transform(x, z) }

    private fun explode(v: Npc) {
        if (!alive(v)) return
        val middle = centre()
        ring(v).forEachIndexed { i, t -> gfxAt(1328, t, delay = i * 3) }
        areaSound(middle, 3428, radius = 15, delay = 5)
        val close = raiders().filter { it.tile.getDistance(middle) <= 4 }
        val sharing = maxOf(near.count { it in close }, close.size)
        if (sharing > 0 && blast > 0) {
            val dmg = minOf(99, blast / sharing)
            close.forEach { p -> if (!(p in far && p.isProtectedFrom(CombatClass.MAGIC))) damage(v, p, dmg) }
        }
    }

    private fun around(middle: Tile, min: Int, max: Int): Tile {
        repeat(50) {
            val t = middle.transform(world.random(-max..max), world.random(-max..max))
            if (t.getDistance(middle) > min && contains(t) && gg.rsmod.game.model.Direction.NESW.any { d -> world.collision.canTraverse(t, d, projectile = false, water = false) }) return t
        }
        return middle
    }

    private fun chooseCrystal(v: Npc) {
        val live = crystals.indices.filter { alive(crystals[it]) }
        val choices = live.filter { it != current }
        if (live.isEmpty()) {
            val index = (0..3).filter { it != current }.random()
            crystalLocs[index]?.let { removeLoc(it) }
            crystalLocs[index] = placeLoc(29774, Tile(crystalTiles[index]))
            crystals[index] = spawn(7568, Tile(crystalTiles[index]))
            current = index
        } else current = if (choices.isNotEmpty()) choices.random() else live.first()
        if (timer <= 0) timer = world.random(66..67)
        state = State.WALKING
        boulderTicks = 0; siphoned = 0
        crystals[current]?.let { v.walkTo(it.tile) }
    }

    private fun walking(v: Npc) {
        val c = crystals.getOrNull(current)
        if (!alive(c)) { goHome(v, false); return }
        boulders(v)
        if (v.hasMoveDestination()) return
        if (v.tile.getDistance(c!!.tile) > 6) { v.walkTo(c.tile); return }
        state = State.SIPHONING
        siphonTicks = 0
        animate(v, 7412)
        CoxIds.npc(7567)?.let { v.setTransmogId(it) }
    }

    private fun siphon(v: Npc) {
        val c = crystals.getOrNull(current)
        if (!alive(c)) {
            if (siphoned > 0) v.setCurrentLifepoints(maxOf(1, v.getCurrentLifepoints() - siphoned))
            siphoned = 0
            CoxIds.npc(7566)?.let { v.setTransmogId(it) }
            goHome(v, false); return
        }
        if (++siphonTicks % 2 == 0) {
            areaSound(centre(), 2885, radius = 12)
            val before = v.getCurrentLifepoints()
            v.setCurrentLifepoints(minOf(v.getMaximumLifepoints(), before + maxOf(1, v.getMaximumLifepoints() / 100)))
            siphoned += v.getCurrentLifepoints() - before
        }
        if (--timer <= 0) {
            val index = current
            crystals[index] = null
            animate(v, 7414)
            CoxIds.npc(7566)?.let { v.setTransmogId(it) }
            later(2) { remove(c!!); crystalLocs[index]?.let { removeLoc(it) }; crystalLocs[index] = placeLoc(29775, Tile(crystalTiles[index])) }
            goHome(v, true)
        }
    }

    private fun goHome(v: Npc, special: Boolean) {
        state = State.RETURNING
        specialNext = special
        waited = 0
        v.walkTo(home)
    }

    private fun returning(v: Npc) {
        if (v.hasMoveDestination()) { boulders(v); return }
        if ((v.tile.x != home.x || v.tile.z != home.z) && waited++ == 0) { v.walkTo(home, detectCollision = false); return }
        if (specialNext) { special(v); return }
        if (waited++ >= 3) chooseCrystal(v)
    }

    private fun boulders(v: Npc) {
        areaSound(v.tile, 3834, radius = 12)
        areaSound(v.tile, 3834, radius = 12, delay = world.random(5..9))
        if (boulderTicks++ % 3 != 0) return
        val middle = centre()
        val sc = scaled(v) ?: return
        val max = CoxMonsters.maxHitFor(7566, sc, 18, CoxMonsters.RNG)
        val targets = raiders().filter { !under(it) && it.tile.getDistance(middle) <= 10 }
        if (targets.isEmpty()) return
        areaSound(middle, 781, radius = 15)
        targets.forEachIndexed { i, p ->
            val t = Tile(p.tile)
            projectile(middle, t, 1329, i * 5, 90 - i * 5, 37, 2, angle = 5, steepness = 30)
            later(2) {
                if (!alive(v)) return@later
                gfxAt(1330, t)
                areaSound(t, 1021, radius = 8)
                raiders().filter { it.tile.getDistance(t) <= 1 }.forEach { attack(v, it, max, CombatClass.RANGED, protectedPercent = 33) }
            }
        }
    }

    override fun passRefusal(p: Player, passage: Tile): String? = blockedNear(passage, exits.map { it.tile })

    override fun incoming(npc: Npc, hit: Hit) {
        val src = hit.source as? Player
        var pct = 100
        if (npc === vasa) {
            if (state == State.SPECIAL || state == State.WAKING) { pct = 0; src?.message("Vasa Nistirio is preparing a powerful spell and can ignore your puny attack.") }
            else if (state == State.SIPHONING) pct = 0
        } else if (npc in crystals) {
            if (crystals.indexOf(npc) != current || state != State.SIPHONING) { pct = 0; src?.message("The crystal is currently invulerable to damage.") }
            else if (hit.sourceCombatClass == CombatClass.RANGED) pct = 0
            else if (hit.sourceCombatClass == CombatClass.MAGIC) pct = 33
        } else return
        if (pct < 100) hit.hitmarks.forEach { it.damage = it.damage * pct / 100 }
    }

    override fun onNpcDeath(npc: Npc) {
        if (npc !== vasa) {
            val i = crystals.indexOf(npc)
            if (i >= 0) { crystals[i] = null; crystalLocs[i]?.let { removeLoc(it) }; crystalLocs[i] = placeLoc(29775, Tile(crystalTiles[i])) }
            return
        }
        state = State.DEAD
        completed = true
        // Wiki 'Vasa Nistirio' drops: overload (+)(4), endarkened juice x5, Xeric's aid (+)(4) x2, twisted (+)(4) x2.
        Tile(npc.tile).let { at -> drop(at, 20996); drop(at, 20911, 5); drop(at, 20984, 2); drop(at, 20936, 2) }
        (near + far).forEach { CoxRooms.blockPrayers(it, 0, null) }
        val left = crystals.filterNotNull(); val f = fire; fire = null
        later(3) {
            left.forEach { remove(it) }
            removeLoc(f)
            val viewers = raiders()
            exits.forEach { e ->
                seq(7506).takeIf { it >= 0 }?.let { a -> viewers.forEach { it.write(gg.rsmod.game.message.impl.LocAnimMessage(e, a)) } }
                areaSound(e.tile, 1021, radius = 10); areaSound(e.tile, 3821, radius = 10, delay = 5)
            }
            later(4) { exits.toList().forEach { removeLoc(it) }; exits.clear() }
        }
    }

    companion object {
        val CRYSTALS = listOf(
            listOf(Tile(3270, 5285), Tile(3270, 5303), Tile(3287, 5303), Tile(3287, 5285)),
            listOf(Tile(3301, 5285), Tile(3301, 5302), Tile(3319, 5302), Tile(3319, 5285)),
            listOf(Tile(3333, 5285), Tile(3333, 5303), Tile(3350, 5303), Tile(3350, 5285)),
        )
        val EXITS = listOf(listOf(Tile(3268, 5295), Tile(3268, 5296)), listOf(Tile(3311, 5307), Tile(3312, 5307)), listOf(Tile(3355, 5296), Tile(3355, 5295)))
        val FIRE = listOf(Tile(3279, 5281), Tile(3311, 5281), Tile(3343, 5281))
        val CENTRE = listOf(Tile(3278, 5293), Tile(3309, 5293), Tile(3341, 5294))
    }
}
