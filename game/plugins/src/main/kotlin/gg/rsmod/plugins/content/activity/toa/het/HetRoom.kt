package gg.rsmod.plugins.content.activity.toa.het

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.*

/**
 * N462 Path of Het challenge room (ToaRoomKey(HET, challenge)). The raid owns membership/clear/wipe; this room owns the puzzle state
 * ([HetBoard]), the scenery it places (through the room scope, removed with it) and the seal.
 * Strategies 1072: "If the team wipes during this room, the statue progress is reset, along with a new puzzle being drafted".
 */
class HetRoom(private val raid: ToaRaid) : ToaRoom {
    var board: HetBoard? = null
        private set
    private var run: ToaRoomRun? = null
    private var seal: Npc? = null
    var sealHp = 0
        private set
    /** Tick until which the seal is weakened (exposed); -1 = protected. */
    var weakenedUntil = -1
        private set
    private var tick = 0
    private val placed = hashMapOf<HetPos, GameObject>()
    private val orbs = mutableListOf<Pair<Npc, HetDir>>()

    val weakened: Boolean get() = tick < weakenedUntil

    override fun start(run: ToaRoomRun) {
        this.run = run
        val scope = raid.scope(run) ?: return
        val layout = HetLayouts.ALL[raid.world.random(HetLayouts.ALL.lastIndex)]
        val b = HetBoard(layout, group = raid.roster.size > 1)
        board = b
        tick = 0
        weakenedUntil = -1
        sealHp = HetSeal.hp(raid.roster.size.coerceIn(1, 8))
        // Statues and the seal (OSRS: dynamic encounter locs/npc, not on the map).
        loc(HetIds.CASTER, layout.caster.sw, rotation(layout.casterFacing))?.let { scope.spawn(it) }
        loc(HetIds.TARGET, layout.target.sw, 0)?.let { scope.spawn(it) }
        HetIds.npc(HetIds.SEAL)?.let { id ->
            val npc = Npc(id, tile(layout.seal.sw), raid.world)
            npc.respawnOverride = false
            npc.walkRadius = 0
            if (scope.spawn(npc)) seal = npc
        }
        layout.walls.forEach { wall -> wall.tiles.forEachIndexed { i, p ->
            val end = i == 0 || i == wall.tiles.lastIndex
            val id = when { wall.worn && end -> HetIds.WORN_END; wall.worn -> HetIds.WORN; end -> HetIds.STONE_END; else -> HetIds.STONE }
            // ONZEKER T23: end pieces face outwards along the wall, internal pieces follow the wall's axis.
            val rot = if (wall.horizontal) (if (end && i == 0) 3 else 1) else (if (end && i == 0) 2 else 0)
            loc(id, p, rot)?.let { obj -> scope.spawn(obj); placed[p] = obj }
        } }
        b.mirrors.forEach { showMirror(it) }
        scope.queue {
            while (true) {
                wait(1)
                cycle()
            }
        }
    }

    private fun tile(p: HetPos, height: Int = 0) = ToaMap.instance(raid.map, Tile(p.x, p.z, height))
    fun pos(t: Tile): HetPos? = ToaMap.source(raid.map, t)?.let { HetPos(it.x, it.z) }

    private fun loc(osrs: Int, p: HetPos, rotation: Int): DynamicObject? =
        HetIds.loc(osrs)?.let { DynamicObject(it, 10, rotation, tile(p)) }

    private fun rotation(d: HetDir) = when (d) { HetDir.NORTH -> 0; HetDir.EAST -> 1; HetDir.SOUTH -> 2; HetDir.WEST -> 3 }

    private fun showMirror(m: HetMirror) {
        val scope = run?.let { raid.scope(it) } ?: return
        placed.remove(m.pos)?.let { raid.world.remove(it) }
        val id = when { m.dirty -> HetIds.MIRROR_DIRTY; m.movable -> HetIds.MIRROR; else -> HetIds.MIRROR_FIXED }
        loc(id, m.pos, m.rotation)?.let { scope.spawn(it); placed[m.pos] = it }
    }

    private fun clearTile(p: HetPos) { placed.remove(p)?.let { raid.world.remove(it) } }

