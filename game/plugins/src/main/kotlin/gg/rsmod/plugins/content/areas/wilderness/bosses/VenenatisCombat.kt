package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy

/**
 * N522 bundle 4a: OSRS Venenatis in the Silk Chasm (npc 6610 imported, OsrsNpcImportTool batch venenatis).
 *
 * OSRS Wiki "Venenatis" / "Venenatis/Strategies" / "Venenatis' Spiderling (monster)": melee (max 21) at an adjacent player, ranged
 * (max 35) or magic (max 30) at the others; "She will begin the fight using 8 range attacks, followed by 8 magic attacks, all while
 * moving position every 4 attacks. She will spawn spiderlings on the 1st range attack in cycle, and deploy her web attack on the 3rd
 * mage attack in the cycle"; "begins by summoning two spiderlings" (5 hitpoints, max 3, "drain prayer by 1 for every attack regardless
 * if it hits or not"; they "increase her max hit"); the sticky web "damages and drains prayer by 3 and run energy by 10".
 * Near Reality Venenatis.java / VenenatisSpiderling.java / WebObject.java (donor): every player in reach is attacked each swing, melee
 * 2 in 3 when adjacent, +2 max hit per living spiderling, the projectiles 2356/2358 and impacts 2357/2359, the 3-tick start, the
 * move to a random tile of 3415-3432 x 10195-10214 more than 3 tiles away, the web pattern (a 9x9 with rounded corners: edge pieces
 * 47085 harmless, corners 47086 and centre 47084 hurt, 3 damage + 10 run energy + 3 prayer each tick, 20 ticks), the spiderling's
 * message. Not ported: "hit through protection prayers" with spiderlings alive (no source gives how much - the donor blocks fully too).
 */
object VenenatisCombat {
    const val NPC = Npcs.VENENATIS_OSRS
    const val SPIDERLING = Npcs.VENENATIS_SPIDERLING_OSRS

    /** N522 bundle 4b: Spindel (OSRS 11998) - "otherwise her mechanics are identical" (OSRS Wiki "Venenatis/Strategies"). */
    const val SPINDEL = Npcs.SPINDEL_OSRS
    const val SPINDEL_SPIDERLING = Npcs.SPINDEL_SPIDERLING_OSRS

    /** One spider boss and her chasm: OSRS Wiki max hits, Near Reality MIDDLE / MOVE_AREA / WEB_AREA / chasm polygon. */
    data class Arena(
        val npc: Int,
        val spiderling: Int,
        val maxMelee: Int,
        val maxRanged: Int,
        val maxMagic: Int,
        val spawn: Tile,
        val middle: Tile,
        val chasmX: IntRange,
        val chasmZ: IntRange,
        val moveX: IntRange,
        val moveZ: IntRange,
        val corridorX: IntRange,
        val corridorZ: IntRange,
    ) {
        fun inChasm(t: Tile): Boolean = t.height == spawn.height && t.x in chasmX && t.z in chasmZ

        fun inMoveArea(x: Int, z: Int): Boolean = x in moveX && z in moveZ

        fun inWebArea(x: Int, z: Int): Boolean = inMoveArea(x, z) || (x in corridorX && z in corridorZ)
    }

    val VENENATIS_ARENA =
        Arena(
            Npcs.VENENATIS_OSRS, Npcs.VENENATIS_SPIDERLING_OSRS, 21, 35, 30, Tile(3423, 10203, 2), Tile(3423, 10204, 2),
            3404..3442, 10183..10226, 3415..3432, 10195..10214, 3422..3425, 10184..10195,
        )

    /** Spindel: max 14 melee / 31 ranged / 24 magic (OSRS Wiki infobox); the Web Chasm (donor WebChasm / Spindel.java). */
    val SPINDEL_ARENA =
        Arena(
            Npcs.SPINDEL_OSRS, Npcs.SPINDEL_SPIDERLING_OSRS, 14, 31, 24, Tile(1630, 11547, 2), Tile(1630, 11547, 2),
            1610..1651, 11524..11571, 1623..1640, 11539..11558, 1630..1633, 11526..11539,
        )

