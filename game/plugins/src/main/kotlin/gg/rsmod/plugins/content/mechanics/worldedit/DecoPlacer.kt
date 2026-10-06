package gg.rsmod.plugins.content.mechanics.worldedit

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.message.impl.LocAddChangeMessage
import gg.rsmod.game.message.impl.LocCustomiseMessage
import gg.rsmod.game.message.impl.LocDelMessage
import gg.rsmod.game.message.impl.UpdateZonePartialFollowsMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.LocFit
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.ext.message

/**
 * Developer place mode (owner 2026-10-06, `agent-handoffs/2026-10-06-78-branding-plaatsmodus-opdracht.txt`): put the 78
 * logo / wide banner / flag (or any placed decoration) on a wall or tile and set it precisely - along the wall, height,
 * distance to the wall, uniform scale, quarter-turns and a small angle - with a live preview that only the editor sees.
 *
 * One [Session] per editing player. Every key sends the preview to that player alone (LOC_ADD_CHANGE + LOC_CUSTOMISE,
 * the 667 client's per-instance customisation carrying a [LocFit]); nothing is saved and no other client sees it until
 * Save, which appends one world edit group ([WorldEdits.commit]: remove the old placement when one is being adjusted,
 * then the new spawn with its fit) and re-applies the file, so every client - now, entering later, relogging, after a
 * restart - gets the same placement. Cancel puts back exactly what the editor saw before; undo takes the whole group back.
 *
 * Status goes to the client as the console marker `__RSPS_DEV__:deco|...` (RspsDev overlay); the keys are read by the
 * RSPS client (RspsDev) and sent as `devdeco <op>`.
 */
object DecoPlacer {
    /** What is being adjusted: the loc that stands in the world now (removed on save), or null for a new placement. */
    data class Original(val id: Int, val tile: Tile, val type: Int, val rot: Int)

    class Session(
        val locId: Int,
        val name: String,
        var tile: Tile,
        var type: Int,
        var rot: Int,
        var fit: LocFit,
        val original: Original?,
    ) {
        var coarse = false

        /** Where the editor's client currently shows the preview (tile and settings), to take it away again. */
        var previewTile: Tile? = null
        var previewSettings = 0
        val isWall: Boolean get() = type in 4..8
    }

    private val SESSION = AttributeKey<Session>()

    /** Steps in [LocFit] units: 4 = 1/128 tile (the finest the client can show, 512 units per tile). */
    const val OFFSET_FINE = 4
    const val OFFSET_COARSE = 32
    const val SCALE_FINE = 2
    const val SCALE_COARSE = 16
    const val YAW_FINE = 46
    const val YAW_COARSE = 228

    /** Beyond this distance the preview is out of the editor's loaded area; the session is dropped. */
    private const val MAX_DISTANCE = 30

    /** Tile edge a wall rotation stands on (0 west, 1 north, 2 east, 3 south) as a step out of the tile. */
    val EDGE_DX = intArrayOf(-1, 0, 1, 0)
    val EDGE_DZ = intArrayOf(0, 1, 0, -1)

    /** World direction of the loc's local +x (along the wall, east at rotation 0) per quarter-turn. */
    val ALONG_DX = intArrayOf(1, 0, -1, 0)
    val ALONG_DZ = intArrayOf(0, -1, 0, 1)

    fun session(player: Player): Session? = player.attr[SESSION]

    private fun name(world: World, id: Int): String = runCatching { world.definitions.get(ObjectDef::class.java, id).name }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" } ?: "loc $id"

    /** The edge (wall-rotation numbering) a wall decoration with loc rotation [rot] hangs on: rot 0 = north edge. */
    fun edgeOf(rot: Int): Int = (rot + 1) and 3

    fun rotFor(edge: Int): Int = (edge + 3) and 3

