package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy

/**
 * N606 12-11 22:5x (eigenaar: 'de mest komt buiten de map', 'de mager npc in kephiri spawnd in de muur', 'veel grafische issues ...
 * tooling van grafisch flink uitbreiden'): automatic graphical audit of every ToA placement, TEST SERVER ONLY (rsps.testServer).
 *  - PLACEMENT: every npc/loc a ToA encounter or room puts down must stand on floor that a player in the room can walk to, with its
 *    whole footprint out of walls. Otherwise one 'toagfx PLACEMENT' PROBLEM line (what, OSRS id, tile, size, why).
 *  - SYNC: every tracked ToA npc that lives >= 2 ticks within 15 tiles of a player but is not in that player's local npc list
 *    (the class of the invisible Ba-Ba boulders / orbs) -> one 'toagfx SYNC' PROBLEM line per npc.
 * Lines are grep-able ('toagfx') and collected by effect_report.py. Never changes game state.
 */
object ToaGfxAudit {
    val enabled: Boolean get() = System.getProperty("rsps.testServer") == "true"

    /** Spawned children that legitimately live off the walkable floor (OSRS): Zebak tail 11731 + moat crocodile 11741 in the water,
     *  Wardens charging statues 11746/11747 on their troughs. Extend only with proof from a run (false positive). */
    val OFF_FLOOR_OK = setOf(11731, 11741, 11746, 11747, 11748, 11749, 11738, 11723) // 11738 Zebak wave over the whole row incl. water; 11723 Kephri swarms crawl in from the edge (spawn seq 9605)
    // 11748/11749 (N609 14-11): the P1 charging statues now spawn as their OSRS PHASE1 ids instead of 11746/11747 - same plinth tiles (blocking platform 45605), same reason.

