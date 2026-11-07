package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import kotlin.math.abs

/** Ba-Ba oldid15200300; Strategies oldid15392814 lines1338-1355; Boulder page in 03-baba-brontekst.
 * June2025: ordinary melee fully protected; blockers fully null Rock Throw. T16 keeps unpublished cadence/formula out.
 */
object BabaCombat : CombatScript() {
    override val ids: IntArray get() = listOfNotNull(ToaBossNpcs.local(ToaBossNpcs.BABA)).toIntArray()
    val STATS = ToaNpcStats(380, 150, 160, 80, 100, 0, 6, strengthBonus = 26,
        stab = 80, slash = 160, crush = 240, mage = 280, range = 200, deathSeq = 9754, meleeStyle = gg.rsmod.game.model.combat.StyleType.CRUSH)
    val BABOON_STATS = ToaNpcStats(10, 0, 0, 50, 50, 80, 4, rangedBonus = 80, rangedStrength = 20,
        stab = 10, slash = 50, crush = 50, mage = 50, range = 50, deathSeq = 9754)
    fun maxHit(c: ToaEncounterContext) = ToaScale.damage(24, c)
    fun rollingThreshold(stage: Int, hp: Int, max: Int): Boolean = stage < 2 && hp.toLong() * 3 <= max.toLong() * (2 - stage)
    fun crackedHp(@Suppress("UNUSED_PARAMETER") path: Int): Int = 25 // Owner: no path level variants.
    /** Boulder page oldid15197004 table: half the ordinary group HP slope, no raid-level multiplier. */
    fun boulderHp(size: Int): Int { require(size in 1..8); return listOf(25, 36, 47, 55, 62, 70, 77, 85)[size - 1] }
    // ONZEKER T16: interpolate published1-4baboons as one per two party members, capped4.
    fun baboonCount(size: Int) = ((size + 1) / 2).coerceIn(1, 4)
    // ONZEKER T16: base interval from two measured2tick special clips; no Boulderdash variant.
    fun rollingInterval(@Suppress("UNUSED_PARAMETER") boulderdash: Boolean) = 4
    fun validCracks(columns: List<Int>): Boolean = columns.size == 10 && columns.first() in 1..3 &&
        columns.all { it in 0..4 } && columns.zipWithNext().all { abs(it.first - it.second) <= 2 }
    const val BOULDER = 11782 // RuneLite NpcID TOA_BABA_BOULDER
    const val CRACKED_BOULDER = 11783
    const val BABOON_MAX = 1 // ONZEKER T16: individual Baboon Wiki infobox explicitly publishes "1?"; prefer that over a guessed formula.
    fun blockerDamage(kind: BlockerKind) = when (kind) { BlockerKind.DEBRIS -> 10; BlockerKind.SARCOPHAGUS -> 30; BlockerKind.BABOON -> 12; BlockerKind.PLAYER -> 0 }
    /** Cache2686 map15188, 40-loc-placements.tsv: all eight sarcophagi, retaining their facing. */
    val COFFINS_OSRS = listOf(3803, 3808, 3813, 3818).flatMap { x -> listOf(Tile(x, 5400) to 3, Tile(x, 5416) to 1) }.toMap()
    enum class BlockerKind { DEBRIS, SARCOPHAGUS, BABOON, PLAYER }
    data class Blocker(val tile: Tile, val kind: BlockerKind, val size: Int = 1, val player: Player? = null, val damage: (Int) -> Unit)
    data class RollingRow(val tiles: List<Tile>, val cracked: Int)
    fun overlaps(a: Tile, sizeA: Int, b: Tile, sizeB: Int): Boolean = a.height == b.height &&
        a.x < b.x + sizeB && a.x + sizeA > b.x && a.z < b.z + sizeB && a.z + sizeA > b.z
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        if (npc.attr[ToaEncounter.SESSION]?.closed != false) ToaEncounter.context(npc)?.let { ToaEncounter.attach(npc, it, ::Session) }
    }
    class Session(npc: Npc, input: ToaEncounterContext) : ToaBossSession(npc, input) {
        override val stats = STATS
        override val minimumDefence = 60 // Wiki Ba-Ba55: base80 ->60.
        var rollingStage = 0
        var rolling = false
        var target: Player? = null
        var rollingDirector: (Session) -> Unit = { it.defaultRolling() }
        var specialDirector: (Session) -> Unit = { it.defaultSpecial() }
        val origin = Tile(npc.spawnTile)
        var meleePastSpawnLine: (Player) -> Boolean = { it.tile.z >= origin.z + 12 }
        val baboons = mutableListOf<Npc>()
        val debris = mutableListOf<Npc>()
        val boulders = mutableListOf<Npc>()
        val blockers = mutableListOf<Blocker>()
        private val coffins = linkedMapOf<Tile, Int>()
        // ONZEKER T02: debug-relative placement; the raid adapter maps COFFINS_OSRS into its instance before configure().
        var coffinTiles: Map<Tile, Int> = listOf(-10, -5, 0, 5).flatMap { x ->
            listOf(origin.transform(x, -8) to 3, origin.transform(x, 8) to 1)
        }.toMap()
        private val coffinCleanup = mutableMapOf<Tile, () -> Unit>()
        private var autoCount = 0
        private var specialIndex = 0
        private var underTicks = 0
        override fun configure() {
            super.configure()
            // ONZEKER T02/T16: room-relative edge positions; sarcophagus HP uses three documented30damage Rock Throws.
            coffinTiles.keys.forEach { coffins[it] = 90 }
            coffins.keys.forEach { t -> coffinCleanup[t] = placeObject(45752, t, coffinTiles.getValue(t)) }
        }
        private fun damageCoffin(t: Tile, amount: Int) {
            val hp = coffins[t] ?: return
            if (hp <= 0) return
            coffins[t] = (hp - amount).coerceAtLeast(0)
            if (coffins[t] == 0) { coffinCleanup.remove(t)?.invoke(); coffinCleanup[t] = placeObject(45753, t, coffinTiles.getValue(t)) }
        }
        private fun defaultSpecial() {
            if (++autoCount % 4 != 0) return // ONZEKER T16: four6tick autos between specials, fallback to published attack speed.
            when (specialIndex++ % 3) {
                0 -> {
                    val radius = 2
                    val centre = target?.let { npc.getFrontFacingTile(it) } ?: npc.getCentreTile()
                    val tiles = mutableSetOf<Tile>()
                    for (dx in -radius..radius) for (dz in -radius..radius) {
                        if (if (radius == 2) dx == 0 || dz == 0 else dx * dx + dz * dz <= 10) tiles += centre.transform(dx, dz)
                    }
                    // The swipe animation supplies its frame sounds; no unrelated baboon magic spot on the ground.
                    // ONZEKER T16: seq9742=2ticks supplies warning; base24 published melee max for unquantified special.
                    shockwave(tiles, 2) { ToaScale.damage(24, context) }
                }
                1 -> {
                    animate(9744)
                    rockfall(4, 24,
                        listOf(origin.transform(-2, -2), origin.transform(6, -2)))
                    later(stats.speed) { animate(9749); rebuildBlockers(); rockThrow(6, 24) }
                }
                else -> summonBaboons(List(baboonCount(context.partySize)) { i -> origin.transform(if (i % 2 == 0) -3 else 7, i * 2 - 4) })
            }
        }
        private fun rebuildBlockers() {
            blockers.clear()
            debris.filter { it.isSpawned() && !it.isDead() }.forEach { d -> blockers += Blocker(Tile(d.tile), BlockerKind.DEBRIS, size = d.getSize()) { amount ->
                d.setCurrentLifepoints((d.getCurrentLifepoints() - amount).coerceAtLeast(0)); if (d.isDead()) remove(d)
            } }
            baboons.filter { it.isSpawned() && !it.isDead() }.forEach { b -> blockers += Blocker(Tile(b.tile), BlockerKind.BABOON) { amount ->
                b.setCurrentLifepoints((b.getCurrentLifepoints() - amount).coerceAtLeast(0)); if (b.isDead()) { remove(b) }
            } }
            coffins.filterValues { it > 0 }.keys.forEach { t -> blockers += Blocker(t, BlockerKind.SARCOPHAGUS) { amount -> damageCoffin(t, amount) } }
            players().forEach { p -> blockers += Blocker(Tile(p.tile), BlockerKind.PLAYER, player = p) {} }
        }
        private fun defaultRolling() {
            // Strategies1353: the scream destroys the previous Rockfall debris.
            debris.toList().forEach { remove(it) }; debris.clear()
            npc.stopMovement(); animate(9748); npc.moveTo(origin.transform(0, 14))
            players().forEach { p ->
                var tile = Tile(p.tile)
                repeat((p.tile.z - (origin.z - 9)).coerceAtLeast(0)) {
                    if (npc.world.collision.canStep(tile, Direction.SOUTH)) tile = tile.transform(0, -1)
                }
                p.stopMovement(); p.teleportTo(tile)
            }
            var lane = 1 + npc.world.random(2)
            val rows = List(10) { index ->
                if (index > 0) {
                    val legal = (0..4).filter { abs(it - lane) <= 2 }; lane = legal[npc.world.random(legal.lastIndex)]
                }
                RollingRow(List(5) { col -> origin.transform(-5 + col * 3, 11) }, lane)
            }
            // ONZEKER T02/T16: five3wide lanes over24tiles; falling/rolling clip-derived cadence, pending OSRS room trace.
            later(2) { rollingStones(rows, rollingInterval(false), 1, Direction.SOUTH, 24, 24) }
        }
        private fun minionsTick() {
            baboons.toList().forEach { b ->
                if (b.isDead() || !b.isSpawned()) { baboons.remove(b); return@forEach }
                if (tick % BABOON_STATS.speed != 0) return@forEach
                val coffin = coffins.filterValues { it > 0 }.keys.minByOrNull { it.getDistance(b.tile) }
                if (coffin != null && tick % (BABOON_STATS.speed * 3) == 0) {
                    b.walkTo(coffin)
                    if (b.tile.isWithinRadius(coffin, 1)) damageCoffin(coffin, 20)
                } else players().minByOrNull { it.tile.getDistance(b.tile) }?.let { p ->
                    ToaBossFx.projectile(b, p, 2244, false, 2)
                    animate(9744, b); attack(p, ToaScale.damage(BABOON_MAX, context), CombatClass.RANGED, 2, actor = b)
                }
            }
            if (tick % stats.speed == 0) coffins.filterValues { it == 0 }.keys.forEach { t ->
                val p = players().minByOrNull { it.tile.getDistance(t) } ?: return@forEach
                val target = Tile(p.tile); gfx(2246, target)
                later(2) {
                    val radius = 0
                    players().filter { it.tile.isWithinRadius(target, radius) }.forEach { victim ->
                        // ONZEKER T16: unquantified sarcophagus hit uses the published Ba-Ba max24.
                        attack(victim, ToaScale.damage(24, context), null, accuracy = false)
                    }
                }
            }
        }

        override fun incoming(hit: Hit) {
            if (rolling && hit.source is Player && hit.hitmarks.any { incomingStyle(it.type) == CombatClass.MELEE } && meleePastSpawnLine(hit.source as Player)) endRolling()
        }
        override fun onTick() {
            if (!rolling && rollingThreshold(rollingStage, npc.getCurrentLifepoints(), npc.getMaximumLifepoints())) {
                rollingStage++; rolling = true; newPhase()
                rollingDirector(this)
            }
            minionsTick()
            if (rolling) return
            val live = players()
            val tank = target?.takeIf { it in live } ?: live.firstOrNull() ?: return
            target = tank; npc.facePawn(tank); follow(tank)
            underTicks = if (distance(tank.tile) == 0) underTicks + 1 else 0
            if (underTicks >= 2 && tick >= nextAttack) {
                // ONZEKER T16: accelerated stomp uses measured2tick9742clip,1tick warning, published base24 max.
                val tiles = mutableSetOf<Tile>()
                for (dx in 0 until npc.getSize()) for (dz in 0 until npc.getSize()) tiles += npc.tile.transform(dx, dz)
                shockwave(tiles, 1) { ToaScale.damage(24, context) }
                nextAttack = tick + 2; return
            }
            if (tick >= nextAttack && meleeReach(tank.tile)) {
                animate(9743)
                attack(tank, maxHit(context), CombatClass.MELEE, delay = 1)
                nextAttack = tick + stats.speed
                specialDirector(this)
                // Strategies1338: subsequent aggro is based on current HP; stable room order resolves equal HP (ONZEKER T16).
                target = live.minByOrNull { it.getCurrentLifepoints() }
            }
        }
        /** One fixed target tile per player, captured at cast; not a homing falling rock. */
        fun rockfall(impactDelay: Int, baseDamage: Int, debrisTiles: List<Tile>) {
            require(impactDelay >= 0 && debrisTiles.size == 2)
            val tiles = players().map { Tile(it.tile) }
            // Both debris shadows are telegraphed as well as the player-targeted rocks (Strategies1342).
            (tiles + debrisTiles).forEach { gfx(2250, it) }
            later(impactDelay) {
                players().filter { p -> tiles.any { sameTile(it, p.tile) } }.forEach { attack(it, ToaScale.damage(baseDamage, context), null, accuracy = false) }
                // Debris HP is 10 per room participant (Strategies1342), not the generic raid HP multiplier.
                debrisTiles.forEach { tile ->
                    players().filter { p -> sameTile(tile, p.tile) }.forEach { attack(it, ToaScale.damage(baseDamage, context), null, accuracy = false) }
                    players().filter { p -> !sameTile(tile, p.tile) && overlaps(tile, 3, p.tile, 1) }.forEach { p ->
                        val d = Direction.between(tile.transform(1, 1), p.tile).takeIf { it != Direction.NONE } ?: Direction.SOUTH
                        var end = Tile(p.tile)
                        repeat(3) { if (overlaps(tile, 3, end, 1) && npc.world.collision.canStep(end, d)) end = end.transform(d.getDeltaX(), d.getDeltaZ()) }
                        p.stopMovement(); p.teleportTo(end)
                    }
                    spawn(11784, tile, STATS.copy(hp = 10 * players().size.coerceAtLeast(1)), path = false, group = false)?.let { piece ->
                        piece.combatDef = piece.combatDef.copy(lifepoints = 10 * players().size.coerceAtLeast(1))
                        piece.setCurrentLifepoints(piece.getMaximumLifepoints()); debris += piece
                    }
                }
            }
        }
        /** ~6 ticks is only an estimate in the Wiki (T16), so the caller supplies the measured delay. */
        fun rockThrow(delay: Int, baseDamage: Int) {
            val targets = players()
            later(delay) {
                rebuildBlockers()
                val cover = targets.filter { eligible(it) }.associateWith { p ->
                    blockers.filter { distance(p.tile, it.tile, it.size) <= 1 && (it.kind != BlockerKind.PLAYER || it.player !== p) }
                }
                cover.forEach { (p, near) ->
                    if (near.isEmpty()) attack(p, ToaScale.damage(baseDamage, context), null, accuracy = false)
                }
                // Snapshot cover before the volley; each incoming rock spends blocker HP (debris has10HP per participant).
                cover.values.flatten().forEach { it.damage(blockerDamage(it.kind)) }
                if (debris.none { it.isSpawned() && !it.isDead() }) {
                    rockfall(4, baseDamage, listOf(origin.transform(-2, -2), origin.transform(6, -2)))
                }
            }
        }
        fun shockwave(tiles: Set<Tile>, delay: Int, damageAt: (Player) -> Int) {
            require(delay >= 0)
            animate(9742)
            later(delay) { players().filter { p -> tiles.any { sameTile(it, p.tile) } }.forEach { attack(it, damageAt(it), null, accuracy = false) } }
        }
        /** Counts/positions are source inputs: T16 only proves 1-4, not a team-size formula. */
        fun summonBaboons(tiles: List<Tile>) {
            require(tiles.size in 1..4)
            baboons.removeAll { !it.isSpawned() || it.isDead() }
            if (baboons.isNotEmpty()) return
            tiles.forEach { tile -> spawn(ToaBossNpcs.BABOON, tile, BABOON_STATS, path = false, group = false)?.let { baboon ->
                baboon.combatDef = baboon.combatDef.copy(lifepoints = 10)
                baboon.setCurrentLifepoints(10)
                guaranteeMaxHit(baboon)
                baboons += baboon
            } }
        }
        fun guaranteeMaxHit(child: Npc, rangedOnly: Boolean = false) {
            child.hitModifier = { hit ->
                val p = hit.source as? Player
                if (p != null && !eligible(p)) hit.hitmarks.forEach { it.damage = 0 }
                else if (p != null) hit.hitmarks.forEach { mark ->
                    if (!rangedOnly || (incomingStyle(mark.type) ?: gg.rsmod.plugins.content.combat.CombatConfigs.getCombatClass(p)) == CombatClass.RANGED) {
                        mark.damage = hit.sourceMaxHit.coerceAtLeast(0)
                    }
                }
            }
        }
        fun rollingStones(rows: List<RollingRow>, spawnInterval: Int, stepInterval: Int, direction: Direction,
            travelSteps: Int, baseDamage: Int, hp: Int = boulderHp(context.partySize)) {
            require(rows.all { it.tiles.size == 5 } && validCracks(rows.map { it.cracked }))
            require(spawnInterval > 0 && stepInterval > 0 && travelSteps > 0 && hp > 0)
            rows.forEachIndexed { index, row -> later(index * spawnInterval) {
                if (!rolling) return@later
                animate(9749)
                row.tiles.forEachIndexed { col, tile ->
                    val cracked = col == row.cracked
                    spawn(if (cracked) CRACKED_BOULDER else BOULDER, tile, STATS.copy(hp = hp), path = false, group = false)?.let { rock ->
                        rock.combatDef = rock.combatDef.copy(lifepoints = hp); rock.setCurrentLifepoints(hp)
                        if (cracked) guaranteeMaxHit(rock, rangedOnly = true)
                        boulders += rock
                        roll(rock, direction, travelSteps, stepInterval, baseDamage)
                    }
                }
            } }
            later(rows.lastIndex * spawnInterval + travelSteps * stepInterval) { endRolling() }
        }
        private fun roll(rock: Npc, direction: Direction, steps: Int, interval: Int, damage: Int) {
            if (!rolling || !rock.isSpawned() || rock.isDead()) return
            if (steps == 0) { boulders.remove(rock); remove(rock); return }
            val next = rock.tile.transform(direction.getDeltaX(), direction.getDeltaZ())
            rock.moveTo(next)
            (baboons + debris).filter { overlaps(it.tile, it.getSize(), next, rock.getSize()) }.toList().forEach { remove(it); baboons.remove(it); debris.remove(it) }
            players().filter { p -> overlaps(p.tile, 1, next, rock.getSize()) }.forEach { p ->
                attack(p, ToaScale.damage(damage, context), null, accuracy = false)
                var destination = Tile(p.tile)
                repeat(4) {
                    if (npc.world.collision.canStep(destination, direction)) destination = destination.transform(direction.getDeltaX(), direction.getDeltaZ())
                }
                p.stopMovement(); p.teleportTo(destination)
                // Mind the Gap uses the actual room pit predicate, not a guessed z coordinate.
            }
            later(interval) { roll(rock, direction, steps - 1, interval, damage) }
        }
        fun endRolling() {
            if (!rolling) return
            rolling = false; newPhase()
            animate(9751)
            boulders.toList().forEach { remove(it) }; boulders.clear()
            nextAttack = tick + stats.speed
        }
        override fun close() { coffinCleanup.clear(); coffins.clear(); baboons.clear(); debris.clear(); boulders.clear(); blockers.clear(); super.close() }
    }
}
