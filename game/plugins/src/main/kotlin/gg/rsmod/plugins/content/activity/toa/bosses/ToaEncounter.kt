package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.PlayerAttackRules
import gg.rsmod.plugins.content.combat.removeCombatTarget
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import kotlin.math.abs
import kotlin.math.max

/** Room adapter. The raid owns membership, coordinates and completion/rewards.
 * Owner 2026-11-06 21:5x: Entry/Normal/Expert are RL0/150/300. Legacy path/invocation
 * fields remain source-compatible with the raid adapter but have no gameplay effects.
 */
interface ToaEncounterContext {
    val players: List<Player>
    val partySize: Int
    val raidLevel: Int
    val pathLevel: Int
    val invocations: Set<String>
    /** Raid adapters MUST delegate to ToaRoomScope.spawn so child damageMap contributes to room points/loot.
     * The standalone debugger has no raid scope and uses the ordinary world spawner.
     */
    fun spawnChild(npc: Npc): Boolean = npc.world.spawn(npc)
}

/** A bounded, public test encounter. A player leaving the room is immediately ineligible for pending effects. */
class StandaloneToaContext(
    private val owner: Player,
    private val centre: Tile,
    override val raidLevel: Int = 0,
    override val partySize: Int = 1,
    override val pathLevel: Int = 0,
    override val invocations: Set<String> = emptySet(),
) : ToaEncounterContext {
    init { ToaScale.validate(this) }
    private val participants: List<Player> = run {
        val nearby = mutableListOf<Player>()
        owner.world.players.forEach { if (it !== owner && it.isOnline && it.tile.height == centre.height && it.tile.isWithinRadius(centre, 24)) nearby += it }
        listOf(owner) + nearby.sortedBy { it.tile.getDistance(centre) }.take(partySize - 1)
    }
    // Test arena envelope, not an assertion about the OSRS lair's collision/spawn coordinates (T02).
    override val players: List<Player>
        get() {
            return participants.filter { it.isOnline && it.tile.height == centre.height && it.tile.isWithinRadius(centre, 24) }
        }
}

/** Wiki ToA scaling (10-schaal.tsv). All values are real HP, never the combat DSL's historical HP*10 unit.
 * T04: one final integer truncation is explicit here; hidden OSRS intermediate rounding remains unverified.
 */
object ToaScale {
    fun validate(c: ToaEncounterContext) {
        require(c.partySize in 1..8)
        require(c.raidLevel in 0..600)
        require(c.pathLevel in 0..6)
    }
    fun groupTenths(size: Int): Int {
        require(size in 1..8)
        return 10 + 9 * minOf(size - 1, 2) + 6 * maxOf(size - 3, 0)
    }
    fun pathPercent(level: Int): Int {
        require(level in 0..6)
        return 100 // Owner: no path levels, including legacy contexts supplied by older adapters.
    }
    fun hp(base: Int, c: ToaEncounterContext, path: Boolean = true, group: Boolean = true): Int =
        (base.toLong() * (250 + c.raidLevel) * (if (path) pathPercent(c.pathLevel) else 100) *
            (if (group) groupTenths(c.partySize) else 10) / 250000).toInt().coerceAtLeast(1)
    // Wiki: damage increases 0.4% per RL, capped at RL375; group size changes HP, not damage.
    fun damage(base: Int, c: ToaEncounterContext, path: Boolean = true): Int =
        (base.toLong() * (250 + minOf(c.raidLevel, 375)) * (if (path) pathPercent(c.pathLevel) else 100) / 25000).toInt()
    fun stat(base: Int, c: ToaEncounterContext): Int = (base.toLong() * (250 + c.raidLevel) / 250).toInt()
}

/** Base OSRS protection fractions. The simplified raid never weakens prayer through invocations. */
object ToaProtection {
    fun damage(roll: Int, protected: Boolean, numerator: Int = 0, denominator: Int = 1, @Suppress("UNUSED_PARAMETER") quiet: Boolean = false): Int {
        require(roll >= 0 && denominator > 0 && numerator in 0..denominator)
        if (!protected) return roll
        // Owner21:5x: the obsolete Quiet Prayers argument is ignored, including direct legacy callers.
        return roll * numerator / denominator
    }
}