    fun arena(npc: Npc): Arena = if (npc.id == SPINDEL || npc.id == SPINDEL_SPIDERLING) SPINDEL_ARENA else VENENATIS_ARENA

    const val ANIM_RANGED = 16180 // NPC_VENENATIS_RANGED_01 9989
    const val ANIM_MAGIC = 16181 // _MAGIC_01 9990
    const val ANIM_MELEE = 16182 // _MELEE_01 9991
    const val ANIM_DEATH = 16183 // _DEATH_01 9992
    const val ANIM_SPAWN = 16184 // _SPAWN_01 9993

    const val GFX_RANGED = 3210 // FX_VENENATIS_RANGED_PROJECTILE 2356
    const val GFX_RANGED_HIT = 3211 // _RANGED_IMPACT 2357
    const val GFX_MAGIC = 3212 // _MAGIC_PROJECTILE 2358
    const val GFX_MAGIC_HIT = 3213 // _MAGIC_IMPACT 2359
    const val GFX_WEB = 3214 // _WEB_PROJECTILE 2360
    const val GFX_WEB_HURT = 3215 // _WEB_IMPACT 2361

    const val WEB_CENTRE = 63105 // OSRS 47084
    const val WEB_EDGE = 63106 // OSRS 47085
    const val WEB_CORNER = 63107 // OSRS 47086

    const val MAX_MELEE = 21
    const val MAX_RANGED = 35
    const val MAX_MAGIC = 30
    const val SPIDERLING_BONUS = 2
    const val SPIDERLINGS = 2
    const val WEB_TICKS = 20
    const val REACH = 25

    val SPAWN = Tile(3423, 10203, 2)
    val MIDDLE = Tile(3423, 10204, 2)

    /** Swings in this 16-attack cycle (0-7 ranged, 8-15 magic) and the warm-up ticks after a spawn. */
    val ATTACKS = AttributeKey<Int>()
    val STARTED = AttributeKey<Int>()

    /** The spiderlings of this Venenatis that are alive. */
    val SPIDERLINGS_ALIVE = AttributeKey<MutableList<Npc>>()

    /** A spiderling's Venenatis and the life it was summoned in (donor: a spiderling dies with its Venenatis). */
    val OWNER = AttributeKey<Npc>()
    val OWNER_LIFE = AttributeKey<Int>()
    val LIFE = AttributeKey<Int>()

    /** Every spiderling summoned and not yet removed (the owner's own list does not survive its death). */
    private val summoned = java.util.Collections.synchronizedList(mutableListOf<Npc>())

    /** The spiderling's Venenatis is dead, gone or in a later life. */
    fun orphaned(spiderling: Npc): Boolean {
        val owner = spiderling.attr[OWNER] ?: return true
        return owner.isDead() || !owner.isSpawned() || owner.attr[LIFE] != spiderling.attr[OWNER_LIFE]
    }

    /** The Silk Chasm floor, square 13727 (Near Reality SilkChasm 3404-3442 x 10183-10226, plane 2). */
    fun inChasm(tile: Tile): Boolean = VENENATIS_ARENA.inChasm(tile)

    /** The move area (donor MOVE_AREA). */
    fun inMoveArea(x: Int, z: Int): Boolean = VENENATIS_ARENA.inMoveArea(x, z)

    /** The donor WEB_AREA: the move area plus the corridor 3422-3425 x 10184-10195. */
    fun inWebArea(x: Int, z: Int): Boolean = VENENATIS_ARENA.inWebArea(x, z)

    fun ranged(attack: Int): Boolean = attack % 16 < 8

    fun spawnsSpiderlings(attack: Int): Boolean = attack % 16 == 0

    fun shootsWeb(attack: Int): Boolean = attack % 16 == 10

    /** After this swing (the counter already advanced): every 4 attacks she moves. */
    fun movesAfter(attacksDone: Int): Boolean = attacksDone % 4 == 0

