package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*

/** Zebak oldid15351537; Strategies oldid15392814 lines807-839; Jug oldid15139696; Blood Cloud oldid15196975.
 * T17: use the detailed Strategies blood-heal rule (2x), retain the Blood Blitz conflict in the dossier.
 * T18/T01: special layout/cadence, acid ramp and projectile split timings require measured inputs.
 */
object ZebakCombat : CombatScript() {
    override val ids: IntArray get() = listOfNotNull(ToaBossNpcs.local(ToaBossNpcs.ZEBAK)).toIntArray()
    val STATS = ToaNpcStats(580, 250, 140, 70, 100, 120, 7, 160, 215, 220, 100, 16, 30,
        160, 160, 260, 200, 110, 9634)
    val CROC_STATS = ToaNpcStats(30, 150, 60, 100, 30, 1, 7, strengthBonus = 100,
        stab = 150, slash = 350, crush = 350, mage = 5, range = 150, deathSeq = 2038) // Crocodile (ToA) infobox in05-brontekst.
    val thresholds = listOf(85, 70, 55, 40)
    fun enraged(hp: Int, max: Int) = hp.toLong() * 4 < max
    fun waterRadius(upsetStomach: Boolean) = if (upsetStomach) 1 else 2
    fun acidRadius(upsetStomach: Boolean) = if (upsetStomach) 2 else 1
    fun narrowedGap(base: Int, path: Int) = (base - minOf(path / 2, 2)).coerceAtLeast(1)
    fun bloodHeal(dealt: Int) = dealt * 2
    // ONZEKER T18: Wiki says faster every two path levels; reduce the published7/4 intervals by one at2 and4.
    fun attackInterval(enraged: Boolean, path: Int) = ((if (enraged) 4 else 7) - minOf(path / 2, 2)).coerceAtLeast(2)
    fun maxHit(style: CombatClass, c: ToaEncounterContext) = ToaScale.damage(if (style == CombatClass.MELEE) 38 else 16, c)
    enum class Special { ROAR, WAVES }
    data class Rock(val tile: Tile, var hp: Int = 150)
    /** A rock blocks the ray away from Zebak for the next three floor tiles. Direction is room geometry supplied by the adapter. */
    fun behindRock(player: Tile, rock: Tile, direction: Direction): Boolean =
        player.height == rock.height && (1..3).any { n -> player.x == rock.x + direction.getDeltaX() * n && player.z == rock.z + direction.getDeltaZ() * n }
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        if (npc.attr[ToaEncounter.SESSION]?.closed != false) ToaEncounter.context(npc)?.let { ToaEncounter.attach(npc, it, ::Session) }
    }
    class Session(npc: Npc, context: ToaEncounterContext) : ToaBossSession(npc, context) {
        override val stats = STATS
        override val minimumDefence = 50 // Wiki Zebak base70 ->50 drain floor.
        var isEnraged = false
        var thresholdIndex = 0
        var nextSpecial = Special.ROAR
        var firstSpecialVerified = true // ONZEKER T18: first choice is roar; thereafter the published alternating order.
        val pending = ArrayDeque<Special>()
        val acid = mutableSetOf<Tile>()
        val rocks = mutableListOf<Rock>()
        val jugs = mutableSetOf<Npc>()
        private val specialNpcs = mutableSetOf<Npc>()
        var tail: Npc? = null
        var autoSelector: (Session) -> ToaAutoAttack = { it.defaultAuto() }
        var specialDirector: (Session, Special) -> Unit = { s, special -> s.defaultSpecial(special) }
        var bloodDirector: (Session) -> Unit = { it.defaultBlood() }
        var acidDirector: (Session, Player) -> Unit = { s, p -> s.acidHit(p) }
        var pathAttackInterval: (Session) -> Int = { attackInterval(it.isEnraged, it.context.pathLevel) }
        val centre = npc.spawnTile.transform(4, -8)
        private val acidExposure = mutableMapOf<Player, Int>()
        private var specialUntil = 0
        private var autoCount = 0
        private var waveSet = 0
        private val bleeding = mutableMapOf<Player, Pair<Int, Tile>>()
        private val water = mutableMapOf<Player, Pair<Npc?, Int>>()
        var onPlatform: (Tile) -> Boolean = { it.height == centre.height && it.x in centre.x - 8..centre.x + 8 && it.z in centre.z - 8..centre.z + 8 }
        override fun configure() {
            super.configure()
            // ONZEKER T02: relative ramp positions for debug arena; real raid adapter supplies the room geometry.
            placeObject(45509, centre.transform(-9, -5)); placeObject(45509, centre.transform(9, -5))
            tail = spawn(ToaBossNpcs.ZEBAK_TAIL, npc.spawnTile.transform(0, 9), STATS.copy(hp = 1))?.also { t ->
                t.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }
            }
        }
        private fun defaultAuto(): ToaAutoAttack {
            val close = players().any { meleeReach(it.tile) }
            val style = if (close) CombatClass.MELEE else if (npc.world.random(1) == 0) CombatClass.RANGED else CombatClass.MAGIC
            // ONZEKER T01/T18: melee chip follows Akkha75% reduction; ranged/magic flight uses the4tick attack clip.
            return ToaAutoAttack(if (style == CombatClass.MELEE) 38 else 16, style, if (style == CombatClass.MELEE) 1 else 4,
                if (style == CombatClass.MELEE) 1 else 0, if (style == CombatClass.MELEE) 4 else 1,
                projectile = if (style == CombatClass.MAGIC) 2176 else if (style == CombatClass.RANGED) 2178 else -1)
        }
        private fun acidHit(p: Player) {
            // ONZEKER T18: escalating tile damage2,4,6... uses the published poison2 as starting value; exact ramp unpublished.
            val exposure = (acidExposure[p] ?: 0) + 1
            acidExposure[p] = exposure
            p.poison(2)
            attack(p, exposure * 2, null, accuracy = false, minHit = exposure * 2)
        }
        private fun defaultSpecial(special: Special) {
            jugs.toList().forEach { remove(it) }; jugs.clear()
            // ONZEKER T02: room-relative layouts until OSRS spawn traces exist; central jug can always clear a rock's safe strip.
            val rockTiles = listOf(-4, 0, 4).map { centre.transform(it, 0) }
            listOf(-6, -3, 0, 3, 6).forEach { x ->
                val tile = centre.transform(x, -3)
                spawn(11735, tile, STATS.copy(hp = 1), path = false, group = false)?.let { jug ->
                    jugs += jug; specialNpcs += jug
                    jug.hitModifier = { h ->
                        h.hitmarks.forEach { it.damage = 0 }
                        h.addAction { if (jug in jugs) shatterJug(jug) }
                    }
                }
            }
            listOf(-6, -3, 0, 3, 6).forEach { x -> addAcid(centre.transform(x, -1)); addAcid(centre.transform(x, -5)) }
            if (special == Special.ROAR) {
                rocks.clear(); rocks += rockTiles.map(::Rock)
                rockTiles.forEach { tile -> spawn(11737, tile, STATS.copy(hp = 150), path = false, group = false)?.let { r ->
                    specialNpcs += r; r.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }
                    later(4 * stats.speed + 27) { remove(r) }
                } }
                // Wiki: four regular attacks to position jugs; roar clip9628=270cycles=9ticks (92-seqticks).
                roar(4 * stats.speed, 9, 50, Direction.SOUTH) // ONZEKER T18: max50 fallback;9tick clip spaces the three roars.
                later(4 * stats.speed) { nextAttack = tick + 27 }
                specialUntil = tick + 4 * stats.speed + 27
            } else {
                val direction = if (waveSet++ % 2 == 0) Direction.NORTH else Direction.SOUTH
                val gap = narrowedGap(5, context.pathLevel)
                // ONZEKER T18: three waves,7ticks apart, one row/tick; wave gap from published path2/4 reductions.
                repeat(3) { index -> later(index * stats.speed) {
                    val row = (-8..8).filter { it !in -(gap / 2)..(gap - 1) / 2 }.map { x ->
                        centre.transform(x, if (direction == Direction.NORTH) -8 else 8)
                    }
                    wave(row, direction, 17, 1, 8) { p, tile -> enterWater(p, tile) }
                } }
                specialUntil = tick + 3 * stats.speed + 17
            }
        }
        private fun defaultBlood() {
            // ONZEKER T17: every fourth auto, alternating barrage and clouds; Strategies2x heal takes priority over old Blitz text.
            if (autoCount % 4 != 0) return
            if (autoCount % 8 == 0) {
                val amount = if (inv("Blood Thinners")) 3 else 1
                repeat(amount) { i ->
                    val tile = centre.transform(i * 2 - amount + 1, -6)
                    val victim = players().minByOrNull { it.tile.getDistance(tile) }?.takeIf { it.tile.isWithinRadius(tile, 8) }
                    // ONZEKER T17: hidden cloud HP uses same arena's published crocodile HP30; exact cloud HP remains unmeasured.
                    spawn(if (inv("Arterial Spray")) 11742 else 11743, tile, STATS.copy(hp = 30), path = false, group = false)?.let {
                        bloodCloud(it, victim, if (inv("Arterial Spray")) 4 else 2)
                    }
                }
            } else bloodBarrage(players(), 11, 3)
        }
        override fun onTick() {
            bleeding.toMap().forEach { (p, state) ->
                if (!eligible(p) || tick >= state.first) bleeding.remove(p)
                else {
                    val steps = p.tile.getDistance(state.second).coerceAtMost(2)
                    if (steps > 0) attack(p, steps * 2, null, accuracy = false, minHit = steps * 2)
                    bleeding[p] = state.first to Tile(p.tile)
                }
            }
            water.toMap().forEach { (p, state) ->
                if (!eligible(p) || onPlatform(p.tile)) leaveWater(p)
                else {
                    p.setVarp(173, 0); p.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] = 2
                    state.first?.takeIf { it.isSpawned() && !it.isDead() }?.let { croc ->
                        croc.walkTo(p.tile)
                        if (tick % CROC_STATS.speed == 0 && croc.tile.isWithinRadius(p.tile, 2)) {
                            animate(2039, croc)
                            attack(p, ToaScale.damage(18, context), CombatClass.MELEE, 1, 1, 4, actor = croc)
                        }
                    }
                }
            }
            if (!isEnraged && enraged(npc.getCurrentLifepoints(), npc.getMaximumLifepoints())) {
                isEnraged = true; pending.clear(); newPhase(); specialUntil = tick
                specialNpcs.toList().forEach { remove(it) }; specialNpcs.clear(); jugs.clear(); rocks.clear()
                animate(9628); tail?.let { animate(9629, it) }
                npc.combatDef = npc.combatDef.copy(attackSpeed = attackInterval(true, context.pathLevel))
            }
            while (thresholdIndex < thresholds.size && npc.getCurrentLifepoints().toLong() * 100 <= npc.getMaximumLifepoints().toLong() * thresholds[thresholdIndex]) {
                if (!isEnraged) { pending.addLast(nextSpecial); nextSpecial = if (nextSpecial == Special.ROAR) Special.WAVES else Special.ROAR }
                thresholdIndex++
            }
            players().filter { p -> acid.any { sameTile(it, p.tile) } }.forEach {
                // Poison is documented; its escalation formula is not. The caller supplies that function, not a made-up ramp.
                acidDirector(this, it)
            }
            acidExposure.keys.removeAll { p -> !eligible(p) || acid.none { sameTile(it, p.tile) } }
            if (tick < nextAttack) return
            if (pending.isNotEmpty()) {
                if (tick >= specialUntil) specialDirector(this, pending.removeFirst())
            }
            autoSelector(this).let { auto ->
                animate(if (auto.style == CombatClass.MELEE) { if (isEnraged) 9622 else 9620 } else { if (isEnraged) 9626 else 9624 })
                tail?.let { animate(if (auto.style == CombatClass.MELEE) { if (isEnraged) 9623 else 9621 } else { if (isEnraged) 9627 else 9625 }, it) }
                players().filter { auto.style != CombatClass.MELEE || meleeReach(it.tile) }.forEach { p ->
                    auto.fire(this, p) { damage ->
                        // ONZEKER T18: no published proc chance/duration; binary50% chance, two7tick intervals, movement2 uses acid poison2.
                        if (auto.style == CombatClass.MELEE && damage > 0 && npc.world.random(1) == 0) bleeding[p] = (tick + 14) to Tile(p.tile)
                    }
                    if (auto.style != CombatClass.MELEE) later(2) {
                        gfx(if (auto.style == CombatClass.MAGIC) 2186 else 2185, p.tile)
                        ToaBossFx.projectile(npc, p, if (auto.style == CombatClass.MAGIC) 2181 else 2182, auto.style == CombatClass.MAGIC, 2)
                    }
                }
            }
            autoCount++
            if (inv("Not Just a Head")) bloodDirector(this)
            nextAttack = tick + pathAttackInterval(this)
        }
        fun addAcid(centre: Tile) {
            val radius = acidRadius(inv("Upset Stomach"))
            for (dx in -radius..radius) for (dz in -radius..radius) acid += centre.transform(dx, dz)
        }
        fun enterWater(p: Player, tile: Tile) {
            if (!eligible(p) || onPlatform(tile) || p in water) return
            val runState = p.getVarp(173)
            val croc = spawn(11741, tile.transform(1, 0), CROC_STATS, path = false, group = false)
            water[p] = croc to runState; p.setVarp(173, 0)
        }
        fun leaveWater(p: Player) {
            val state = water.remove(p) ?: return
            state.first?.let { remove(it) }
            if (p.isOnline) p.setVarp(173, state.second)
        }
        fun climbUp(p: Player) {
            if (!eligible(p) || p !in water) return
            p.stopMovement(); p.teleportTo(Tile(p.tile.x.coerceIn(centre.x - 8, centre.x + 8), p.tile.z.coerceIn(centre.z - 8, centre.z + 8), centre.height))
            leaveWater(p)
        }
        fun shatterJug(jug: Npc) {
            val centre = Tile(jug.tile); val radius = waterRadius(inv("Upset Stomach"))
            acid.removeAll { it.height == centre.height && it.isWithinRadius(centre, radius) }
            gfx(2192, centre); jugs.remove(jug); specialNpcs.remove(jug); remove(jug)
        }
        /** Collision-tested single steps support cardinal AND diagonal push/pull without rolling through a rock. */
        fun moveJug(jug: Npc, direction: Direction, steps: Int) {
            require(steps >= 0 && direction != Direction.NONE)
            fun step(left: Int) {
                if (!jug.isSpawned() || left == 0) return
                val next = jug.tile.transform(direction.getDeltaX(), direction.getDeltaZ())
                if (!npc.world.collision.canStep(jug.tile, direction)) return
                jug.moveTo(next)
                if (rocks.any { it.hp > 0 && sameTile(it.tile, next) }) { shatterJug(jug); return }
                later(1) { step(left - 1) }
            }
            step(steps)
        }
        /** Three roar impacts. The first-impact tick and inter-roar spacing must come from a tick reference (T18). */
        fun roar(firstImpact: Int, spacing: Int, baseDamage: Int, direction: Direction) {
            require(firstImpact >= 0 && spacing > 0 && baseDamage >= 0)
            repeat(3) { n -> later(firstImpact + n * spacing) {
                animate(9628); tail?.let { animate(9629, it) }
                val standing = rocks.filter { it.hp > 0 }
                players().filter { p -> standing.none { behindRock(p.tile, it.tile, direction) } }.forEach {
                    attack(it, ToaScale.damage(baseDamage, context), null, accuracy = false)
                }
                // One -50 per wave, regardless of how many players shelter behind the same rock.
                standing.forEach { it.hp = (it.hp - 50).coerceAtLeast(0) }
            } }
        }
        /** The room adapter supplies the proven start edge, gap and end row; no unconstrained random layout can close every route. */
        fun wave(row: List<Tile>, direction: Direction, steps: Int, interval: Int, baseDamage: Int,
            onWater: (Player, Tile) -> Unit) {
            require(steps > 0 && interval > 0 && baseDamage in 6..10)
            val segments = row.mapNotNull { t -> spawn(11738, t, STATS.copy(hp = 1), path = false, group = false) }.also { specialNpcs += it }
            fun advance(current: List<Tile>, left: Int) {
                if (left == 0) { segments.forEach { specialNpcs.remove(it); remove(it) }; return }
                current.forEach { t -> acid.removeAll { sameTile(it, t) } }
                jugs.toList().filter { jug -> current.any { sameTile(it, jug.tile) } }.forEach { moveJug(it, direction, 1) }
                players().filter { p -> current.any { sameTile(it, p.tile) } }.forEach { p ->
                    val damage = ToaScale.damage(baseDamage, context)
                    attack(p, damage, null, accuracy = false, minHit = damage)
                    val next = p.tile.transform(direction.getDeltaX(), direction.getDeltaZ())
                    val canStep = npc.world.collision.canStep(p.tile, direction)
                    if (canStep) p.teleportTo(next)
                    if (!onPlatform(next) || !canStep) onWater(p, next)
                }
                segments.forEach { it.moveTo(it.tile.transform(direction.getDeltaX(), direction.getDeltaZ())) }
                later(interval) { advance(current.map { it.transform(direction.getDeltaX(), direction.getDeltaZ()) }, left - 1) }
            }
            advance(row, steps)
        }
        fun bloodBarrage(victims: List<Player>, baseMax: Int, delay: Int) {
            if (!inv("Not Just a Head")) return
            val radius = if (inv("Arterial Spray")) 2 else 1 // ONZEKER T17: wider-area invocation doubles normal3x3 radius.
            victims.filter { eligible(it) }.forEach { p -> later(delay) {
                if (eligible(p)) {
                    val stacked = victims.count { eligible(it) && it.tile.isWithinRadius(p.tile, radius) }
                    attack(p, ToaScale.damage(baseMax * stacked, context), CombatClass.MAGIC,
                        accuracy = false, after = { dealt -> heal(bloodHeal(dealt)) })
                }
            } }
        }
        fun bloodCloud(cloud: Npc, target: Player?, baseDrain: Int) {
            fun cycleCloud() {
                if (!cloud.isSpawned() || cloud.isDead()) return
                cloud.setCurrentLifepoints((cloud.getCurrentLifepoints() - 2).coerceAtLeast(0))
                if (cloud.getCurrentLifepoints() == 0) { remove(cloud); return }
                // Never acquire a fresh target. Leaving the room does not transfer the cloud's aggro to another player.
                if (target != null && eligible(target)) {
                    cloud.walkTo(target.tile)
                    if (target.tile.isWithinRadius(cloud.tile, 1)) attack(target, baseDrain, null, accuracy = false, after = { dealt ->
                        if (cloud.isSpawned()) cloud.setCurrentLifepoints(minOf(cloud.getMaximumLifepoints(), cloud.getCurrentLifepoints() + dealt))
                    })
                } else if (target == null) {
                    val direction = Direction.NESW[npc.world.random(3)]
                    if (npc.world.collision.canStep(cloud.tile, direction)) cloud.walkTo(cloud.tile.transform(direction.getDeltaX(), direction.getDeltaZ()))
                }
                later(1, acrossPhase = true) { cycleCloud() }
            }
            cycleCloud()
        }
        override fun close() { water.keys.toList().forEach { if (eligible(it)) climbUp(it); leaveWater(it) }; bleeding.clear(); acidExposure.clear(); pending.clear(); acid.clear(); rocks.clear(); jugs.clear(); super.close() }
    }
}