    private class Tracked(val npc: Npc, val what: String, val born: Int)
    private val tracked = java.util.Collections.synchronizedList(mutableListOf<Tracked>())
    private val reported = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Npc, Boolean>())

    private val localNpcsGetter by lazy {
        Player::class.java.declaredMethods.firstOrNull { it.name.startsWith("getLocalNpcs") }?.also { it.isAccessible = true }
    }

    @Suppress("UNCHECKED_CAST")
    private fun localNpcs(p: Player): Collection<Npc>? = try { localNpcsGetter?.invoke(p) as? Collection<Npc> } catch (_: Throwable) { null }

    /** A player-reachable floor tile for every footprint tile? Anchor = a player in the room (players always stand on floor). */
    fun floorProblem(world: World, tile: Tile, size: Int, anchor: Tile): String? {
        if (tile.height != anchor.height) return "andere verdieping (anker ${anchor.height})"
        if (!tile.isWithinRadius(anchor, 40)) return "ver buiten de kamer (${tile.getDistance(anchor)} tegels van een speler)"
        val walls = (0 until size).flatMap { dx -> (0 until size).map { dz -> tile.transform(dx, dz) } }.filter { world.collision.isClipped(it) }
        val request = PathRequest.Builder().setPoints(anchor, tile).setSourceSize(1, 1).setTargetSize(size, size).setTouchRadius(1)
            .setProjectilePath(false).clipPathNodes(node = true, link = true).clipDiagonalTiles().build()
        val route = BFSPathFindingStrategy(world.collision).calculateRoute(request)
        return when {
            walls.size == size * size -> "volledig in muur/blokkade (${walls.size}/${size * size} tegels)"
            !route.success -> "niet bereikbaar vanaf de vloer van de speler (buiten de kamer / achter een muur)"
            walls.isNotEmpty() -> "deels in muur (${walls.size}/${size * size} tegels: ${walls.take(3).joinToString { "${it.x},${it.z}" }})"
            else -> null
        }
    }

    fun nearbyPlayers(world: World, tile: Tile): List<Player> {
        val out = mutableListOf<Player>()
        world.players.forEach { if (it.tile.height == tile.height && it.tile.isWithinRadius(tile, 64)) out += it }
        return out
    }

    @Volatile private var loopWorld: World? = null
    /** One world loop for the SYNC check, so puzzle rooms without an encounter are covered too. */
    private fun ensureLoop(world: World) {
        if (loopWorld === world) return
        loopWorld = world
        world.queue { while (true) { wait(1); val ps = mutableListOf<Player>(); world.players.forEach { ps += it }; tick(world, ps) } }
    }

    fun npcSpawned(npc: Npc, osrs: Int, players: List<Player>, what: String) {
        if (!enabled) return
        ensureLoop(npc.world)
        val anchor = players.firstOrNull { it.tile.height == npc.tile.height }?.tile ?: return
        if (osrs !in OFF_FLOOR_OK) floorProblem(npc.world, npc.tile, npc.getSize(), anchor)?.let { why ->
            Trace.problem(Trace.Cat.NPCPATH) { "toagfx PLACEMENT npc $what osrs=$osrs local=${npc.id} at=${npc.tile.x},${npc.tile.z},${npc.tile.height} size=${npc.getSize()}: $why" }
        }
        tracked += Tracked(npc, "$what osrs=$osrs", npc.world.currentCycle)
    }

    /** Locs that are DEFINED to sit on unwalkable tiles: 45649 = Wardens P3 collapsed floor (WardensCombat.defaultEnrage marks the
     *  tile unwalkable on purpose; photobook 13-11 00:2x gave 141 false alarms). Extend only with proof.
     *  45643 = Wardens P3 floor: placed on the void BEFORE WardensCombat.setFloorWalkable(tile, true) opens the tile (round 3 13-11:
     *  247 false alarms, all on the P3 floor grid). 45752 = Ba-Ba sarcophagus: exactly the 8 cache2686 5/15188/1 static placements
     *  (3803/3808/3813/3818 x 5400/5416, rot 3/1), whose map copy already blocks that tile.
     *  45606..45626 = Wardens charging_platform_0.._20 (N609 14-11): placed exactly on the static blocking platform 45605 it replaces
     *  (WardensCombat.showCharge looks the map loc up), so 'volledig in muur' is the plinth itself (fx42 test: 1/1 tegels, 6672/6700,32). */
    val LOC_OFF_FLOOR_OK = setOf(45649, 45643, 45752) + (45606..45626)

    fun locPlaced(world: World, osrs: Int, local: Int, tile: Tile, size: Int, players: List<Player>, what: String) {
        if (!enabled || osrs in LOC_OFF_FLOOR_OK) return
        val anchor = players.firstOrNull { it.tile.height == tile.height }?.tile ?: return
        floorProblem(world, tile, size, anchor)?.let { why ->
            Trace.problem(Trace.Cat.NPCPATH) { "toagfx PLACEMENT loc $what osrs=$osrs local=$local at=${tile.x},${tile.z},${tile.height}: $why" }
        }
    }

    private val missingSeen = java.util.Collections.synchronizedSet(HashSet<String>())
    /** N606 tooling 1: an animation/effect that is requested but cannot be sent (no import for the rig / spotanim). */
    fun missing(kind: String, detail: String) {
        if (!enabled || !missingSeen.add("$kind:$detail")) return
        Trace.problem(Trace.Cat.NPCPATH) { "toagfx MISSING $kind $detail" }
    }

    /** Consecutive ticks an npc has been missing from a nearby client; reported at [SYNC_TICKS]. */
    private val absent = HashMap<Npc, Int>()
    private const val SYNC_TICKS = 3

    /** Call once per tick (encounter cycle): SYNC check for every tracked npc. */
    fun tick(world: World, players: List<Player>) {
        if (!enabled || tracked.isEmpty()) return
        val now = world.currentCycle
        synchronized(tracked) {
            tracked.removeAll { !it.npc.isSpawned() || it.npc.isDead() }
            absent.keys.retainAll(tracked.map { it.npc }.toSet())
            for (t in tracked) {
                if (now - t.born < 2 || t.npc in reported) continue
                // 13-11 round 3: Akkha's memory special hides her on purpose (invisible = off every client) and a moveTo jump
                // (Ba-Ba to her throne, the Arcane scarab) re-adds the npc a tick later - both gave one-tick false alarms.
                if (t.npc.invisible) { absent.remove(t.npc); continue }
                var missingNow = false
                for (p in players) {
                    // 13: the client's npc view ends at 15-16 tiles (Ba-Ba at 16 = false alarm in the photobook test 13-11)
                    if (p.tile.height != t.npc.tile.height || !p.tile.isWithinRadius(t.npc.tile, 13)) continue
                    val local = localNpcs(p) ?: return
                    if (t.npc !in local) {
                        missingNow = true
                        val ticks = (absent[t.npc] ?: 0) + 1
                        absent[t.npc] = ticks
                        if (ticks < SYNC_TICKS) break
                        reported += t.npc
                        Trace.problem(Trace.Cat.NPCPATH) { "toagfx SYNC npc ${t.what} local=${t.npc.id} at=${t.npc.tile.x},${t.npc.tile.z} alive ${now - t.born} ticks, ${p.tile.getDistance(t.npc.tile)} tiles from ${p.username}, NOT on that client ($ticks ticks in a row)" }
                        break
                    }
                }
                if (!missingNow) absent.remove(t.npc)
            }
        }
    }
}