    /** The donor web pattern: 9x9 without the two outer tiles of each corner; edge = the outer ring. Null = no web there. */
    fun webPiece(row: Int, column: Int): Int? {
        if ((row == 0 || row == 8) && (column < 2 || column > 6)) return null
        if ((row == 1 || row == 7) && (column < 1 || column > 7)) return null
        return when {
            row == 0 || row == 8 || column == 0 || column == 8 -> WEB_EDGE
            (row == 1 || row == 7) && (column == 1 || column == 7) -> WEB_CORNER
            else -> WEB_CENTRE
        }
    }

    fun spiderlings(npc: Npc): MutableList<Npc> = npc.attr[SPIDERLINGS_ALIVE] ?: mutableListOf<Npc>().also { npc.attr[SPIDERLINGS_ALIVE] = it }

    fun living(npc: Npc): Int = spiderlings(npc).count { it.isSpawned() && !it.isDead() }

    fun spiderlingsToSummon(id: Int, alive: Int): Int =
        if (id == SPINDEL) minOf(SPIDERLINGS, (10 - alive).coerceAtLeast(0)) else SPIDERLINGS

    /** Wiki Strategies: the damaging web is a centred 7x5; the outside pieces are scenery. */
    fun inDamagingWeb(centre: Tile, tile: Tile): Boolean = tile.height == centre.height &&
        kotlin.math.abs(tile.x - centre.x) <= 3 && kotlin.math.abs(tile.z - centre.z) <= 2

    fun recoilImmune(id: Int): Boolean = id == NPC || id == SPINDEL || id == SPIDERLING || id == SPINDEL_SPIDERLING

    /** Melee reach of a large npc: a tile next to its footprint, not diagonal past a corner. */
    fun adjacent(npc: Npc, t: Tile): Boolean = gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(
        npc.world.collision, npc.tile, npc.getSize(), npc.getSize(), t, 1, 1)

    fun targets(npc: Npc): List<Player> {
        val list = mutableListOf<Player>()
        npc.world.players.forEach { p ->
            if (p.isOnline && !p.isDead() && !p.invisible && arena(npc).inChasm(p.tile) && p.tile.isWithinRadius(npc.getCentreTile(), REACH) &&
                npc.hasLineOfSightTo(p, projectile = true, maximumDistance = REACH)) list += p
        }
        return list
    }

    fun onSpawn(npc: Npc) {
        // Spiderlings left from her previous life go with it.
        synchronized(summoned) { summoned.filter { it.attr[OWNER] === npc } }.forEach { if (it.isSpawned()) npc.world.remove(it); summoned.remove(it) }
        npc.attr[LIFE] = (npc.attr[LIFE] ?: 0) + 1
        npc.attr[ATTACKS] = 0
        npc.attr[STARTED] = 0
        npc.animate(ANIM_SPAWN)
    }

    fun onDeath(npc: Npc) {
        val mine = synchronized(summoned) { summoned.filter { it.attr[OWNER] === npc } }
        mine.forEach { if (it.isSpawned()) npc.world.remove(it) }
        summoned.removeAll(mine)
        spiderlings(npc).clear()
    }

