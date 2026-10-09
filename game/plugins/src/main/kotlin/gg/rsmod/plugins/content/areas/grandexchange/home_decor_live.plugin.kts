package gg.rsmod.plugins.content.areas.grandexchange

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.decor.HomeDecorBake
import gg.rsmod.game.model.decor.HomeDecorFile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.timer.TimerKey
import java.nio.file.Files

/*
 * Applies `data/cfg/home_decor.txt` (see HomeDecorFile) shortly after boot and again every time the file changes, so the
 * home can be dressed while players watch. A file that does not parse is reported and the current dressing stays.
 * The first pass waits until every boot-time npc (home hall, 78 Store, GE audit) is in place, so `npc` lines move those
 * npcs instead of spawning a second copy.
 *
 * Owner 2026-10-09: the hall is baked into region 12598's map file (RoyalHallDecorBakeTool, HomeDecorBake.MANIFEST), so it
 * is complete when the loading screen ends. Lines in the file and the manifest are already in the map and are not spawned;
 * only lines the file has beyond the bake are spawned, and baked locs the file dropped are taken out (and come back when
 * the line does). Both are reported as a PROBLEM: the next bake puts them in the map.
 */

val DECOR_POLL = TimerKey()
val POLL_TICKS = 2
val FIRST_PASS_TICKS = 5

/** Option-less, walk-through loc (GE wall banner) used only to address a ground decoration the server does not hold. */
val BLIND_REMOVAL_ID = 60279

/** The Grand Exchange home, where `npc` lines look for the npc to move. */
fun inHome(tile: Tile) = tile.height == 0 && tile.x in 3136..3199 && tile.z in 3440..3520

val spawned = ArrayList<DynamicObject>()

/** Cache objects hidden by `del` lines, keyed by tile and type, and the copies put back when a line is dropped. */
val hidden = LinkedHashMap<Pair<Tile, Int>, GameObject>()
val restored = HashMap<Pair<Tile, Int>, DynamicObject>()

/** Baked locs (manifest lines) the file no longer has, taken out of the world; a line that returns brings a copy back. */
val unbaked = LinkedHashSet<HomeDecorFile.Placement>()

/** Npcs already placed by this file, by id. */
val placedNpcs = HashMap<Int, Npc>()
var lastModified = -1L

fun covered(key: Pair<Tile, Int>, design: HomeDecorFile.Design) =
    design.removals.any { it.tile == key.first && (it.type == null || it.type == key.second) }

fun placeNpc(post: HomeDecorFile.NpcPost) {
    var current = placedNpcs[post.id]
    if (current == null) world.npcs.forEach { if (current == null && it.id == post.id && inHome(it.tile)) current = it }
    val old = current
    if (old != null && old.tile == post.tile) return
    val npc =
        Npc(post.id, post.tile, world).also {
            it.respawnOverride = true
            it.static = old?.static ?: true
            it.walkRadius = 0
        }
    if (old != null) world.remove(old)
    world.spawn(npc)
    if (npc.def.options.none { it != null && it.equals("Attack", ignoreCase = true) }) npc.setCombatLevel(0)
    npc.setSpawnFacing(post.facing)
    npc.faceTile(post.tile.step(post.facing))
    placedNpcs[post.id] = npc
}

fun applyDesign(design: HomeDecorFile.Design): String {
    spawned.forEach { if (world.isSpawned(it)) world.remove(it) }
    spawned.clear()

    var found = 0
    var blind = 0
    design.removals.forEach { removal ->
        val chunk = world.chunks.getOrCreate(removal.tile)
        val targets =
            chunk.getEntities<GameObject>(removal.tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
                .filter { removal.type == null || it.type == removal.type }
                .toList()
        targets.forEach { obj ->
            val key = obj.tile to obj.type
            world.remove(obj)
            if (restored.remove(key) == null) hidden[key] = obj
            found++
        }
        // The client still draws a ground decoration the server never registered; tell it to drop that slot anyway.
        if (targets.isEmpty() && removal.type == 22 && (removal.tile to 22) !in hidden) {
            world.remove(StaticObject(BLIND_REMOVAL_ID, 22, 0, removal.tile))
            blind++
        }
    }
    hidden.keys.filter { !covered(it, design) && it !in restored }.forEach { key ->
        val copy = DynamicObject(hidden.getValue(key))
        world.spawn(copy)
        restored[key] = copy
    }

    val bake = HomeDecorBake.diff(design, HomeDecorBake.loadManifest())
    bake.missing.filter { it !in unbaked }.forEach { p ->
        world.chunks.getOrCreate(p.tile).getEntities<GameObject>(p.tile, EntityType.STATIC_OBJECT)
            .firstOrNull { it.id == p.id && it.type == p.type && it.rot == p.rot }?.let { world.remove(it) }
        unbaked += p
    }
    val back = unbaked.filter { it !in bake.missing }
    (bake.extra + back).forEach { placement ->
        val obj = DynamicObject(placement.id, placement.type, placement.rot, placement.tile)
        world.spawn(obj)
        spawned += obj
    }
    if (bake.extra.isNotEmpty() || bake.missing.isNotEmpty()) {
        Trace.problem(Trace.Cat.PLUGIN) {
            "home_decor.txt differs from the baked map (${HomeDecorBake.MANIFEST}): ${bake.extra.size} lines spawned one by one " +
                "(first ${bake.extra.take(3).map { "${it.id}@${it.tile.x},${it.tile.z},${it.tile.height}" }}), ${bake.missing.size} baked locs taken out " +
                "- run RoyalHallDecorBakeTool to put them in the map"
        }
    }
    design.npcs.forEach { placeNpc(it) }
    return "${design.placements.size} objects (${bake.inMap.size} in the map, ${bake.extra.size + back.size} spawned, ${bake.missing.size} taken out), " +
        "${design.removals.size} del lines ($found found, $blind blind), ${design.npcs.size} npcs"
}

fun reloadIfChanged() {
    val path = HomeDecorFile.PATH
    val modified = if (Files.exists(path)) Files.getLastModifiedTime(path).toMillis() else 0L
    if (modified == lastModified) return
    lastModified = modified
    try {
        val design = HomeDecorFile.load(path)
        var summary = ""
        // ROOT_CAUSES #144: the dressing goes in under the owner's developer-mode edits, which are applied again on top.
        val edits = gg.rsmod.plugins.content.mechanics.worldedit.WorldEdits.underEdits(world) { summary = applyDesign(design) }
        logger.info("Home decor applied from {}: {}; world edits re-applied on top: {}.", path.toAbsolutePath(), summary, edits)
    } catch (e: Exception) {
        logger.error("Home decor not applied, keeping the current dressing: {}", e.toString())
    }
}

on_world_init {
    world.timers[DECOR_POLL] = FIRST_PASS_TICKS
}

on_timer(DECOR_POLL) {
    reloadIfChanged()
    world.timers[DECOR_POLL] = POLL_TICKS
}
