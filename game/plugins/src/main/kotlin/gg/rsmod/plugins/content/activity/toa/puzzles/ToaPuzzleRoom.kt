package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.path.MeleeReach
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import java.util.IdentityHashMap

/** Room-generation ownership for the three puzzle cards; never registers a boss or Path of Het. */
object ToaPuzzles {
    private val active = IdentityHashMap<ToaRaid, ToaPuzzleRoom>()
    val OWNER = AttributeKey<ToaPuzzleRoom>()
    val SOURCE = AttributeKey<Int>()
    fun of(player: Player): ToaPuzzleRoom? = ToaLobby.of(player.world).raid(player.uid)?.let(active::get)
        ?.takeIf { it.eligible(player) }
    internal fun attach(room: ToaPuzzleRoom) { check(active.putIfAbsent(room.raid, room) == null) }
    internal fun detach(room: ToaPuzzleRoom) { if (active[room.raid] === room) active.remove(room.raid) }
    fun registerCrondis(): AutoCloseable = ToaRooms.register(ToaRoomKey(ToaPath.CRONDIS, false), ::CrondisRoom)
}

/** Ground items use the same scope lifetime as actors/locs; no ambient/world-global item spawns. */
internal fun ToaRoomScope.spawn(item: GroundItem) {
    check(active && raid.map.area.contains(item.tile))
    raid.world.spawn(item)
    onClose { if (item.isSpawned(raid.world)) raid.world.remove(item) }
}

abstract class ToaPuzzleRoom(val raid: ToaRaid) : ToaRoom {
    lateinit var scope: ToaRoomScope
        private set
    var tick = 0
        private set
    val live: Boolean get() = ::scope.isInitialized && scope.active
    protected val players: List<Player> get() = if (live) raid.activePlayers()
        .filter { it.tile.height == scope.run.key.path?.challengeEntry?.height } else emptyList()
    private val present = linkedMapOf<PlayerUID, Player>()
    private val warnings = hashSetOf<String>()
    private val lastAction = hashMapOf<PlayerUID, Int>()
    private val placed = hashMapOf<Pair<Tile, Int>, GameObject>()
    private val points = hashMapOf<PlayerUID, Int>()
    private var credited = false

    final override fun start(run: ToaRoomRun) {
        scope = raid.scope(run) ?: return
        ToaPuzzles.attach(this)
        scope.onClose { closePuzzle(); present.clear(); lastAction.clear(); placed.clear(); points.clear(); ToaPuzzles.detach(this) }
        if (!startPuzzle()) return
        scope.queue {
            while (scope.active) {
                //A newly invoked world queue immediately cycles its first wait; wait BEFORE work to avoid two room ticks on startup.
                wait(1)
                if (!live) return@queue
                val current = players.associateBy { it.uid }
                present.filterKeys { it !in current }.values.toList().forEach { leave(it) }
                current.filterKeys { it !in present }.values.forEach { enter(it) }
                present.clear(); present.putAll(current)
                cycle()
                tick++
            }
        }
    }

    protected abstract fun startPuzzle(): Boolean
    protected abstract fun cycle()
    protected abstract fun closePuzzle()
    protected fun award(player: PlayerUID, amount: Int) {
        require(amount >= 0)
        if (live && player in raid.roster && !credited) points[player] = (points[player] ?: 0) + amount
    }
    final override fun clear(run: ToaRoomRun) {
        if (!::scope.isInitialized || scope.run !== run || credited) return
        credited = true
        //Same room boundary as ToaChest's NPC damage maps. A wipe cannot leak a failed attempt's puzzle points into the retry.
        points.forEach { (uid, amount) -> raid.points.damage(uid, amount) }
        points.clear()
    }
    protected open fun enter(player: Player) {}
    protected open fun leave(player: Player) {}
    fun eligible(player: Player): Boolean = live && player in players

    /** Serialise packet actions against the world tick; two different players remain independent. */
    protected fun act(player: Player): Boolean {
        if (!eligible(player) || lastAction[player.uid] == raid.world.currentCycle) return false
        lastAction[player.uid] = raid.world.currentCycle
        return true
    }

    protected fun instance(source: Tile): Tile = ToaMap.instance(raid.map, source)
    protected fun source(tile: Tile): Tile? = ToaMap.source(raid.map, tile)
    protected fun missing(fact: String): Boolean {
        if (warnings.add(fact)) Trace.problem(Trace.Cat.COMBAT) {
            "ToA puzzle ${scope.run.key}: missing OSRS reference/asset $fact; N462 import/reference check required"
        }
        return false
    }

