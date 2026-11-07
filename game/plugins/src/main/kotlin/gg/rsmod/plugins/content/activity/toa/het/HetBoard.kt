package gg.rsmod.plugins.content.activity.toa.het

/**
 * N462 Path of Het - the light puzzle as plain state (no packets, no timers), in OSRS source coordinates.
 *
 * Sources (team/dossiers/N462-notities/toa/08-het*.txt, OSRS Wiki Tombs_of_Amascut/Strategies oldid15392814 lines 1062-1098,
 * Het's_Seal oldid15214038, Mirror_(Tombs_of_Amascut), Caster_statue, Shielded_statue, cache2686 locs 45455-45491):
 * - the caster statue (loc 45486, 3x3) periodically shoots a light beam; mirrors (loc 45455 movable: Pick-up / Rotate-clockwise /
 *   Rotate-anticlockwise / Push; 45456 fixed; 45457 dirty: Clean) reflect it; stone barriers (45458/45460) block it, worn barriers
 *   (45462/45464, Break) block it until broken with a pickaxe (45466 destroyed); when the beam reaches the shielded statue (45485, 3x3)
 *   Het's Seal (npc 11706 -> 11707 weakened) can be mined for 15 ticks.
 * - "There are three mirrors that can be picked up, placed, rotated and pushed. The other mirrors are fixed; in group encounters, some
 *   of these are dirty and must be cleaned first to reflect light." "The light can always be reflected correctly using 1-3 mirrors."
 *
 * ONZEKER T22 (no source): which two sides a mirror reflects between per rotation. A mirror has four rotations (Rotate-clockwise /
 * -anticlockwise) and reflects a beam by 90 degrees, so each rotation is one corner: rotation r reflects between FACES[r]; a beam
 * hitting the back of a mirror is blocked. To be checked against the model in the test client (the shiny face must face both sides).
 */
enum class HetDir(val dx: Int, val dz: Int) {
    NORTH(0, 1), EAST(1, 0), SOUTH(0, -1), WEST(-1, 0);

    val opposite: HetDir get() = values()[(ordinal + 2) % 4]
}

data class HetPos(val x: Int, val z: Int) {
    fun step(d: HetDir, n: Int = 1) = HetPos(x + d.dx * n, z + d.dz * n)
}

/** A square of [size] tiles whose south-west tile is [sw] (statues and the seal are 3x3 locs/npcs). */
data class HetFootprint(val sw: HetPos, val size: Int = 3) {
    operator fun contains(p: HetPos) = p.x in sw.x until sw.x + size && p.z in sw.z until sw.z + size
    val centre: HetPos get() = HetPos(sw.x + size / 2, sw.z + size / 2)
}

class HetMirror(var pos: HetPos, var rotation: Int, val movable: Boolean, var dirty: Boolean = false) {
    init { require(rotation in 0..3) }

    /** The direction the beam leaves in, or null when it hits the back (or a dirty mirror): it stops here. */
    fun reflect(travel: HetDir): HetDir? {
        if (dirty) return null
        val (a, b) = FACES[rotation]
        return when (travel.opposite) { a -> b; b -> a; else -> null }
    }

    fun rotate(clockwise: Boolean) { rotation = (rotation + if (clockwise) 1 else 3) % 4 }

    companion object {
        /** ONZEKER T22: rotation -> the two sides of the reflecting corner (see class comment). */
        val FACES = arrayOf(HetDir.NORTH to HetDir.EAST, HetDir.EAST to HetDir.SOUTH, HetDir.SOUTH to HetDir.WEST, HetDir.WEST to HetDir.NORTH)

        /** The rotation that turns a beam travelling [travel] into [exit] (a 90 degree turn), or null. */
        fun rotationFor(travel: HetDir, exit: HetDir): Int? = FACES.indexOfFirst { (a, b) ->
            (travel.opposite == a && exit == b) || (travel.opposite == b && exit == a)
        }.takeIf { it >= 0 }
    }
}

data class HetMirrorSpec(val pos: HetPos, val rotation: Int, val movable: Boolean, val dirtyInGroups: Boolean = false)

/** A wall of single-tile barrier pieces from [from] to [to] (one row or one column); [worn] = breakable with a pickaxe. */
data class HetWall(val from: HetPos, val to: HetPos, val worn: Boolean) {
    init { require(from.x == to.x || from.z == to.z) }
    val horizontal: Boolean get() = from.z == to.z
    val tiles: List<HetPos> get() =
        if (horizontal) (minOf(from.x, to.x)..maxOf(from.x, to.x)).map { HetPos(it, from.z) }
        else (minOf(from.z, to.z)..maxOf(from.z, to.z)).map { HetPos(from.x, it) }
}

data class HetLayout(
    val name: String,
    val source: String,
    val caster: HetFootprint,
    val casterFacing: HetDir,
    val target: HetFootprint,
    val seal: HetFootprint,
    val walls: List<HetWall>,
    val mirrors: List<HetMirrorSpec>,
    /** Floor tiles the beam may cross (inclusive). */
    val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int,
) {
    init { require(mirrors.count { it.movable } <= 3) }
}