    /** Called once per tick by the room queue. */
    fun cycle() {
        val b = board ?: return
        val r = run ?: return
        if (r.status != ToaRoomStatus.RUNNING) return
        tick++
        if (tick % HetIds.BEAM_INTERVAL == 0) fire(b)
        if (seal != null && !weakened && seal!!.getTransmogId() >= 0) seal!!.setTransmogId(-1)
        if (tick % HetIds.ORB_INTERVAL == 0) spawnOrb(b)
        moveOrbs(b)
    }

    private fun fire(b: HetBoard) {
        val beam = b.trace()
        // Beam visual: the impact sparkle on the last tile (ONZEKER T24: OSRS draws the beam itself client-side; FX_BEAM_* not imported).
        HetIds.spot(HetIds.BEAM_IMPACT)?.let { gfx ->
            (beam.turns + listOfNotNull(beam.tiles.lastOrNull())).forEach { raid.world.spawn(TileGraphic(tile(it), gfx, 0)) }
        }
        // Strategies 1068: "Getting caught in the light beam will also inflict damage, albeit lower than the orbs."
        raid.activePlayers().filter { p -> pos(p.tile)?.let { it in beam.tiles } == true }
            .forEach { it.hit(HetIds.BEAM_DAMAGE) }
        // The beam itself: one flat projectile per straight segment (caster -> each mirror -> the end), ONZEKER T24.
        HetIds.spot(HetIds.BEAM)?.let { gfx ->
            val points = listOf(b.layout.caster.centre) + beam.turns + listOfNotNull(beam.tiles.lastOrNull()?.takeIf { it !in beam.turns })
            points.zipWithNext().forEach { (from, to) ->
                raid.world.spawn(gg.rsmod.game.model.entity.Projectile.Builder().setTiles(tile(from), tile(to)).setGfx(gfx)
                    .setHeights(HetIds.BEAM_HEIGHT, HetIds.BEAM_HEIGHT).setSlope(0, 0).setTimes(0, HetIds.BEAM_CYCLES).build())
            }
        }
        if (beam.hitTarget) {
            weakenedUntil = tick + HetSeal.WEAKENED_TICKS
            seal?.let { s -> HetIds.npc(HetIds.SEAL_WEAKENED)?.let { s.setTransmogId(it) } }
        }
    }

    private fun spawnOrb(b: HetBoard) {
        val id = HetIds.npc(HetIds.ORB) ?: return
        val l = b.layout
        val dir = HetDir.values()[raid.world.random(3)]
        val start = when (dir) {
            HetDir.NORTH -> HetPos(raid.world.random(l.minX..l.maxX), l.minZ)
            HetDir.SOUTH -> HetPos(raid.world.random(l.minX..l.maxX), l.maxZ)
            HetDir.EAST -> HetPos(l.minX, raid.world.random(l.minZ..l.maxZ))
            HetDir.WEST -> HetPos(l.maxX, raid.world.random(l.minZ..l.maxZ))
        }
        if (b.blocked(start)) return
        val scope = run?.let { raid.scope(it) } ?: return
        val npc = Npc(id, tile(start), raid.world)
        npc.respawnOverride = false
        npc.walkRadius = 0
        if (scope.spawn(npc)) orbs += npc to dir
    }

    private fun moveOrbs(b: HetBoard) {
        val iterator = orbs.iterator()
        while (iterator.hasNext()) {
            val (orb, dir) = iterator.next()
            if (!orb.isSpawned()) { iterator.remove(); continue }
            val here = pos(orb.tile) ?: run { iterator.remove(); raid.world.remove(orb); return }
            val hit = raid.activePlayers().filter { pos(it.tile) == here }
            if (hit.isNotEmpty()) {
                hit.forEach { it.hit(HetIds.ORB_DAMAGE) }
                iterator.remove(); raid.world.remove(orb); continue
            }
            // Strategies 1068: "In group encounters orbs may cause mirrors they collide with to become dirty".
            b.mirrorAt(here)?.takeIf { raid.roster.size > 1 && !it.dirty }?.let { it.dirty = true; showMirror(it) }
            val next = here.step(dir)
            if (next.x !in b.layout.minX..b.layout.maxX || next.z !in b.layout.minZ..b.layout.maxZ || b.blocked(next)) {
                iterator.remove(); raid.world.remove(orb); continue
            }
            orb.moveTo(tile(next))
        }
    }

    // ---- player actions -------------------------------------------------------------------------------------------------------

