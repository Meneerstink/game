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
import gg.rsmod.plugins.content.combat.poison
import gg.rsmod.plugins.content.combat.PlayerAttackRules

/** Kephri oldid15200302; Strategies oldid15392814 lines947-987; individual scarab/egg pages in 04-kephri-brontekst.
 * Source correction: the shielded infobox says 150 HP, not the unsupported 200 in 04-kephri.txt's summary.
 * T13-T15: retain uncertain heavy-hit boundary, exact heal formulas, second swarm timeline and larvae counts as inputs.
 */
object KephriCombat : CombatScript() {
    override val ids: IntArray get() = listOfNotNull(ToaBossNpcs.local(ToaBossNpcs.KEPHRI)).toIntArray()
    val STATS = ToaNpcStats(150, 0, 0, 80, 125, 0, 6, magicBonus = 125, magicStrength = 50,
        stab = 60, slash = 300, crush = 100, mage = 200, range = 300, deathSeq = 9582,
        species = setOf(gg.rsmod.plugins.api.NpcSpecies.KALPHITE)) // Wiki infobox attribute: kalphite; Keris family checks this tag.
    private val SCARAB_SPECIES = setOf<Any>(gg.rsmod.plugins.api.NpcSpecies.KALPHITE)
    val SOLDIER_STATS = ToaNpcStats(40, 75, 80, 80, 100, 95, 7, 100, 250, 350, 55, 0, 65, 15, 250, 30, -15, 250, 9590, SCARAB_SPECIES)
    val SPITTING_STATS = ToaNpcStats(40, 1, 80, 80, 100, 95, 3, 0, 250, 350, 55, 0, 65, 15, 250, 30, 250, 125, 9590, SCARAB_SPECIES)
    val ARCANE_STATS = ToaNpcStats(40, 75, 80, 80, 100, 95, 6, 0, 250, 350, 55, 0, 65, 15, 250, 30, 75, 50, 9597, SCARAB_SPECIES)
    val AGILE_STATS = ToaNpcStats(30, 60, 20, 5, 30, 40, 4, 0, 150, 150, 25, 5, 25, deathSeq = 9597, species = SCARAB_SPECIES)
    val EGG_STATS = ToaNpcStats(35, 1, 1, 0, 1, 1, 4, stab = -100, slash = -100, crush = -100, mage = -100, range = -100)
    val SWARM_STATS = EGG_STATS.copy(hp = 10, deathSeq = 9608)
    enum class Phase { SHIELD, RECHARGE, FINAL }
    fun swarmCount(intermission: Int): Int = when (intermission) { 1 -> 28; 2 -> 24; else -> 0 }
    fun healingSwarms(intermission: Int): Int = when (intermission) { 1 -> 18; 2 -> 14; else -> 0 }
    fun firstSwarmInterval(index: Int): Int { require(index in 0..27); return when (index) { in 0..3 -> 4; in 4..7 -> 3; in 8..11 -> 2; else -> 1 } }
    fun arcaneHitCount(size: Int) = 2 + size // Current individual Arcane page: misses count; thralls/burn do not.
    fun overlords(breakNumber: Int, @Suppress("UNUSED_PARAMETER") more: Boolean = false): Set<Int> = when (breakNumber) {
        1 -> setOf(ToaBossNpcs.SPITTING)
        2 -> setOf(ToaBossNpcs.SOLDIER, ToaBossNpcs.ARCANE)
        else -> emptySet()
    }
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        if (npc.attr[ToaEncounter.SESSION]?.closed != false) ToaEncounter.context(npc)?.let { ToaEncounter.attach(npc, it, ::Session) }
    }
    class Session(npc: Npc, input: ToaEncounterContext) : ToaBossSession(npc, input) {
        override val stats = STATS
        override val minimumDefence = 60 // Strategies951.
        var phase = Phase.SHIELD
        var shieldBreaks = 0
        var shield = ToaScale.hp(150, context)
        var dungIndex = 0
        val dung = mutableSetOf<Tile>()
        val eggs = mutableMapOf<Npc, Boolean>()
        val overlords = mutableMapOf<Int, Npc>()
        val agile = mutableSetOf<Npc>()
        var autoDirector: (Session) -> Unit = { s -> s.fireball(s.players().map { Tile(it.tile) }, 4,
            24, 1, 3) } // ONZEKER T01/T14: seq9575=4ticks; base24 from infobox.
        var specialDirector: (Session) -> Unit = { it.defaultSpecial() }
        var rechargeDirector: (Session) -> Unit = { it.defaultRecharge() }
        var dungDirector: (Session, List<Player>) -> Unit = { s, victims -> s.defaultDung(victims) }
        val centre = npc.spawnTile.transform(2, 2)
        // ONZEKER T02: relative corner coordinates; directions are explicitly documented in Strategies961-973.
        var minionTiles: Map<Int, Tile> = mapOf(ToaBossNpcs.ARCANE to centre.transform(6, -6),
            ToaBossNpcs.SPITTING to centre.transform(6, 6), ToaBossNpcs.SOLDIER to centre.transform(6, 0))
        val cleanupDung = mutableListOf<() -> Unit>()
        private var autoCount = 0
        override fun configure() {
            super.configure()
            ToaBossFx.weakness(npc, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.FIRE, 40)
            ToaBossFx.spot(2141)?.let { npc.graphic(it) }
        }
        private val arcaneCharge = mutableMapOf<Npc, Int>()
        private val maxHitTargets = mutableSetOf<Npc>()
        private fun defaultSpecial() {
            if (++autoCount % 4 != 0) return // ONZEKER T14: specials alternate every four6tick autos.
            agile.removeAll { !it.isSpawned() || it.isDead() }
            val limit = 2 // ONZEKER T15: base two dark eggs; exact active scarab cap remains unpublished.
            if (autoCount % 8 == 4 || agile.size >= limit) defaultDung(dungTargets()) else {
                val placements = linkedMapOf<Tile, Boolean>()
                // ONZEKER T02/T15: two base light/dark pairs evenly distributed around Kephri; no invocation variant.
                repeat(limit) { i ->
                    placements[centre.transform(if (i % 2 == 0) -6 else 6, i * 3 - 5)] = true
                    placements[centre.transform(i * 3 - 5, if (i % 2 == 0) -6 else 6)] = false
                }
                incubation(placements, 6, 24, limit) // ONZEKER T14: hatch after6tick attack interval; explosion published boss max24.
            }
        }
        private fun defaultDung(victims: List<Player>) {
            animate(9577) // ONZEKER T02/T14: named3tick radial magic clip; exact Dung Strike action binding untraced.
            val marked = victims.associateWith { Tile(it.tile) }
            // ONZEKER T14: warning3ticks=seq9577; stun2seconds rounds up to4game ticks.
            dungStrike(victims, 3, 4, destination = { p ->
                val start = marked.getValue(p)
                val dx = (start.x - centre.x).compareTo(0); val dz = (start.z - centre.z).compareTo(0)
                val direction = gg.rsmod.game.model.Direction.between(start, start.transform(dx, if (dz == 0 && dx == 0) -1 else dz))
                var end = Tile(p.tile)
                repeat(10) {
                    if (npc.world.collision.canStep(end, direction)) end = end.transform(direction.getDeltaX(), direction.getDeltaZ())
                }
                // ONZEKER T14: prefer the furthest reachable tile along the outward ray, then nearest free landing within10.
                if (!sameTile(end, p.tile) && end !in dung) end else
                    (-10..10).flatMap { x -> (-10..10).map { z -> start.transform(x, z) } }
                        .filter { it !in dung && !npc.world.collision.isClipped(it) && !sameTile(it, start) }
                        .minByOrNull { it.getDistance(start) }
            }, tiles = { p ->
                val start = marked.getValue(p); val end = p.tile
                val dx = (end.x - start.x).compareTo(0); val dz = (end.z - start.z).compareTo(0)
                (0..3).map { start.transform(dx * it, (if (dx == 0 && dz == 0) -1 else dz) * it) }
            }, place = { t -> placeObject(45149, t) }) // TOA_KEPHRI_DUNG01; cache2686 ObjectID1, local64599 in90-locmap.
        }
        private fun defaultRecharge() {
            val count = swarmCount(shieldBreaks)
            val cap = ToaScale.hp(150, context) * 115 / 100 // ONZEKER T14: Wiki says approximately115%.
            shield = 0
            val edges = listOf(centre.transform(8, 8), centre.transform(8, -8), centre.transform(0, -9),
                centre.transform(-8, -8), centre.transform(-8, 8), centre.transform(0, 9))
            var at = 0
            val start = npc.world.random(5)
            val schedule = List(count) { i ->
                // ONZEKER T14: second24swarm phase truncates the published28schedule, no measured skip probability.
                at += firstSwarmInterval(i)
                at to edges[(start + i) % edges.size]
            }
            val heal = (ToaScale.hp(150, context) / (10 + context.partySize - 1)).coerceAtLeast(1)
            swarms(schedule, List(count) { heal }, { tile -> tile.getDistance(centre).coerceAtLeast(1) }, cap)
            // ONZEKER T14: phase ends after last spawn+8travel ticks+7tick measured wakeup9581.
            later(at + 15) { finishRecharge(shield) }
        }
        private fun minionsTick() {
            agile.removeAll { !it.isSpawned() || it.isDead() }
            agile.forEach { scarab -> players().minByOrNull { it.tile.getDistance(scarab.tile) }?.let { p ->
                if (p.tile.isWithinRadius(scarab.tile, 2)) {
                    val dx = (scarab.tile.x - p.tile.x).compareTo(0); val dz = (scarab.tile.z - p.tile.z).compareTo(0)
                    val d = gg.rsmod.game.model.Direction.between(scarab.tile, scarab.tile.transform(dx, dz))
                    // Agile Scarab43: retreats at RUNNING speed. Movement is independent of its four-tick ranged attack.
                    var end = Tile(scarab.tile)
                    repeat(2) { if (d != gg.rsmod.game.model.Direction.NONE && npc.world.collision.canStep(end, d)) end = end.transform(dx, dz) }
                    if (!sameTile(end, scarab.tile)) scarab.walkTo(end, gg.rsmod.game.model.MovementQueue.StepType.FORCED_RUN)
                }
                if (tick % AGILE_STATS.speed == 0) {
                    scarab.facePawn(p)
                    animate(9594, scarab)
                    ToaBossFx.projectile(scarab, p, 2154, false, 2)
                    attack(p, 5, CombatClass.RANGED, 2, actor = scarab)
                }
            } }
            overlords.toMap().forEach { (id, scarab) ->
                if (!scarab.isSpawned() || scarab.isDead()) { overlords.remove(id); arcaneCharge.remove(scarab); return@forEach }
                when (id) {
                    ToaBossNpcs.SPITTING -> {
                        val victims = players().filter { distance(it.tile, scarab.tile, scarab.getSize()) <= 8 }
                        if (victims.isEmpty()) players().minByOrNull { it.tile.getDistance(scarab.tile) }?.let { scarab.walkTo(it.tile) }
                        if (tick % SPITTING_STATS.speed == 0 && victims.isNotEmpty()) {
                            scarab.facePawn(victims.first())
                            animate(9588, scarab)
                            victims.forEach { p ->
                                ToaBossFx.projectile(scarab, p, 2152, false, 2)
                                // ONZEKER T14: ranged formula lvl95/bonus0 gives10; prayer reduction follows documented Kephri two-thirds.
                                attack(p, ToaScale.damage(10, context), CombatClass.RANGED, 2, 1, 3, actor = scarab,
                                    after = { damage -> if (damage > 0 && eligible(p)) p.poison(6) })
                            }
                        }
                    }
                    ToaBossNpcs.SOLDIER -> {
                        if (tick % SOLDIER_STATS.speed == 0) players().minByOrNull { it.tile.getDistance(scarab.tile) }?.let { p ->
                            scarab.facePawn(p)
                            scarab.walkTo(p.tile)
                            if (actorMeleeReach(scarab, p.tile)) { animate(9587, scarab); attack(p, ToaScale.damage(17, context), CombatClass.MELEE, 1, actor = scarab) } // Soldier Scarab infobox max17.
                        }
                        if (tick % 8 == 0) { // ONZEKER T14: ~5sec rounds to8ticks;5-7linear by current HP as Strategies says.
                            val amount = 5 + 2 * scarab.getCurrentLifepoints() / scarab.getMaximumLifepoints().coerceAtLeast(1)
                            // Specific Soldier Scarab article: its swarm restores shield OR health, respectively.
                            // This active minion heal is separate from Kephri's removed passive final-phase regeneration.
                            if (phase == Phase.FINAL) heal(amount) else { shield = minOf(ToaScale.hp(150, context) * 115 / 100, shield + amount); if (phase == Phase.SHIELD) npc.setCurrentLifepoints(shield) }
                            gfx(2150, scarab.tile)
                        }
                    }
                    ToaBossNpcs.ARCANE -> {
                        val charge = (arcaneCharge[scarab] ?: 0) + 1; arcaneCharge[scarab] = charge
                        if (charge == 1) { animate(9593, scarab); gfx(2149, scarab.tile) }
                        // ONZEKER T14: four6tick charge intervals; published well over65 fallback66base max.
                        if (charge >= 24) {
                            players().forEach { attack(it, ToaScale.damage(66, context), CombatClass.MAGIC, 3, 1, 3, accuracy = false, actor = scarab) }
                            arcaneCharge[scarab] = 0
                            val route = listOf(centre.transform(6, -6), centre.transform(-6, 6), centre.transform(0, -7), centre.transform(6, 6))
                            val index = route.indexOfFirst { sameTile(it, scarab.tile) }.coerceAtLeast(0)
                            scarab.moveTo(route[(index + 1) % route.size])
                        }
                    }
                }
            }
        }

        override fun incoming(hit: Hit) {
            if (phase == Phase.RECHARGE) { hit.hitmarks.forEach { it.damage = 0 }; return }
            if (phase == Phase.FINAL) return
            hit.hitmarks.forEach { mark ->
                mark.damage = mark.damage.coerceIn(0, shield)
                shield -= mark.damage
                if (shield == 0 && phase == Phase.SHIELD) {
                    // Reserve 1 HP so the engine does not invoke real death for a broken shield.
                    val reserve = if (shieldBreaks == 2) ToaScale.hp(80, context) else 1
                    npc.setCurrentLifepoints(npc.getCurrentLifepoints() + reserve)
                    breakShield(preserveIncoming = true)
                }
            }
        }
        fun breakShield(preserveIncoming: Boolean = false) {
            if (phase != Phase.SHIELD) return
            shieldBreaks++; newPhase()
            eggs.keys.toList().forEach { remove(it) }; eggs.clear()
            if (shieldBreaks >= 3) {
                phase = Phase.FINAL
                npc.combatDef = stats.copy(hp = 80).definition(context).copy(deathAnimation = listOf(ToaBossFx.seqFor(npc, stats.deathSeq)))
                applyBonuses()
                npc.graphic(-1)
                ToaBossFx.weakness(npc, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.FIRE, 35)
                if (!preserveIncoming) npc.setCurrentLifepoints(npc.getMaximumLifepoints())
                npc.stats.setCurrentLevel(2, npc.stats.getMaxLevel(2))
                gfx(2143, npc.tile)
                dungDirector(this, players())
            } else {
                phase = Phase.RECHARGE; animate(9579)
                ToaBossFx.spot(2142)?.let { npc.graphic(it) } // Cache KEPHRI_SHIELD_DEAD.
                overlords(shieldBreaks).forEach { id ->
                    val tile = minionTiles[id] ?: when (id) {
                        ToaBossNpcs.ARCANE -> centre.transform(6, -6)
                        ToaBossNpcs.SPITTING -> centre.transform(6, 6)
                        else -> centre.transform(6, 0)
                    } // ONZEKER T02: preserve the documented corner/direction fallback for incomplete room geometry.
                    spawnOverlord(id, tile)
                }
                rechargeDirector(this)
            }
        }
        override fun onTick() {
            minionsTick()
            // Strategies0969: bomber scarabs continue the dodgeable auto during shield recharge.
            if (tick >= nextAttack) {
                autoDirector(this)
                if (phase == Phase.SHIELD) specialDirector(this)
                nextAttack = tick + stats.speed
            }
        }
        fun finishRecharge(restoredShield: Int) {
            require(restoredShield >= 0)
            if (phase != Phase.RECHARGE) return
            newPhase(); shield = restoredShield; phase = Phase.SHIELD
            children.toList().filter { it.id == ToaBossNpcs.local(ToaBossNpcs.SWARM) }.forEach { remove(it) }
            npc.setCurrentLifepoints(maxOf(1, shield)); animate(9581); gfx(2144, npc.tile)
            nextAttack = tick + stats.speed
            later(stats.speed) { if (phase == Phase.SHIELD) ToaBossFx.spot(2141)?.let { npc.graphic(it) } }
            if (shield == 0) breakShield()
        }
        /** Tile-targeted, dodgeable fireball; prayer does not make the tile safe. */
        fun fireball(targetTiles: List<Tile>, impact: Int, baseMax: Int,
            protectionNumerator: Int, protectionDenominator: Int) {
            require(impact >= 0 && baseMax >= 0)
            animate(9575)
            targetTiles.forEach { gfx(2147, it) }
            later(impact) {
                targetTiles.forEach { gfx(2148, it) }
                val radius = 0
                players().filter { p -> targetTiles.any { p.tile.isWithinRadius(it, radius) } }.forEach {
                    attack(it, ToaScale.damage(baseMax, context), CombatClass.MAGIC, accuracy = false,
                        protectedNumerator = protectionNumerator, protectedDenominator = protectionDenominator)
                }
            }
        }
        fun dungTargets(all: Boolean = phase == Phase.FINAL): List<Player> {
            val live = players()
            if (live.isEmpty() || all) return live
            val amount = 1
            val selected = (0 until amount).map { live[(dungIndex + it) % live.size] }
            dungIndex = (dungIndex + amount) % live.size
            return selected
        }
        fun dungStrike(victims: List<Player>, delay: Int, stunTicks: Int,
            destination: (Player) -> Tile?, tiles: (Player) -> List<Tile>, place: (Tile) -> (() -> Unit)) {
            require(delay >= 0 && stunTicks >= 0)
            victims.filter { eligible(it) }.forEach { p ->
                ToaBossFx.spot(2146)?.let { p.graphic(it) }
                later(delay) {
                    if (!eligible(p)) return@later
                    val end = destination(p)
                    if (end == null) {
                        fun trapped() { if (!eligible(p) || destination(p) != null) return
                            attack(p, 5, null, accuracy = false, minHit = 5); later(1) { trapped() } }
                        attack(p, 5, null, accuracy = false, minHit = 5); later(1) { trapped() }
                        return@later
                    }
                    require(p.tile.isWithinRadius(end, 10))
                    p.message("Kephri throws you back!"); p.stopMovement()
                    p.timers[gg.rsmod.game.model.timer.FROZEN_TIMER] = stunTicks; p.teleportTo(end)
                    tiles(p).forEach { tile -> if (dung.add(tile)) { gfx(2145, tile); cleanupDung += place(tile) } }
                }
            }
        }
        fun incubation(placements: Map<Tile, Boolean>, hatchDelay: Int, explosionMax: Int, agileLimit: Int) {
            require(hatchDelay >= 0 && agileLimit >= 0)
            animate(9578)
            placements.forEach { (tile, dark) -> spawn(if (dark) 11729 else 11728, tile, EGG_STATS)?.let { egg ->
                eggs[egg] = dark; maxPlayerHits(egg)
                later(hatchDelay) {
                    eggs.remove(egg)
                    if (!egg.isSpawned() || egg.isDead()) return@later
                    // Specific Egg(Tombs of Amascut) article resolves Strategies0957's ambiguous plural:
                    // light eggs explode3x3, whereas dark eggs hatch Agile Scarabs.
                    if (dark) {
                        agile.removeAll { !it.isSpawned() || it.isDead() }
                        if (agile.size < agileLimit && dung.none { sameTile(it, tile) }) {
                            // Wiki: Agile stats do NOT scale with raid level.
                            spawn(ToaBossNpcs.AGILE, tile, AGILE_STATS, path = false, group = false)?.let { scarab ->
                                // ONZEKER T14 inference: infobox scaledhp=Yes retains ordinary party HP;
                                // Strategies0957 only exempts the Agile's stats from RAID LEVEL, not party size.
                                scarab.combatDef = AGILE_STATS.definition(ToaFixedContext(context.players, context.partySize)).copy(completesEncounter = false,
                                    deathAnimation = listOf(ToaBossFx.seqFor(scarab, AGILE_STATS.deathSeq)))
                                applyBonuses(scarab)
                                scarab.combatDef.stats.forEachIndexed { i, level -> scarab.stats.setMaxLevel(i, level); scarab.stats.setCurrentLevel(i, level) }
                                scarab.setCurrentLifepoints(scarab.getMaximumLifepoints()); agile += scarab
                            }
                        }
                    } else players().filter { it.tile.isWithinRadius(tile, 1) }.forEach { attack(it, ToaScale.damage(explosionMax, context), null, accuracy = false) }
                    remove(egg)
                }
            } }
        }
        fun maxPlayerHits(child: Npc) {
            maxHitTargets += child
            child.hitModifier = { hit -> if (hit.source is Player && !eligible(hit.source as Player)) hit.hitmarks.forEach { it.damage = 0 } }
        }
        override fun playerAttackRule(player: Player, target: Npc, type: HitType): PlayerAttackRules.Rule? =
            if (target in maxHitTargets) PlayerAttackRules.Rule.MAX_HIT else null
        fun spawnOverlord(id: Int, tile: Tile): Npc? {
            // ONZEKER T14: reuse a surviving same-type overlord rather than create an unbounded duplicate across shield breaks.
            overlords[id]?.takeIf { it.isSpawned() && !it.isDead() }?.let { return it }
            val spec = when (id) { ToaBossNpcs.SOLDIER -> SOLDIER_STATS; ToaBossNpcs.SPITTING -> SPITTING_STATS; ToaBossNpcs.ARCANE -> ARCANE_STATS; else -> return null }
            return spawn(id, tile, spec)?.also { child ->
                child.combatDef = spec.definition(context, scaleDefence = false).copy(completesEncounter = false,
                    deathAnimation = listOf(ToaBossFx.seqFor(child, spec.deathSeq)))
                applyBonuses(child)
                ToaBossFx.weakness(child, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.FIRE, 50)
                child.stats.setMaxLevel(2, spec.defence); child.stats.setCurrentLevel(2, spec.defence)
                overlords[id] = child
                if (id == ToaBossNpcs.ARCANE) arcaneRelocation(child, listOf(centre.transform(6, -6), centre.transform(-6, 6),
                    centre.transform(0, -7), centre.transform(6, 6)), 39) // T13: individual Arcane page/Mod Ash explicitly says39 or more; prefer over older Strategies40.
            }
        }
        fun arcaneRelocation(scarab: Npc, route: List<Tile>, heavyThreshold: Int) {
            require(route.size == 4 && heavyThreshold in 39..41)
            var hits = 0
            scarab.hitModifier = { hit ->
                if (hit.source is Player && !eligible(hit.source as Player)) hit.hitmarks.forEach { it.damage = 0 }
                else if (hit.source is Player && hit.hitmarks.any { incomingStyle(it.type) != null || it.type == HitType.BLOCK.id && hit.sourceMaxHit >= 0 }) {
                    val combatMarks = hit.hitmarks.filter { incomingStyle(it.type) != null || it.type == HitType.BLOCK.id && hit.sourceMaxHit >= 0 }
                    hits += combatMarks.size // includes misses; excludes thralls and untyped burn ticks.
                    // T13: specific Arcane article/Mod Ash says39+ in ONE hit; do not sum the older Strategies4tick window.
                    if (hits >= arcaneHitCount(context.partySize) || combatMarks.any { it.damage >= heavyThreshold }) {
                        hits = 0; val index = (route.indexOfFirst { sameTile(it, scarab.tile) }.coerceAtLeast(0) + 1) % route.size
                        arcaneCharge[scarab] = 0; scarab.moveTo(route[index]); scarab.graphic(-1)
                    }
                }
            }
        }
        fun swarms(schedule: List<Pair<Int, Tile>>, heals: List<Int>, arrivalDelay: (Tile) -> Int, cap: Int) {
            require(schedule.size == swarmCount(shieldBreaks) && heals.size == schedule.size)
            require(cap >= 0 && heals.all { it >= 0 })
            schedule.forEachIndexed { index, (at, tile) -> later(at) {
                spawn(ToaBossNpcs.SWARM, tile, SWARM_STATS)?.let { swarm ->
                    maxPlayerHits(swarm)
                    ToaBossFx.weakness(swarm, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.FIRE, 40) // Scarab Swarm infobox/changelog25June2025.
                    animate(9605, swarm); swarm.walkTo(npc.tile) // cache NPC_SWARM01_SCARAB01_SPAWN01.
                    fun arrive(left: Int) {
                        if (!swarm.isSpawned() || swarm.isDead()) return
                        if (distance(swarm.tile) <= 1) {
                            if (index < healingSwarms(shieldBreaks)) { animate(9607, swarm); shield = minOf(cap, shield + heals[index]) }
                            remove(swarm)
                        } else if (left <= 0) remove(swarm)
                        else { swarm.walkTo(npc.tile); later(1) { arrive(left - 1) } }
                    }
                    later(arrivalDelay(tile)) { arrive(8) } // ONZEKER T14:8extra ticks uses measured wakeup rounded7+one travel tick.
                }
            } }
        }
        override fun close() {
            cleanupDung.forEach { it() }; cleanupDung.clear(); dung.clear(); eggs.clear(); overlords.clear(); agile.clear(); maxHitTargets.clear(); arcaneCharge.clear()
            super.close()
        }
    }
}

/** Fixed combat levels and raid-level HP; group HP remains an independent source input. */
internal class ToaFixedContext(override val players: List<Player>, override val partySize: Int = 1) : ToaEncounterContext {
    override val raidLevel = 0; override val pathLevel = 0; override val invocations = emptySet<String>()
}
