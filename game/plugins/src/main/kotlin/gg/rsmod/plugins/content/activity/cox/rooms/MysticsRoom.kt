package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxRaid

/**
 * The Skeletal Mystics' room (DARK_ALTAR_ROOM). Wiki (Skeletal Mystic): two Magic attacks (a standard one and a damaging Vulnerability)
 * and Melee; one that can reach its target who prays against Magic melees it, one that can't keeps casting.
 * tsps rsprox captures (3282, 3285, 3389): cast 5528; fire = gfx 1322 (height 164), projectile 130, sounds 155/156, impact 131 (splash 85);
 * Vulnerability = gfx 1321, projectile 109, sounds 127/3012, impact 110. Dying 5491, gone 5 ticks later. The portals appear when the last
 * one dies: 49996 start (Travel-End), 49997 middle (Travel: 'Choose location'), 49998 end (Travel-Start).
 * ONZEKER (tsps, not on the Wiki): the tiles, counts (3, 4 at 3+, 5 at 5+, 6 at 8+), 1 in 5 Vulnerability draining Defence 4-8, fire base
 * 25 + raiders/4, melee anim 5485 base 25, protection letting half through; the Mark of Power 29741 blocks the way on until all are dead.
 */
class MysticsRoom(raid: CoxRaid, floor: Int, laid: CoxLaidRoom) : CoxRoom(raid, floor, laid) {
    private val mystics = mutableListOf<Npc>()
    private var mark: GameObject? = null
    val portals = mutableListOf<Tile>()

    override fun load() {
        val count = when (val n = scaling().size) { in 0..2 -> 3; in 3..4 -> 4; in 5..7 -> 5; else -> 6 }
        SPAWNS[variant].shuffled().take(count).forEach { s ->
            spawn(IDS.random(), tile(s.x, s.z, 2), speed = 4, deathSeq = 5491)?.let { mystics += it; fighters[it] = Fighter(4, 7) }
        }
        val m = MARK[variant]
        mark = placeLoc(29741, tile(m.x, m.z, 2))
        PORTALS[variant].forEach { portals += tile(it.x, it.z) }
    }

    override fun onTick() = fight(::swing)

    private fun swing(npc: Npc, p: Player) {
        val base = osrs(npc)
        val scaled = scaled(npc) ?: return
        if (meleeReach(npc, p.tile) && (p.isProtectedFrom(CombatClass.MAGIC) || world.random(1) == 0)) {
            animate(npc, 5485)
            play(p, 511)
            attack(npc, p, CoxMonsters.maxHitFor(base, scaled, 25, CoxMonsters.STR), CombatClass.MELEE, protectedPercent = 50)
            return
        }
        val vuln = world.random(4) == 0
        animate(npc, 5528)
        gfxOn(npc, if (vuln) 1321 else 1322, height = 164)
        val dist = npc.tile.getDistance(p.tile)
        val end = 86 + 5 * dist
        projectile(npc.tile, p, if (vuln) 109 else 130, 30, end, 85, 31)
        val lands = end / 30
        play(p, if (vuln) 127 else 155)
        play(p, if (vuln) 3012 else 156, delay = end)
        val max = CoxMonsters.maxHitFor(base, scaled, 25 + scaling().size / 4, CoxMonsters.MAG)
        attack(npc, p, max, CombatClass.MAGIC, delay = lands, protectedPercent = 50) { dealt ->
            gfxOn(p, if (dealt > 0) (if (vuln) 110 else 131) else 85, height = 124)
            if (vuln && dealt >= 0) {
                val def = p.skills.getCurrentLevel(Skills.DEFENCE)
                p.skills.setCurrentLevel(Skills.DEFENCE, maxOf(0, def - world.random(4..8)))
            }
        }
    }

    override fun passRefusal(p: Player, passage: Tile): String? = blockedNear(passage, listOfNotNull(mark?.tile))

    override fun onNpcDeath(npc: Npc) {
        if (npc !in mystics || mystics.any { alive(it) }) return
        completed = true
        removeLoc(mark)
        mark = null
        later(1) { listOf(49996, 49997, 49998).forEachIndexed { i, id -> portals.getOrNull(i)?.let { placeLoc(id, it) } } }
    }

    /** The portals: Travel-End / Travel-Start, or from the middle either way (tsps: overlay 634 + move 2 ticks later). */
    fun travel(p: Player, toEnd: Boolean) {
        val to = if (toEnd) portals.getOrNull(2) else portals.getOrNull(0)
        to ?: return
        play(p, 3952)
        p.lock()
        later(2) { p.unlock(); p.moveTo(to) }
    }

    companion object {
        val IDS = listOf(7604, 7605, 7606)
        val SPAWNS = listOf(
            listOf(Tile(3278, 5261), Tile(3286, 5265), Tile(3284, 5271), Tile(3277, 5273), Tile(3273, 5265), Tile(3278, 5270)),
            listOf(Tile(3315, 5262), Tile(3318, 5259), Tile(3309, 5258), Tile(3301, 5256), Tile(3306, 5264), Tile(3312, 5259)),
            listOf(Tile(3342, 5262), Tile(3342, 5266), Tile(3340, 5268), Tile(3338, 5274), Tile(3337, 5268), Tile(3338, 5260)),
        )
        val MARK = listOf(Tile(3270, 5264), Tile(3309, 5272), Tile(3348, 5268))
        val PORTALS = listOf(
            listOf(Tile(3278, 5250), Tile(3283, 5266), Tile(3272, 5267)),
            listOf(Tile(3311, 5250), Tile(3308, 5260), Tile(3307, 5270)),
            listOf(Tile(3341, 5255), Tile(3342, 5268), Tile(3348, 5271)),
        )
    }
}