    fun rotate(player: Player, obj: GameObject, clockwise: Boolean) {
        val b = board ?: return
        val p = pos(obj.tile) ?: return
        val m = b.mirrorAt(p)?.takeIf { it.movable } ?: return
        m.rotate(clockwise)
        player.animate(HetIds.PLAYER_PUSH_ANIM)
        showMirror(m)
    }

    fun pickUp(player: Player, obj: GameObject) {
        val b = board ?: return
        val p = pos(obj.tile) ?: return
        val item = HetIds.item(HetIds.MIRROR_ITEM) ?: return
        if (player.inventory.isFull) { player.message("You don't have enough inventory space.") ; return }
        if (!b.pickUp(p)) return
        clearTile(p)
        player.inventory.add(item)
    }

    fun place(player: Player, slot: Int) {
        val b = board ?: return
        val item = HetIds.item(HetIds.MIRROR_ITEM) ?: return
        val p = pos(player.tile) ?: return
        if (player.inventory[slot]?.id != item || !b.place(p, 0)) return // ONZEKER T25: no OSRS message known
        player.inventory.remove(item, 1, beginSlot = slot)
        showMirror(b.mirrorAt(p)!!)
    }

    fun push(player: Player, obj: GameObject) {
        val b = board ?: return
        val p = pos(obj.tile) ?: return
        val me = pos(player.tile) ?: return
        val dir = when {
            me.x < p.x && me.z == p.z -> HetDir.EAST; me.x > p.x && me.z == p.z -> HetDir.WEST
            me.z < p.z && me.x == p.x -> HetDir.NORTH; me.z > p.z && me.x == p.x -> HetDir.SOUTH
            else -> return
        }
        val m = b.mirrorAt(p) ?: return
        b.push(p, dir) ?: return
        clearTile(p)
        player.animate(HetIds.PLAYER_PUSH_ANIM)
        showMirror(m)
    }

    fun clean(player: Player, obj: GameObject) {
        val b = board ?: return
        val p = pos(obj.tile) ?: return
        if (!b.clean(p)) return
        player.animate(HetIds.PLAYER_PUSH_ANIM)
        b.mirrorAt(p)?.let { showMirror(it) }
    }

    /** Break a worn barrier with a pickaxe: one pickaxe roll interval (Strategies: "Better pickaxes break the stone barriers faster"). */
    suspend fun breakBarrier(task: gg.rsmod.game.model.queue.QueueTask, player: Player, obj: GameObject) {
        val b = board ?: return
        val p = pos(obj.tile) ?: return
        val pick = HetPickaxe.of(player) ?: run { player.message("You need a pickaxe to mine this rock. You do not have a pickaxe which you have the Mining level to use.") ; return }
        val wall = b.layout.walls.firstOrNull { it.worn && p in it.tiles } ?: return
        player.animate(pick.animation)
        task.wait(pick.rollTicks)
        if (board !== b) return
        wall.tiles.forEach { t -> if (b.breakWorn(t)) { clearTile(t); loc(HetIds.WORN_DESTROYED, t, 0)?.let { o ->
            raid.scope(run ?: return@let)?.spawn(o); placed[t] = o } } }
        player.animate(-1)
    }

    /** "Destroy" on Het's Seal: one hit every [HetSeal.HIT_TICKS] while the seal is weakened. */
    suspend fun mine(task: gg.rsmod.game.model.queue.QueueTask, player: Player) {
        val pick = HetPickaxe.of(player) ?: run { player.message("You need a pickaxe to mine this rock. You do not have a pickaxe which you have the Mining level to use.") ; return }
        while (board != null && run?.status == ToaRoomStatus.RUNNING && sealHp > 0) {
            if (!weakened) { player.animate(-1); return }
            player.animate(pick.animation)
            task.wait(HetSeal.HIT_TICKS)
            if (!weakened || sealHp <= 0) break
            val damage = HetSeal.hit(player.skills.getCurrentLevel(Skills.MINING), pick.tier, raid.world.random(2))
            sealHp = (sealHp - damage).coerceAtLeast(0)
            player.addXp(Skills.MINING, HetSeal.XP_PER_HIT)
            if (sealHp == 0) { destroySeal(); break }
        }
        player.animate(-1)
    }

