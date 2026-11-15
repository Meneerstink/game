package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.combat.poison

/**
 * The Lizardman Shamans' room. Wiki (Lizardman shaman (Chambers of Xeric)): the surface shamans' attacks (ranged, poison blob, jump,
 * exploding spawns), the blob ~25% stronger (up to 39 without Shayzien armour); two shamans for solo and small teams.
 * tsps rsprox captures (3282, 3389): the spirit tendrils (29768) and the exit's two blocking spawns (7575) are there from the start;
 * ranged 7193 + projectile 1291 (sound 385) landing 3 ticks later; the acid = projectile 1293 landing as gfx 1294 (sound 1070) 5-6 ticks
 * later; dying 7196, gone 3 ticks later; 5 ticks after the last one starts dying the blockers raise their hands (7159, sound 665), the
 * tendrils go a tick later, the blockers burst 3 ticks after that (gfx 1295, sound 1021).
 * ONZEKER (tsps, not on the Wiki): tiles, counts (2, 3 at 4+, 4 at 8+), bases 24, blob 20-39 within 2 tiles (poison 1 in 4), melee 7158,
 * the jump (7152, landing 6946 6 ticks later beside the target, 20-25 within a tile), spawns (1 in 61 a tick, at most every 33 ticks,
 * three by the target, exploding 6 ticks later for up to 10 within a tile).
 */
class ShamansRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val shamans = mutableListOf<Npc>()
    private val blockers = mutableListOf<Npc>()
    private val tendrils = mutableListOf<GameObject>()
    private val spawnsAt = HashMap<Npc, Int>()

    override fun load() {
        val count = when (scaling().size) { in 0..3 -> 2; in 4..7 -> 3; else -> 4 }
        SHAMANS[variant].shuffled().take(count).forEach { s ->
            spawn(listOf(7573, 7574).random(), tile(s.x, s.z, 3), speed = 4, deathSeq = 7196)?.let { shamans += it; fighters[it] = Fighter(4, 8) }
        }
        TENDRILS[variant].forEach { t -> placeLoc(29768, tile(t.x, t.z), rotation = 0)?.let { tendrils += it } }
        BLOCKERS[variant].forEach { (at, face) ->
            val t = tile(at.x, at.z)
            spawn(7575, t)?.let { b -> b.faceTile(tile(face.x, face.z)); blockers += b; blockTile(t, true) }
        }
    }

    override fun onTick() {
        fight(::swing)
        for (s in shamans) {
            if (!alive(s) || tick < (fighters[s]?.busyUntil ?: 0)) continue
            val target = nearest(s) ?: continue
            if (world.random(60) == 0 && tick >= (spawnsAt[s] ?: 0)) { spawnsAt[s] = tick + 33; spawns(target) }
        }
    }

    private fun adjacent(npc: Npc, p: Player) = meleeReach(npc, p.tile)

    private fun swing(npc: Npc, p: Player) {
        val o = osrs(npc); val sc = scaled(npc) ?: return
        var style = world.random(if (adjacent(npc, p)) 6 else 5)
        if (style == 5 && !jump(npc, p)) style = if (adjacent(npc, p)) 6 else 4
        if (style == 5) return
        if (style == 6) {
            play(p, 1695)
            animate(npc, 7158)
            attack(npc, p, CoxMonsters.maxHitFor(o, sc, 24, CoxMonsters.STR), CombatClass.MELEE)
            return
        }
        areaSound(npc.tile, 385, radius = 10)
        animate(npc, 7193)
        if (style == 4) {
            val at = Tile(p.tile)
            val end = 61 + 90 + 5 * npc.tile.getDistance(at)
            projectile(npc.tile, at, 1293, 61, end - 61, 105, 1, angle = 30)
            later(maxOf(1, end / 30)) {
                if (!alive(npc)) return@later
                gfxAt(1294, at)
                areaSound(at, 1070, radius = 8)
                raiders().filter { it.tile.getDistance(at) <= 2 }.forEach { r ->
                    damage(npc, r, world.random(20..39) * shayzienShare(r) / 100)
                    if (world.random(3) == 0) r.poison(10)
                }
            }
            return
        }
        val end = 61 + 35 + 5 * npc.tile.getDistance(p.tile)
        projectile(npc.tile, p, 1291, 61, end - 61, 105, 36, angle = 5)
        attack(npc, p, CoxMonsters.maxHitFor(o, sc, 24, CoxMonsters.RNG), CombatClass.RANGED, delay = maxOf(1, end / 30))
    }

    /** Wiki: Shayzien armour (5) protects from the blob; each top-tier piece worn takes a fifth off. */
    private fun shayzienShare(p: Player): Int {
        val worn = SHAYZIEN.count { id -> gg.rsmod.plugins.content.activity.cox.CoxIds.item(id)?.let { p.equipment.contains(it) } ?: p.equipment.contains(id) }
        return if (worn >= 5) 0 else 100 - worn * 20
    }

    private fun jump(npc: Npc, p: Player): Boolean {
        val at = Tile(p.tile)
        val landing = at.transform(-1, -1)
        for (dx in 0..2) for (dz in 0..2) if (world.collision.isBlocked(landing.transform(dx, dz), gg.rsmod.game.model.Direction.NORTH, false)) return false
        val f = fighters[npc] ?: return false
        f.busyUntil = tick + JUMP_TICKS + 2
        animate(npc, 7152)
        areaSound(npc.tile, 2467, radius = 8)
        later(JUMP_TICKS) {
            if (!alive(npc)) return@later
            npc.moveTo(landing)
            animate(npc, 6946)
            areaSound(at, 1695, radius = 8)
            raiders().filter { it.tile.getDistance(at) <= 1 }.forEach { damage(npc, it, world.random(20..25)) }
        }
        return true
    }

    private fun spawns(p: Player) {
        val at = Tile(p.tile)
        repeat(3) {
            var t = at
            for (tries in 0 until 20) {
                val c = at.transform(world.random(-2..2), world.random(-2..2))
                if (!world.collision.isBlocked(c, gg.rsmod.game.model.Direction.NORTH, false)) { t = c; break }
            }
            val spawn = spawn(7575, t) ?: return@repeat
            spawn.faceTile(at)
            later(6) { explode(spawn, 10, 2) }
        }
    }

    private fun explode(spawn: Npc, max: Int, burstAfter: Int) {
        if (!alive(spawn)) return
        animate(spawn, 7159)
        areaSound(spawn.tile, 665, radius = 7)
        later(burstAfter) {
            if (!spawn.isSpawned()) return@later
            gfxAt(1295, spawn.tile)
            areaSound(spawn.tile, 1021, radius = 5)
            raiders().filter { it.tile.getDistance(spawn.tile) <= 1 }.forEach { damage(null, it, world.random(0..max)) }
            remove(spawn)
        }
    }

    override fun passRefusal(p: Player, passage: Tile): String? = blockedNear(passage, blockers.map { it.tile })

    override fun onNpcDeath(npc: Npc) {
        if (npc !in shamans || shamans.any { alive(it) }) return
        completed = true
        later(5) { blockers.forEach { explode(it, 1, 3) } }
        later(6) { tendrils.toList().forEach { removeLoc(it) }; tendrils.clear() }
        later(8) { blockers.forEach { b -> blockTile(b.tile, false) } }
    }

    companion object {
        const val JUMP_TICKS = 6
        /** Shayzien armour (5): gloves, boots, helm, greaves, body (tsps; Wiki 'Shayzien armour (5)'). */
        val SHAYZIEN = listOf(13377, 13378, 13379, 13380, 13381)
        val SHAMANS = listOf(
            listOf(Tile(3273, 5270), Tile(3281, 5269), Tile(3278, 5262), Tile(3273, 5255)),
            listOf(Tile(3306, 5260), Tile(3306, 5267), Tile(3315, 5267), Tile(3316, 5259)),
            listOf(Tile(3340, 5258), Tile(3346, 5259), Tile(3341, 5268), Tile(3347, 5270)),
        )
        val TENDRILS = listOf(
            listOf(Tile(3279, 5249), Tile(3280, 5249), Tile(3289, 5262), Tile(3289, 5261)),
            listOf(Tile(3311, 5251), Tile(3310, 5251)),
            listOf(Tile(3342, 5251), Tile(3343, 5251)),
        )
        val BLOCKERS = listOf(
            listOf(Tile(3268, 5263) to Tile(3269, 5263), Tile(3268, 5264) to Tile(3269, 5264)),
            listOf(Tile(3311, 5275) to Tile(3311, 5274), Tile(3312, 5275) to Tile(3312, 5274)),
            listOf(Tile(3353, 5265) to Tile(3352, 5265), Tile(3353, 5264) to Tile(3352, 5264)),
        )
    }
}