    /**
     * Starts a new placement of [locId]. With [wall] (a straight wall or corner on [tile]) it hangs on that wall as a
     * wall decoration, first on the side the player stands (F turns it to the other side); without, it stands on
     * [tile] as scenery. Returns false (with a message) when the player cannot place there.
     */
    fun startNew(player: Player, locId: Int, tile: Tile, wall: GameObject?): Boolean {
        val world = player.world
        var at = tile
        var type = 10
        var rot = 0
        if (wall != null) {
            val edges = when (wall.type) {
                0 -> listOf(wall.rot)
                2 -> listOf(wall.rot, (wall.rot + 1) and 3)
                else -> emptyList()
            }
            if (edges.isNotEmpty()) {
                val p = player.tile
                val outside = edges.firstOrNull { e -> (p.x - tile.x) * EDGE_DX[e] + (p.z - tile.z) * EDGE_DZ[e] > 0 }
                val (hangTile, edge) =
                    if (outside != null) {
                        Tile(tile.x + EDGE_DX[outside], tile.z + EDGE_DZ[outside], tile.height) to ((outside + 2) and 3)
                    } else {
                        tile to edges.maxByOrNull { e -> -((p.x - tile.x) * EDGE_DX[e] + (p.z - tile.z) * EDGE_DZ[e]) }!!
                    }
                at = hangTile
                type = 4
                rot = rotFor(edge)
            }
        }
        if (!tooFar(player, at)) return false
        if (!customisable(player, locId)) return false
        end(player, restore = true)
        val session = Session(locId, name(world, locId), at, type, rot, LocFit(), original = null)
        player.attr[SESSION] = session
        show(player, session)
        player.message("Place mode: ${session.name} - arrows move, PgUp/PgDn wall distance, +/- size, Q/E angle, R turn, F other side, T step, Enter save, Esc cancel.")
        return true
    }

    /** Adjusts (or with [copy] starts a copy of) the loc [obj] with its current fit. */
    fun startAdjust(player: Player, obj: GameObject, copy: Boolean): Boolean {
        if (obj.type !in 4..11 && obj.type !in 0..3) {
            player.message("That cannot be adjusted here.")
            return false
        }
        if (obj.type in 0..3) {
            player.message("That is a wall; adjust the decoration on it (or place one with the 78 entries).")
            return false
        }
        if (!tooFar(player, obj.tile)) return false
        if (!customisable(player, obj.id)) return false
        end(player, restore = true)
        val fit = obj.fit ?: LocFit()
        val original = if (copy) null else Original(obj.id, obj.tile, obj.type, obj.rot)
        val session = Session(obj.id, name(player.world, obj.id), obj.tile, obj.type, obj.rot, fit, original)
        player.attr[SESSION] = session
        show(player, session)
        player.message(if (copy) "Copy of ${session.name}: move it with the keys, Enter saves it as a new placement." else "Adjusting ${session.name}: keys move it, Enter saves, Esc puts it back.")
        return true
    }

    /**
     * Only a loc the client builds as a dynamic loc (definition opcode 177: the 78 logo / wide banner / flag) shows a
     * placement fit; on any other loc the client keeps the plain model, so the mode refuses it instead of pretending.
     */
    private fun customisable(player: Player, locId: Int): Boolean {
        val def = runCatching { player.world.definitions.get(ObjectDef::class.java, locId) }.getOrNull()
        if (def != null && def.customisable) return true
        player.message("${name(player.world, locId)} ($locId) cannot be fine-placed: only the 78 logo, wide banner and flag carry per-placement settings (use Nudge / Rotate / Pick for it).")
        return false
    }

    private fun tooFar(player: Player, tile: Tile): Boolean {
        if (tile.height != player.tile.height || !tile.isWithinRadius(player.tile, MAX_DISTANCE)) {
            player.message("Too far away to place there - stand closer (within $MAX_DISTANCE tiles, same level).")
            return false
        }
        return true
    }