    private fun destroySeal() {
        val r = run ?: return
        val b = board ?: return
        seal?.let { raid.world.remove(it) }; seal = null
        // Broken seal stays after the clear (Het's Seal 'Broken seal' 45489); outside the room scope, released with the instance.
        loc(HetIds.SEAL_BROKEN, b.layout.seal.sw, 0)?.let { raid.world.spawn(it) }
        r.clear(raid.world.currentCycle)
    }

    override fun clear(run: ToaRoomRun) = close()
    override fun wipe(run: ToaRoomRun) = close()
    override fun reset(run: ToaRoomRun) = close()

    private fun close() {
        board = null; seal = null; placed.clear(); orbs.clear(); weakenedUntil = -1
        // A wipe drafts a new puzzle: the next start() builds a fresh board; the scope removed every placed loc/npc.
    }

    companion object {
        val ROOM = AttributeKey<HetRoom>()
    }
}

/** OSRS ids of the room's imported assets (data/cfg/toa/ids.tsv) + the ONZEKER numbers this room needs. */
object HetIds {
    const val CASTER = 45486
    const val TARGET = 45485
    const val MIRROR = 45455
    const val MIRROR_FIXED = 45456
    const val MIRROR_DIRTY = 45457
    const val STONE = 45458
    const val STONE_END = 45460
    const val WORN = 45462
    const val WORN_END = 45464
    const val WORN_DESTROYED = 45466
    const val SEAL_BROKEN = 45489
    const val SEAL = 11706
    const val SEAL_WEAKENED = 11707
    const val ORB = 11708
    const val MIRROR_ITEM = 27296
    /** FX_BEAM_IMPACT01 (RuneLite SpotanimID 2120) - ONZEKER T24 that this is the Het beam; not imported yet (no local id = no gfx). */
    const val BEAM_IMPACT = 2120
    /** FX_BEAM_ATTACK03 (SpotanimID 2114, model 45320 = same model range as the Het locs 457xx/459xx; seq 9368) - ONZEKER T24 which of
     * 2113-2119 the Het beam is (2113/2115 recolour, 2114/2116-2119 other models); check against the screenshot in the test client. */
    const val BEAM = 2114
    const val BEAM_HEIGHT = 60
    /** Projectile flight in client cycles (30 = one game tick): long enough to read as a beam. ONZEKER T24. */
    const val BEAM_CYCLES = 40

    /** ONZEKER T24 (no source): ticks between two caster beams. 15-tick weakened window + a short protected gap. */
    const val BEAM_INTERVAL = 20
    /** ONZEKER T24: "moderate damage" (Orb of Darkness Wiki) / beam "lower than the orbs" (Strategies 1068) - no numbers published. */
    const val ORB_DAMAGE = 8
    const val BEAM_DAMAGE = 4
    /** ONZEKER T24: orb spawn cadence (not published). */
    const val ORB_INTERVAL = 6
    /** Pushing/rotating a mirror: the generic 'push' animation (ONZEKER T24, no OSRS seq named for it). */
    const val PLAYER_PUSH_ANIM = 832

    fun loc(osrs: Int): Int? = ToaIds.loc(osrs)
    fun npc(osrs: Int): Int? = ToaIds.npc(osrs)
    fun item(osrs: Int): Int? = ToaIds.item(osrs)
    fun spot(osrs: Int): Int? = ToaIds.spot(osrs)
}

/** The best usable pickaxe in the inventory or worn, with its Het damage tier (Mod Ash: dragon or better +2, below rune -3). */
data class HetPickaxe(val animation: Int, val rollTicks: Int, val tier: HetSeal.Tier) {
    companion object {
        fun of(player: Player): HetPickaxe? {
            val mining = player.skills.getMaxLevel(Skills.MINING)
            val type = gg.rsmod.plugins.content.skills.mining.PickaxeType.values.reversed().firstOrNull {
                mining >= it.level && (player.equipment.contains(it.item) || player.inventory.contains(it.item))
            } ?: return null
            val name = player.world.definitions.get(ItemDef::class.java, type.item).name.lowercase()
            val tier = when {
                listOf("dragon", "crystal", "3rd age", "infernal").any { it in name } -> HetSeal.Tier.DRAGON
                "rune" in name || "gilded" in name -> HetSeal.Tier.RUNE
                else -> HetSeal.Tier.BELOW_RUNE
            }
            return HetPickaxe(type.animation, type.ticksBetweenRolls, tier)
        }
    }
}
