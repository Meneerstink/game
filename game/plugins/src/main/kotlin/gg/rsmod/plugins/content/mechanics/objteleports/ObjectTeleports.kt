package gg.rsmod.plugins.content.mechanics.objteleports

import com.google.gson.Gson
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.INTERACTING_OPT_ATTR
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Entity
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sourced object travel: rifts, cave and dungeon entrances, ladders, ropes, chains, trapdoors and
 * staircases whose destination is data rather than code.
 *
 * Every unbound rift, entrance and ladder used to answer "Nothing interesting happens." because the
 * only travel mechanisms were per-id plugins. The table at `data/cfg/object-teleports/` is
 * converted from Void's `data/**/*.teles.toml` (string ids resolved through Void's `*.objs.toml`;
 * quest, minigame and activity files excluded as parked scope). An entry only fires for the exact
 * object id (or its transform), tile and option it was recorded for, so an entry whose object is
 * absent from the revision-667 map can never move a player.
 *
 * Semantics follow Void `ObjectTeleports.teleportTile`: `near` = nearest tile of the area to the
 * player, `delta` = the player's own tile shifted, `to` = a fixed tile. Climbables play Void's
 * `climb_up` (828) / `climb_down` (827) with a 2-tick delay (`Stairs.kt`); everything else moves
 * after one tick.
 */
object ObjectTeleports {
    const val CLIMB_UP_ANIM = 828
    const val CLIMB_DOWN_ANIM = 827

    data class Point(
        val x: Int = 0,
        val z: Int = 0,
        val height: Int = 0,
    )

    data class Area(
        val width: Int = 1,
        val height: Int = 1,
    )

    data class Entry(
        val id: Int = -1,
        val name: String? = null,
        val option: String? = null,
        val tile: Point? = null,
        val to: Point? = null,
        val delta: Point? = null,
        val near: Area? = null,
        val source: String? = null,
    )

    private class Table(
        val teleports: List<Entry>? = null,
    )

    private var byTile: Map<Int, List<Entry>> = emptyMap()

    val size: Int get() = byTile.values.sumOf { it.size }

    fun load(path: Path): Int {
        val table = Files.newBufferedReader(path).use { Gson().fromJson(it, Table::class.java) }
        load(table.teleports.orEmpty())
        return size
    }

    fun load(entries: List<Entry>) {
        byTile = entries.filter { it.tile != null && it.id >= 0 }.groupBy { key(it.tile!!.x, it.tile.z, it.tile.height) }
    }

    /**
     * Keeps only entries whose object id exists in this cache and still advertises the recorded
     * option; returns the rejected entries so the caller can report each one by name.
     */
    fun retainValid(optionsOf: (Int) -> List<String?>?): List<Entry> {
        val rejected = mutableListOf<Entry>()
        byTile =
            byTile.mapValues { (_, entries) ->
                entries.filter { entry ->
                    val wanted = normalise(entry.option)
                    val options = optionsOf(entry.id)
                    val ok = options != null && options.any { normalise(it) == wanted || (normalise(it) == "climb" && wanted.startsWith("climb-")) }
                    if (!ok) rejected += entry
                    ok
                }
            }.filterValues { it.isNotEmpty() }
        return rejected
    }

    fun normalise(option: String?): String = option?.trim()?.lowercase()?.replace(' ', '-') ?: ""

    fun entriesAt(tile: Tile): List<Entry> = byTile[key(tile.x, tile.z, tile.height)].orEmpty()

    /** The entry recorded for this object id set, tile and option; null when the data has none. */
    fun find(
        tile: Tile,
        ids: Collection<Int>,
        option: String?,
    ): Entry? {
        val wanted = normalise(option)
        return entriesAt(tile).firstOrNull { it.id in ids && normalise(it.option) == wanted }
    }

    fun destination(
        entry: Entry,
        from: Tile,
    ): Tile {
        val to = entry.to
        val delta = entry.delta
        val near = entry.near
        return when {
            to != null && near != null ->
                Tile(
                    from.x.coerceIn(to.x, to.x + near.width - 1),
                    from.z.coerceIn(to.z, to.z + near.height - 1),
                    to.height,
                )
            delta != null -> Tile(from.x + delta.x, from.z + delta.z, from.height + delta.height)
            to != null -> Tile(to.x, to.z, to.height)
            else -> from
        }
    }

    /** Void `Stairs.isLadder`: travel through these plays the climb animation. */
    fun isClimbable(name: String): Boolean {
        val compact = name.replace(" ", "")
        return name.contains("ladder", true) || name.contains("rope", true) || name.contains("chain", true) ||
            name.contains("vine", true) || compact.equals("trapdoor", true) || compact.equals("manhole", true)
    }

    /**
     * Handles option [opt] (1-based) on [obj] when the table has an entry for it. A "Climb" option
     * with both directions recorded opens Void's up/down choice.
     */
    fun tryTeleport(
        player: Player,
        obj: GameObject,
        opt: Int,
    ): Boolean {
        val defs = player.world.definitions
        val transformed = obj.getTransform(player)
        val def = defs.get(ObjectDef::class.java, transformed)
        val option = def.options.getOrNull(opt - 1) ?: return false
        val ids = setOf(transformed, obj.id)
        find(obj.tile, ids, option)?.let {
            travel(player, def.name, it)
            return true
        }
        if (normalise(option) != "climb") {
            return false
        }
        val up = find(obj.tile, ids, "climb-up")
        val down = find(obj.tile, ids, "climb-down")
        when {
            up != null && down != null ->
                player.queue {
                    when (options("Go up the stairs.", "Go down the stairs.", "Never mind.", title = "What would you like to do?")) {
                        1 -> travel(player, def.name, up)
                        2 -> travel(player, def.name, down)
                    }
                }
            up != null -> travel(player, def.name, up)
            down != null -> travel(player, def.name, down)
            else -> return false
        }
        return true
    }

    /**
     * For hand-written handlers bound to an object id that only implement some of that id's tiles:
     * the remaining tiles fall through to the table instead of silently doing nothing.
     */
    fun fallback(player: Player): Boolean {
        val obj = player.getInteractingGameObj()
        val opt = player.attr[INTERACTING_OPT_ATTR] ?: return false
        if (tryTeleport(player, obj, opt)) {
            return true
        }
        player.message(Entity.NOTHING_INTERESTING_HAPPENS)
        return false
    }

    private fun travel(
        player: Player,
        name: String,
        entry: Entry,
    ) {
        val climb = isClimbable(name)
        val down = normalise(entry.option) == "climb-down" || entry.name?.endsWith("_down") == true
        player.lockingQueue(lockState = LockState.FULL) {
            if (climb) {
                player.animate(if (down) CLIMB_DOWN_ANIM else CLIMB_UP_ANIM)
                wait(2)
            } else {
                wait(1)
            }
            player.moveTo(destination(entry, player.tile))
        }
    }

    private fun key(
        x: Int,
        z: Int,
        height: Int,
    ): Int = (height shl 30) or (x shl 15) or z
}