data class ToaNpcStats(
    val hp: Int, val attack: Int, val strength: Int, val defence: Int, val magic: Int, val ranged: Int,
    val speed: Int, val attackBonus: Int = 0, val magicBonus: Int = 0, val rangedBonus: Int = 0,
    val strengthBonus: Int = 0, val rangedStrength: Int = 0, val magicStrength: Int = 0,
    val stab: Int = 0, val slash: Int = 0, val crush: Int = 0, val mage: Int = 0, val range: Int = 0,
    val deathSeq: Int = -1,
    val species: Set<Any> = emptySet(),
    val meleeStyle: StyleType = StyleType.SLASH,
) {
    fun definition(c: ToaEncounterContext, path: Boolean = true, group: Boolean = true, scaleDefence: Boolean = true): NpcCombatDef {
        ToaScale.validate(c)
        val bonuses = MutableList(18) { 0 }
        // The melee formula reads ATTACK_STAB/SLASH/CRUSH (0..2); NPC-specific bonus helpers still use10..13.
        for (index in 0..2) bonuses[index] = attackBonus
        bonuses[3] = magicBonus; bonuses[4] = rangedBonus
        bonuses[5] = stab; bonuses[6] = slash; bonuses[7] = crush; bonuses[8] = mage; bonuses[9] = range
        bonuses[10] = attackBonus; bonuses[11] = strengthBonus; bonuses[12] = rangedStrength; bonuses[13] = magicStrength
        return NpcCombatDef.DEFAULT.copy(
            lifepoints = ToaScale.hp(hp, c, path, group),
            // T04: Wiki specifies accuracy/defence scaling, not Strength-derived max hits. Scripted maxima scale separately.
            stats = listOf(ToaScale.stat(attack, c), strength, if (scaleDefence) ToaScale.stat(defence, c) else defence,
                ToaScale.stat(magic, c), ToaScale.stat(ranged, c)),
            attackStyleType = meleeStyle, attackSpeed = speed, bonuses = bonuses, respawnDelay = -1, poisonImmunity = true, venomImmunity = true,
            deathAnimation = listOf(ToaBossFx.seq(deathSeq)), waitFullDeathAnimation = true,
            species = species,
        )
    }
}

/** Common ownership/lifecycle. No global encounter singleton: two rooms cannot share timers, minions or phase state. */
object ToaEncounter {
    data class Definition(val osrsNpc: Int, val stats: ToaNpcStats, val factory: (Npc, ToaEncounterContext) -> ToaBossSession)
    val definitions = linkedMapOf<String, Definition>()
    val CONTEXT = AttributeKey<ToaEncounterContext>()
    val SESSION = AttributeKey<ToaBossSession>()
    val MINION_SESSION = AttributeKey<ToaBossSession>()
    val MELEE_STYLE = AttributeKey<StyleType>()
    // SESSION on a player is also the ::toaboss debugger's ownership handle. A raid cache must not grant that command ownership.
    internal val PLAYER_SESSION = AttributeKey<ToaBossSession>()
    private val ACTIVE = AttributeKey<MutableSet<ToaBossSession>>()

    fun context(npc: Npc): ToaEncounterContext? = npc.attr[CONTEXT]
    fun forPlayer(player: Player): ToaBossSession? {
        val cached = player.attr[PLAYER_SESSION] ?: player.attr[SESSION]
        cached?.takeIf { it.npc.world === player.world && it.active() && it.eligible(player) }?.let { return it }
        player.attr.remove(PLAYER_SESSION)
        // A late room entrant may not have requested its encounter before.
        // Search only this world's attached encounters, never the world's entire NPC slot array.
        return player.world.attr[ACTIVE]?.firstOrNull { it.active() && it.eligible(player) }?.also { it.bind(player) }
    }
    internal fun unregister(session: ToaBossSession) {
        if (!session.attached) return
        session.attached = false
        val world = session.npc.world
        world.attr[ACTIVE]?.let { sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) world.attr.remove(ACTIVE)
        }
    }
    fun attach(npc: Npc, context: ToaEncounterContext, factory: (Npc, ToaEncounterContext) -> ToaBossSession): ToaBossSession {
        ToaScale.validate(context)
        npc.attr[SESSION]?.close()
        npc.attr[CONTEXT] = context
        val session = factory(npc, context)
        npc.attr[SESSION] = session
        session.configure()
        npc.world.attr[ACTIVE].let { existing ->
            val sessions = existing ?: linkedSetOf<ToaBossSession>().also { npc.world.attr[ACTIVE] = it }
            sessions += session
        }
        session.attached = true
        session.players().forEach(session::bind)
        npc.world.queue {
            try {
                while (session.active()) {
                    if (session.players().isEmpty()) break
                    session.cycle()
                    wait(1)
                }
            } finally {
                session.close()
                if (context is StandaloneToaContext) session.finishStandalone(npc)
            }
        }
        return session
    }
    fun stop(npc: Npc) { npc.attr[SESSION]?.close() }
    /** Resolve before dealHit rolls: landed hits retain ordinary damage-map, XP and launch-style accounting. */
    fun playerAttackRule(player: Player, target: Npc, type: HitType): PlayerAttackRules.Rule? {
        val session = target.attr[SESSION] ?: target.attr[MINION_SESSION] ?: return null
        if (!target.isSpawned() || target.isDead() || !session.active() || !session.eligible(player)) return null
        return session.playerAttackRule(player, target, type)
    }
}

