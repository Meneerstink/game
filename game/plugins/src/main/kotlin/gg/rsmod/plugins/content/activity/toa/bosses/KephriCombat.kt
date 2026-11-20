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
    /** Cache2686 seq9519 toa_dung_drop (spotanim 2145) = 90 client cycles = 3 ticks before the pile lies on the floor. */
    const val DUNG_DROP_TICKS = 3
    /** B2 20-11: the row starts at the edge of Kephri's platform (just outside her 5x5 body), the ray stops at the wall (max 24 tiles),
     *  the piles fall DUNG_FALL_START ticks after the throw (+0..DUNG_FALL_SPREAD from the platform to the wall). */
    const val DUNG_ROW_START = 3
    const val DUNG_RAY_MAX = 24
    const val DUNG_FALL_START = 3
    const val DUNG_FALL_SPREAD = 1
    /** OSRS VFX_SCARAB_EXPLOSION01..04: the Kephri bomb hit (RuneLite ToA Mistake Tracker KEPHRI_BOMB_GRAPHICS_ID). */
    val BOMB_EXPLOSIONS = intArrayOf(2156, 2157, 2158, 2159)
    /** Final phase hp (OSRS 80), shortened with the other bosses (ToaDuration). */
    val FINAL_HP = ToaDuration.boss(80)
    val STATS = ToaNpcStats(ToaDuration.boss(150), 0, 0, 80, 125, 0, 6, magicBonus = 125, magicStrength = 50,
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
        var shield = ToaScale.hp(STATS.hp, context)
        /** N606 OSRS HP HUD (Kaoz 16:37-17:45): the shield in cyan down to "0 / 240" during the recharge, then the final health green/red. */
        override fun hpHud() = if (!active()) null else if (phase == Phase.FINAL) super.hpHud()
            else gg.rsmod.plugins.content.mechanics.hud.BossHpHud.Bar(npc, shield, ToaScale.hp(STATS.hp, context),
                gg.rsmod.plugins.content.mechanics.hud.BossHpHud.Style.SHIELD)
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
        /** ToA Extended Kephri (reads the real game): ATK_COUNT_INIT 3 / ATK_COUNT_MAX 5 - the first special after three fireballs,
         *  then a special after every five; the special replaces that attack (NPC_KEPHRI_ATTACK_SPECIAL_EGGS 9578 resets the count). */
        private var fireballsBeforeSpecial = 3
        override fun configure() {
            super.configure()
            ToaBossFx.weakness(npc, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.FIRE, 40)
            ToaBossFx.spot(2141)?.let { npc.graphic(it) }
        }
        private val arcaneCharge = mutableMapOf<Npc, Int>()
        private val soldierNext = mutableMapOf<Npc, Int>()
        private val maxHitTargets = mutableSetOf<Npc>()
        private fun defaultSpecial() {
            // ONZEKER T14: which special comes first; they alternate Dung Strike / Mass Incubation (dung in its place with too many scarabs).
            ++autoCount
            agile.removeAll { !it.isSpawned() || it.isDead() }
            val limit = 2 // ONZEKER T15: base two dark eggs; exact active scarab cap remains unpublished.
            if (autoCount % 2 == 1 || agile.size >= limit) defaultDung(dungTargets()) else {
                val placements = linkedMapOf<Tile, Boolean>()
                // ONZEKER T02/T15: two base light/dark pairs evenly distributed around Kephri; no invocation variant.
                repeat(limit) { i ->
                    placements[centre.transform(if (i % 2 == 0) -6 else 6, i * 3 - 5)] = true
                    placements[centre.transform(i * 3 - 5, if (i % 2 == 0) -6 else 6)] = false
                }
                // ToA Extended Kephri TICK_COUNT_EGG = 15: an egg hatches/explodes 15 ticks after it lands. Explosion = published boss max24.
                incubation(placements, 15, 24, limit)
            }
        }
        private fun defaultDung(victims: List<Player>) {
            animate(9578) // ToA Extended: every special resets its attack counter on 9578 (fireballs are 9577); ONZEKER T14 for dung.
            val marked = victims.associateWith { Tile(it.tile) }
            // ONZEKER T14: warning3ticks=seq9577; stun2seconds rounds up to4game ticks.
            // 12-11 22:5x (eigenaar 'kephri room ... er wordt geen poep getekend'): OSRS (wiki Strategies0954 'Piles of dung will then fall
            // down the direction the player was knocked back in'; wiki image 'Soldier Scarab Trapping.png') = STRAIGHT rows of piles along
            // the knockback path. Our diagonal 4-tile line looked like scattered stains. Knockback + row now follow the dominant axis away
            // from Kephri and cover every tile the player was thrown over (landing tile excluded).
            fun outward(start: Tile): gg.rsmod.game.model.Direction {
                val rx = start.x - centre.x; val rz = start.z - centre.z
                return when {
                    rx == 0 && rz == 0 -> gg.rsmod.game.model.Direction.SOUTH
                    kotlin.math.abs(rx) >= kotlin.math.abs(rz) -> if (rx > 0) gg.rsmod.game.model.Direction.EAST else gg.rsmod.game.model.Direction.WEST
                    else -> if (rz > 0) gg.rsmod.game.model.Direction.NORTH else gg.rsmod.game.model.Direction.SOUTH
                }
            }
            // NACHTRUN 20-11 B2 (eigenaar 'dung niet tot aan de muur / juiste timing / sluit een speler in afhankelijk van de tile'; DUNG-ONDERZOEK.txt
            // B3-B8, hoofdbron video 4AqB1Tf1LhQ met tick-timer): Kephri slingert de speler langs de as weg van haar TOT TEGEN DE MUUR
            // (wiki 'sends them flying to the back of the arena'), daarna VALLEN de stapels op ELKE tegel van de lijn vanaf de rand van haar
            // platform TOT AAN DE MUUR (inclusief de landingstegel). De speler (2 s stun) kiest zelf links/rechts; blijft hij op de lijn staan:
            // lichte schade + geduwd naar een loopbare tegel binnen 10 (geen? snelle schade). Zelfde lijn als een oude rij = stapelt.
            dungStrike(victims, 3, 4, destination = { p ->
                val start = Tile(p.tile)
                val direction = outward(start)
                var end = start
                var steps = 0
                // Furthest walkable tile along the outward ray (wall / existing pile stops the flight).
                while (steps < DUNG_RAY_MAX && npc.world.collision.canStep(end, direction)) {
                    val next = end.transform(direction.getDeltaX(), direction.getDeltaZ())
                    if (next in dung) break
                    end = next; steps++
                }
                end
            }, tiles = { _, from, end ->
                val direction = outward(from)
                val horizontal = direction == gg.rsmod.game.model.Direction.EAST || direction == gg.rsmod.game.model.Direction.WEST
                val sign = if (direction == gg.rsmod.game.model.Direction.EAST || direction == gg.rsmod.game.model.Direction.NORTH) 1 else -1
                val last = if (horizontal) (end.x - centre.x) * sign else (end.z - centre.z) * sign
                // Row starts at the edge of Kephri's platform (distance 3 = just outside her 5x5 body) and runs to the landing tile.
                (DUNG_ROW_START..last).map { k ->
                    if (horizontal) Tile(centre.x + sign * k, from.z, from.height) else Tile(from.x, centre.z + sign * k, from.height)
                }.filter { !npc.world.collision.isClipped(it) }
            }, place = { t -> placeObject(45504, t) }) // Cache2686 KEPHRI_DUNG: blocks walking, not projectiles (18/27); 45149 is static decor.
        }
        private fun defaultRecharge() {
            val count = swarmCount(shieldBreaks)
            val cap = ToaScale.hp(STATS.hp, context) * 115 / 100 // ONZEKER T14: Wiki says approximately115%.
            shield = 0
            // NACHTRUN 20-11 (wiki Kephri_Shield_Spawn_locations.png, 6 vakken van 3 tegels op de rand; wiki-tekst NE,SE,S,SW,NW,N; foto-camera
            // kijkt van west naar oost: boven = oostmuur): NE/SE = oostmuur z centre+3 / -5, SW/NW = westmuur zelfde z, N/S = noord-/zuidmuur bij
            // x centre+1. De vloer loopt x centre-8..+7, z centre-9..+7 (clipmap 20-11), dus de OUDE hoektegels (+-8,+-8) en (0,+9) lagen
            // BUITEN de arena. Per spawn een willekeurige tegel van de 3 (lang de muur); kloksgewijs vanaf een willekeurige start.
            val edges = listOf(centre.transform(7, 3) to true, centre.transform(7, -5) to true, centre.transform(1, -9) to false,
                centre.transform(-8, -5) to true, centre.transform(-8, 3) to true, centre.transform(1, 7) to false)
            var at = 0
            val start = npc.world.random(5)
            val schedule = List(count) { i ->
                // ONZEKER T14: second24swarm phase truncates the published28schedule, no measured skip probability.
                at += firstSwarmInterval(i)
                val (base, alongZ) = edges[(start + i) % edges.size]
                val spread = npc.world.random(2) - 1
                at to (if (alongZ) base.transform(0, spread) else base.transform(spread, 0))
            }
            val heal = (ToaScale.hp(STATS.hp, context) / (10 + context.partySize - 1)).coerceAtLeast(1)
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
                        // TOA-1911 (eigenaar 'niet aggressive'): infobox aggressive=Yes. Out of range it walks to the nearest player
                        // every tick (was blocked behind Kephri's body by naive pathing and never moved - testclient 19-11).
                        if (victims.isEmpty()) players().minByOrNull { it.tile.getDistance(scarab.tile) }?.let { scarab.walkTo(it.tile) }
                        else scarab.stopMovement()
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
                        // TOA-1911 (eigenaar 'niet aggressive'): it chased only once per 7 ticks (walkTo inside the attack-speed check).
                        // Infobox aggressive=Yes, speed 7: follow the nearest player EVERY tick, swing as soon as in reach and off cooldown.
                        players().minByOrNull { it.tile.getDistance(scarab.tile) }?.let { p ->
                            scarab.facePawn(p)
                            if (!actorMeleeReach(scarab, p.tile)) scarab.walkTo(p.tile)
                            else if (tick >= (soldierNext[scarab] ?: 0)) {
                                soldierNext[scarab] = tick + SOLDIER_STATS.speed
                                animate(9587, scarab); attack(p, ToaScale.damage(17, context), CombatClass.MELEE, 1, actor = scarab) // Soldier Scarab infobox max17.
                            }
                        }
                        if (tick % 8 == 0) { // ONZEKER T14: ~5sec rounds to8ticks;5-7linear by current HP as Strategies says.
                            val amount = 5 + 2 * scarab.getCurrentLifepoints() / scarab.getMaximumLifepoints().coerceAtLeast(1)
                            // Specific Soldier Scarab article: its swarm restores shield OR health, respectively.
                            // This active minion heal is separate from Kephri's removed passive final-phase regeneration.
                            if (phase == Phase.FINAL) heal(amount) else { shield = minOf(ToaScale.hp(STATS.hp, context) * 115 / 100, shield + amount); if (phase == Phase.SHIELD) npc.setCurrentLifepoints(shield) }
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
                            // TOA-1911: no teleport after firing - Kephri wiki changelog 'Arcane Scarabs should no longer ... teleport
                            // immediately after firing'. It relocates only through arcaneRelocation (2+N hits or one hit of 39+).
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
                    val reserve = if (shieldBreaks == 2) ToaScale.hp(FINAL_HP, context) else 1
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
                npc.combatDef = stats.copy(hp = FINAL_HP).definition(context).copy(deathAnimation = listOf(ToaBossFx.seqFor(npc, stats.deathSeq)))
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
                if (phase == Phase.SHIELD && fireballsBeforeSpecial <= 0) { specialDirector(this); fireballsBeforeSpecial = 5 }
                else { autoDirector(this); if (phase == Phase.SHIELD) fireballsBeforeSpecial-- }
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
            animate(9577) // NPC_KEPHRI_ATTACK_MAGIC_RADIAL: ToA Extended ANIM_ID_FIREBALL_ATTACK = 9577 (was 9575 ATTACK_RANGED).
            // N609 13-11: the OSRS bomb (RuneLite ToA Mistake Tracker KephriDetector) = projectile 2266 onto the tile, a shadow graphic
            // GARGBOSS_DEBRIS_SHADOW_120/90/60 (1447/1446/2111 = 4/3/2 ticks to the hit) and on the hit VFX_SCARAB_EXPLOSION01-04
            // (2156-2159). Kaoz Entry guide 889.4-892.4 s: orange drop from high above, yellow-orange burst, dark shadow. Was 2147/2148
            // = the kamikaze scarab PROJECTILE drawn as a floor graphic. ONZEKER: which explosion variant per tile (random).
            val shadow = when (impact) { 4 -> 1447; 3 -> 1446; 2 -> 2111; else -> -1 }
            // N609 14-11: first the pre-fireball 1481 rising from her back, then 2266 onto the tiles (ToaBossFx.kephriBomb, video source).
            ToaBossFx.kephriBomb(npc, centre, targetTiles, impact)
            targetTiles.forEach { t -> if (shadow >= 0) gfx(shadow, t) }
            later(impact) {
                targetTiles.forEach { gfx(BOMB_EXPLOSIONS[npc.world.random(BOMB_EXPLOSIONS.lastIndex)], it) }
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
            destination: (Player) -> Tile?, tiles: (Player, Tile, Tile) -> List<Tile>, place: (Tile) -> (() -> Unit)) {
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
                    require(p.tile.isWithinRadius(end, DUNG_RAY_MAX + 2))
                    val from = Tile(p.tile)
                    p.message("Kephri throws you back!"); p.stopMovement()
                    p.timers[gg.rsmod.game.model.timer.FROZEN_TIMER] = stunTicks; p.teleportTo(end)
                    // NACHTRUN 20-11 B2 (owner video 4AqB1Tf1LhQ, timer 0:36.1 throw -> 0:38.9 first piles -> 0:39.8 row complete = ~6 ticks):
                    // the piles FALL from the platform edge towards the wall (first at DUNG_FALL_START, the wall end DUNG_FALL_SPREAD ticks
                    // later), each pile takes DUNG_DROP_TICKS (spotanim 2145 / seq 9519 = 90 cycles) and then blocks the tile. The stun
                    // (4 ticks) ends before the first pile lands, so the player can still click one tile left/right = pick the side.
                    // A pile CAN land on a player (08-11 'I can't reach that' is solved by the B6 push below, wiki Strategies 954-956).
                    val row = tiles(p, from, end)
                    row.forEachIndexed { i, tile ->
                        if (!dung.add(tile)) return@forEachIndexed // an older pile is already there = stacks
                        val offset = DUNG_FALL_START + if (row.size > 1) i * DUNG_FALL_SPREAD / (row.size - 1) else 0
                        later(offset, acrossPhase = true) { gfx(2145, tile) }
                        later(offset + DUNG_DROP_TICKS, acrossPhase = true) {
                            if (tile !in dung) return@later
                            cleanupDung += place(tile)
                            players().filter { sameTile(it.tile, tile) }.forEach { crushed(it) }
                        }
                    }
                }
            }
        }
        /** B6 (wiki Strategies 954-956): a pile lands on the player = light damage + shoved to a walkable tile within ten tiles;
         *  no such tile = rapid damage until free or dead. */
        private fun crushed(p: Player) {
            if (!eligible(p)) return
            attack(p, ToaScale.damage(4, context), null, accuracy = false, minHit = 1)
            fun free(): Tile? = (1..10).firstNotNullOfOrNull { r ->
                (-r..r).flatMap { dx -> (-r..r).map { dz -> p.tile.transform(dx, dz) } }
                    .filter { maxOf(kotlin.math.abs(it.x - p.tile.x), kotlin.math.abs(it.z - p.tile.z)) == r }
                    .filter { it !in dung && !npc.world.collision.isClipped(it) }
                    .minByOrNull { it.getDistance(p.tile) }
            }
            val target = free()
            if (target != null) { p.stopMovement(); p.teleportTo(target); return }
            fun rapid() { if (!eligible(p) || free() != null) return
                attack(p, 5, null, accuracy = false, minHit = 5); later(1) { rapid() } }
            rapid()
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
        /** N606 12-11 (eigenaar 'de mager npc in kephiri spawnd in de muur'): the 3x3 corner tiles (+6,+6)/(-6,+6) put the top row
         *  on z+7 = the north wall (floor runs centre-9..+7 in z). Shift a footprint toward the centre until every tile is free
         *  floor (max 3 steps); the documented corner stays the target, only the wall overlap is removed. */
        fun fitFloor(tile: Tile, size: Int): Tile {
            fun free(t: Tile) = (0 until size).all { dx -> (0 until size).all { dz -> !npc.world.collision.isClipped(t.transform(dx, dz)) } }
            var t = tile
            repeat(3) {
                if (free(t)) return t
                t = t.transform((centre.x - t.x).coerceIn(-1, 1), (centre.z - t.z).coerceIn(-1, 1))
            }
            return if (free(t)) t else tile
        }
        fun spawnOverlord(id: Int, tile: Tile): Npc? {
            // ONZEKER T14: reuse a surviving same-type overlord rather than create an unbounded duplicate across shield breaks.
            overlords[id]?.takeIf { it.isSpawned() && !it.isDead() }?.let { return it }
            val spec = when (id) { ToaBossNpcs.SOLDIER -> SOLDIER_STATS; ToaBossNpcs.SPITTING -> SPITTING_STATS; ToaBossNpcs.ARCANE -> ARCANE_STATS; else -> return null }
            return spawn(id, fitFloor(tile, 3), spec)?.also { child ->
                child.combatDef = spec.definition(context, scaleDefence = false).copy(completesEncounter = false,
                    deathAnimation = listOf(ToaBossFx.seqFor(child, spec.deathSeq)))
                applyBonuses(child)
                ToaBossFx.weakness(child, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.FIRE, 50)
                child.stats.setMaxLevel(2, spec.defence); child.stats.setCurrentLevel(2, spec.defence)
                overlords[id] = child
                // TOA-1911: the mobile overlords path AROUND Kephri's 5x5 body (naive steps stood still behind her).
                if (id != ToaBossNpcs.ARCANE) child.smartPathfinding = true
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
            cleanupDung.forEach { it() }; cleanupDung.clear(); dung.clear(); eggs.clear(); overlords.clear(); agile.clear(); maxHitTargets.clear(); arcaneCharge.clear(); soldierNext.clear()
            super.close()
        }
    }
}

/** Fixed combat levels and raid-level HP; group HP remains an independent source input. */
internal class ToaFixedContext(override val players: List<Player>, override val partySize: Int = 1) : ToaEncounterContext {
    override val raidLevel = 0; override val pathLevel = 0; override val invocations = emptySet<String>()
}