    /** One key of the place mode; [dir] is +1 / -1 for the value keys. Unknown or invalid input changes nothing. */
    fun key(player: Player, op: String, dir: Int): Boolean {
        val s = session(player) ?: run {
            if (op != "status") player.message("No decoration selected - right-click a wall or tile: 78 logo / banner / flag, or a placed one: Adjust.")
            return false
        }
        if (!s.tile.isWithinRadius(player.tile, MAX_DISTANCE + 10) || s.tile.height != player.tile.height) {
            player.message("You walked away from the decoration - placement cancelled (nothing saved).")
            end(player, restore = true)
            return false
        }
        val step = if (s.coarse) OFFSET_COARSE else OFFSET_FINE
        val d = if (dir >= 0) 1 else -1
        var f = s.fit
        when (op) {
            "along" -> f = f.copy(dx = f.dx + d * step)
            "up" -> f = f.copy(dy = f.dy - d * step)
            "out" -> f = f.copy(dz = f.dz - d * step)
            "scale" -> f = f.copy(scale = (f.scale + d * (if (s.coarse) SCALE_COARSE else SCALE_FINE)).coerceIn(LocFit.MIN_SCALE, LocFit.MAX_SCALE))
            "yaw" -> f = f.copy(yaw = f.yaw + d * (if (s.coarse) YAW_COARSE else YAW_FINE))
            "turn" -> s.rot = (s.rot + d) and 3
            "flip" -> {
                val e = edgeOf(s.rot)
                s.tile = Tile(s.tile.x + EDGE_DX[e], s.tile.z + EDGE_DZ[e], s.tile.height)
                s.rot = (s.rot + 2) and 3
                f = f.copy(dx = -f.dx)
            }
            "step" -> s.coarse = !s.coarse
            "status" -> {}
            "save" -> return save(player)
            "cancel" -> {
                end(player, restore = true)
                player.message("Placement cancelled - nothing changed.")
                return true
            }
            "delete" -> return delete(player)
            "copy" -> return copy(player)
            "undo" -> {
                end(player, restore = true)
                val dropped = WorldEdits.undo(player.world, 1)
                player.message(if (dropped == 0) "No world edits to undo." else "Last placement undone ($dropped edits).")
                return true
            }
            else -> {
                player.message("Unknown place-mode key '$op'.")
                gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INPUT) { "place mode: unknown key '$op' from ${player.username} - nothing changed" }
                return false
            }
        }
        // Along the wall is continuous across tiles: more than half a tile off becomes the next tile.
        while (f.dx > LocFit.UNITS_PER_TILE / 2) {
            f = f.copy(dx = f.dx - LocFit.UNITS_PER_TILE)
            s.tile = Tile(s.tile.x + ALONG_DX[s.rot], s.tile.z + ALONG_DZ[s.rot], s.tile.height)
        }
        while (f.dx < -LocFit.UNITS_PER_TILE / 2) {
            f = f.copy(dx = f.dx + LocFit.UNITS_PER_TILE)
            s.tile = Tile(s.tile.x - ALONG_DX[s.rot], s.tile.z - ALONG_DZ[s.rot], s.tile.height)
        }
        s.fit = f.copy(dy = f.dy.coerceIn(-LocFit.MAX_OFFSET, LocFit.MAX_OFFSET), dz = f.dz.coerceIn(-LocFit.MAX_OFFSET, LocFit.MAX_OFFSET), yaw = ((f.yaw % LocFit.YAW_TURN) + LocFit.YAW_TURN) % LocFit.YAW_TURN)
        show(player, s)
        return true
    }

    /** Saves the session as one world edit group and ends it; everyone then sees the placement. */
    fun save(player: Player): Boolean {
        val s = session(player) ?: return false
        val world = player.world
        val occupant = WorldEdits.occupant(world, s.tile, s.type)
        val original = s.original
        val originalObj = original?.let { o -> WorldEdits.objectsAt(world, o.tile).firstOrNull { it.id == o.id && it.type == o.type } }
        if (occupant != null && occupant !== originalObj) {
            player.message("That spot already holds ${name(world, occupant.id)} (${occupant.id}) in the same layer - move it first or pick another spot.")
            gg.rsmod.game.model.Trace.problem(gg.rsmod.game.model.Trace.Cat.INPUT) { "place mode: save of ${s.name} at ${s.tile.x},${s.tile.z},${s.tile.height} refused - layer held by ${occupant.id}" }
            return false
        }
        val action = ArrayList<WorldEdits.Edit>()
        if (original != null) action += WorldEdits.Edit("remove", original.id, original.tile.x, original.tile.z, original.tile.height)
        val fit = s.fit.normalised().takeUnless { it.isIdentity }
        action += WorldEdits.Edit("spawn", s.locId, s.tile.x, s.tile.z, s.tile.height, s.type, s.rot, fit = fit)
        // The preview is replaced by the real placement on the same tile; take it away first where it differs.
        end(player, restore = false)
        WorldEdits.commit(world, action)
        player.message("Saved ${s.name} at ${s.tile.x},${s.tile.z},${s.tile.height} (${describe(s)}). Undo: U in place mode or undoedit.")
        return true
    }

    private fun delete(player: Player): Boolean {
        val s = session(player) ?: return false
        end(player, restore = true)
        val original = s.original
        if (original == null) {
            player.message("Placement discarded (it was not saved yet).")
            return true
        }
        WorldEdits.commit(player.world, listOf(WorldEdits.Edit("remove", original.id, original.tile.x, original.tile.z, original.tile.height)))
        player.message("Removed ${s.name} at ${original.tile.x},${original.tile.z},${original.tile.height} (undoedit to take it back).")
        return true
    }

    /** Saves the current state (when it is new or changed) and continues with an independent copy of it. */
    private fun copy(player: Player): Boolean {
        val s = session(player) ?: return false
        if (!save(player)) return false
        val copy = Session(s.locId, s.name, s.tile, s.type, s.rot, s.fit, original = null)
        copy.coarse = s.coarse
        player.attr[SESSION] = copy
        show(player, copy)
        player.message("Copy of ${s.name}: move it with the keys and press Enter to save it; both are independent.")
        return true
    }

    /** Ends the session; with [restore] the editor's client gets back exactly what stands there in the world. */
    fun end(player: Player, restore: Boolean) {
        val s = player.attr[SESSION] ?: return
        player.attr.remove(SESSION)
        if (restore) s.previewTile?.let { resync(player, it, s.previewSettings) }
        player.message("__RSPS_DEV__:deco|off", type = ChatMessageType.CONSOLE)
    }

    /** Logout: forget the session (the client is gone; nothing was saved). */
    fun drop(player: Player) {
        player.attr.remove(SESSION)
    }

    private fun zone(player: Player, tile: Tile): UpdateZonePartialFollowsMessage {
        val local = player.lastKnownRegionBase!!.toLocal(tile)
        return UpdateZonePartialFollowsMessage(local.x shr 3, local.z shr 3, tile.height)
    }

    private fun hash(tile: Tile): Int = (tile.chunkOffsetX shl 4) or (tile.chunkOffsetZ and 0x7)

    /** Takes the preview off the editor's client and shows what the world really holds in that layer on [tile]. */
    private fun resync(player: Player, tile: Tile, settings: Int) {
        if (player.lastKnownRegionBase == null) return
        player.write(zone(player, tile), LocDelMessage(settings, hash(tile)))
        val occupant = WorldEdits.occupant(player.world, tile, settings shr 2) ?: return
        player.write(zone(player, tile), LocAddChangeMessage(occupant.id, occupant.settings.toInt(), hash(tile)))
        occupant.fit?.let { player.write(zone(player, tile), LocCustomiseMessage(occupant.id, occupant.settings.toInt(), hash(tile), it)) }
    }

    /** Sends the preview (editor only) and the status line. */
    private fun show(player: Player, s: Session) {
        if (player.lastKnownRegionBase == null) return
        val settings = (s.type shl 2) or (s.rot and 3)
        val previous = s.previewTile
        if (previous != null && (!previous.sameAs(s.tile) || (s.previewSettings shr 2) != s.type)) resync(player, previous, s.previewSettings)
        player.write(zone(player, s.tile), LocAddChangeMessage(s.locId, settings, hash(s.tile)))
        player.write(zone(player, s.tile), LocCustomiseMessage(s.locId, settings, hash(s.tile), s.fit.normalised()))
        s.previewTile = s.tile
        s.previewSettings = settings
        player.message("__RSPS_DEV__:deco|on|${status(s).joinToString("|")}", type = ChatMessageType.CONSOLE)
    }

    private val BACK = arrayOf("north", "east", "south", "west")

    private fun tiles(units: Int): String = String.format(java.util.Locale.ROOT, "%+.3f", units / LocFit.UNITS_PER_TILE.toDouble())

    fun describe(s: Session): String {
        val f = s.fit
        return "along ${tiles(f.dx)} up ${tiles(-f.dy)} out ${tiles(-f.dz)} tile, size ${f.scale * 100 / LocFit.SCALE_ONE}%, angle ${String.format(java.util.Locale.ROOT, "%+.1f", LocFit.degrees(if (f.yaw > LocFit.YAW_TURN / 2) f.yaw - LocFit.YAW_TURN else f.yaw))} deg"
    }

    /** The overlay lines: what, where and how (values the client shows next to the keys). */
    fun status(s: Session): List<String> {
        val where = if (s.isWall) "back ${BACK[s.rot]}" else "rotation ${s.rot}"
        val mode = if (s.original == null) "new" else "adjusting"
        return listOf(
            "${s.name}  ${s.tile.x},${s.tile.z},${s.tile.height}  $where  ($mode)",
            describe(s),
            "step: ${if (s.coarse) "coarse 1/16 tile, 12.5%, 5 deg" else "fine 1/128 tile, 1.6%, 1 deg"}",
        )
    }
}