    object Combat : CombatScript() {
        override val ids = intArrayOf(NPC, SPINDEL)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                // Donor: three ticks after a spawn before the first swing.
                val started = npc.attr[STARTED] ?: 3
                if (started < 3) {
                    npc.attr[STARTED] = started + 1
                    it.wait(1)
                    continue
                }
                var moved = false
                if (npc.moveToAttackRange(it, target, distance = 10, projectile = true)) {
                    moved = swing(npc, target as? Player)
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                // Donor: while she shuffles to her new spot she neither attacks nor follows (up to 12 ticks of walking).
                if (moved) {
                    var walked = 0
                    while (npc.hasMoveDestination() && walked < 12) {
                        it.wait(1)
                        walked++
                    }
                }
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        /** One swing at every player in reach; true when she then shuffles to another spot. */
        private fun swing(npc: Npc, primary: Player?): Boolean {
            val attack = npc.attr[ATTACKS] ?: 0
            val players = targets(npc)
            if (players.isEmpty()) return false
            val bonus = living(npc) * SPIDERLING_BONUS
            val arena = arena(npc)
            val rangedPhase = ranged(attack)
            var animated = false
            players.forEach { p ->
                val adjacent = adjacent(npc, p.tile)
                if (adjacent) {
                    if (!animated) npc.animate(ANIM_MELEE).also { animated = true }
                    melee(npc, p, arena.maxMelee + bonus)
                } else if (rangedPhase) {
                    if (!animated) npc.animate(ANIM_RANGED).also { animated = true }
                    rangedHit(npc, p, arena.maxRanged + bonus)
                } else {
                    if (!animated) npc.animate(ANIM_MAGIC).also { animated = true }
                    magicHit(npc, p, arena.maxMagic + bonus)
                }
            }
            if (spawnsSpiderlings(attack)) summonSpiderlings(npc, primary?.takeIf { it in players } ?: players.first())
            if (shootsWeb(attack)) shootWeb(npc, Tile((primary?.takeIf { it in players } ?: players.first()).tile))
            val done = (attack + 1) % 16
            npc.attr[ATTACKS] = done
            if (!movesAfter(done)) return false
            shuffle(npc)
            return true
        }

        private fun melee(npc: Npc, p: Player, max: Int) {
            npc.prepareAttack(CombatClass.MELEE, StyleType.STAB, WeaponStyle.ACCURATE)
            val land = MeleeCombatFormula.getAccuracy(npc, p) >= npc.world.randomDouble()
            npc.dealHit(target = p, maxHit = max.toDouble(), landHit = land, delay = 1, hitType = HitType.MELEE)
        }

        private fun rangedHit(npc: Npc, p: Player, max: Int) {
            npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            // Donor projectile (2356, 43, 25, 30, 15, 15, 64, 0): start 43, end 25, delay 30, angle 15.
            npc.world.spawn(npc.createProjectile(p, GFX_RANGED, startHeight = 43, endHeight = 25, delay = 30, angle = 15))
            val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(p), p.getCentreTile())
            val land = RangedCombatFormula.getAccuracy(npc, p) >= npc.world.randomDouble()
            npc.dealHit(target = p, maxHit = max.toDouble(), landHit = land, delay = delay, hitType = HitType.RANGE, onHit = { p.graphic(GFX_RANGED_HIT) })
        }

