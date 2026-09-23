package gg.rsmod.plugins.content.mechanics.worldedit

import com.google.gson.GsonBuilder
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import java.io.File

/**
 * Owner 2026-09-23: small world changes (remove / place / rotate scenery) without a server restart.
 *
 * Every such change lives in one data file, [FILE], applied at boot and re-applied live by `reloadedits` or by the
 * developer-mode right-click entries. A reload first undoes everything the previous apply did (spawned objects removed,
 * removed or replaced originals put back), then applies the file again, so the file is always the single truth.
 */
object WorldEdits {
    val FILE = File("C:/RSPS/game/game/data/world_edits.json")

    /** One edit. [op] is "remove" (take loc [id] off the tile) or "spawn" (put loc [id] there with [type]/[rot]). */
    data class Edit(
        val op: String,
        val id: Int,
        val x: Int,
        val z: Int,
        val level: Int = 0,
        val type: Int = 10,
        val rot: Int = 0,
        val note: String? = null,
        /** Edits made by one click share a group, so undo takes the whole click back. */
        val group: Long = 0,
    ) {
        val tile: Tile get() = Tile(x, z, level)
    }

    private data class EditFile(val edits: List<Edit> = emptyList())

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Originals taken out of the world (removed or replaced by a spawn), to put back on undo. */
    private val takenOut = ArrayList<GameObject>()

    /** Objects this system spawned, to remove on undo. */
    private val spawned = ArrayList<GameObject>()

    /** Npcs removed by an edit (put back on undo) and npcs spawned by one (removed on undo). */
    private val removedNpcs = ArrayList<Npc>()
    private val spawnedNpcs = ArrayList<Npc>()

    /** The eight facings an npc can be given, in clockwise order; an npc edit's [Edit.rot] indexes this list. */
    val FACINGS =
        listOf(
            Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
            Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST,
        )

    /** The npc with [id] posted at [tile] (spawn tile first, then where it stands now, then within 3 tiles). */
    fun npcAt(
        world: World,
        tile: Tile,
        id: Int,
    ): Npc? =
        world.npcs.firstOrNull { it.id == id && it.spawnTile.sameAs(tile) }
            ?: world.npcs.firstOrNull { it.id == id && it.tile.sameAs(tile) }
            ?: world.npcs.firstOrNull { it.id == id && it.tile.height == tile.height && it.tile.isWithinRadius(tile, 3) }

    fun face(
        npc: Npc,
        rot: Int,
    ) {
        val index = ((rot % 8) + 8) % 8
        val direction = FACINGS[index]
        facings[npc] = index
        npc.setSpawnFacing(direction)
        npc.faceTile(npc.tile.step(direction))
    }

    fun load(): MutableList<Edit> =
        if (!FILE.exists()) {
            mutableListOf()
        } else {
            (gson.fromJson(FILE.readText(), EditFile::class.java)?.edits ?: emptyList()).toMutableList()
        }

    fun save(edits: List<Edit>) {
        FILE.parentFile?.mkdirs()
        FILE.writeText(gson.toJson(EditFile(edits)))
    }

    fun objectsAt(
        world: World,
        tile: Tile,
    ): List<GameObject> =
        world.chunks.getOrCreate(tile)
            .getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            .toList()

    private fun undo(world: World) {
        spawned.forEach { world.remove(it) }
        spawned.clear()
        takenOut.asReversed().forEach { world.spawn(it) }
        takenOut.clear()
        spawnedNpcs.forEach { world.remove(it) }
        spawnedNpcs.clear()
        removedNpcs.forEach { world.spawn(it) }
        removedNpcs.clear()
    }

    /** Undoes the previous apply and applies the file again; returns the number of edits applied. */
    fun reload(world: World): Int {
        undo(world)
        val edits = load()
        var applied = 0
        for (edit in edits) {
            when (edit.op) {
                "remove" -> {
                    val targets = objectsAt(world, edit.tile).filter { it.id == edit.id }
                    targets.forEach {
                        world.remove(it)
                        if (it in spawned) spawned.remove(it) else takenOut += it
                    }
                    if (targets.isNotEmpty()) applied++
                }
                "spawn" -> {
                    // World.spawn replaces the object in the same client layer on the tile; keep that one to put back.
                    objectsAt(world, edit.tile).firstOrNull { world.locLayer(it.type) == world.locLayer(edit.type) }?.let {
                        if (it in spawned) spawned.remove(it) else takenOut += it
                    }
                    val obj = DynamicObject(edit.id, edit.type, edit.rot and 3, edit.tile)
                    world.spawn(obj)
                    spawned += obj
                    applied++
                }
                "npc_remove" -> {
                    val npc = npcAt(world, edit.tile, edit.id) ?: continue
                    world.remove(npc)
                    if (npc in spawnedNpcs) spawnedNpcs.remove(npc) else removedNpcs += npc
                    applied++
                }
                "npc_spawn" -> {
                    val npc = Npc(edit.id, edit.tile, world).also {
                        it.respawnOverride = true
                        it.static = true
                        it.walkRadius = 0
                    }
                    if (world.spawn(npc)) {
                        spawnedNpcs += npc
                        face(npc, edit.rot)
                        applied++
                    }
                }
                "npc_face" -> {
                    val npc = npcAt(world, edit.tile, edit.id) ?: continue
                    face(npc, edit.rot)
                    applied++
                }
            }
        }
        return applied
    }