data class HetBeam(val tiles: List<HetPos>, val hitTarget: Boolean, val turns: List<HetPos>)

class HetBoard(val layout: HetLayout, group: Boolean) {
    val mirrors: MutableList<HetMirror> = layout.mirrors.map { HetMirror(it.pos, it.rotation, it.movable, group && it.dirtyInGroups) }.toMutableList()
    private val stone: Set<HetPos> = layout.walls.filter { !it.worn }.flatMap { it.tiles }.toSet()
    val worn: MutableSet<HetPos> = layout.walls.filter { it.worn }.flatMap { it.tiles }.toMutableSet()

    fun mirrorAt(p: HetPos): HetMirror? = mirrors.firstOrNull { it.pos == p }
    fun blocked(p: HetPos): Boolean = p in stone || p in worn || p in layout.caster || p in layout.seal

    /** Free floor for a placed/pushed mirror: in the room, no barrier, statue, seal or other mirror. */
    fun free(p: HetPos): Boolean = p.x in layout.minX..layout.maxX && p.z in layout.minZ..layout.maxZ && !blocked(p) &&
        p !in layout.target && mirrorAt(p) == null

    /** Trace the beam from the caster's face until it hits the target, a barrier, a mirror's back or leaves the room. */
    fun trace(maxSteps: Int = 256): HetBeam {
        val tiles = mutableListOf<HetPos>()
        val turns = mutableListOf<HetPos>()
        var dir = layout.casterFacing
        var p = layout.caster.centre.step(dir, layout.caster.size / 2 + 1)
        repeat(maxSteps) {
            if (p in layout.target) return HetBeam(tiles, true, turns)
            if (p.x !in layout.minX..layout.maxX || p.z !in layout.minZ..layout.maxZ || blocked(p)) return HetBeam(tiles, false, turns)
            tiles += p
            mirrorAt(p)?.let { m ->
                dir = m.reflect(dir) ?: return HetBeam(tiles, false, turns)
                turns += p
            }
            p = p.step(dir)
        }
        return HetBeam(tiles, false, turns)
    }

    fun pickUp(p: HetPos): Boolean {
        val m = mirrorAt(p)?.takeIf { it.movable } ?: return false
        mirrors.remove(m)
        return true
    }

    fun place(p: HetPos, rotation: Int = 0): Boolean {
        if (!free(p) || mirrors.count { it.movable } >= 3) return false
        mirrors += HetMirror(p, rotation, movable = true)
        return true
    }

    /** Push a movable mirror one tile in [dir] (away from the player) if that tile is free. */
    fun push(p: HetPos, dir: HetDir): HetPos? {
        val m = mirrorAt(p)?.takeIf { it.movable } ?: return null
        val to = p.step(dir)
        if (!free(to)) return null
        m.pos = to
        return to
    }

    fun breakWorn(p: HetPos): Boolean = worn.remove(p)
    fun clean(p: HetPos): Boolean { val m = mirrorAt(p)?.takeIf { it.dirty } ?: return false; m.dirty = false; return true }
}

/** Het's Seal: HP, the 15-tick weakened window and the mining hit (Mod Ash 25-03-2023 via Strategies line 1078). */
object HetSeal {
    /** Het's Seal changes 20-05-2026: base Hitpoints 122 -> 119 (solo). */
    const val BASE_HP = 119
    /** Strategies 1070: "mine the obelisk for 15 ticks (9 seconds) before the seal is protected once more". */
    const val WEAKENED_TICKS = 15
    /** Het's Seal page: "35 Mining experience every 3 ticks"; Strategies: 7-8 hits per 15-tick window = one hit per 2 ticks.
     * ONZEKER T21: the two sources disagree on the interval; the hit count (7 hits x 17 = 119 = 1 cycle) needs 2 ticks. */
    const val HIT_TICKS = 2
    const val XP_PER_HIT = 35.0

    /** "Its health scales with the number of players but not with raid level" - ONZEKER T21: the general ToA group HP rule
     * (100% + 90% for players 2-3 + 60% from the 4th, 10-schaal.tsv) because the Wiki gives no separate seal table. */
    fun hp(partySize: Int): Int {
        require(partySize in 1..8)
        val tenths = 10 + 9 * minOf(partySize - 1, 2) + 6 * maxOf(partySize - 3, 0)
        return BASE_HP * tenths / 10
    }

    enum class Tier { BELOW_RUNE, RUNE, DRAGON }

    /** "The min damage is 8, 15 or 17 depending on your stat being <85, <100 or 100+. Add 2 for a dragon pick or better, subtract 3 for
     * a pick below rune. Add 0, 1 or 2 randomly" (Mod Ash); Summer Sweep Up 25-06-2025: the top threshold is 99 instead of 100. */
    fun hit(visibleMining: Int, tier: Tier, roll: Int): Int {
        require(roll in 0..2)
        val base = when { visibleMining >= 99 -> 17; visibleMining >= 85 -> 15; else -> 8 }
        return base + when (tier) { Tier.DRAGON -> 2; Tier.RUNE -> 0; Tier.BELOW_RUNE -> -3 } + roll
    }
}