        private fun magicHit(npc: Npc, p: Player, max: Int) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.world.spawn(npc.createProjectile(p, GFX_MAGIC, startHeight = 43, endHeight = 25, delay = 30, angle = 15))
            val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(p), p.getCentreTile())
            val land = MagicCombatFormula.getAccuracy(npc, p) >= npc.world.randomDouble()
            npc.dealHit(target = p, maxHit = max.toDouble(), landHit = land, delay = delay, hitType = HitType.MAGIC, onHit = { p.graphic(GFX_MAGIC_HIT, height = 120) })
        }

        /**
         * N522 (testclient 2026-11-04): in the single-way (singles-plus, OSRS Wiki "Singles-plus combat") Web Chasm the aggression
         * pick never chose a player already fighting Spindel, so her spiderlings stood idle - OSRS: "otherwise her mechanics are
         * identical" and the spiderlings "drain prayer on every hit" (Venenatis/Strategies). Outside multi-combat a new spiderling
         * therefore joins the boss's target at once, as Vet'ion's hellhounds do; in the multi Silk Chasm aggression still picks.
         */
        internal fun summonSpiderlings(npc: Npc, target: Player? = null) {
            val list = spiderlings(npc)
            list.removeAll { !it.isSpawned() || it.isDead() }
            repeat(spiderlingsToSummon(npc.id, list.size)) {
                val middle = arena(npc).middle
                val tile = Tile(middle.x + npc.world.random(-5..5), middle.z + npc.world.random(-5..5), middle.height)
                val spiderling = Npc(arena(npc).spiderling, tile, npc.world)
                spiderling.respawns = false
                spiderling.attr[OWNER] = npc
                spiderling.attr[OWNER_LIFE] = npc.attr[LIFE] ?: 0
                if (npc.world.spawn(spiderling)) {
                    list += spiderling
                    summoned += spiderling
                    if (target != null && !spiderling.tile.isMulti(npc.world)) spiderling.attack(target)
                }
            }
        }

        /** The donor's web: a projectile to the tile, then the 9x9 pattern centred on it for 20 ticks. */
        private fun shootWeb(npc: Npc, centre: Tile) {
            val world = npc.world
            val life = WildernessBossLife.token(npc)
            world.spawn(npc.createProjectile(centre, GFX_WEB, startHeight = 43, endHeight = 0, delay = 30, angle = 25, lifespan = 30 + 40))
            val origin = Tile(centre.x - 4, centre.z - 4, centre.height)
            world.queue {
                wait(2)
                if (!WildernessBossLife.active(npc, life)) return@queue
                val webs = mutableListOf<DynamicObject>()
                for (row in 0..8) for (column in 0..8) {
                    val piece = webPiece(row, column) ?: continue
                    val x = origin.x + row
                    val z = origin.z + column
                    if (!arena(npc).inWebArea(x, z)) continue
                    val obj = DynamicObject(piece, 11, 0, Tile(x, z, centre.height))
                    world.spawn(obj)
                    webs += obj
                }
                repeat(WEB_TICKS) {
                    if (!WildernessBossLife.active(npc, life)) return@repeat
                    webs.forEach { w ->
                        if (w.id == WEB_EDGE || !inDamagingWeb(centre, w.tile)) return@forEach
                        world.players.forEach { p ->
                            if (p.isOnline && !p.isDead() && p.tile.sameAs(w.tile)) {
                                npc.dealHit(target = p, minHit = 3.0, maxHit = 3.0, landHit = true, delay = 0, hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
                                p.runEnergy = (p.runEnergy - 10.0).coerceAtLeast(0.0)
                                p.sendRunEnergy(p.runEnergy.toInt())
                                p.alterPrayerPoints(-3)
                                p.graphic(GFX_WEB_HURT)
                            }
                        }
                    }
                    wait(1)
                }
                webs.forEach { world.remove(it) }
            }
        }

        /** Donor: a random tile of the move area more than 3 tiles away; else the middle. */
        private fun shuffle(npc: Npc) {
            val arena = arena(npc)
            val options = mutableListOf<Tile>()
            for (x in arena.moveX) for (z in arena.moveZ) {
                if (kotlin.math.abs(x - npc.tile.x) > 3 || kotlin.math.abs(z - npc.tile.z) > 3) options += Tile(x, z, npc.tile.height)
            }
            val dest = options.randomOrNull() ?: arena.middle
            npc.walkTo(dest)
        }
    }

    /** The spiderling: max 3 stab, and every attack drains 1 prayer (wiki), with the donor's message. */
    object SpiderlingCombat : CombatScript() {
        override val ids = intArrayOf(SPIDERLING, SPINDEL_SPIDERLING)

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                if (orphaned(npc)) {
                    summoned.remove(npc)
                    npc.world.remove(npc)
                    return
                }
                npc.facePawn(target)
                if (npc.moveToAttackRange(it, target, distance = 1, projectile = false)) {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.STAB, WeaponStyle.ACCURATE)
                    npc.animate(npc.combatDef.attackAnimation)
                    val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
                    npc.dealHit(target = target, maxHit = 3.0, landHit = land, delay = 1, hitType = HitType.MELEE)
                    (target as? Player)?.let { p ->
                        gg.rsmod.plugins.content.combat.audio.WildernessBossAudio.dispatch(npc, "attack.drain", p)
                        p.alterPrayerPoints(-1)
                        gg.rsmod.plugins.content.combat.audio.WildernessBossAudio.dispatch(npc, "impact.drain", p)
                    }
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }
    }
}