    /** What already holds [type]'s client layer on [tile] (a new loc there would replace it), or null when free. */
    fun occupant(
        world: World,
        tile: Tile,
        type: Int,
    ): GameObject? = objectsAt(world, tile).firstOrNull { world.locLayer(it.type) == world.locLayer(type) }

    /** Moves (or with [copy], duplicates) loc [id] from [from] to [to], keeping its type and rotation. */
    fun moveLoc(
        world: World,
        from: Tile,
        id: Int,
        to: Tile,
        copy: Boolean,
    ): Boolean {
        val obj = objectsAt(world, from).firstOrNull { it.id == id } ?: return false
        val action = ArrayList<Edit>()
        if (!copy) action += Edit("remove", id, from.x, from.z, from.height)
        action += Edit("spawn", id, to.x, to.z, to.height, obj.type, obj.rot)
        commit(world, action)
        return true
    }

    /** Facing index in [FACINGS] last given to an npc by an edit. */
    private val facings = java.util.WeakHashMap<Npc, Int>()

    /** Current facing index of [npc] in [FACINGS] (SOUTH when no edit has turned it yet). */
    fun facingIndex(npc: Npc): Int = facings[npc] ?: 4

    /** Npc edits: remove / rotate an eighth clockwise / move / copy. Returns false when no such npc is there. */
    fun editNpc(
        world: World,
        op: String,
        tile: Tile,
        id: Int,
        to: Tile? = null,
    ): Boolean {
        val npc = npcAt(world, tile, id) ?: return false
        val post = npc.spawnTile
        val action = ArrayList<Edit>()
        when (op) {
            "remove" -> action += Edit("npc_remove", id, post.x, post.z, post.height)
            "rotate" -> action += Edit("npc_face", id, post.x, post.z, post.height, rot = (facingIndex(npc) + 1) % 8)
            "move", "copy" -> {
                val target = to ?: return false
                if (op == "move") action += Edit("npc_remove", id, post.x, post.z, post.height)
                action += Edit("npc_spawn", id, target.x, target.z, target.height, rot = facingIndex(npc))
            }
            else -> return false
        }
        commit(world, action)
        return true
    }

    /**
     * Appends one click's edits under a fresh group number, saves and re-applies live. Edits are only ever appended
     * (a rotate or move of an already edited thing is a new remove + spawn), so [undo] can always take exactly one
     * click back by dropping its group.
     */
    fun commit(
        world: World,
        action: List<Edit>,
    ): Int {
        val edits = load()
        val group = (edits.maxOfOrNull { it.group } ?: 0L) + 1
        edits += action.map { it.copy(group = group) }
        save(edits)
        return reload(world)
    }

    fun add(
        world: World,
        edit: Edit,
    ): Int = commit(world, listOf(edit))

    fun removeLoc(
        world: World,
        tile: Tile,
        id: Int,
    ): Int = commit(world, listOf(Edit("remove", id, tile.x, tile.z, tile.height)))

    /** Turns loc [id] on [tile] a quarter clockwise; returns false when no such loc stands there. */
    fun rotateLoc(
        world: World,
        tile: Tile,
        id: Int,
    ): Boolean {
        val obj = objectsAt(world, tile).firstOrNull { it.id == id } ?: return false
        commit(
            world,
            listOf(
                Edit("remove", id, tile.x, tile.z, tile.height),
                Edit("spawn", id, tile.x, tile.z, tile.height, obj.type, (obj.rot + 1) and 3),
            ),
        )
        return true
    }

    /** Takes back the last [count] clicks (whole groups); returns how many edits were dropped. */
    fun undo(
        world: World,
        count: Int,
    ): Int {
        val edits = load()
        var dropped = 0
        repeat(count) {
            val last = edits.lastOrNull() ?: return@repeat
            val group = last.group
            while (edits.isNotEmpty() && edits.last().group == group) {
                edits.removeAt(edits.size - 1)
                dropped++
                if (group == 0L) break
            }
        }
        if (dropped > 0) {
            save(edits)
            reload(world)
        }
        return dropped
    }
}