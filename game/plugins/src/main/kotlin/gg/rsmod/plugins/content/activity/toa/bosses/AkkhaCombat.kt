package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*
import kotlin.math.abs

/** Sources: Akkha oldid15383708; Strategies oldid15392814 lines1108-1153; official Summer Sweep Up: Combat (25Jun2025).
 * T12/T01 are deliberately explicit: unpublished first-flash/impact/orb spawn timing is supplied by an evidence-backed director,
 * not inferred from a skeletal clip duration. Public mechanic methods below execute a supplied, validated sequence.
 */
object AkkhaCombat : CombatScript() {
    override val ids: IntArray get() = listOfNotNull(ToaBossNpcs.local(ToaBossNpcs.AKKHA)).toIntArray()
    val STATS = ToaNpcStats(400, 100, 140, 80, 100, 100, 6, 115, 115, 115, 30, 40, 40,
        60, 120, 120, 10, 60, 9792)
    val SHADOW_STATS = STATS.copy(hp = 70, defence = 30)
    // Wiki infobox gives 55 as overall max, not a complete per-style table. Do not silently use 55 for all three styles.
    const val PUBLISHED_MAX = 55
    fun autosBeforeSpecial(stayVigilant: Boolean) = if (stayVigilant) 5 else 6
    fun nextStyle(style: CombatClass) = when (style) {
        CombatClass.MELEE -> CombatClass.RANGED
        CombatClass.RANGED -> CombatClass.MAGIC
        else -> CombatClass.MELEE
    }
    fun shadowThreshold(stage: Int, max: Int): Int = max * (80 - 20 * stage) / 100
    fun enrageHits(size: Int) = 3 + size - 1
    fun blastInterval(feelingSpecial: Boolean) = if (feelingSpecial) 2 else 4
    fun extraMemoryBursts(path: Int) = minOf(path / 2, 2)
    fun orbDamage(beforeCap: Int, protected: Boolean): Int = minOf(25, if (protected) beforeCap * 3 / 4 else beforeCap)
    enum class Quadrant { FIRE, SHADOW, LIGHTNING, ICE }
    fun quadrant(t: Tile, centre: Tile): Quadrant = when {
        t.x < centre.x && t.z >= centre.z -> Quadrant.FIRE
        t.x >= centre.x && t.z >= centre.z -> Quadrant.SHADOW
        t.x >= centre.x -> Quadrant.LIGHTNING
        else -> Quadrant.ICE
    }
    fun adjacent(a: Quadrant, b: Quadrant): Boolean = abs(a.ordinal - b.ordinal) in listOf(1, 3)
    fun detonationHits(a: Tile, b: Tile, diagonal: Boolean): Boolean {
        val dx = abs(a.x - b.x); val dz = abs(a.z - b.z)
        return a.height == b.height && dx <= 1 && dz <= 1 && dx + dz > 0 &&
            if (diagonal) dx == dz else dx == 0 || dz == 0
    }
    data class Tank(val player: Player, val distance: Int, val highestDefence: Int, val bulwark: Boolean)
    fun tank(candidates: List<Tank>): Player? = candidates.withIndex().minWithOrNull(
        compareBy<IndexedValue<Tank>> { it.value.distance }
            .thenByDescending { it.value.highestDefence * if (it.value.bulwark) 3 else 2 }.thenBy { it.index })?.value?.player

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        if (npc.attr[ToaEncounter.SESSION]?.closed != false) {
            val c = ToaEncounter.context(npc) ?: return
            ToaEncounter.attach(npc, c, ::Session)
        }
    }
    class Session(npc: Npc, context: ToaEncounterContext) : ToaBossSession(npc, context) {
        override val stats = STATS
        var style = CombatClass.MELEE
        var autos = 0
        var shadowStage = 0
        var enraged = false
        var successfulHits = 0
        var memoryActive = false
        var target: Player? = null
        val shadows = linkedMapOf<Quadrant, Npc>()
        var centre = npc.spawnTile.transform(1, 1)
        // T02: these are explicit room inputs; the adapter must set the four actual corner spawn tiles.
        // ONZEKER T02: cache2686 square14676 floor-platform envelope3672..3687; NPC corners inferred inside that envelope.
        var shadowTiles: Map<Quadrant, Tile> = mapOf(Quadrant.FIRE to centre.transform(-7, 5), Quadrant.SHADOW to centre.transform(5, 5),
            Quadrant.LIGHTNING to centre.transform(5, -7), Quadrant.ICE to centre.transform(-7, -7))
        var specialDirector: ((Session) -> Unit)? = { it.defaultSpecial() }
        var orbDirector: ((Session) -> Unit)? = { it.defaultOrbs() }
        var shadowChargeDirector: ((Session) -> Unit)? = { it.chargeShadows() }
        // ONZEKER T12: standard NPC melee/ranged formula applied to published base levels/bonuses; magic uses same18 fallback.
        //55 in the infobox equals22 at the capped RL375 damage factor, and is not multiplied by2.5 again.
        var autoEvidence: Map<CombatClass, ToaAutoAttack> = mapOf(
            CombatClass.MELEE to ToaAutoAttack(22, CombatClass.MELEE, 1, 1, 4, prayerAtLaunch = true),
            CombatClass.RANGED to ToaAutoAttack(18, CombatClass.RANGED, 3, 1, 10, prayerAtLaunch = true),
            CombatClass.MAGIC to ToaAutoAttack(18, CombatClass.MAGIC, 3, 1, 10, prayerAtLaunch = true))
        private var shadowCharge = 0

        fun immune(): Boolean = memoryActive || (shadowStage > 0 && !enraged &&
            shadows[quadrant(npc.getCentreTile(), centre)]?.let { it.isSpawned() && !it.isDead() } != false
            )

        override fun incoming(hit: Hit) {
            var remaining = npc.getCurrentLifepoints()
            hit.hitmarks.forEach { mark ->
                val attackStyle = incomingStyle(mark.type)
                if (immune() || (!enraged && attackStyle == style) || (enraged && attackStyle != CombatClass.MELEE)) mark.damage = 0
                if (!enraged && mark.damage >= remaining) {
                    // Intercept before Pawn.hitsCycle starts a real death. Remaining marks see the new phase.
                    mark.damage = remaining.coerceAtLeast(0)
                    enterEnrage(preserveIncoming = true)
                    remaining = maxOf(1, npc.getMaximumLifepoints() / 5)
                } else {
                    remaining -= mark.damage
                    if (enraged && mark.damage > 0 && hit.source is Player) {
                        successfulHits++
                        if (successfulHits >= enrageHits(context.partySize)) {
                            successfulHits = 0
                            relocate()
                        }
                    }
                }
            }
        }
        override fun onTick() {
            val max = npc.getMaximumLifepoints()
            if (!enraged && shadowStage < 4 && npc.getCurrentLifepoints() <= shadowThreshold(shadowStage, max)) restoreShadows()
            if (enraged) { orbDirector?.invoke(this) ?: unresolved("T12: unstable-orb spawn/move cadence and base damage"); return }
            shadowChargeDirector?.invoke(this)
            if (memoryActive || tick < nextAttack) return
            val live = players()
            val tank = target?.takeIf { it in live } ?: live.firstOrNull() ?: return
            target = tank
            npc.facePawn(tank)
            follow(tank)
            val meleeTarget = live.firstOrNull { distance(it.tile) == 0 } ?: tank
            if (style == CombatClass.MELEE && !meleeReach(meleeTarget.tile)) return
            if (autos >= autosBeforeSpecial(inv("Stay Vigilant"))) {
                specialDirector?.invoke(this) ?: unresolved("T12: special selection/first impact/expiry; no guessed rotation")
                autos = 0; style = nextStyle(style); retarget()
            } else {
                val spec = autoEvidence[style]
                if (spec != null) {
                    animate(when (style) { CombatClass.MELEE -> 9770; CombatClass.RANGED -> 9772; else -> 9774 })
                    val victims = if (style == CombatClass.MELEE) live.filter { it.tile.isWithinRadius(meleeTarget.tile, 1) && meleeReach(it.tile) } else live
                    victims.forEach { spec.fire(this, it) }
                    if (inv("Keep Back") && style != CombatClass.MELEE) {
                        autoEvidence[CombatClass.MELEE]?.let { swipe -> live.filter { meleeReach(it.tile) }.forEach { swipe.fire(this, it) } }
                    }
                } else unresolved("T01/T12: Akkha $style per-style max/protection/flight")
                autos++
            }
            nextAttack = tick + stats.speed
        }
        fun retarget() {
            target = tank(players().map { p -> Tank(p, p.tile.getDistance(npc.getCentreTile()),
                (5..9).maxOf { p.equipmentBonuses[it] }, p.equipment[3]?.let {
                    p.world.definitions.get(ItemDef::class.java, it.id).name.startsWith("Dinh's bulwark")
                } == true) })
        }
        private fun defaultSpecial() {
            // ONZEKER T12: Wiki lists the choices but no weights; uniform selection among legal specials.
            val choices = if (context.partySize == 1) listOf(0, 2) else listOf(0, 1, 2)
            val selected = if (inv("Double Trouble")) {
                if (context.partySize == 1) listOf(0, 2) else choices.shuffled().take(2)
            } else listOf(choices[npc.world.random(choices.lastIndex)])
            selected.forEach { choice -> when (choice) {
                0 -> {
                    val count = 4 + extraMemoryBursts(context.pathLevel) // ONZEKER T12: four base flashes, one per arena quadrant.
                    val pattern = mutableListOf(Quadrant.values()[npc.world.random(3)])
                    repeat(count - 1) {
                        val options = Quadrant.values().filter { adjacent(pattern.last(), it) }
                        pattern += options[npc.world.random(options.lastIndex)]
                    }
                    pattern.forEachIndexed { i, q -> later(i * 2) {
                        shadowTiles[q]?.let { gfx(when(q) { Quadrant.FIRE ->2258; Quadrant.SHADOW ->2257; Quadrant.LIGHTNING ->2256; Quadrant.ICE ->2259 }, it) }
                    } }
                    // ONZEKER T12: flash2ticks=stomp seq9788(60cycles), disappear1tick=seq9784(30cycles), impact after full preview.
                    memoryBlast(pattern, count * 2 + 1, 22)
                }
                1 -> detonate(stats.speed, 22, inv("Feeling Special?") && npc.world.random(1) == 1)
                else -> trail(players().associateWith { Tile(it.tile) }, stats.speed * 2, 1, stats.speed, 22)
            } }
        }
        private fun defaultOrbs() {
            // ONZEKER T12: spawn cadence uses published6tick attack interval; travel one tile/tick, base shadow max22.
            if (tick % stats.speed != 0) return
            val d = Direction.values().filter { it != Direction.NONE }.random()
            val edge = centre.transform(-d.getDeltaX() * 8, -d.getDeltaZ() * 8)
            unstableOrb(edge, d, 17, 1, 22)
        }
        private fun chargeShadows() {
            if (memoryActive || shadowStage == 0) return
            // ONZEKER T12: charge spans six of Akkha's6tick autos; no published hourglass duration.
            if (++shadowCharge < stats.speed * 6) return
            shadowCharge = 0
            shadows.forEach { (q, shadow) -> if (shadow.isSpawned() && !shadow.isDead()) {
                animate(9788, shadow); players().filter { quadrant(it.tile, centre) == q }.forEach { elementalHit(it, 22) }
            } }
        }
        fun restoreShadows() {
            shadowStage++
            shadowCharge = 0
            shadows.values.toList().forEach { remove(it) }; shadows.clear()
            if (shadowTiles.size == 4) shadowTiles.forEach { (q, t) -> spawn(ToaBossNpcs.AKKHA_SHADOW, t, SHADOW_STATS)?.let { shadows[q] = it } }
            else unresolved("T02: four shadow corner coordinates")
            message(if (shadowStage == 1) "Shadows appear throughout the room!" else "The shadows in the room are restored!")
        }
        fun enterEnrage(preserveIncoming: Boolean = false) {
            if (enraged) return
            enraged = true; newPhase(); memoryActive = false
            val restored = maxOf(1, npc.getMaximumLifepoints() / 5)
            npc.setCurrentLifepoints(if (preserveIncoming) npc.getCurrentLifepoints() + restored else restored)
            shadows.values.toList().forEach { remove(it) }; shadows.clear()
            relocate()
        }
        private fun relocate() {
            val choices = shadowTiles.values.filter { !sameTile(it, npc.tile) }
            choices.randomOrNull()?.let { npc.moveTo(it); animate(9785) }
        }
        /** Validate the whole pattern before scheduling anything; adjacent flash rule is not a diagonal random walk. */
        fun memoryBlast(pattern: List<Quadrant>, firstImpact: Int, baseDamage: Int) {
            require(pattern.isNotEmpty() && pattern.zipWithNext().all { adjacent(it.first, it.second) })
            require(firstImpact >= 0 && baseDamage >= 0)
            memoryActive = true; animate(9784); message("Sections of the room start to glow...")
            val interval = blastInterval(inv("Feeling Special?"))
            pattern.forEachIndexed { i, safe -> later(firstImpact + i * interval) {
                players().filter { quadrant(it.tile, centre) != safe }.forEach { elementalHit(it, baseDamage) }
            } }
            later(firstImpact + pattern.lastIndex * interval + interval) { memoryActive = false; animate(9785) }
        }
        fun detonate(delay: Int, baseDamage: Int, diagonal: Boolean = false) {
            if (context.partySize == 1) return
            message("You have been marked for detonation!")
            later(delay) {
                val live = players(); val tiles = live.associateWith { Tile(it.tile) }
                live.forEach { victim -> if (tiles.any { (source, t) -> source !== victim && detonationHits(t, tiles.getValue(victim), diagonal) }) elementalHit(victim, baseDamage) }
            }
        }
        fun elementalHit(p: Player, baseDamage: Int) {
            attack(p, ToaScale.damage(baseDamage, context), null, accuracy = false)
            gfx(when (quadrant(p.tile, centre)) { Quadrant.FIRE -> 2258; Quadrant.SHADOW -> 2257; Quadrant.LIGHTNING -> 2256; Quadrant.ICE -> 2259 }, p.tile)
            // ONZEKER T12: expiry uses four published6tick attack intervals; proc cadence derives from2tick stomp seq9788.
            when (quadrant(p.tile, centre)) {
                Quadrant.FIRE -> repeat(4) { i -> later((i + 1) * stats.speed) { if (eligible(p)) attack(p, 5, null, accuracy = false, minHit = 5) } }
                Quadrant.SHADOW -> {
                    listOf(gg.rsmod.plugins.api.Skills.ATTACK, gg.rsmod.plugins.api.Skills.STRENGTH, gg.rsmod.plugins.api.Skills.DEFENCE,
                        gg.rsmod.plugins.api.Skills.RANGED, gg.rsmod.plugins.api.Skills.MAGIC).forEach { skill -> p.skills.alterCurrentLevel(skill, -3) }
                    repeat(3) { i -> later((i + 1) * 2) { if (eligible(p)) attack(p, 3, null, accuracy = false, minHit = 3) } }
                }
                Quadrant.LIGHTNING -> gg.rsmod.plugins.content.mechanics.prayer.Prayers.disableOverheads(p, stats.speed)
                Quadrant.ICE -> {
                    val timer = gg.rsmod.game.model.timer.ATTACK_DELAY
                    p.timers[timer] = (if (p.timers.has(timer)) p.timers[timer] else 0) + 1
                    later(stats.speed * 4) { if (eligible(p)) p.message("The ice around you melts away.") }
                }
            }
        }
        fun trail(previous: Map<Player, Tile>, duration: Int, armDelay: Int, orbLife: Int, baseDamage: Int) {
            require(duration > 0 && armDelay >= 0 && orbLife > 0)
            message("Magical orbs begin to materialise around you!")
            val last = previous.toMutableMap()
            fun sample(left: Int) {
                if (left == 0) { message("The magical orbs stop materialising."); return }
                players().forEach { p ->
                    val old = last.put(p, Tile(p.tile)) ?: return@forEach
                    if (!sameTile(old, p.tile)) {
                        trailOrb(old, armDelay, orbLife, baseDamage)
                        if (inv("Feeling Special?")) {
                            val dx = (p.tile.x - old.x).coerceIn(-1, 1); val dz = (p.tile.z - old.z).coerceIn(-1, 1)
                            // Strategies/ToA invocation descriptions: additional orb two tiles in front.
                            trailOrb(p.tile.transform(dx * 2, dz * 2), armDelay, orbLife, baseDamage)
                        }
                    }
                }
                later(1) { sample(left - 1) }
            }
            sample(duration)
        }
        private fun trailOrb(tile: Tile, armDelay: Int, life: Int, baseDamage: Int) {
            val id = when (quadrant(tile, centre)) { Quadrant.FIRE -> 11802; Quadrant.SHADOW -> 11801; Quadrant.LIGHTNING -> 11800; Quadrant.ICE -> 11803 }
            val orb = spawn(id, tile, SHADOW_STATS.copy(hp = 1), path = false, group = false)
            fun check(left: Int) {
                if (left == 0) { orb?.let { remove(it) }; return }
                val touched = players().filter { sameTile(it.tile, tile) }
                if (touched.isNotEmpty()) { touched.forEach { elementalHit(it, baseDamage) }; orb?.let { remove(it) }; return }
                later(1) { check(left - 1) }
            }
            later(armDelay) { check(life) }
        }
        fun unstableOrb(start: Tile, direction: Direction, steps: Int, interval: Int, baseDamage: Int) {
            require(steps > 0 && interval > 0 && baseDamage >= 0 && direction != Direction.NONE)
            val orb = spawn(ToaBossNpcs.UNSTABLE_ORB, start, SHADOW_STATS.copy(hp = 1), path = false, group = false) ?: return
            orb.faceTile(start.transform(direction.getDeltaX(), direction.getDeltaZ()))
            fun move(left: Int) {
                if (!orb.isSpawned()) return
                if (left == 0) { remove(orb); return }
                val touched = players().filter { sameTile(it.tile, orb.tile) }
                if (touched.isNotEmpty()) {
                    val beforeCap = ToaScale.damage(baseDamage, context) / touched.size
                    touched.forEach { p ->
                        val damage = orbDamage(beforeCap, p.isProtectedFrom(CombatClass.MAGIC))
                        attack(p, damage, null, accuracy = false, minHit = damage); gfx(2260, p.tile)
                    }
                    remove(orb); return
                }
                orb.moveTo(orb.tile.transform(direction.getDeltaX(), direction.getDeltaZ()))
                later(interval) { move(left - 1) }
            }
            move(steps)
        }
    }
}

/** Each supplied field must have a server/tick source. A missing T01 field does not acquire an invented default. */
data class ToaAutoAttack(val baseMax: Int, val style: CombatClass, val delay: Int,
    val protectionNumerator: Int = 0, val protectionDenominator: Int = 1, val prayerAtLaunch: Boolean = false) {
    init { require(baseMax >= 0 && delay >= 0 && protectionDenominator > 0 && protectionNumerator in 0..protectionDenominator) }
    fun fire(s: ToaBossSession, p: Player) {
        if (style != CombatClass.MELEE) ToaBossFx.projectile(s.npc, p, if (style == CombatClass.MAGIC) 2253 else 2255, style == CombatClass.MAGIC, delay)
        s.attack(p, ToaScale.damage(baseMax, s.context), style, delay, protectionNumerator, protectionDenominator,
            prayerAtLaunch = prayerAtLaunch)
    }
}
