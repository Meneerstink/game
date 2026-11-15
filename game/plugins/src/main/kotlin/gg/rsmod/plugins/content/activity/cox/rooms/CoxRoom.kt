package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.game.message.impl.LocAnimMessage
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.cox.CoxIds
import gg.rsmod.plugins.content.activity.cox.CoxLaidRoom
import gg.rsmod.plugins.content.activity.cox.CoxMap
import gg.rsmod.plugins.content.activity.cox.CoxRaid
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula

/**
 * One room of a running raid (ToA pattern: ToaBossSession/ToaRoomScope). The room spawns what it holds when the raid begins ([load]),
 * runs its encounter every tick ([tick]) and owns its npcs, locs and timers; [close] removes all of it.
 *
 * Tiles are given as the donor and RuneLite give them: OSRS cache coordinates of the room's version (west / straight / east block at
 * chunk x 408 / 412 / 416), turned with the room by [CoxMap.roomTile]. Every OSRS id goes through [CoxIds] (npc, seq, spotanim, synth,
 * loc); a spotanim below 1000 or a sound below 3800 is the same id in both caches (OsrsFxImportTool notes) and is used as is.
 *
 * Exit crystals (tsps rsprox captures, CoxShared OBJECT.CRYSTAL): the crystal RAIDS_ROOM_CRYSTAL 30018 (2x2) blocks a room's exit until its
 * encounter is done; done = loc anim 7506, area sounds 1021 and 3821 (5 cycles later, range 10), gone 4 ticks later.
 */
abstract class CoxRoom(val raid: CoxRaid, val floor: Int, val laid: CoxLaidRoom) {
    val world get() = raid.world
    val variant: Int get() = laid.variant
    var completed = false
        protected set
    var closed = false
        private set
    var tick = 0
        private set

    private val osrsOf = HashMap<Npc, Int>()
    /** prepareAttack overwrites combatDef.attackStyleType with the last style (a cast left MAGIC there: 'Invalid combat style'). */
    private val meleeStyleOf = HashMap<Npc, StyleType>()
    private val bossOf = HashMap<Npc, Boolean>()
    val npcs = mutableListOf<Npc>()
    private val deadSeen = HashSet<Npc>()
    private val locs = mutableListOf<GameObject>()
    private data class Event(val tick: Int, val action: () -> Unit)
    private val events = mutableListOf<Event>()
    private var exitCrystal: GameObject? = null

    /** ::coxclear (test command): every hit lands in full from then on (submerged/shelled/anvil immunities skipped). */
    var testClear = false

    /** Points this room has paid; capped at [pointsCap] (tsps: 5 x the room's monsters' hitpoints, +5%, unverified). */
    private var paid = 0
    protected var pointsCap = 0

    open fun load() {}
    open fun onTick() {}
    open fun onNpcDeath(npc: Npc) {}
    open fun onClose() {}

    fun tile(x: Int, z: Int, size: Int = 1, sizeZ: Int = size): Tile = CoxMap.roomTile(raid.map, floor, laid, Tile(x, z), size, sizeZ)
    fun <T> respective(west: T, straight: T, east: T): T = when (variant) { 0 -> west; 1 -> straight; else -> east }

    open fun contains(t: Tile): Boolean = CoxMap.roomAt(raid.map, raid.layout, t)?.let { (f, r) -> f == floor && r === laid } ?: false
    fun raiders(): List<Player> = world.players.entries.filterNotNull().filter { it.isOnline && !it.isDead() && contains(it.tile) }