    protected fun spawn(sourceId: Int, tile: Tile, definition: NpcCombatDef? = null): Npc? {
        val id = ToaIds.npc(sourceId) ?: run { missing("npc:$sourceId"); return null }
        val npc = Npc(id, instance(tile), raid.world)
        npc.respawnOverride = false
        npc.walkRadius = 0
        npc.naivePathfinding = true //Existing OSRS diagonal-first route strategy; no new movement rules.
        npc.attr[ToaPuzzles.OWNER] = this
        npc.attr[ToaPuzzles.SOURCE] = sourceId
        if (!ToaPuzzleActors.spawn(scope, npc, definition)) return null
        val previousModifier = npc.hitModifier
        npc.hitModifier = { hit ->
            previousModifier?.invoke(hit)
            val attacker = hit.source as? Player
            if (attacker != null && !eligible(attacker)) hit.hitmarks.forEach { it.damage = 0 }
        }
        return npc
    }

    protected fun place(osrs: Int, tile: Tile, shape: Int = 10, rotation: Int = 0): GameObject? {
        val id = ToaIds.loc(osrs) ?: run { missing("loc:$osrs"); return null }
        val key = tile to raid.world.locLayer(shape)
        placed[key]?.takeIf { it.id == id && it.type == shape && it.rot == rotation && it.isSpawned(raid.world) }?.let { return it }
        return DynamicObject(id, shape, rotation, instance(tile)).also { scope.spawn(it); placed[key] = it }
    }

    /** Hide only a loc placed by this scope. Its displaced original is restored by the scope at cleanup. */
    protected fun unplace(tile: Tile, shape: Int = 22) {
        placed.remove(tile to raid.world.locLayer(shape))?.let { if (it.isSpawned(raid.world)) raid.world.remove(it) }
    }

    protected fun animate(npc: Npc, osrsSeq: Int) {
        val seq = ToaIds.npcSeq(npc.attr[ToaPuzzles.SOURCE] ?: return, osrsSeq)
        if (seq == null) missing("npcseq:${npc.attr[ToaPuzzles.SOURCE]}:$osrsSeq") else npc.animate(seq)
    }

    protected fun clearIf(done: Boolean) { if (live && done) scope.run.clear(raid.world.currentCycle) }
    protected fun drain(player: Player, skill: Int, amount: Int) {
        player.skills.setCurrentLevel(skill, (player.skills.getCurrentLevel(skill) - amount).coerceAtLeast(0))
    }
    protected fun damage(player: Player, amount: Int) {
        if (!eligible(player)) return
        player.hit(amount).setCancelIf { !eligible(player) }
    }

    protected fun inMeleeRange(npc: Npc, tile: Tile): Boolean {
        if (npc.tile.height != tile.height) return false
        val size = npc.getSize()
        return MeleeReach.bordersWithoutWall(raid.world.collision, npc.tile, size, size, tile, 1, 1)
    }
    protected fun follow(npc: Npc, tile: Tile) {
        if (!npc.timers.has(FROZEN_TIMER) && !inMeleeRange(npc, tile)) npc.walkTo(tile)
        else npc.stopMovement()
    }

    protected fun attack(npc: Npc, player: Player, maximum: Int, style: CombatClass,
                         fixed: Boolean = false, protectedNumerator: Int = 0, protectedDenominator: Int = 1,
                         after: (Boolean, Int, Int) -> Unit = { _, _, _ -> }) {
        if (!eligible(player) || !npc.isSpawned() || npc.isDead()) return
        npc.prepareAttack(style, if (style == CombatClass.MELEE) npc.combatDef.attackStyleType
            else if (style == CombatClass.MAGIC) StyleType.MAGIC else StyleType.RANGED, WeaponStyle.ACCURATE)
        val accuracy = when (style) {
            CombatClass.MELEE -> MeleeCombatFormula.getUnprotectedAccuracy(npc, player)
            CombatClass.RANGED -> RangedCombatFormula.getUnprotectedAccuracy(npc, player)
            else -> MagicCombatFormula.getUnprotectedAccuracy(npc, player)
        }
        val lands = raid.world.randomDouble() < accuracy
        val roll = if (!lands) 0 else if (fixed) maximum else raid.world.random(0..maximum)
        val amount = if (player.isProtectedFrom(style)) roll * protectedNumerator / protectedDenominator else roll
        val hit = npc.dealHit(player, minHit = amount.toDouble(), maxHit = amount.toDouble(), landHit = lands,
            delay = 0, applyDeflectProtection = false, hitType = when (style) {
                CombatClass.MELEE -> HitType.MELEE; CombatClass.RANGED -> HitType.RANGE; else -> HitType.MAGIC
            }).hit
        hit.setCancelIf { !eligible(player) || !npc.isSpawned() || npc.isDead() }
        hit.addAction { after(lands, hitmarks.sumOf { it.damage }, roll) }
    }
}
