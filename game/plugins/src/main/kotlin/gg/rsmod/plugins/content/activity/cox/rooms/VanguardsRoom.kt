package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * The Vanguards. Wiki (Vanguard): they rise when a player comes near; the melee one hits its target three times, the ranged and magic
 * ones fire at their target and two tiles around it; protection prayers reduce, not stop, the damage; a difference of more than 40% of
 * their health (33.3% with five or more) makes them shuffle at once and heal to full, otherwise they shuffle every 20-36 ticks; in their
 * shells they can't be hurt, a fatal hit in the shell leaves 1 hitpoint.
 * tsps rsprox (843, 3285, 3389): woken 7428, 5 ticks later melee 7527 / ranged 7528 / magic 7529 (rise 7438 / 7443 / 7433); attacks every 4
 * ticks: melee 7441 (sound 3308); ranged 7446 (sound 239) projectiles 1332-1334 landing 2 ticks later as gfx 305 (sound 360); magic 7436
 * (sound 3530) three projectiles 1331 landing 3 ticks later as gfx 659 (sound 156); swirls 57 (healthy) / 56 (low) every 2 ticks before a
 * shuffle; shells 7442 / 7447 / 7437 (walking form 7526), up again 9-13 ticks later; out of balance they heal 4 ticks in (7431); dying 7432.
 * ONZEKER (tsps): tiles, shell paths, trample 1-5, bases (melee 3 x 18, ranged/magic 22), prayers letting a third through, the message
 * 'The Vanguard is protected by its shell.', exit crystal 30017.
 */
class VanguardsRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private class Style(val form: Int, val rise: Int, val fall: Int, val attack: Int, val base: Int, val stat: Int, val sound: Int)
    private val styles = listOf(
        Style(7527, 7438, 7442, 7441, 18, CoxMonsters.STR, 3308),
        Style(7528, 7443, 7447, 7446, 22, CoxMonsters.RNG, 239),
        Style(7529, 7433, 7437, 7436, 22, CoxMonsters.MAG, 3530),
    )
    private enum class Stage { BURIED, RISING, FIGHTING, SHUFFLING }
    private var stage = Stage.BURIED
    private val vanguards = mutableListOf<Npc>()
    private val roleOf = HashMap<Npc, Style>()
    private val shelled = HashSet<Npc>()
    private val spots = mutableListOf<Tile>()
    private var nextShuffle = 0
    private var warning = -1
    private var next = 0

    override fun load() {
        SPOTS[variant].forEach { s -> spots += tile(s.x - 1, s.z - 1, 3) }
        spots.forEach { at -> spawn(7525, Tile(at), speed = 4, deathSeq = 7432)?.let { vanguards += it; shelled += it } }
        blockExit(CRYSTAL, 30017)
    }

    private fun living() = vanguards.filter { alive(it) }

    private fun unbalanced(): Boolean {
        val l = living()
        if (l.size < 2) return false
        val limit = (if (scaling().size >= 5) 100 / 3 else 40) * l.maxOf { it.getMaximumLifepoints() } / 100
        return l.maxOf { it.getCurrentLifepoints() } - l.minOf { it.getCurrentLifepoints() } > limit
    }

    override fun onTick() {
        when (stage) {
            Stage.BURIED -> if (raiders().any { p -> vanguards.any { p.tile.getDistance(it.tile) < 7 } }) rise()
            Stage.FIGHTING -> {
                fightAll()
                if (warning < 0 && (unbalanced() || tick >= nextShuffle)) warning = 0
                if (warning >= 0) {
                    if (warning % 2 == 0) swirl()
                    if (++warning > 6) shuffle()
                }
            }
            Stage.SHUFFLING -> trample()
            Stage.RISING -> {}
        }
    }

    private fun rise() {
        stage = Stage.RISING
        val roles = styles.shuffled()
        vanguards.forEachIndexed { i, npc -> animate(npc, 7428); roleOf[npc] = roles[i]; configure(npc, roles[i].form, 4, 7432, keepHp = true) }
        later(5) {
            living().forEach { npc -> val r = roleOf[npc] ?: return@forEach; CoxIds.npc(r.form)?.let { npc.setTransmogId(it) }; animate(npc, r.rise) }
            standUp()
        }
    }

    private fun standUp() {
        shelled.clear()
        stage = Stage.FIGHTING
        nextShuffle = tick + world.random(20..36)
    }

    private fun fightAll() {
        for (npc in living()) {
            val r = roleOf[npc] ?: continue
            val t = nearest(npc) ?: continue
            val melee = r.form == 7527
            val inReach = if (melee) meleeReach(npc, t.tile) else distance(t.tile, npc) <= 10
            if (!inReach) { if (melee) npc.walkTo(t.tile); continue }
            npc.stopMovement(); npc.facePawn(t)
            if (tick >= (npcNext[npc] ?: 0)) { npcNext[npc] = tick + 4; swing(npc, r, t) }
        }
    }
    private val npcNext = HashMap<Npc, Int>()

    private fun swing(npc: Npc, r: Style, p: Player) {
        val sc = scaled(npc) ?: return
        val max = CoxMonsters.maxHitFor(r.form, sc, r.base, r.stat)
        animate(npc, r.attack)
        areaSound(npc.tile, r.sound, radius = 10)
        if (r.form == 7527) {
            repeat(3) { attack(npc, p, max, CombatClass.MELEE, delay = 1, protectedPercent = 33) }
            return
        }
        val ranged = r.form == 7528
        val tiles = volleyTiles(npc, p)
        val lands = if (ranged) 2 else 3
        tiles.forEachIndexed { i, t ->
            if (ranged) projectile(npc.tile, t, listOf(1332, 1333, 1334)[i % 3], listOf(26, 25, 23)[i % 3], 64 - listOf(26, 25, 23)[i % 3], 25, 1, angle = listOf(19, 16, 10)[i % 3], steepness = 96)
            else projectile(npc.tile, t, 1331, listOf(45, 30, 30)[i % 3], 96 - listOf(45, 30, 30)[i % 3], 37, 1, angle = 60 + i, steepness = 64)
            raiders().filter { it.tile.x == t.x && it.tile.z == t.z }.forEach {
                attack(npc, it, max, if (ranged) CombatClass.RANGED else CombatClass.MAGIC, delay = lands, protectedPercent = 33)
            }
        }
        later(lands) {
            tiles.forEachIndexed { i, t ->
                gfxAt(if (ranged) 305 else 659, t, delay = if (ranged) 0 else 10)
                areaSound(t, if (ranged) 360 else 156, radius = 8, delay = i * 3)
            }
        }
    }

    /** The target's tile and two free ones within 3 of it, not under the vanguard (tsps). */
    private fun volleyTiles(npc: Npc, p: Player): List<Tile> {
        val at = Tile(p.tile)
        val tiles = mutableListOf(at)
        var tries = 0
        while (tiles.size < 3 && tries++ < 50) {
            val t = at.transform(world.random(-3..3), world.random(-3..3))
            if (tiles.any { it.x == t.x && it.z == t.z } || !contains(t)) continue
            if (t.x - npc.tile.x in 0..2 && t.z - npc.tile.z in 0..2) continue
            tiles += t
        }
        return tiles
    }

    private fun swirl() {
        val l = living()
        val lowest = l.minOfOrNull { it.getCurrentLifepoints() * 1000 / it.getMaximumLifepoints() } ?: return
        l.forEach { npc ->
            val low = l.size > 1 && npc.getCurrentLifepoints() * 1000 / npc.getMaximumLifepoints() == lowest
            gfxOn(npc, if (low) 56 else 57)
        }
    }

    private fun shuffle() {
        val heal = unbalanced()
        warning = -1
        stage = Stage.SHUFFLING
        val step = if (world.random(1) == 0) 1 else 2
        living().forEach { npc ->
            shelled += npc
            roleOf[npc]?.let { animate(npc, it.fall) }
            CoxIds.npc(7526)?.let { npc.setTransmogId(it) }
            val from = spots.indexOfFirst { it.x == npc.tile.x && it.z == npc.tile.z }.takeIf { it >= 0 } ?: vanguards.indexOf(npc)
            npc.walkTo(spots[(from + step) % 3], detectCollision = false)
        }
        if (heal) later(4) { living().forEach { animate(it, 7431); it.setCurrentLifepoints(it.getMaximumLifepoints()) } }
        later(world.random(9..13)) {
            living().forEach { npc -> roleOf[npc]?.let { r -> CoxIds.npc(r.form)?.let { npc.setTransmogId(it) }; animate(npc, r.rise) } }
            standUp()
        }
    }

    private fun trample() {
        living().filter { it in shelled && it.hasMoveDestination() }.forEach { npc ->
            raiders().filter { it.tile.x - npc.tile.x in 0..2 && it.tile.z - npc.tile.z in 0..2 }.forEach { damage(npc, it, world.random(1..5)) }
        }
    }

    /** In the shell nothing hurts; a fatal hit in the shell, or one that leaves the others out of balance, leaves 1 (Wiki). */
    override fun incoming(npc: Npc, hit: Hit) {
        if (npc !in vanguards) return
        if (npc in shelled) {
            hit.hitmarks.forEach { it.damage = 0 }
            (hit.source as? Player)?.message("The Vanguard is protected by its shell.")
            return
        }
        val total = hit.hitmarks.sumOf { it.damage }
        val others = living().filter { it !== npc }
        if (total >= npc.getCurrentLifepoints() && others.size == 2 && stage == Stage.FIGHTING && unbalanced()) {
            var left = npc.getCurrentLifepoints() - 1
            hit.hitmarks.forEach { m -> val d = minOf(m.damage, left.coerceAtLeast(0)); m.damage = d; left -= d }
        }
    }

    private var overloads = 0

    /**
     * Wiki 'Vanguard' drops: by style - melee elder (+)(4) + Xeric's aid (+)(4) x2, ranged twisted (+)(4) + Xeric's aid + revitalisation (+)(4)
     * x2, magic kodai (+)(4) + Xeric's aid + prayer enhance (+)(4); overload (+)(4) 1/3 each, at least one per room (the last one gives it).
     */
    override fun onNpcDeath(npc: Npc) {
        if (npc !in vanguards) return
        val at = Tile(npc.tile)
        when (roleOf[npc]?.form) {
            7527 -> { drop(at, 20924); drop(at, 20984, 2) }
            7528 -> { drop(at, 20936); drop(at, 20984); drop(at, 20960, 2) }
            7529 -> { drop(at, 20948); drop(at, 20984); drop(at, 20972) }
        }
        val last = living().isEmpty()
        if (world.random(2) == 0 || (last && overloads == 0)) { drop(at, 20996); overloads++ }
        if (last) clearExit()
    }

    companion object {
        /** The three spots' centres by version (tsps). */
        val SPOTS = listOf(
            listOf(Tile(3285, 5329), Tile(3277, 5334), Tile(3276, 5324)),
            listOf(Tile(3317, 5330), Tile(3309, 5334), Tile(3309, 5324)),
            listOf(Tile(3347, 5325), Tile(3341, 5334), Tile(3337, 5325)),
        )
        val CRYSTAL = listOf(Tile(3268, 5327), Tile(3311, 5339), Tile(3350, 5328))
    }
}