abstract class ToaBossSession(npc: Npc, input: ToaEncounterContext) {
    /** Active form; multi-form encounters retain their adapter root separately for completion. */
    var npc: Npc = npc
        protected set
    val context: ToaEncounterContext = object : ToaEncounterContext {
        override val players get() = input.players
        override val partySize get() = input.partySize
        override val raidLevel get() = input.raidLevel
        override val pathLevel = 0
        override val invocations = emptySet<String>()
        override fun spawnChild(npc: Npc): Boolean = input.spawnChild(npc)
    }
    init { ToaScale.validate(input) }
    var closed = false
        private set
    var tick = 0
        private set
    var nextAttack = 0
    var generation = 0
        private set
    val children = mutableListOf<Npc>()
    internal var attached = false
    private val boundPlayers = mutableSetOf<Player>()
    internal fun bind(player: Player) {
        player.attr[ToaEncounter.PLAYER_SESSION] = this
        boundPlayers += player
    }
    private data class Event(val tick: Int, val generation: Int?, val action: () -> Unit)
    private val events = mutableListOf<Event>()
    private val objectCleanup = mutableSetOf<() -> Unit>()
    abstract val stats: ToaNpcStats
    open val minimumDefence: Int = 0
    fun applyBonuses(actor: Npc = npc) {
        actor.equipmentBonuses.fill(0)
        actor.combatDef.bonuses.forEachIndexed { i, bonus -> if (i < actor.equipmentBonuses.size) actor.equipmentBonuses[i] = bonus }
    }
    open fun configure() {
        npc.respawnOverride = false
        npc.naivePathfinding = true
        npc.attr[ToaEncounter.MELEE_STYLE] = stats.meleeStyle
        npc.combatDef = stats.definition(context).let { d -> d.copy(deathAnimation = listOf(ToaBossFx.seqFor(npc, stats.deathSeq))) }
        applyBonuses()
        npc.combatDef.stats.forEachIndexed { i, level -> npc.stats.setMaxLevel(i, level); npc.stats.setCurrentLevel(i, level) }
        npc.setCurrentLifepoints(npc.getMaximumLifepoints())
        npc.hitModifier = { hit ->
            val source = hit.source
            if (source is Player && !eligible(source)) hit.hitmarks.forEach { it.damage = 0 } else incoming(hit)
        }
    }
    fun players(): List<Player> = context.players.filter { it.isOnline && !it.isDead() && it.tile.height == npc.tile.height }
    fun eligible(p: Player): Boolean = !closed && p.isOnline && !p.isDead() && players().any { it === p }
    fun active(): Boolean = !closed && npc.isSpawned() && !npc.isDead()
    fun cycle() {
        if (!active()) return
        children.removeAll { !it.isSpawned() }
        if (minimumDefence > 0 && npc.stats.getCurrentLevel(2) < ToaScale.stat(minimumDefence, context)) {
            npc.stats.setCurrentLevel(2, ToaScale.stat(minimumDefence, context))
        }
        val due = events.filter { it.tick <= tick }.toList()
        events.removeAll(due.toSet())
        for (e in due) {
            if (!active()) return
            if (e.generation == null || e.generation == generation) e.action()
        }
        if (active()) onTick()
        tick++
    }
    abstract fun onTick()
    open fun incoming(hit: Hit) {}
    /** The debug root normally disappears on exit. Multi-form encounters may retain it for their final death sequence. */
    open fun finishStandalone(root: Npc) {
        if (root.isSpawned() && !root.isDead()) root.world.remove(root)
    }
    open fun playerAttackRule(player: Player, target: Npc, type: HitType): PlayerAttackRules.Rule? = null
    fun later(delay: Int, acrossPhase: Boolean = false, action: () -> Unit) {
        require(delay >= 0)
        if (delay == 0) { if (active()) action(); return }
        events += Event(tick + delay, if (acrossPhase) null else generation, action)
    }
    /** Cancels previous phase's projectiles, telegraphs and scheduled hits together. */
    fun newPhase() { generation++; events.removeAll { it.generation != null } }
    open fun close() {
        if (closed) return
        closed = true; generation++; events.clear()
        ToaEncounter.unregister(this)
        boundPlayers.forEach { if (it.attr[ToaEncounter.PLAYER_SESSION] === this) it.attr.remove(ToaEncounter.PLAYER_SESSION) }
        boundPlayers.clear()
        children.toList().forEach { if (it.isSpawned()) npc.world.remove(it) }
        children.clear()
        objectCleanup.toList().asReversed().forEach { it() }; objectCleanup.clear()
        npc.stopMovement()
        npc.removeCombatTarget()
        npc.hitModifier = null
    }
    fun spawn(osrs: Int, tile: Tile, spec: ToaNpcStats, path: Boolean = true, group: Boolean = true): Npc? {
        val id = ToaBossNpcs.local(osrs) ?: run { missingAsset("npc $osrs"); return null }
        val child = Npc(id, Tile(tile), npc.world)
        child.respawnOverride = false
        child.attr[ToaEncounter.MINION_SESSION] = this
        child.naivePathfinding = true
        child.attr[ToaEncounter.MELEE_STYLE] = spec.meleeStyle
        if (!context.spawnChild(child)) return null
        child.combatDef = spec.definition(context, path, group).let { d -> d.copy(completesEncounter = false, deathAnimation = listOf(ToaBossFx.seqFor(child, spec.deathSeq))) }
        applyBonuses(child)
        child.combatDef.stats.forEachIndexed { i, level -> child.stats.setMaxLevel(i, level); child.stats.setCurrentLevel(i, level) }
        child.setCurrentLifepoints(child.getMaximumLifepoints())
        child.hitModifier = { hit -> if (hit.source is Player && !eligible(hit.source as Player)) hit.hitmarks.forEach { it.damage = 0 } }
        children += child
        return child
    }
    fun remove(child: Npc) { children.remove(child); if (child.isSpawned()) npc.world.remove(child) }
    /** Dynamic encounter locs retain collision from their imported definition, and restore the displaced scenery on cleanup. */
    fun placeObject(osrs: Int, tile: Tile, rotation: Int = 0): () -> Unit {
        val id = ToaBossFx.loc(osrs) ?: run { missingAsset("loc $osrs"); return {} }
        val previous = npc.world.getObject(tile, ObjectType.INTERACTABLE)
        // Imported loc definitions decide collision. Never mutate the shared cache definitions.
        val obj = DynamicObject(id, 10, rotation, Tile(tile))
        npc.world.spawn(obj)
        var removed = false
        lateinit var cleanup: () -> Unit
        cleanup = {
            if (!removed) {
                removed = true
                if (obj.isSpawned(npc.world)) {
                    npc.world.remove(obj)
                    previous?.let { npc.world.spawn(DynamicObject(it)) }
                }
                objectCleanup.remove(cleanup)
            }
        }
        objectCleanup += cleanup
        return cleanup
    }
    fun animate(osrs: Int, who: Npc = npc) { ToaBossFx.seqFor(who, osrs).takeIf { it >= 0 }?.let { who.animate(it) } }
    fun gfx(osrs: Int, tile: Tile) { ToaBossFx.spot(osrs)?.let { npc.world.spawn(gg.rsmod.game.model.TileGraphic(tile, it, 0)) } }
    fun message(text: String) { players().forEach { it.message(text) } }
    fun inv(@Suppress("UNUSED_PARAMETER") name: String): Boolean = false // Owner: no invocation-specific rules.
    fun heal(amount: Int) { npc.setCurrentLifepoints(minOf(npc.getMaximumLifepoints(), npc.getCurrentLifepoints() + amount)) }
    fun distance(tile: Tile, origin: Tile = npc.tile, size: Int = npc.getSize()): Int =
        max(maxOf(origin.x - tile.x, 0, tile.x - (origin.x + size - 1)), maxOf(origin.z - tile.z, 0, tile.z - (origin.z + size - 1)))
    open fun meleeReach(tile: Tile): Boolean = actorMeleeReach(npc, tile)
    fun actorMeleeReach(actor: Npc, tile: Tile): Boolean {
        val t = actor.tile; val size = actor.getSize()
        if (tile.height != t.height) return false
        // Preserve the encounter's underfoot attacks, but share the engine's wall check for adjacent bodies (N596). The edge is
        // checked from the player's (walkable) tile: a boss on blocked terrain (Ba-Ba's throne dais x3819+, Zebak's pool) failed
        // the check from its own side and never hit back (07-11 testclient, owner 'ba ba attackt niet eens terug'). Real walls
        // flag both sides of the edge, so they still block.
        return distance(tile, t, size) == 0 || gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(
            actor.world.collision, tile, 1, 1, t, size, size)
    }
    fun follow(p: Player) { if (!meleeReach(p.tile)) npc.walkTo(p.tile) }
    /** Resolve protection at impact (Zebak), and cancel on phase/death/logout/teleport/reset. */
    fun attack(p: Player, maxHit: Int, style: CombatClass?, delay: Int = 0,
        protectedNumerator: Int = 0, protectedDenominator: Int = 1, accuracy: Boolean = true,
        prayerAtLaunch: Boolean = false, minHit: Int = 0,
        actor: Npc = npc, after: (Int) -> Unit = {}) {
        require(maxHit >= 0 && minHit in 0..maxHit && delay >= 0)
        require(protectedDenominator > 0 && protectedNumerator in 0..protectedDenominator)
        if (!eligible(p)) return
        val phase = generation
        val launchProtection = if (prayerAtLaunch && style != null) p.isProtectedFrom(style) else null
        if (style != null) actor.prepareAttack(style, when (style) {
            CombatClass.MELEE -> actor.attr[ToaEncounter.MELEE_STYLE] ?: StyleType.SLASH
            CombatClass.RANGED -> StyleType.RANGED
            else -> StyleType.MAGIC
        }, WeaponStyle.ACCURATE)
        val lands = !accuracy || style == null || when (style) {
            CombatClass.MELEE -> MeleeCombatFormula.getUnprotectedAccuracy(actor, p)
            CombatClass.RANGED -> RangedCombatFormula.getUnprotectedAccuracy(actor, p)
            else -> MagicCombatFormula.getUnprotectedAccuracy(actor, p)
        } >= npc.world.randomDouble()
        val rolled = if (lands) npc.world.random(minHit..maxHit) else 0
        later(delay) {
            if (!eligible(p) || !actor.isSpawned() || actor.isDead()) return@later
            val protected = launchProtection ?: (style != null && p.isProtectedFrom(style))
            val damage = ToaProtection.damage(rolled, protected, protectedNumerator, protectedDenominator)
            val hit = actor.dealHit(p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = lands && damage > 0,
                delay = 0, hitType = when (style) { CombatClass.MELEE -> HitType.MELEE; CombatClass.RANGED -> HitType.RANGE;
                    CombatClass.MAGIC -> HitType.MAGIC; else -> HitType.REGULAR_HIT }, applyDeflectProtection = false)
            hit.hit.setCancelIf { !active() || generation != phase || !eligible(p) || !actor.isSpawned() || actor.isDead() }
            hit.hit.addAction {
                val dealt = hitmarks.sumOf { it.damage }
                after(dealt)
            }
        }
    }
    fun incomingStyle(type: Int): CombatClass? = when (type) {
        HitType.MELEE.id, HitType.CRIT_MELEE.id -> CombatClass.MELEE
        HitType.RANGE.id, HitType.CRIT_RANGE.id -> CombatClass.RANGED
        HitType.MAGIC.id, HitType.CRIT_MAGIC.id -> CombatClass.MAGIC
        else -> null
    }
    fun incomingStyle(hit: Hit, type: Int): CombatClass? = hit.sourceCombatClass ?: incomingStyle(type)
    fun sameTile(a: Tile, b: Tile): Boolean = a.height == b.height && a.x == b.x && a.z == b.z
    private val missing = mutableSetOf<String>()
    fun missingAsset(key: String) {
        if (missing.add(key)) gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            "ToA ${npc.id}: missing imported asset $key (N462 T01/T02; regenerate cfg/toa/ids.tsv after imports)"
        }
    }
}