    /** Raiders the raid scales to: those there at the start (Wiki: 'raid party at the start'), at least 1. */
    fun scaling(): CoxScaling {
        val members = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }.ifEmpty { raiders() }
        val size = maxOf(1, raid.points.size, members.size)
        val maxCombat = members.maxOfOrNull { it.combatLevel } ?: 126
        val maxHp = members.maxOfOrNull { it.skills.getMaxLevel(gg.rsmod.plugins.api.Skills.CONSTITUTION) } ?: 99
        val mining = members.map { it.skills.getMaxLevel(gg.rsmod.plugins.api.Skills.MINING) }.ifEmpty { listOf(1) }.average().toInt()
        return CoxScaling(size, maxCombat, maxHp, mining)
    }

    fun osrs(npc: Npc): Int = osrsOf[npc] ?: -1
    fun scaled(npc: Npc): CoxScaled? = CoxMonsters.scale(osrs(npc), scaling())

    /** Spawns OSRS npc [osrsNpc] at [at] (already a raid tile), scaled for the raid; [speed] = its attack speed. */
    fun spawn(osrsNpc: Int, at: Tile, speed: Int = 4, deathSeq: Int = -1, meleeStyle: StyleType = StyleType.CRUSH): Npc? {
        val id = CoxIds.npc(osrsNpc) ?: run { missing("npc $osrsNpc"); return null }
        val npc = Npc(id, at, world)
        npc.respawnOverride = false
        npc.naivePathfinding = true
        if (!world.spawn(npc)) return null
        osrsOf[npc] = osrsNpc
        configure(npc, osrsNpc, speed, deathSeq, meleeStyle)
        npcs += npc
        CoxRooms.owner[npc] = this
        return npc
    }

    /** Gives [npc] the scaled stats of [osrsNpc] (a form change keeps the current hitpoints when [keepHp]). */
    fun configure(npc: Npc, osrsNpc: Int, speed: Int = 4, deathSeq: Int = -1, meleeStyle: StyleType = StyleType.CRUSH, keepHp: Boolean = false) {
        osrsOf[npc] = osrsNpc
        meleeStyleOf[npc] = meleeStyle
        val monster = CoxMonsters.of(osrsNpc)
        bossOf[npc] = monster?.boss ?: false
        val scaled = CoxMonsters.scale(osrsNpc, scaling())
        if (scaled == null) {
            // Npcs without Wiki combat stats (crabs, grubs, the meat tree, blockers, bats ...) still pass every hit through the room.
            npc.hitModifier = { hit ->
                val source = hit.source
                if (source is Player && !raid.map.area.contains(source.tile)) hit.hitmarks.forEach { it.damage = 0 }
                else if (!testClear) incoming(npc, hit)
            }
            return
        }
        val hp = npc.getCurrentLifepoints()
        npc.combatDef = CoxMonsters.definition(scaled, speed, if (deathSeq >= 0) seqFor(npc, deathSeq) else -1, meleeStyle)
        npc.equipmentBonuses.fill(0)
        npc.combatDef.bonuses.forEachIndexed { i, b -> if (i < npc.equipmentBonuses.size) npc.equipmentBonuses[i] = b }
        npc.combatDef.stats.forEachIndexed { i, level -> npc.stats.setMaxLevel(i, level); npc.stats.setCurrentLevel(i, level) }
        npc.setCurrentLifepoints(if (keepHp && hp > 0) hp.coerceAtMost(npc.getMaximumLifepoints()) else npc.getMaximumLifepoints())
        if (!keepHp) pointsCap += CoxDuration.pointsFor(npc.getMaximumLifepoints(), bossOf[npc] == true) * 105 / 100
        npc.hitModifier = { hit ->
            val source = hit.source
            if (source is Player && !raid.map.area.contains(source.tile)) hit.hitmarks.forEach { it.damage = 0 }
            else {
                if (!testClear) incoming(npc, hit)
                if (source is Player) {
                    val dealt = minOf(hit.hitmarks.sumOf { it.damage }, npc.getCurrentLifepoints())
                    val points = CoxDuration.pointsFor(dealt, bossOf[npc] == true).coerceAtMost((pointsCap - paid).coerceAtLeast(0))
                    if (points > 0) { paid += points; raid.addPoints(source.username, points) }
                }
            }
        }
    }

    /** A hit about to land on one of the room's npcs; change its damage here (immunities, weaknesses). */
    open fun incoming(npc: Npc, hit: gg.rsmod.game.model.Hit) {}

    fun remove(npc: Npc) {
        npcs.remove(npc); osrsOf.remove(npc); bossOf.remove(npc); CoxRooms.owner.remove(npc)
        if (npc.isSpawned()) world.remove(npc)
    }

    fun later(delay: Int, action: () -> Unit) {
        if (delay <= 0) { if (!closed) action(); return }
        events += Event(tick + delay, action)
    }

    internal fun cycle() {
        if (closed) return
        for (npc in npcs.toList()) {
            if ((npc.isDead() || !npc.isSpawned()) && deadSeen.add(npc)) onNpcDeath(npc)
        }
        val due = events.filter { it.tick <= tick }
        events.removeAll(due.toSet())
        due.forEach { if (!closed) it.action() }
        if (!closed) onTick()
        tick++
    }

    /**
     * A raid drop on [at] for everyone (tsps rsprox: raid drops are public at once). [osrs] goes through CoxIds (raid items); [classic] is an
     * id shared by both caches (bones, onyx, long/curved bone).
     */
    fun drop(at: Tile, osrs: Int, amount: Int = 1, classic: Boolean = false) {
        val id = if (classic) osrs else CoxIds.item(osrs) ?: run { missing("item $osrs"); return }
        world.spawn(gg.rsmod.game.model.entity.GroundItem(id, amount, Tile(at)))
    }

    /** Drops every pending timed action (an encounter that ended: no attack lands after the death). */
    fun cancelEvents() = events.clear()

    fun alive(npc: Npc?): Boolean = npc != null && npc.isSpawned() && !npc.isDead()

    fun close() {
        if (closed) return
        closed = true
        events.clear()
        npcs.toList().forEach { remove(it) }
        locs.toList().forEach { if (it is DynamicObject && it.isSpawned(world)) world.remove(it) }
        locs.clear()
        onClose()
    }

    // ------------------------------------------------------------------ locs

    /** Places OSRS loc [osrs] on raid tile [at] (type 10, turned with the room unless [rotation] given). */
    fun placeLoc(osrs: Int, at: Tile, rotation: Int = laid.rotation, type: Int = 10): GameObject? {
        val id = CoxIds.loc(osrs) ?: run { missing("loc $osrs"); return null }
        val obj = DynamicObject(id, type, rotation and 3, at)
        world.spawn(obj)
        locs += obj
        return obj
    }

    fun removeLoc(obj: GameObject?) {
        obj ?: return
        locs.remove(obj)
        if (obj is DynamicObject && obj.isSpawned(world)) world.remove(obj)
    }

    /** The room's exit crystal at one of three cache tiles (west / straight / east version), 2x2, turned with the room. */
    fun blockExit(tiles: List<Tile?>, osrsLoc: Int = 30018) {
        val src = tiles.getOrNull(variant) ?: return
        exitCrystal = placeLoc(osrsLoc, tile(src.x, src.z, 2))
    }

    /**
     * May [p] go through the passage at [passage]? Null = yes, else the refusal line. Passages move a player through (cox_raid.plugin.kts), so
     * a room's own blockers must answer here: the exit crystal standing by that passage keeps it shut ('I can't reach that!').
     */
    open fun passRefusal(p: Player, passage: Tile): String? {
        val c = exitCrystal ?: return null
        return if (c.tile.height == passage.height && c.tile.getDistance(passage) <= 4) "I can't reach that!" else null
    }

    /** 'I can't reach that!' when one of [blockers] stands within 4 tiles of [passage]. */
    fun blockedNear(passage: Tile, blockers: List<Tile>): String? =
        if (blockers.any { it.height == passage.height && it.getDistance(passage) <= 4 }) "I can't reach that!" else null

    /** Every loc of this room (template map locs included) with one of the OSRS ids. */
    fun roomLocs(vararg osrs: Int): List<GameObject> {
        val ids = HashSet<Int>()
        for (o in osrs) gg.rsmod.plugins.content.activity.cox.CoxIds.loc(o)?.let { ids.add(it) }
        val x0 = raid.map.area.bottomLeftX + laid.gridX * CoxMap.ROOM_TILES
        val z0 = raid.map.area.bottomLeftZ + laid.gridZ * CoxMap.ROOM_TILES
        val h = gg.rsmod.plugins.content.activity.cox.CoxLayouts.FLOOR_PLANES[floor]
        val out = mutableListOf<GameObject>()
        for (cx in 0 until CoxMap.ROOM_TILES step 8) for (cz in 0 until CoxMap.ROOM_TILES step 8) {
            val chunk = world.chunks.get(Tile(x0 + cx, z0 + cz, h), createIfNeeded = false) ?: continue
            chunk.getEntities<GameObject>(gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                .filterTo(out) { it.id in ids && it.tile.height == h }
        }
        return out
    }

    /** The encounter is done: the crystal shatters and goes 4 ticks later. */
    fun clearExit() {
        completed = true
        val crystal = exitCrystal ?: return
        exitCrystal = null
        val viewers = world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) && it.tile.height == crystal.tile.height }
        seq(7506).takeIf { it >= 0 }?.let { anim -> viewers.forEach { it.write(LocAnimMessage(crystal, anim)) } }
        areaSound(crystal.tile, 1021, radius = 10)
        areaSound(crystal.tile, 3821, radius = 10, delay = 5)
        later(4) { removeLoc(crystal) }
    }

    // ------------------------------------------------------------------ fx

    fun seq(osrs: Int): Int = CoxIds.seq(osrs) ?: -1
    fun seqFor(npc: Npc, osrsSeq: Int): Int {
        val o = osrsOf[npc]
        // Old human/skeleton sequences (2007 ids below 6000: 426 bow, 440 sweep, 836 death, 1162 cast, 1203, 5528) are the same in the
        // 667 cache and drive the same rigs; only newer ones must be imported.
        return (if (o != null) CoxIds.npcSeq(o, osrsSeq) else CoxIds.seq(osrsSeq)) ?: osrsSeq.takeIf { it in 0 until 6000 }
            ?: run { missing("seq $osrsSeq for npc $o"); -1 }
    }
    fun animate(npc: Npc, osrsSeq: Int) { seqFor(npc, osrsSeq).takeIf { it >= 0 }?.let { npc.animate(it) } }
    fun animatePlayer(p: Player, osrsSeq: Int) { (CoxIds.seq(osrsSeq) ?: osrsSeq.takeIf { it < 7000 })?.let { p.animate(it) } }

    fun spot(osrs: Int): Int? = CoxIds.spot(osrs) ?: osrs.takeIf { it in 0 until 1000 && it !in IMPORTED_LOW } ?: run { missing("spotanim $osrs"); null }
    fun sound(osrs: Int): Int = CoxIds.synth(osrs) ?: osrs

    fun gfxAt(osrs: Int, at: Tile, height: Int = 0, delay: Int = 0) { spot(osrs)?.let { world.spawn(TileGraphic(at, it, height, delay)) } }
    fun gfxOn(pawn: gg.rsmod.game.model.entity.Pawn, osrs: Int, height: Int = 0, delay: Int = 0) { spot(osrs)?.let { pawn.graphic(it, height, delay) } }
    fun play(p: Player, osrs: Int, delay: Int = 0) = p.playSound(sound(osrs), delay = delay)
    fun areaSound(at: Tile, osrs: Int, radius: Int = 8, delay: Int = 0) = world.spawn(AreaSound(at, sound(osrs), radius.coerceAtMost(15), 1, delay))

    /** A flight from [from] to [target] lasting [cycles] client cycles (30 = 1 tick) after [delay] cycles. */
    fun projectile(from: Tile, target: gg.rsmod.game.model.entity.Pawn, osrs: Int, delay: Int, cycles: Int, startHeight: Int, endHeight: Int,
                   angle: Int = 16, steepness: Int = 64) {
        val gfx = spot(osrs) ?: return
        world.spawn(Projectile.Builder().setTiles(start = from, target = target).setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = endHeight).setSlope(angle = angle, steepness = steepness)
            .setTimes(delay = delay, lifespan = cycles).build())
    }

    fun projectile(from: Tile, to: Tile, osrs: Int, delay: Int, cycles: Int, startHeight: Int, endHeight: Int, angle: Int = 16, steepness: Int = 64) {
        val gfx = spot(osrs) ?: return
        world.spawn(Projectile.Builder().setTiles(start = from, target = to).setGfx(gfx = gfx)
            .setHeights(startHeight = startHeight, endHeight = endHeight).setSlope(angle = angle, steepness = steepness)
            .setTimes(delay = delay, lifespan = cycles).build())
    }

    // ------------------------------------------------------------------ combat

    /** The nearest raider in the room (the donor's engage(): nearest raider). */
    fun nearest(npc: Npc): Player? = raiders().minByOrNull { it.tile.getDistance(npc.tile) }

    fun distance(t: Tile, npc: Npc): Int {
        val o = npc.tile; val s = npc.getSize()
        return maxOf(maxOf(o.x - t.x, 0, t.x - (o.x + s - 1)), maxOf(o.z - t.z, 0, t.z - (o.z + s - 1)))
    }

    /** Next to [npc]'s body on a side (not a corner), no wall between (engine MeleeReach). */
    fun meleeReach(npc: Npc, t: Tile): Boolean {
        if (t.height != npc.tile.height) return false
        val s = npc.getSize()
        return distance(t, npc) == 0 || gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(world.collision, t, 1, 1, npc.tile, s, s)
    }

    /**
     * One attack roll + hit (ToaBossSession.attack pattern): accuracy by the engine formulas with the npc's scaled levels, damage rolled
     * at launch, protection read when it lands ([protectedPercent] lets that share through), hit lands [delay] ticks later.
     */
    fun attack(npc: Npc, p: Player, maxHit: Int, style: CombatClass, delay: Int = 0, protectedPercent: Int = 0,
               accuracy: Boolean = true, after: (Int) -> Unit = {}) {
        if (!alive(npc) || !p.isOnline || p.isDead()) return
        npc.prepareAttack(style, when (style) {
            CombatClass.MELEE -> meleeStyleOf[npc] ?: StyleType.CRUSH
            CombatClass.RANGED -> StyleType.RANGED
            else -> StyleType.MAGIC
        }, WeaponStyle.ACCURATE)
        val lands = !accuracy || when (style) {
            CombatClass.MELEE -> MeleeCombatFormula.getUnprotectedAccuracy(npc, p)
            CombatClass.RANGED -> RangedCombatFormula.getUnprotectedAccuracy(npc, p)
            else -> MagicCombatFormula.getUnprotectedAccuracy(npc, p)
        } >= world.randomDouble()
        val rolled = if (lands && maxHit > 0) world.random(0..maxHit) else 0
        later(delay) {
            if (!alive(npc) || !p.isOnline || p.isDead() || !raid.map.area.contains(p.tile)) return@later
            val damage = if (p.isProtectedFrom(style)) rolled * protectedPercent / 100 else rolled
            val hit = npc.dealHit(p, minHit = damage.toDouble(), maxHit = damage.toDouble(), landHit = lands && damage > 0, delay = 0,
                hitType = when (style) { CombatClass.MELEE -> HitType.MELEE; CombatClass.RANGED -> HitType.RANGE; CombatClass.MAGIC -> HitType.MAGIC
                    else -> HitType.REGULAR_HIT }, applyDeflectProtection = false)
            hit.hit.addAction { after(hitmarks.sumOf { it.damage }) }
        }
    }

    /** Plain damage (no roll) after [delay] ticks, e.g. a falling rock on a tile. */
    fun damage(npc: Npc?, p: Player, amount: Int, delay: Int = 0) {
        later(delay) {
            if (!p.isOnline || p.isDead() || !raid.map.area.contains(p.tile)) return@later
            val src = npc?.takeIf { alive(it) } ?: p
            src.dealHit(p, minHit = amount.toDouble(), maxHit = amount.toDouble(), landHit = amount > 0, delay = 0,
                hitType = HitType.REGULAR_HIT, applyDeflectProtection = false)
        }
    }

    fun message(text: String) = raiders().forEach { it.message(text) }

    /** Blocks (or frees) walking on [t] - for npcs that stand in a gap (shaman blockers). */
    fun blockTile(t: Tile, on: Boolean) {
        val b = gg.rsmod.game.model.collision.CollisionUpdate.Builder()
        b.setType(if (on) gg.rsmod.game.model.collision.CollisionUpdate.Type.ADD else gg.rsmod.game.model.collision.CollisionUpdate.Type.REMOVE)
        b.putTile(t, false, *gg.rsmod.game.model.Direction.NESW)
        world.collision.applyUpdate(b.build())
    }

    // ------------------------------------------------------------------ simple fighters

    /** Per-npc state for [fight]: when it may swing next and whether a scripted move holds it. */
    class Fighter(val speed: Int, val range: Int) { var next = 0; var busyUntil = 0 }
    val fighters = HashMap<Npc, Fighter>()

    /**
     * The donor's engage(): each living fighter takes the nearest raider of the room, walks into [Fighter.range] (1 = melee reach) and
     * swings every [Fighter.speed] ticks through [swing]. Call from [onTick].
     */
    fun fight(swing: (Npc, Player) -> Unit) {
        for ((npc, f) in fighters.toList()) {
            if (!alive(npc)) { fighters.remove(npc); continue }
            if (tick < f.busyUntil) continue
            val target = nearest(npc) ?: continue
            val inReach = if (f.range <= 1) meleeReach(npc, target.tile) else distance(target.tile, npc) <= f.range &&
                world.collision.raycast(npc.tile, target.tile, projectile = true)
            if (!inReach) { npc.walkTo(target.tile); continue }
            npc.stopMovement()
            npc.facePawn(target)
            if (tick >= f.next) { f.next = tick + f.speed; swing(npc, target) }
        }
    }

    private val missingSeen = HashSet<String>()
    fun missing(key: String) {
        if (missingSeen.add(key)) gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            "CoX room ${laid.room}: missing imported asset $key (regenerate data/cfg/cox/ids.tsv)"
        }
    }

    companion object {
        /** OSRS spotanims below 1000 that differ in the 667 cache and were imported (cox-fx). */
        val IMPORTED_LOW = setOf(40, 645, 659, 660)
    }
}
