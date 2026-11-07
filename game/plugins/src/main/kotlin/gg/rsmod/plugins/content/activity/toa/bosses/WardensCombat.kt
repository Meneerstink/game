package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.PlayerAttackRules
import gg.rsmod.plugins.content.activity.toa.ToaBossRooms
import gg.rsmod.plugins.content.activity.toa.ToaHeadIcons

/** Sources: Elidinis' Warden oldid15272445; Tumeken's Warden oldid15343238; Strategies oldid15392814 lines1429-1579.
 * T19: detailed two-source route wins over general ToA text. T20: detailed P3 intervals7/6/4/3 win over generic invocation table.
 * Geometry/cutscenes are adapter inputs; this file never edits the raid skeleton, chunks, rewards or player death rules.
 */
object WardensCombat : CombatScript() {
    override val ids: IntArray get() = listOf(ToaBossNpcs.OBELISK, ToaBossNpcs.ELIDINIS_P2, ToaBossNpcs.TUMEKEN_P2,
        ToaBossNpcs.ELIDINIS_P3, ToaBossNpcs.TUMEKEN_P3).mapNotNull(ToaBossNpcs::local).distinct().toIntArray()
    val OBELISK_STATS = ToaNpcStats(260, 200, 150, 100, 100, 100, 4, stab = 70, slash = 70, crush = 70, mage = 50, range = 20)
    val ELIDINIS_P2_STATS = ToaNpcStats(140, 300, 150, 100, 190, 190, 7, 0, 225, 225, 10, 0, 0, 70, 70, 70, -30, 70, 9662)
    val TUMEKEN_P2_STATS = ELIDINIS_P2_STATS.copy(speed = 8, strengthBonus = 25, magicStrength = 12, rangedStrength = 12)
    val P3_STATS = ToaNpcStats(880, 150, 150, 150, 150, 150, 7, 0, 230, 300, 40, 40, 40, 40, 40, 20, 20, 20, 9662)
    enum class Warden { ELIDINIS, TUMEKEN }
    enum class Phase { OBELISK, P2, CORE, P3, SIPHONS, ENRAGE }
    enum class Slam { EAST, WEST, CENTRE }
    enum class ObeliskSpecial { CONVERGING, WINDMILL, LIGHTNING }
    val OBELISK_CENTRE = Tile(3808, 5154, 1) // Obelisk Wiki LocLine, cache2686 npc11751 size3.
    // map15696 p1 loc45646..48:21wide north floor (x3926..3946,z5157..5165); Phantom Wiki centre3937,5142 south.
    // Strategies1546 calls the Warden's RIGHT east, so it faces NORTH. Never reverse the wave/enrage direction.
    // ONZEKER T02: exact NPC/landing not published; infer the5x5 Warden just south of a16deep playable floor.
    const val ARENA_HALF_WIDTH = 10
    const val ARENA_DEPTH = 16
    val P3_ORIGIN = Tile(3934, 5145, 1)
    val P3_CENTRE = Tile(3936, 5158, 1)
    val P3_ENTRY = Tile(3936, 5156, 1)
    fun phaseTwo(blockedWest: Boolean) = if (blockedWest) Warden.TUMEKEN else Warden.ELIDINIS
    fun opposite(warden: Warden) = if (warden == Warden.ELIDINIS) Warden.TUMEKEN else Warden.ELIDINIS
    fun slamInterval(@Suppress("UNUSED_PARAMETER") invocations: Set<String>): Int = 7 // Owner: no Overclocked/Insanity.
    /** Strategies1546: side hits include centre; the combined slam leaves the centre column safe. */
    fun slamHits(slam: Slam, offsetX: Int): Boolean = when (slam) {
        Slam.EAST -> offsetX >= 0
        Slam.WEST -> offsetX <= 0
        Slam.CENTRE -> offsetX != 0
    }
    fun siphonHp(living: Int): Int { require(living in 1..8); return if (living <= 3) 1 else living / 2 }
    fun coreExposure(hp: Int, max: Int): Int = when {
        hp.toLong() * 5 >= max.toLong() * 4 -> 21
        hp.toLong() * 5 >= max.toLong() * 3 -> 29
        else -> 37
    }
    /** 75% reduction to the additional-RL slope (2022 changelog). T04: final truncation, intermediate OSRS order unverified. */
    fun coreHp(c: ToaEncounterContext): Int = (4500L * (1000 + c.raidLevel) * ToaScale.groupTenths(c.partySize) / 10000).toInt()
    fun coreDamage(hit: Int) = hit * 5
    fun allowedStyle(warden: Warden, returnedCores: Int): CombatClass =
        if ((warden == Warden.ELIDINIS) == (returnedCores % 2 == 0)) CombatClass.MAGIC else CombatClass.RANGED
    /** Akkha's Phantom oldid15196965: ranged first, then three attacks of each style. */
    fun akkhaPhantomStyle(attackIndex: Int): CombatClass = if (attackIndex / 3 % 2 == 0) CombatClass.RANGED else CombatClass.MAGIC
    fun phantoms(warden: Warden) = if (warden == Warden.ELIDINIS) listOf(11777, 11776) else listOf(11774, 11775)
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        if (npc.attr[ToaEncounter.SESSION]?.closed != false) ToaEncounter.context(npc)?.let { ToaEncounter.attach(npc, it, ::Session) }
    }
    class Session(npc: Npc, input: ToaEncounterContext) : ToaBossSession(npc, input) {
        val rootNpc = npc
        override val stats = OBELISK_STATS
        override val minimumDefence: Int get() = if (phase in listOf(Phase.P3, Phase.SIPHONS, Phase.ENRAGE)) 120 else 60
        var phase = Phase.OBELISK
        var phaseTwoWarden = Warden.TUMEKEN
        var phaseThreeWarden = Warden.ELIDINIS
        var westBlocked = 0
        var eastBlocked = 0
        var returnedCores = 0
        var coreHealth = coreHp(context)
        val coreMaximum = coreHp(context)
        var core: Npc? = null
        var siphonStage = 0
        var slamIndex = 0
        var enraged = false
        val siphons = mutableSetOf<Npc>()
        private val reversedSiphons = mutableSetOf<Npc>()
        val usedObeliskSpecials = mutableSetOf<ObeliskSpecial>()
        var p1Director: (Session) -> Unit = { it.defaultP1() }
        var p2Director: (Session) -> Unit = { it.defaultP2() }
        var slamDirector: (Session, Slam) -> Unit = { s, slam -> s.defaultSlam(slam) }
        var siphonDirector: (Session) -> Unit = { it.defaultSiphons() }
        var enrageDirector: (Session) -> Unit = { it.defaultEnrage() }
        var phantomDirector: (Session, Int) -> Unit = { s, id -> s.defaultPhantom(id) }
        var phaseChanged: ((Phase) -> Unit)? = null
        var origin = Tile(npc.spawnTile)
        var centre = origin.transform(1, 1) // P1/P2 obelisk centre; P3 installs its separate north-facing floor geometry.
        var coreTile: Tile? = null // ONZEKER T02: relative to the active P2 form unless a measured tile is supplied.
        private val charging = mutableMapOf<Warden, Npc>()
        private val energy = mutableMapOf(Warden.ELIDINIS to 0, Warden.TUMEKEN to 0)
        private val chargedAttacks = mutableMapOf(Warden.ELIDINIS to 0, Warden.TUMEKEN to 0)
        private val phantomNpcs = mutableMapOf<Int, Npc>()
        private val phantomNext = mutableMapOf<Int, Int>()
        private val phantomAttacks = mutableMapOf<Int, Int>()
        private var p2Attacks = 0
        private var lastShieldHit = 0
        private var removedRows = 0
        private var enrageStart = 0
        private val floor = mutableMapOf<Tile, () -> Unit>()
        private val lightningStruck = mutableMapOf<Tile, Int>()
        private var amascut: Npc? = null
        private val uncreditedP2 = mutableSetOf<Npc>()
        private val raidRoom = input is ToaBossRooms.RaidContext
        private val standaloneRoom = input is StandaloneToaContext
        private val initialCentre = npc.spawnTile.transform(npc.getSize() / 2, npc.getSize() / 2)

        /** Same unrotated source->instance translation as the adapter, accounting for its centred obelisk spawn. */
        fun fromSource(tile: Tile): Tile = Tile(initialCentre.x + tile.x - OBELISK_CENTRE.x,
            initialCentre.z + tile.z - OBELISK_CENTRE.z, tile.height)

        // Wiki centres3794/3823,5154; cache2686 npc11746/11747 SIZE5, so subtract2 on each axis.
        fun chargingTile(warden: Warden): Tile = fromSource(Tile(
            if (warden == Warden.ELIDINIS) 3792 else 3821, 5152, initialCentre.height))

        /** Strategies1474: the active Warden comes out to pursue players. Cache2686 loc45605 blocks the charging plinth
         * (west3792..3796 / east3820..3824). Keeping P2 there blocks even ranged LOS and ejects an unreachable core.
         * T02: these are the nearest complete5x5 footprints on the room floor x3797..3819, not measured OSRS landing pins.
         */
        fun phaseTwoTile(warden: Warden): Tile = fromSource(Tile(
            if (warden == Warden.ELIDINIS) 3797 else 3815, 5152, initialCentre.height))

        fun transferToPhaseThree() {
            val party = players().toList() // Snapshot before moving root: eligibility checks use its plane.
            origin = fromSource(P3_ORIGIN); centre = fromSource(P3_CENTRE)
            npc.stopMovement(); npc.moveTo(origin)
            party.forEachIndexed { index, p ->
                p.stopMovement(); p.teleportTo(fromSource(P3_ENTRY).transform(index % 4 - 1, index / 4))
            }
        }

        override fun playerAttackRule(player: Player, target: Npc, type: HitType): PlayerAttackRules.Rule? = when {
            target === npc && phase == Phase.P2 && incomingStyle(type.id) == allowedStyle(phaseTwoWarden, returnedCores) -> PlayerAttackRules.Rule.ACCURATE
            target === core && phase == Phase.CORE && incomingStyle(type.id) == CombatClass.MELEE -> PlayerAttackRules.Rule.MAX_HIT
            target in siphons && phase == Phase.SIPHONS && target !in reversedSiphons && incomingStyle(type.id) == CombatClass.MELEE -> PlayerAttackRules.Rule.ACCURATE
            else -> null
        }

        private fun addEnergy(w: Warden) {
            energy[w] = energy.getValue(w) + 1
            if (energy.getValue(w) >= 8) { energy[w] = 0; wardenCharged(w) }
        }

        private fun defaultP1() {
            if (charging.isEmpty()) {
                listOf(Warden.ELIDINIS to 11746, Warden.TUMEKEN to 11747).forEach { (w, id) ->
                    spawn(id, chargingTile(w), OBELISK_STATS)?.let { child ->
                        child.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }
                        child.faceTile(origin); animate(9665, child) // Cache NPC_WARDENS_DORMANT01 for the charging statues.
                        charging[w] = child
                    }
                }
            }
            // ONZEKER T20: pulse every4ticks (cast9721); Ancient Haste3ticks; charge threshold8(measured9723clip).
            if (tick % 4 != 0) return
            animate(9721)
            Warden.values().forEach { w ->
                val west = w == Warden.ELIDINIS
                val lanes = if (tick < 24) listOf(0) else listOf(-1, 0, 1)
                lanes.forEach { lane ->
                    val start = origin.transform(if (west) -1 else 3, lane)
                    val direction = if (west) -1 else 1
                    val statue = charging[w]
                    val travel = if (statue == null) 7 else
                        kotlin.math.abs(start.x - (statue.tile.x + if (west) statue.getSize() - 1 else 0)).coerceAtLeast(1)
                    val orb = spawn(11769, start, OBELISK_STATS.copy(hp = 1), path = false, group = false)
                    fun pulse(left: Int) {
                        if (orb != null && !orb.isSpawned()) return
                        val tile = orb?.tile ?: start.transform(direction * (travel - left), 0)
                        val blocked = players().firstOrNull { sameTile(it.tile, tile) }
                        if (blocked != null) {
                            absorbOrb(blocked, west); orb?.let { remove(it) }
                            // ONZEKER T20: blocking slows but never stops charge (Strategies1429); one point per four absorbed orbs.
                            if ((if (west) westBlocked else eastBlocked) % 4 == 0) addEnergy(w)
                            return
                        }
                        if (left == 0) {
                            orb?.let { remove(it) }
                            addEnergy(w)
                            return
                        }
                        orb?.moveTo(tile.transform(direction, 0)); later(1) { pulse(left - 1) }
                    }
                    pulse(travel)
                }
            }
        }
        private fun wardenCharged(w: Warden) {
            val index = chargedAttacks.getValue(w); chargedAttacks[w] = index + 1
            if (index % 2 == 1) {
                message(if (w == Warden.ELIDINIS) "<col=0000ff>A large ball of energy is shot your way...</col>" else "<col=ff8000>A large ball of energy is shot your way...</col>")
                charging[w]?.let { child -> animate(9668, child); players().forEach { ToaBossFx.projectile(child, it, if (w == Warden.ELIDINIS) 2237 else 2238, true, 6) } }
                // ONZEKER T20:6tick slow shot from measured launch2+cast4; group damage caps30/~7 when handled properly.
                chargedShot(players(), w, 6) { p ->
                    val neighbours = players().count { it !== p && it.tile.isWithinRadius(p.tile, 1) }
                    if (w == Warden.ELIDINIS) minOf(90, 30 * (neighbours + 1)) else 30 / (neighbours + 1)
                }
            } else {
                // ONZEKER T02/T20: UFO positions use room-relative3x3grid; interval4 from obelisk cast clip; base26 P3 published max.
                charging[w]?.let { animate(9666, it) } // ONZEKER T02/T20: measured special clip for UFO discharge.
                val x = if (w == Warden.ELIDINIS) -5 else 5
                for (z in -12..0 step 4) {
                    val tile = centre.transform(x, z + 6); gfx(2235, tile)
                    later(4) { players().filter { it.tile.isWithinRadius(tile, 1) }.forEach { attack(it, ToaScale.damage(26, context, path = false), null, accuracy = false) } }
                }
            }
        }
        private fun defaultP2() {
            p2Attacks++
            val target = players().firstOrNull() ?: return
            npc.facePawn(target)
            if (phaseTwoWarden == Warden.TUMEKEN) follow(target)
            else if (distance(target.tile) > 4) npc.walkTo(target.tile)
            val max = if (phaseTwoWarden == Warden.ELIDINIS) 20 else 22
            if (p2Attacks % 5 == 0) { // ONZEKER T20: special every fifth7/8tick attack; exact selection schedule unpublished.
                animate(9661)
                if (coreHealth.toLong() * 100 > coreMaximum.toLong() * 55) {
                    val style = CombatClass.values().filter { it in listOf(CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC) }[npc.world.random(2)]
                    players().forEach { p ->
                        gg.rsmod.plugins.content.mechanics.prayer.Prayers.disableOverheads(p, 1)
                        ToaBossFx.projectile(npc, p, when (style) { CombatClass.MELEE -> 2204; CombatClass.RANGED -> 2206; else -> 2208 }, true, 4)
                        attack(p, ToaScale.damage(max, context, path = false), style, 4, accuracy = false)
                    }
                } else {
                    val tiles = players().map { Tile(it.tile) }
                    tiles.forEach { gfx(2210, it) }
                    later(4) { players().filter { p -> tiles.any { sameTile(it, p.tile) } }.forEach { p ->
                        p.stopMovement(); p.timers[gg.rsmod.game.model.timer.FROZEN_TIMER] = 4
                        p.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] = 4; ToaBossFx.spot(2211)?.let { p.graphic(it) }
                    } }
                }
            } else {
                val style = if (meleeReach(target.tile)) CombatClass.MELEE else if (npc.world.random(1) == 0) CombatClass.RANGED else CombatClass.MAGIC
                animate(when (style) { CombatClass.MELEE -> 9659; CombatClass.RANGED -> 9660; else -> 9661 })
                players().filter { style != CombatClass.MELEE || it === target }.forEach { p ->
                    if (style != CombatClass.MELEE) ToaBossFx.projectile(npc, p, if (style == CombatClass.RANGED) 2241 else 2213, style == CombatClass.MAGIC, 4)
                    // ONZEKER T01/T20: 4tick projectile clip9660/61; normal protection fully blocks, special alone disables prayers.
                    attack(p, ToaScale.damage(max, context, path = false), style, if (style == CombatClass.MELEE) 0 else 4)
                }
            }
            if (p2Attacks % 4 == 0) defaultObelisk()
        }
        private fun defaultObelisk() {
            val choices = ObeliskSpecial.values().filter { it !in usedObeliskSpecials }.ifEmpty { ObeliskSpecial.values().toList() }
            val kind = choices[npc.world.random(choices.lastIndex)]
            obeliskSpecial(kind) {
                gfx(when (kind) { ObeliskSpecial.CONVERGING -> 2199; ObeliskSpecial.WINDMILL -> 2200; else -> 2198 }, origin)
                // ONZEKER T20: warnings2ticks=9727clip; beams advance1tile/tick; max26 or39with Penetration (1.5fallback).
                fun hurt(tiles: Set<Tile>, delay: Int) { later(delay) { players().filter { p -> tiles.any { sameTile(it, p.tile) } }.forEach { p ->
                    attack(p, ToaScale.damage(26, context, path = false), null, accuracy = false)
                } } }
                when (kind) {
                    ObeliskSpecial.CONVERGING -> repeat(8) { i ->
                        val row = mutableSetOf<Tile>()
                        for (x in -8..8) { row += origin.transform(x, 8 - i); row += origin.transform(x, i - 8) }
                        hurt(row, 2 + i)
                        val column = mutableSetOf<Tile>()
                        for (z in -8..8) { column += origin.transform(8 - i, z); column += origin.transform(i - 8, z) }
                        hurt(column, 10 + i)
                    }
                    ObeliskSpecial.WINDMILL -> repeat(8) { i ->
                        val direction = gg.rsmod.game.model.Direction.ANGLED_ORDER[i]
                        val opposite = direction.getOpposite()
                        val tiles = (1..12).flatMap { n -> listOf(origin.transform(direction.getDeltaX() * n, direction.getDeltaZ() * n), origin.transform(opposite.getDeltaX() * n, opposite.getDeltaZ() * n)) }.toSet()
                        tiles.forEach { gfx(2198, it) }; hurt(tiles, 2 + i * 2)
                    }
                    ObeliskSpecial.LIGHTNING -> players().map { Tile(it.tile) }.forEach { t ->
                        val tiles = mutableSetOf<Tile>()
                        for (dx in -3..3) for (dz in -3..3) if (!(kotlin.math.abs(dx) == 2 && dz == 0 || kotlin.math.abs(dz) == 2 && dx == 0)) tiles += t.transform(dx, dz)
                        gfx(2198, t); hurt(tiles, 4)
                    }
                }
            }
        }
        private fun defaultSlam(slam: Slam) {
            // ONZEKER T02/T20: wave travels NORTH from the Warden over the floor; three rows/tick, sampled for late dodges.
            repeat(ARENA_DEPTH) { row -> later(1 + row / 3) {
                players().filter { p -> p.tile.z == centre.z - 8 + row && slamHits(slam, p.tile.x - centre.x) }.forEach { attack(it, ToaScale.damage(26, context, path = false), null, accuracy = false); gfx(2220, it.tile) }
            } }
        }
        private fun defaultSiphons() {
            val living = players().size.coerceAtLeast(1)
            // ONZEKER T02/T20: four solo/eight group siphons over the21wide room; timeout20ticks from core21 fallback.
            val tiles = if (living == 1) listOf(-5, -2, 2, 5).map { centre.transform(it, -3) }
                else listOf(-6, -2, 2, 6).flatMap { x -> listOf(centre.transform(x, -4), centre.transform(x, 2)) }
            spawnSiphons(tiles, 20, npc.getMaximumLifepoints() / 20) { s, left ->
                // ONZEKER T20: base26 per untouched siphon; travelling ring preserves documented skull-skip opportunity.
                repeat(12) { r -> s.later(r) { s.players().filter { kotlin.math.max(kotlin.math.abs(it.tile.x - centre.x), kotlin.math.abs(it.tile.z - centre.z)) == r }.forEach {
                    s.attack(it, ToaScale.damage(26 * left, context, path = false), null, accuracy = false)
                } } }
            }
        }
        private fun defaultPhantom(id: Int) {
            val spec = when (id) { 11774 -> ZebakCombat.STATS; 11775 -> BabaCombat.STATS; 11776 -> KephriCombat.STATS; else -> AkkhaCombat.STATS }
            spawn(id, origin.transform(if (phantomNpcs.isEmpty()) -8 else 8, 5), spec.copy(hp = 1, deathSeq = -1), path = false, group = false)?.let { child ->
                child.faceTile(centre)
                child.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }; phantomNpcs[id] = child
            }
        }
        private fun phantomsTick() {
            if (phase !in listOf(Phase.P3, Phase.ENRAGE, Phase.SIPHONS)) return
            phantomNpcs.forEach { (id, child) ->
                if (!child.isSpawned() || tick < (phantomNext[id] ?: tick)) return@forEach
                val interval = if (id == 11774) ZebakCombat.STATS.speed else 6
                phantomNext[id] = tick + interval
                val count = phantomAttacks[id] ?: 0; phantomAttacks[id] = count + 1
                when (id) {
                    11774, 11777 -> {
                        val style = if (id == 11777) akkhaPhantomStyle(count)
                            else if (npc.world.random(1) == 0) CombatClass.RANGED else CombatClass.MAGIC
                        animate(if (id == 11777) { if (style == CombatClass.MAGIC) 9774 else 9772 } else 9624, child)
                        players().forEach { p ->
                            ToaBossFx.projectile(child, p, if (id == 11777) { if (style == CombatClass.MAGIC) 2253 else 2255 } else if (style == CombatClass.MAGIC) 2176 else 2178, style == CombatClass.MAGIC, 3)
                            attack(p, ToaScale.damage(if (id == 11774) 16 else 18, context), style, 3,
                                if (id == 11777) 1 else 0, if (id == 11777) 10 else 1,
                                prayerAtLaunch = id == 11777, actor = child)
                        }
                    }
                    else -> {
                        val targets = players().map { Tile(it.tile) }; animate(if (id == 11776) 9575 else 9744, child)
                        targets.forEach { gfx(if (id == 11776) 2147 else 2250, it) }
                        // ONZEKER T01/T20: four-tick impact from the original boss's measured projectile clip.
                        later(4) { players().filter { p -> targets.any { sameTile(it, p.tile) } }.forEach {
                            attack(it, ToaScale.damage(24, context), if (id == 11776) CombatClass.MAGIC else null, accuracy = false,
                                protectedNumerator = if (id == 11776) 1 else 0, protectedDenominator = if (id == 11776) 3 else 1, actor = child)
                        } }
                    }
                }
            }
        }
        private fun defaultEnrage() {
            // ONZEKER T02/T20: floor rows every8ticks; lightning every2ticks from measured9722tile duration1.67. No Insanity variant.
            if ((tick - enrageStart) % 8 == 0 && removedRows < 15) {
                val z = centre.z + 7 - removedRows
                for (x in centre.x - ARENA_HALF_WIDTH..centre.x + ARENA_HALF_WIDTH) {
                    val t = Tile(x, z, origin.height)
                    floor.remove(t)?.invoke(); floor[t] = placeObject(45649, t)
                }
                removedRows++
            }
            players().filter { it.tile.z > centre.z + 7 - removedRows }.forEach { p ->
                val end = Tile(p.tile.x, centre.z + 7 - removedRows, p.tile.height)
                p.teleportTo(end); attack(p, ToaScale.damage(26, context, path = false), null, accuracy = false)
            }
            if (tick % 2 != 0) return
            val tiles = players().map { Tile(it.tile) }.toMutableSet()
            repeat(3) { tiles += centre.transform(npc.world.random(ARENA_HALF_WIDTH * 2) - ARENA_HALF_WIDTH,
                npc.world.random((15 - removedRows).coerceAtLeast(0)) - 8) }
            // Strategies1571: recently struck tiles have a grace period before another shadow can target them.
            // ONZEKER T20: two ticks is the measured9722 lightning-tile clip rounded up; exact server grace is untraced.
            tiles.removeAll { tile -> tick - (lightningStruck[tile] ?: -100) < 2 }
            lightningStruck.entries.removeAll { tick - it.value >= 2 }
            tiles.forEach { gfx(2198, it) }
            later(2) { tiles.forEach { lightningStruck[it] = tick; gfx(2197, it) }; players().filter { p -> tiles.any { sameTile(it, p.tile) } }.forEach { attack(it, ToaScale.damage(26, context, path = false), null, accuracy = false) } }
        }

        override fun configure() { super.configure(); applyStats(OBELISK_STATS); npc.scriptedDeath = true }
        override fun incoming(hit: Hit) {
            if (hit.source is Player && !eligible(hit.source as Player)) { hit.hitmarks.forEach { it.damage = 0 }; return }
            when (phase) {
                Phase.OBELISK -> if (hit.hitmarks.sumOf { it.damage } >= npc.getCurrentLifepoints()) {
                    hit.addAction { startPhaseTwo() }
                }
                Phase.P2 -> {
                    lastShieldHit = tick
                    val allowed = allowedStyle(phaseTwoWarden, returnedCores)
                    hit.hitmarks.forEach { mark ->
                        val style = incomingStyle(hit, mark.type)
                        if (style != allowed) mark.damage = 0
                        // Accuracy is resolved before the roll by PlayerAttackRules; never turn BLOCK into uncredited damage.
                        // ONZEKER T20: exact accuracy-bonus-to-damage conversion remains unpublished.
                    }
                    if (hit.hitmarks.sumOf { it.damage } >= npc.getCurrentLifepoints()) hit.addAction { exposeCore() }
                }
                Phase.CORE, Phase.SIPHONS -> hit.hitmarks.forEach { it.damage = 0 }
                Phase.P3 -> {
                    val threshold = maxOf(1, npc.getMaximumLifepoints() / 20)
                    var remaining = npc.getCurrentLifepoints()
                    hit.hitmarks.forEach { mark ->
                        if (!enraged && remaining - mark.damage < threshold) {
                            mark.damage = (remaining - threshold).coerceAtLeast(0)
                            enterEnrage(preserveIncoming = true)
                        }
                        remaining -= mark.damage
                    }
                    // A second hitsplat in the same attack may kill the newly healed enrage form.
                    // Do not leave scriptedDeath=true at0HP: that suppresses the real death/completion hook.
                    if (enraged && hit.hitmarks.sumOf { it.damage } >= npc.getCurrentLifepoints()) npc.scriptedDeath = false
                }
                Phase.ENRAGE -> if (hit.hitmarks.sumOf { it.damage } >= npc.getCurrentLifepoints()) npc.scriptedDeath = false
            }
        }
        override fun onTick() {
            phantomsTick()
            when (phase) {
                Phase.OBELISK -> p1Director(this)
                Phase.P2 -> {
                    // ONZEKER T20: Strategies says slowly restores when idle;1shield point/7ticks after four7tick intervals.
                    if (tick - lastShieldHit > 28 && tick % 7 == 0) heal(1)
                    if (tick >= nextAttack) {
                        p2Director(this)
                        nextAttack = tick + (if (phaseTwoWarden == Warden.ELIDINIS) 7 else 8)
                    }
                }
                Phase.CORE -> Unit
                Phase.SIPHONS -> players().filter { it.getCombatTarget() in siphons }.forEach { it.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY) }
                Phase.P3 -> {
                    if (siphonStage < 4 && npc.getCurrentLifepoints().toLong() * 100 <= npc.getMaximumLifepoints().toLong() * (80 - 20 * siphonStage)) {
                        siphonStage++; phase = Phase.SIPHONS; newPhase(); ToaHeadIcons.show(npc, 9); animate(9682)
                        fun chargingPose() {
                            if (phase != Phase.SIPHONS) return
                            animate(9683); later(4) { chargingPose() } // cache charging idle=120cycles=4ticks.
                        }
                        later(7) { chargingPose() } // cache9682 charge transition=210cycles=7ticks.
                        if (siphonStage in 2..3) phantomDirector(this, phantoms(phaseThreeWarden)[siphonStage - 2])
                        siphonDirector(this)
                        return
                    }
                    if (tick >= nextAttack) {
                        val slam = Slam.values()[slamIndex % 3]
                        animate(when (slam) { Slam.EAST -> 9676; Slam.WEST -> 9674; Slam.CENTRE -> 9678 })
                        slamDirector(this, slam)
                        slamIndex = (slamIndex + 1) % 3
                        nextAttack = tick + slamInterval(context.invocations)
                    }
                }
                Phase.ENRAGE -> enrageDirector(this)
            }
        }
        fun absorbOrb(p: Player, west: Boolean) {
            if (phase != Phase.OBELISK || !eligible(p)) return
            if (west) westBlocked++ else eastBlocked++
            // Absorption is exactly3 unscaled damage (Strategies1429), not a raid-level attack.
            attack(p, 3, null, accuracy = false, minHit = 3)
        }
        fun chargedShot(victims: List<Player>, warden: Warden, delay: Int, groupDamage: (Player) -> Int) {
            val solo = context.partySize == 1
            victims.forEach { p -> attack(p, if (solo) { if (warden == Warden.ELIDINIS) 45 else 30 } else groupDamage(p),
                null, delay, accuracy = false) }
        }
        private fun applyStats(spec: ToaNpcStats) {
            npc.combatDef = spec.definition(context, path = false).copy(completesEncounter = false,
                deathAnimation = listOf(ToaBossFx.seqFor(npc, spec.deathSeq)))
            applyBonuses()
            if (phase != Phase.OBELISK) ToaBossFx.weakness(npc, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.EARTH, 50)
            npc.combatDef.stats.forEachIndexed { i, level -> npc.stats.setMaxLevel(i, level); npc.stats.setCurrentLevel(i, level) }
            npc.setCurrentLifepoints(npc.getMaximumLifepoints())
        }
        /** Separate source identities keep ToaChest's per-NPC point multipliers correct. Never remove the adapter root. */
        private fun activate(osrs: Int, tile: Tile, spec: ToaNpcStats): Boolean {
            val previous = npc
            val actor = spawn(osrs, tile, spec, path = false) ?: return false
            npc = actor
            actor.attr[ToaEncounter.SESSION] = this
            actor.scriptedDeath = true
            actor.hitModifier = { hit ->
                if (hit.source is Player && !eligible(hit.source as Player)) hit.hitmarks.forEach { it.damage = 0 }
                else incoming(hit)
            }
            applyStats(spec)
            // Cache11753/54/56/57: melee+ranged(7) admits magic, melee+magic(8) admits ranged.
            // Strategies1542: P3 has no protection prayer; frame9 is used only while charging siphons.
            if (phase == Phase.P2) ToaHeadIcons.show(actor,
                if (allowedStyle(phaseTwoWarden, returnedCores) == CombatClass.MAGIC) 7 else 8)
            else ToaHeadIcons.clear(actor)
            if (phase == Phase.P2 && returnedCores >= 3) uncreditedP2 += actor
            if (previous !== rootNpc) remove(previous)
            else {
                rootNpc.setCurrentLifepoints(1)
                rootNpc.hitModifier = { hit -> hit.hitmarks.forEach { it.damage = 0 } }
            }
            return true
        }
        fun startPhaseTwo() {
            if (phase != Phase.OBELISK) return
            newPhase(); phase = Phase.P2
            // Tie selection is unpublished. A room adapter must supply a choice if both sides were disrupted equally.
            // ONZEKER T19: ties use the existing Tumeken default; all unequal choices follow detailed Strategies.
            if (westBlocked != eastBlocked) phaseTwoWarden = phaseTwo(westBlocked > eastBlocked)
            phaseThreeWarden = opposite(phaseTwoWarden)
            charging.values.toList().forEach { remove(it) }; charging.clear()
            children.toList().forEach { remove(it) }
            ToaBossNpcs.local(11752)?.let { rootNpc.setTransmogId(it) }
            activate(if (phaseTwoWarden == Warden.ELIDINIS) ToaBossNpcs.ELIDINIS_P2 else ToaBossNpcs.TUMEKEN_P2,
                phaseTwoTile(phaseTwoWarden), if (phaseTwoWarden == Warden.ELIDINIS) ELIDINIS_P2_STATS else TUMEKEN_P2_STATS)
            animate(9663); lastShieldHit = tick; nextAttack = tick + 13; phaseChanged?.invoke(phase) // measured wake9663=13ticks.
        }
        fun exposeCore() {
            if (phase != Phase.P2) return
            newPhase(); phase = Phase.CORE; animate(9670); npc.setCurrentLifepoints(1)
            ToaHeadIcons.clear(npc)
            val tile = coreTile ?: npc.tile.transform(1, -3)
            val duration = coreExposure(coreHealth, coreMaximum)
            core = spawn(if (phaseTwoWarden == Warden.TUMEKEN) ToaBossNpcs.CORE else 11771, tile, P3_STATS.copy(hp = coreMaximum, deathSeq = -1), path = false, group = false)?.also { child ->
                child.combatDef = child.combatDef.copy(lifepoints = coreMaximum); child.setCurrentLifepoints(coreHealth)
                child.scriptedDeath = true
                child.hitModifier = { hit ->
                    hit.hitmarks.forEach { mark ->
                        val p = hit.source as? Player
                        if (p != null && !eligible(p)) mark.damage = 0
                        // Melee max-hit was already rolled by PlayerAttackRules; keep the ordinary hit and its credit.
                        coreHealth = (coreHealth - coreDamage(mark.damage)).coerceAtLeast(0)
                    }
                    hit.addAction {
                        if (coreHealth == 0) startPhaseThree()
                        else if (child.isSpawned()) child.setCurrentLifepoints(coreHealth)
                    }
                }
            }
            later(duration) { returnCore() }
            phaseChanged?.invoke(phase)
        }
        fun returnCore() {
            if (phase != Phase.CORE) return
            core?.let { remove(it) }; core = null
            returnedCores++; newPhase(); phase = Phase.P2
            activate(if (phaseTwoWarden == Warden.ELIDINIS) ToaBossNpcs.ELIDINIS_P2 else ToaBossNpcs.TUMEKEN_P2,
                Tile(npc.tile), if (phaseTwoWarden == Warden.ELIDINIS) ELIDINIS_P2_STATS else TUMEKEN_P2_STATS)
            animate(9672); nextAttack = tick + 4; phaseChanged?.invoke(phase) // ONZEKER T20:4ticks from normal ranged clip.
        }
        fun startPhaseThree() {
            if (phase != Phase.CORE) return
            core?.let { remove(it) }; core = null; newPhase(); phase = Phase.P3
            // Root stays in the lower room until the final P3 actor dies.
            activate(if (phaseThreeWarden == Warden.ELIDINIS) ToaBossNpcs.ELIDINIS_P3 else ToaBossNpcs.TUMEKEN_P3,
                if (raidRoom) fromSource(P3_ORIGIN) else origin, P3_STATS)
            animate(9673); slamIndex = 0; nextAttack = tick + slamInterval(context.invocations)
            if (raidRoom) transferToPhaseThree()
            else {
                centre = origin.transform(2, 13)
                if (standaloneRoom) players().forEachIndexed { index, p ->
                    p.stopMovement(); p.teleportTo(centre.transform(index % 4 - 1, index / 4 - 2))
                }
            }
            // Let the raid transfer the party/NPC to the void and set origin/centre BEFORE building its floor and phantoms.
            phaseChanged?.invoke(phase)
            if (!active()) return
            npc.faceTile(centre)
            // The room's instance already contains square15696; no allocation or raid-skeleton mutation is needed.
            for (x in -ARENA_HALF_WIDTH..ARENA_HALF_WIDTH) for (z in -8..7) {
                val tile = centre.transform(x, z); floor[tile] = placeObject(45643, tile)
            }
            // Phantom Wiki centre3937,5142,size8(cache): south-west3933,5138, behind the north-facing Warden.
            amascut = spawn(11767, if (raidRoom) fromSource(Tile(3933, 5138, 1)) else origin.transform(-1, -7), P3_STATS.copy(hp = 1), path = false, group = false)?.also { a ->
                a.facePawn(npc)
                a.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }
            }
        }
        /** An adapter supplies each proved safe-tile set and the travelling impact tick (T20). */
        fun slam(safe: Set<Tile>, delay: Int, baseMax: Int = 26) {
            later(delay) { players().filter { p -> safe.none { sameTile(it, p.tile) } }.forEach {
                attack(it, ToaScale.damage(baseMax, context, path = false), null, accuracy = false)
            } }
        }
        fun spawnSiphons(tiles: List<Tile>, timeout: Int, reflectedDamage: Int, failed: (Session, Int) -> Unit) {
            require(phase == Phase.SIPHONS && timeout > 0 && tiles.isNotEmpty())
            val hp = siphonHp(players().size.coerceIn(1, 8))
            tiles.forEach { tile -> spawn(11772, tile, P3_STATS.copy(hp = hp), path = false, group = false)?.let { siphon ->
                siphon.combatDef = siphon.combatDef.copy(lifepoints = hp); siphon.setCurrentLifepoints(hp)
                siphon.scriptedDeath = true
                siphons += siphon
                siphon.hitModifier = { hit ->
                    val p = hit.source as? Player
                    hit.hitmarks.forEach { mark ->
                        val style = incomingStyle(hit, mark.type)
                        mark.damage = if (siphon !in reversedSiphons && p != null && eligible(p) && style == CombatClass.MELEE) 1 else 0
                    }
                    hit.addAction {
                        p?.timers?.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
                        if (siphon.getCurrentLifepoints() <= 0) {
                            reversedSiphons += siphon; siphon.setCurrentLifepoints(1); siphon.facePawn(npc)
                            ToaBossNpcs.local(11773)?.let { siphon.setTransmogId(it) }
                        }
                    }
                }
            } }
            later(timeout) {
                val left = siphons.count { it.isSpawned() && it !in reversedSiphons }
                siphons.toList().forEach { remove(it) }; siphons.clear(); reversedSiphons.clear()
                newPhase(); phase = Phase.P3
                ToaHeadIcons.clear(npc)
                if (left == 0) {
                    npc.setCurrentLifepoints((npc.getCurrentLifepoints() - reflectedDamage).coerceAtLeast(1))
                } else failed(this, left)
                slamIndex = 0 // Energy Siphon Wiki: base cycle resets even after a failed set; no Insanity variant.
                if (!enraged && npc.getCurrentLifepoints().toLong() * 20 <= npc.getMaximumLifepoints()) enterEnrage()
                else phaseChanged?.invoke(phase)
                nextAttack = tick + slamInterval(context.invocations)
            }
        }
        fun obeliskSpecial(kind: ObeliskSpecial, execute: () -> Unit) {
            if (usedObeliskSpecials.size == 3) usedObeliskSpecials.clear()
            require(kind !in usedObeliskSpecials) { "Obelisk specials cannot repeat before the full set" }
            usedObeliskSpecials += kind; execute()
        }
        fun enterEnrage(preserveIncoming: Boolean = false) {
            if (enraged) return
            enraged = true; phase = Phase.ENRAGE; newPhase(); animate(9684); enrageStart = tick
            ToaHeadIcons.clear(npc)
            amascut?.let { a ->
                ToaBossNpcs.local(11768)?.let { a.setTransmogId(it) }
                animate(9718, a) // NPC_AMASCUT_GIANT01_ENRAGE01, measured9ticks.
                fun suction() {
                    if (phase != Phase.ENRAGE || !a.isSpawned()) return
                    //11768 and11767 share a local model id, so transmog alone cannot select9717 instead of the9716 idle.
                    animate(9717, a); later(8) { suction() } // cache9717=240cycles=8ticks.
                }
                later(9) { suction() }
            }
            fun enragedIdle() {
                if (phase != Phase.ENRAGE) return
                animate(9685); later(4) { enragedIdle() } // shared-model11763/64 idle,120cycles=4ticks.
            }
            later(2) { enragedIdle() } //9684 transition=60cycles=2ticks.
            val restored = npc.getMaximumLifepoints() / 5
            if (preserveIncoming) npc.setCurrentLifepoints(npc.getCurrentLifepoints() + restored) else heal(restored)
            npc.stats.setMaxLevel(2, ToaScale.stat(180, context)); npc.stats.setCurrentLevel(2, ToaScale.stat(180, context))
            phaseChanged?.invoke(phase)
        }
        override fun close() {
            if (closed) return
            val dying = npc.takeIf { it !== rootNpc && it.isDead() && phase in listOf(Phase.P3, Phase.ENRAGE) }
            val vortex = amascut?.takeIf { dying != null && it.isSpawned() }
            // Wiki: P2 awards no points after its third down. Every down has its own scoped actor/damageMap.
            uncreditedP2.forEach { it.damageMap.reset() }; uncreditedP2.clear()
            if (dying != null) children.remove(dying) // Engine owns the complete33.3tick death animation and despawn.
            if (vortex != null) children.remove(vortex)
            siphons.clear(); reversedSiphons.clear(); floor.clear(); lightningStruck.clear(); charging.clear()
            phantomNpcs.clear(); phantomNext.clear(); phantomAttacks.clear()
            super.close(); npc.scriptedDeath = false
            if (dying != null) rootNpc.world.queue {
                var elapsed = 0
                while (dying.isSpawned() && rootNpc.isSpawned()) {
                    if (vortex?.isSpawned() == true && elapsed++ % 8 == 0) animate(9717, vortex)
                    wait(1)
                }
                vortex?.takeIf { it.isSpawned() }?.let { rootNpc.world.remove(it) }
                completeAfterDespawn(dying)
                if (standaloneRoom && rootNpc.isSpawned()) rootNpc.world.remove(rootNpc)
            }
        }
        override fun finishStandalone(root: Npc) {
            if (root === rootNpc && npc !== root && npc.isDead() && npc.isSpawned() && phase in listOf(Phase.P3, Phase.ENRAGE)) return
            super.finishStandalone(root)
        }
        internal fun completeAfterDespawn(finalActor: Npc) {
            if (closed && phase in listOf(Phase.P3, Phase.ENRAGE) && finalActor === npc && finalActor.isDead() && !finalActor.isSpawned() && rootNpc.isSpawned()) {
                rootNpc.attr[ToaBossRooms.ENCOUNTER_DONE] = true
            }
        }
    }
}
