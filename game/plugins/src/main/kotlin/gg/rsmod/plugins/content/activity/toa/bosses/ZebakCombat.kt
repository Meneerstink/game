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
import gg.rsmod.plugins.content.combat.poison

/** Zebak oldid15351537; Strategies oldid15392814 lines807-839; Jug oldid15139696.
 * Owner21:5x: invocation-only blood attacks and path variants are excluded.
 * T18/T01: special layout/cadence, acid ramp and projectile split timings require measured inputs.
 */
object ZebakCombat : CombatScript() {
    override val ids: IntArray get() = listOfNotNull(ToaBossNpcs.local(ToaBossNpcs.ZEBAK)).toIntArray()
    val STATS = ToaNpcStats(ToaDuration.boss(580), 250, 140, 70, 100, 120, 7, 160, 215, 220, 100, 16, 30,
        160, 160, 260, 200, 110, 9634)
    // Cache2686 NPC11741 TOA_ZEBAK_WATERCROC: Attack250, Strength70, params14(speed)=2,10(strength bonus)=0.
    // Crocodile(ToA) Wiki infobox in05-brontekst is NPC11705, the DIFFERENT Crondis puzzle crocodile.
    // ONZEKER T18: unpublished HP/defensive levels/bonuses and action clips retain that related crocodile's fallback.
    val CROC_STATS = ToaNpcStats(30, 250, 70, 100, 30, 1, 2, strengthBonus = 0,
        stab = 150, slash = 350, crush = 350, mage = 5, range = 150, deathSeq = 2038)
    // ONZEKER T18: actual bite max unpublished; standard NPC melee formula from measured70/+0 gives
    // floor(0.5 + (70+9)*(0+64)/640)=8, rather than the unrelated puzzle crocodile's published18.
    const val CROC_MAX_HIT = 8
    val thresholds = listOf(85, 70, 55, 40)
    fun enraged(hp: Int, max: Int) = hp.toLong() * 4 < max
    fun waterRadius(@Suppress("UNUSED_PARAMETER") upsetStomach: Boolean = false) = 2
    fun acidRadius(@Suppress("UNUSED_PARAMETER") upsetStomach: Boolean = false) = 1
    /** Cache2686 toa_zebak_vomit01-07 (all seven = acid tiles: toa-mistake-tracker ZebakDetector). */
    val ACID_LOCS = listOf(45570, 45571, 45572, 45573, 45574, 45575, 45576)
    /** ONZEKER T02: number of poison impacts per special (unpublished; kept at the earlier 10). */
    const val ACID_IMPACTS = 10
    fun narrowedGap(base: Int, @Suppress("UNUSED_PARAMETER") path: Int) = base.coerceAtLeast(1)
    // Wiki: base7/4 ticks. Owner 21:5x: no path-specific speed changes.
    fun attackInterval(enraged: Boolean, @Suppress("UNUSED_PARAMETER") path: Int) = if (enraged) 4 else 7
    fun maxHit(style: CombatClass, c: ToaEncounterContext) = ToaScale.damage(if (style == CombatClass.MELEE) 38 else 16, c)
    enum class Special { ROAR, WAVES }
    data class Rock(val tile: Tile, var hp: Int = 150) { var npc: Npc? = null }
    /** Cache2686 seq9642 projectile_zebak_pitcher01 / seq9645 projectile_zebak_shield01 = 90 client cycles = 3 ticks of flight. */
    const val SPIT_FLIGHT = 3
    /** N609 14-11: flight of an additional acid splatter (1555) from the impact to a neighbour (Kaoz Entry 672.75-673.25 s). */
    const val SPLASH_FLIGHT = 1
    /** Cache2686 seq9632 npc_zebak01_attack_special = 120 client cycles = 4 ticks (the spit before every special). */
    const val SPIT_TICKS = 4
    /** A rock blocks the ray away from Zebak for the next three floor tiles. Direction is room geometry supplied by the adapter. */
    fun behindRock(player: Tile, rock: Tile, direction: Direction): Boolean =
        player.height == rock.height && (1..3).any { n -> player.x == rock.x + direction.getDeltaX() * n && player.z == rock.z + direction.getDeltaZ() * n }
    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        if (npc.attr[ToaEncounter.SESSION]?.closed != false) ToaEncounter.context(npc)?.let { ToaEncounter.attach(npc, it, ::Session) }
    }
    class Session(npc: Npc, input: ToaEncounterContext) : ToaBossSession(npc, input) {
        override val stats = STATS
        override val minimumDefence = 50 // Wiki Zebak base70 ->50 drain floor.
        var isEnraged = false
        var thresholdIndex = 0
        var nextSpecial = Special.ROAR
        val pending = ArrayDeque<Special>()
        val acid = mutableSetOf<Tile>()
        private val acidCleanup = mutableMapOf<Tile, () -> Unit>()
        val rocks = mutableListOf<Rock>()
        val jugs = mutableSetOf<Npc>()
        private val specialNpcs = mutableSetOf<Npc>()
        var tail: Npc? = null
        var autoSelector: (Session) -> ToaAutoAttack = { it.defaultAuto() }
        var specialDirector: (Session, Special) -> Unit = { s, special -> s.defaultSpecial(special) }
        var acidDirector: (Session, Player) -> Unit = { s, p -> s.acidHit(p) }
        var pathAttackInterval: (Session) -> Int = { attackInterval(it.isEnraged, it.context.pathLevel) }
        // Cache2686 map15700 bridge terrain: the fight platform is EAST of the boss, centred on3934,5408.
        // Boss SW3917,5404 -> platform+17,+4. The raid adapter supplies the translated boss spawn, not another geometry override.
        var centre = npc.spawnTile.transform(17, 4)
        private val acidExposure = mutableMapOf<Player, Int>()
        private var specialUntil = 0
        private var waveSet = 0
        private val bleeding = mutableMapOf<Player, Pair<Int, Tile>>()
        private val water = mutableMapOf<Player, Pair<Npc?, Int>>()
        var onPlatform: (Tile) -> Boolean = { it.height == centre.height && it.x in centre.x - 8..centre.x + 8 && it.z in centre.z - 8..centre.z + 8 }
        override fun configure() {
            super.configure()
            // OSRS cache2686 models 46399/46400 share one origin: body z=-1049..44, tail z=47..1412.
            // Both NPCs are 9x9; moving the tail by nine tiles detaches it. The fight platform is east of that origin
            // (map15700; Wiki "Tombs of Amascut - Fighting Zebak.png"), so both halves face east throughout the fight.
            npc.setSpawnFacing(Direction.EAST)
            npc.static = true
            // cache2686 map15700 loc45509: (3933,5397,rot0) and (3933,5419,rot2).
            placeObject(45509, centre.transform(-1, -11)); placeObject(45509, centre.transform(-1, 11), 2)
            tail = spawn(ToaBossNpcs.ZEBAK_TAIL, npc.spawnTile, STATS.copy(hp = 1))?.also { t ->
                t.setSpawnFacing(Direction.EAST)
                t.static = true
                t.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }
            }
        }
        /** Chompington: Zebak melees the adjacent platform tiles. His own water tiles are walk-blocked (cache2686 15700).
         * Check the edge from the player's dry tile, as the player's melee does; testing from the water rejects valid melee autos.
         * Real walls flag both sides of the edge, so they still block. No terrain collision is removed.
         */
        override fun meleeReach(tile: Tile): Boolean {
            if (tile.height != npc.tile.height) return false
            return distance(tile) == 0 || gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(
                npc.world.collision, tile, 1, 1, npc.tile, npc.getSize(), npc.getSize())
        }
        private fun defaultAuto(): ToaAutoAttack {
            val close = players().any { meleeReach(it.tile) }
            val style = if (close) CombatClass.MELEE else if (npc.world.random(1) == 0) CombatClass.RANGED else CombatClass.MAGIC
            // ONZEKER T01/T18: melee chip follows Akkha75% reduction; ranged/magic flight uses the4tick attack clip.
            return ToaAutoAttack(if (style == CombatClass.MELEE) 38 else 16, style, if (style == CombatClass.MELEE) 1 else 4,
                if (style == CombatClass.MELEE) 1 else 0, if (style == CombatClass.MELEE) 4 else 1,
                projectile = if (style == CombatClass.MAGIC) { if (isEnraged) 2177 else 2176 } else if (style == CombatClass.RANGED) { if (isEnraged) 2179 else 2178 } else -1)
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
            val rockTiles = listOf(-4, 0, 4).map { centre.transform(0, it) }
            val jugTiles = listOf(-6, -3, 0, 3, 6).map { x -> centre.transform(3, x) }
            // N609 14-11 11:5x (eigenaar 'gifplassen dichter dan OSRS'): was a fixed grid of 10 centres x full 3x3 = two solid strips.
            // Wiki Zebak/Strategies0819: "The poison ... will splatter on a random tile in the arena and may splatter additional times in
            // a 3x3 radius around the impact tile"; 0821: "The first iteration of Great Roar will always place rocks in front of him,
            // poison right behind them"; 0815 "Rarely, acid may not cover a tile behind a rock" -> behind each rock a full 3x3 covering
            // the three safe tiles (behindRock), every other impact on a random tile of the same platform area with a partial 3x3.
            // ONZEKER T02: impact count (10 as before) and the 50% neighbour chance are unpublished.
            val behind = if (special == Special.ROAR) rockTiles.map { it.transform(2, 0) } else emptyList()
            // 11:5x testclient pools-1145: x 0..6 kept everything in one strip; the dry platform runs ~4 tiles west and 8 east of the
            // centre (water beyond -5, onPlatform = +-8) -> impacts anywhere on the dry platform, never on a wall/clipped tile.
            val randomCentres = List(ACID_IMPACTS - behind.size) {
                var t = centre.transform((-4..7).random(), (-7..7).random())
                repeat(5) { if (npc.world.collision.isClipped(t)) t = centre.transform((-4..7).random(), (-7..7).random()) }
                t
            }
            val acidCentres = behind + randomCentres
            // N606 / Wiki Zebak/Strategies: "Poison and jugs are always spit out with each special attack" ("throw jugs, poison and
            // two/three rocks onto the arena"). Spit clip seq9632 (+tail9633), then each object flies (pitcher/shield/vomit
            // projectiles, cache spotanims 2173/2172/2194) and only appears where it lands - it no longer pops up out of nowhere.
            animate(9632); tail?.let { animate(9633, it) }
            // The spit clip must not be overwritten by an auto in the same tick; Great Roar still gets its four autos
            // (tick+4, +11, +18, +25) before the first roar at 4*speed.
            nextAttack = maxOf(nextAttack, tick + SPIT_TICKS)
            jugTiles.forEach { spit(2173, it) }
            acidCentres.forEach { spit(2194, it) }
            if (special == Special.ROAR) {
                rocks.clear(); rocks += rockTiles.map(::Rock)
                rockTiles.forEach { spit(2172, it) }
            }
            later(SPIT_FLIGHT) {
                jugTiles.forEach { tile ->
                    spawn(11735, tile, STATS.copy(hp = 1), path = false, group = false)?.let { jug ->
                        jugs += jug; specialNpcs += jug
                        jug.hitModifier = { h ->
                            h.hitmarks.forEach { it.damage = 0 }
                            h.addAction { if (jug in jugs) shatterJug(jug) }
                        }
                    }
                }
                behind.forEach(::addAcid)
                randomCentres.forEach { splatter(it, rockTiles) }
                if (special == Special.ROAR) rocks.forEach { rock ->
                    spawn(11737, rock.tile, STATS.copy(hp = 150), path = false, group = false)?.let { r ->
                        rock.npc = r; specialNpcs += r; r.hitModifier = { h -> h.hitmarks.forEach { it.damage = 0 } }
                        later(4 * stats.speed + 27 - SPIT_FLIGHT) { remove(r) }
                    }
                }
            }
            if (special == Special.ROAR) {
                // Wiki: four regular attacks to position jugs; roar clip9628=270cycles=9ticks (92-seqticks).
                roar(4 * stats.speed, 9, 38, Direction.EAST) // Away from Zebak, which stands west of the source platform.
                later(4 * stats.speed) { nextAttack = tick + 27 }
                specialUntil = tick + 4 * stats.speed + 27
            } else {
                val direction = if (waveSet++ % 2 == 0) Direction.NORTH else Direction.SOUTH
                val gap = narrowedGap(5, context.pathLevel)
                // Wiki Zebak/Strategies (Tidal Waves): "throw jugs and poison before slamming his tail, causing boulders to drop from one
                // direction and cause a massive tidal wave". After the spit clip: tail slam (seq9630/9631) + falling boulders and dust
                // (cache spotanims 2195 ZEBAK_ROCK_FALL / 2174 ZEBAK_DUSTFALL01) along the wave's start edge, then the waves.
                // ONZEKER T18: the waves start with the slam; the exact slam-to-wave tick offset is unpublished.
                val edge = (-8..8).map { x -> centre.transform(x, if (direction == Direction.NORTH) -8 else 8) }
                later(SPIT_TICKS) {
                    animate(9630); tail?.let { animate(9631, it) }
                    edge.forEach { gfx(2195, it); gfx(2174, it) }
                }
                // ONZEKER T18: three waves,7ticks apart, one row/tick; wave gap from published path2/4 reductions.
                repeat(3) { index -> later(SPIT_TICKS + index * stats.speed) {
                    val row = (-8..8).filter { it !in -(gap / 2)..(gap - 1) / 2 }.map { x ->
                        centre.transform(x, if (direction == Direction.NORTH) -8 else 8)
                    }
                    wave(row, direction, 17, 1, 8) { p, tile -> enterWater(p, tile) }
                } }
                specialUntil = tick + SPIT_TICKS + 3 * stats.speed + 17
            }
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
                    gg.rsmod.plugins.content.activity.toa.ToaRaidStats.of(p)?.inZebakWater(p) // The Swimmer (Raid Summary title)
                    p.setVarp(173, 0); p.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] = 2
                    state.first?.takeIf { it.isSpawned() && !it.isDead() }?.let { croc ->
                        croc.walkTo(p.tile)
                        if (tick % CROC_STATS.speed == 0 && actorMeleeReach(croc, p.tile)) {
                            animate(2039, croc)
                            attack(p, ToaScale.damage(CROC_MAX_HIT, context), CombatClass.MELEE, 1, 1, 4, actor = croc)
                        }
                    }
                }
            }
            if (!isEnraged && enraged(npc.getCurrentLifepoints(), npc.getMaximumLifepoints())) {
                isEnraged = true; pending.clear(); newPhase(); specialUntil = tick
                npc.attr[gg.rsmod.game.model.attr.EXAMINE_OVERRIDE_ATTR] = "He seems a tad upset." // wiki Zebak examine2 (OSRS npc 11732)
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
            // Roar callbacks may have extended nextAttack while starting this special.
            if (tick < nextAttack) return
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
            // Owner: blood attacks belong to Not Just a Head and are disabled in simplified ToA.
            nextAttack = tick + pathAttackInterval(this)
        }
        /** One spat object (jug / rock / acid) flying from Zebak's mouth to the tile where it will appear SPIT_FLIGHT ticks later. */
        fun spit(osrs: Int, target: Tile) {
            val gfx = ToaBossFx.spot(osrs) ?: return
            val type = gg.rsmod.plugins.api.ProjectileType.MAGIC
            // ONZEKER T01: standard server packet geometry (as ToaBossFx); flight = the projectile seq duration, ends on the floor.
            npc.world.spawn(gg.rsmod.game.model.entity.Projectile.Builder()
                .setTiles(start = npc.getCentreTile(), target = target).setGfx(gfx = gfx)
                .setHeights(startHeight = type.startHeight, endHeight = 0)
                .setSlope(angle = type.angle, steepness = type.steepness)
                .setTimes(delay = 0, lifespan = SPIT_FLIGHT * 30).build())
        }
        fun addAcid(centre: Tile) {
            val radius = acidRadius()
            for (dx in -radius..radius) for (dz in -radius..radius) {
                val tile = centre.transform(dx, dz)
                // Cache2686 loc45570 toa_zebak_vomit01 is a non-clipping1x1 tile (50-loc-defs).
                // N609 14-11: OSRS has SEVEN acid locs toa_zebak_vomit01-07 (45570-45576; 2 base models 46393/46394, extra bubble models
                // 45075/45078/45080, recolours) and RuneLite toa-mistake-tracker ZebakDetector.ACID_TILE_GAME_OBJECT_IDS treats all seven
                // as acid tiles. Kaoz Entry 656 s: pools of mixed size with bubbles; ours (only vomit01) were all the same size.
                // ONZEKER: which variant OSRS picks per tile is unpublished - uniform random per tile.
                if (acid.add(tile)) acidCleanup[tile] = placeObject(ACID_LOCS.random(), tile)
            }
        }
        /** Wiki Strategies0819: impact tile always, "may splatter additional times in a 3x3 radius" (ONZEKER: 50% per neighbour).
         *  Never on a rock tile (the rock npc stands there). */
        fun splatter(impact: Tile, rockTiles: List<Tile> = emptyList()) {
            val extra = mutableListOf<Tile>()
            for (dx in -1..1) for (dz in -1..1) {
                val tile = impact.transform(dx, dz)
                if ((dx != 0 || dz != 0) && kotlin.random.Random.nextBoolean()) continue
                if (rockTiles.any { sameTile(it, tile) }) continue
                // 12:0x toagfx PLACEMENT 45574 'volledig in muur/blokkade': a neighbour can fall on the platform edge - skip clipped tiles.
                if (npc.world.collision.isClipped(tile)) continue
                if (dx != 0 || dz != 0) { extra += tile; continue }
                if (acid.add(tile)) acidCleanup[tile] = placeObject(ACID_LOCS.random(), tile)
            }
            // N609 14-11: the additional splatters fly out of the impact as TOB_XARPUS_ACIDSPIT 1555 (ToA Extended Zebak
            // PROJECTILE_ID_VENOM_SPLASH, an arena projectile next to VENOM 2194) and land a moment later - Kaoz Entry 672.75 s pools in
            // lines, 673.0 s small specks beside them, 673.25 s the wide field. ONZEKER: flight 1 tick measured on the video.
            extra.forEach { ToaBossFx.tileProjectile(npc, impact, it, 1555, SPLASH_FLIGHT) }
            if (extra.isNotEmpty()) later(SPLASH_FLIGHT) {
                extra.forEach { tile -> if (acid.add(tile)) acidCleanup[tile] = placeObject(ACID_LOCS.random(), tile) }
            }
        }
        private fun clearAcid(predicate: (Tile) -> Boolean) {
            acid.filter(predicate).forEach { tile -> acid.remove(tile); acidCleanup.remove(tile)?.invoke() }
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
            val centre = Tile(jug.tile); val radius = waterRadius()
            clearAcid { it.height == centre.height && it.isWithinRadius(centre, radius) }
            gfx(2192, centre); jugs.remove(jug); specialNpcs.remove(jug); remove(jug)
        }
        /** Collision-tested single steps support cardinal AND diagonal push/pull without rolling through a rock. */
        fun moveJug(jug: Npc, direction: Direction, steps: Int) {
            require(steps >= 0 && direction != Direction.NONE)
            fun step(left: Int) {
                if (!jug.isSpawned() || left == 0) return
                val next = jug.tile.transform(direction.getDeltaX(), direction.getDeltaZ())
                if (!npc.world.collision.canStep(jug.tile, direction)) return
                gg.rsmod.plugins.content.activity.toa.ToaSlide.step(jug, next) // N606: walk, not moveTo
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
                gfx(2184, npc.getCentreTile())
                val standing = rocks.filter { it.hp > 0 }
                players().filter { p -> standing.none { behindRock(p.tile, it.tile, direction) } }.forEach {
                    attack(it, ToaScale.damage(baseDamage, context), null, accuracy = false)
                    // ONZEKER T18: unquantified roar shove uses the same encounter's one-step wave displacement.
                    val end = it.tile.transform(direction.getDeltaX(), direction.getDeltaZ())
                    val canStep = npc.world.collision.canStep(it.tile, direction)
                    if (canStep || !onPlatform(end)) { // same edge rule as the waves: walkable rim = shove, blocked edge = moat
                        it.stopMovement(); it.teleportTo(end)
                        if (!canStep && !onPlatform(end)) enterWater(it, end)
                    }
                }
                // One -50 per wave, regardless of how many players shelter behind the same rock.
                standing.forEach { rock ->
                    rock.hp = (rock.hp - 50).coerceAtLeast(0)
                    // N606: a spent rock shatters visibly (cache spotanim 2191 ZEBAK_SHIELD_BREAK) instead of standing on until cleanup.
                    if (rock.hp == 0) { gfx(2191, rock.tile); rock.npc?.let { specialNpcs.remove(it); remove(it) }; rock.npc = null }
                }
            } }
        }
        /** The room adapter supplies the proven start edge, gap and end row; no unconstrained random layout can close every route. */
        fun wave(row: List<Tile>, direction: Direction, steps: Int, interval: Int, baseDamage: Int,
            onWater: (Player, Tile) -> Unit) {
            require(steps > 0 && interval > 0 && baseDamage in 6..10)
            val segments = row.mapNotNull { t -> spawn(11738, t, STATS.copy(hp = 1), path = false, group = false) }.also { specialNpcs += it }
            fun advance(current: List<Tile>, left: Int) {
                if (left == 0) { segments.forEach { specialNpcs.remove(it); remove(it) }; return }
                current.forEach { t -> clearAcid { sameTile(it, t) } }
                jugs.toList().filter { jug -> current.any { sameTile(it, jug.tile) } }.forEach { moveJug(it, direction, 1) }
                players().filter { p -> current.any { sameTile(it, p.tile) } }.forEach { p ->
                    val damage = ToaScale.damage(baseDamage, context)
                    attack(p, damage, null, accuracy = false, minHit = damage)
                    val next = p.tile.transform(direction.getDeltaX(), direction.getDeltaZ())
                    val canStep = npc.world.collision.canStep(p.tile, direction)
                    // Wiki Zebak (Tidal Waves): "If the wave pushes the player over into the water, they will need to quickly scale back up a
                    // nearby ramp". The moat is walk-blocked for normal walking, so the edge step failed canStep and the player stayed on
                    // the platform (the next tick then left the water at once): over the edge the wave now always moves the player in.
                    // 12-11 22:1x (traced run 3): map15700 has WALKABLE rim land just past centre+-8 (rows 297/298 north, 278/279 south,
                    // clipmap). A push onto walkable rim is an ordinary shove; only a blocked step over the platform edge is the moat
                    // (it put players on dry rim tiles in the water state: crocodile + no attacks, and a ramp they could not climb).
                    val intoWater = !canStep && !onPlatform(next)
                    if (canStep || intoWater) { p.stopMovement(); p.teleportTo(next) }
                    if (intoWater) onWater(p, next)
                }
                segments.forEach { gg.rsmod.plugins.content.activity.toa.ToaSlide.step(it, it.tile.transform(direction.getDeltaX(), direction.getDeltaZ())) } // N606
                later(interval) { advance(current.map { it.transform(direction.getDeltaX(), direction.getDeltaZ()) }, left - 1) }
            }
            advance(row, steps)
        }
        override fun close() {
            // N606: Zebak's death (pre-death closes the session) - the tail plays its own death clip (cache2686 seq9635
            // npc_zebak02_death, with the body's 9634) and goes when the body goes; ToaBossRooms then places both corpses.
            tail?.takeIf { npc.isDead() && it.isSpawned() }?.let { t ->
                children.remove(t)
                ToaBossFx.seqFor(t, 9635).takeIf { it >= 0 }?.let { t.animate(it) }
                // Inside a world queue 'npc' is QueueTask.npc (ctx as Npc, ctx = World) - capture the body first (13-11 ClassCastException).
                val body = npc
                val world = npc.world
                world.queue {
                    while (body.isSpawned()) wait(1)
                    if (t.isSpawned()) world.remove(t)
                }
            }
            closeSession()
        }
        private fun closeSession() { water.keys.toList().forEach { if (eligible(it)) climbUp(it); leaveWater(it) }; bleeding.clear(); acidExposure.clear(); pending.clear(); clearAcid { true }; rocks.clear(); jugs.clear(); specialNpcs.clear(); super.close() }
    }
}
