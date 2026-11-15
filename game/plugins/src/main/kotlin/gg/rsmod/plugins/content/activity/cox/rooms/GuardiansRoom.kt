package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.skills.mining.PickaxeType

/**
 * The Guardians' room. Wiki (Guardian (Chambers of Xeric)): only pickaxes hurt them, the damage times (50 + Mining + the pickaxe's level,
 * at most a dragon pickaxe's) / 150; hitpoints 151 x (1 + team/2) + average Mining x team (CoxMonsters: 151 + avg Mining, then scaled);
 * melee (max 20, speed 4) and now and then a stomp hitting a 3x3; stepping between them gets you knocked back for damage and some points.
 * tsps rsprox (820, 3282, 3389): stepping into the gap -> both sweep (440, sound 2524), the player is thrown a tile back (1258), a hit
 * from each and 38 points from each; melee 1203 (sound 2533); stomp 4278 (sound 3188) + falling rock projectile 645 and gfx 305 on the
 * tile, landing the next tick (sound 360); crumbling 836, then the remains (id + 2); both down -> the gap opens 3 ticks later.
 * ONZEKER (tsps): tiles, invisible blockers 26209, knock-back 0-15 per guardian, stomp base 15, they never move. The knock-back is a
 * one-tile move here (no forcemovement packet yet).
 */
class GuardiansRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val guardians = mutableListOf<Npc>()
    private val blockers = mutableListOf<GameObject>()
    private val blocked = mutableListOf<Tile>()
    private val behind = mutableListOf<Tile>()
    private var downs = 0

    override fun load() {
        val t = TILES[variant]
        behind += tile(t[2].x, t[2].z); behind += tile(t[3].x, t[3].z)
        listOf(7569, 7570).forEachIndexed { i, id ->
            spawn(id, tile(t[i].x, t[i].z, 2), speed = 4, deathSeq = 836)?.let { guardians += it; fighters[it] = Fighter(4, 8) }
        }
        BLOCKED[variant].forEach { b -> val bt = tile(b.x, b.z); blocked += bt; placeLoc(26209, bt, rotation = 0)?.let { blockers += it } }
    }

    override fun onTick() {
        // They never move (tsps): swing at whoever is in reach, stomp at the others.
        for ((npc, f) in fighters.toList()) {
            if (!alive(npc)) continue
            val target = raiders().filter { distance(it.tile, npc) <= f.range }.minByOrNull { distance(it.tile, npc) } ?: continue
            npc.facePawn(target)
            if (tick >= f.next) { f.next = tick + f.speed; swing(npc, target) }
        }
        if (completed || tick % 3 != 0) return
        for (p in raiders()) {
            val index = blocked.indexOfFirst { p.tile.getDistance(it) <= 1 }
            if (index >= 0) knockBack(p, index)
        }
    }

    private fun swing(npc: Npc, p: Player) {
        val o = osrs(npc); val sc = scaled(npc) ?: return
        if (meleeReach(npc, p.tile) && world.random(1) == 0) {
            animate(npc, 1203)
            areaSound(npc.tile, 2533, radius = 6)
            attack(npc, p, CoxMonsters.maxHitFor(o, sc, 20, CoxMonsters.STR), CombatClass.MELEE)
            return
        }
        val at = Tile(p.tile)
        animate(npc, 4278)
        projectile(at, at, 645, 15, 15, 225, 6, angle = 0, steepness = 0)
        areaSound(npc.tile, 3188, radius = 6)
        gfxAt(305, at, delay = 30)
        val max = CoxMonsters.maxHitFor(o, sc, 15, CoxMonsters.STR)
        later(1) {
            areaSound(at, 360, radius = 6)
            raiders().filter { it.tile.getDistance(at) <= 1 }.forEach { damage(npc, it, world.random(0..max)) }
        }
    }

    private fun knockBack(p: Player, index: Int) {
        val living = guardians.filter { alive(it) }
        if (living.isEmpty()) return
        living.forEach { g -> g.faceTile(p.tile); animate(g, 440) }
        blocked.forEach { areaSound(it, 2524, radius = 4) }
        p.stopMovement()
        p.moveTo(behind[index])
        animatePlayer(p, 1258)
        living.forEach { g -> damage(g, p, world.random(0..15)); raid.addPoints(p.username, 38) }
    }

    /** Wiki: only pickaxes, scaled by Mining and the pickaxe (level at most a dragon pickaxe's 61). */
    override fun incoming(npc: Npc, hit: Hit) {
        if (npc !in guardians) return
        val p = hit.source as? Player
        val weapon = p?.getEquipment(EquipmentType.WEAPON)?.id
        val pick = PickaxeType.values().firstOrNull { it.item == weapon }
        if (p == null || pick == null) { hit.hitmarks.forEach { it.damage = 0 }; return }
        val factorTimes150 = 50 + p.skills.getCurrentLevel(Skills.MINING) + minOf(pick.level, 61)
        hit.hitmarks.forEach { it.damage = it.damage * factorTimes150 / 150 }
    }

    override fun onNpcDeath(npc: Npc) {
        val i = guardians.indexOf(npc)
        if (i < 0) return
        val at = Tile(npc.tile)
        // tsps: the same npc turns to its remains (id + 2) 2 ticks after crumbling; here the remains are spawned in its place.
        later(2) { spawn(listOf(7571, 7572)[i], at)?.let { fighters.remove(it) } }
        if (++downs < 2) return
        completed = true
        later(3) { blockers.toList().forEach { removeLoc(it) }; blockers.clear() }
    }

    companion object {
        /** Cache tiles by version: the two guardians, then the tiles behind each gap side (tsps). */
        val TILES = listOf(
            listOf(Tile(3276, 5265), Tile(3276, 5269), Tile(3276, 5267), Tile(3276, 5268)),
            listOf(Tile(3312, 5271), Tile(3308, 5271), Tile(3310, 5271), Tile(3311, 5271)),
            listOf(Tile(3344, 5268), Tile(3344, 5272), Tile(3344, 5271), Tile(3344, 5270)),
        )
        val BLOCKED = listOf(
            listOf(Tile(3275, 5267), Tile(3275, 5268)), listOf(Tile(3310, 5273), Tile(3311, 5273)), listOf(Tile(3346, 5271), Tile(3346, 5270)),
        )
    }
}
