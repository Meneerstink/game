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
        /** Step size: 0 fine, 1 medium, 2 coarse ([OFFSET_STEPS], [SCALE_STEPS], [YAW_STEPS]); T cycles it. */
        var stepLevel = 0

        /**
         * Hung on a diagonal wall (or anything else hung in the middle of its tile): the other side is a half turn on the
         * same tile, not the neighbouring tile across the edge.
         */
        var diagonal = false

        /** Where the editor's client currently shows the preview (tile and settings), to take it away again. */
        var previewTile: Tile? = null
        var previewSettings = 0
        val isWall: Boolean get() = type in 4..8
    }

    private val SESSION = AttributeKey<Session>()

    /**
     * Steps in [LocFit] units per step size (fine / medium / coarse): offsets 4 = 1/128 tile (the finest the client can
     * show, 512 units per tile), 32 = 1/16, 128 = 1/4 tile; scale 1.6% / 12.5% / 25%; angle 1 / 5 / 45 degrees (owner
     * 2026-10-09: high gables and slanted walls took hundreds of presses).
     */
    val OFFSET_STEPS = intArrayOf(4, 32, 128)
    val SCALE_STEPS = intArrayOf(2, 16, 32)
    val YAW_STEPS = intArrayOf(46, 228, 2048)
    private val STEP_NAMES = arrayOf("fine 1/128 tile, 1.6%, 1 deg", "medium 1/16 tile, 12.5%, 5 deg", "coarse 1/4 tile, 25%, 45 deg")

    /**
     * Diagonal wall (shape 9) with rotation r: the side its native decorations face (MapRegion WALLDECOR_DIAGONAL_X/ZOFFSET,
     * the 667 client's offset of a shape-6 decoration off that wall).
     */
    val DIAGONAL_NX = intArrayOf(1, -1, -1, 1)
    val DIAGONAL_NZ = intArrayOf(-1, -1, 1, 1)

    /**
     * Loc rotation whose back points to (bx, bz) once the 78 decoration is turned 45 degrees ([LocFit.yaw] 2048): the
     * models hang with their back to local north, so rotation 0 + 45 degrees puts the back north-east, each quarter-turn
     * a quarter clockwise further (north-east, south-east, south-west, north-west).
     */
    fun diagonalRot(bx: Int, bz: Int): Int =
        when {
            bx > 0 && bz > 0 -> 0
            bx > 0 -> 1
            bz < 0 -> 2
            else -> 3
        }

    /** The editor's own unsaved preview of [id] on [tile] (it exists only in his client, the server has no loc there). */
    fun isPreview(player: Player, tile: Tile, id: Int): Boolean {
        val s = session(player) ?: return false
        val at = s.previewTile ?: return false
        return s.locId == id && at.sameAs(tile)
    }

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
    fun startNew(player: Player, locId: Int, tile: Tile, wall: GameObject?, flat: Boolean = false): Boolean {
        val world = player.world
        var at = tile
        var type = 10
        var rot = 0
        var fit = LocFit()
        var diagonal = false
        val p = player.tile
        if (wall == null) {
            // On the floor: stand it on the tile facing the player (its back away from him).
            val dx = p.x - tile.x
            val dz = p.z - tile.z
            rot = if (Math.abs(dz) >= Math.abs(dx)) (if (dz > 0) 2 else 0) else (if (dx > 0) 3 else 1)
        } else if (wall.type == 9) {
            // Diagonal wall: a wall decoration in the middle of the tile (the diagonal runs through it), turned 45 degrees
            // with its back on the wall line, on the side the player stands (F: the other side).
            val nx = DIAGONAL_NX[wall.rot]
            val nz = DIAGONAL_NZ[wall.rot]
            val side = if ((p.x - tile.x) * nx + (p.z - tile.z) * nz >= 0) 1 else -1
            type = 4
            rot = diagonalRot(-side * nx, -side * nz)
            fit = LocFit(dz = -LocFit.UNITS_PER_TILE / 2, yaw = LocFit.YAW_QUARTER / 2)
            diagonal = true
        } else if (wall.type != 0 && wall.type != 2) {
            // Anything else (a facade, a gable, a pillar, a corner post, a table): hang it flat on the face of the loc's
            // footprint that looks at the player, as a wall decoration on the tile in front of that face.
            val def = runCatching { world.definitions.get(ObjectDef::class.java, wall.id) }.getOrNull()
            val w = if (wall.rot and 1 == 1) def?.length ?: 1 else def?.width ?: 1
            val l = if (wall.rot and 1 == 1) def?.width ?: 1 else def?.length ?: 1
            val x0 = tile.x
            val z0 = tile.z
            val x1 = x0 + w - 1
            val z1 = z0 + l - 1
            // Distance outside the footprint per side (0 west, 1 north, 2 east, 3 south); inside: the nearest face.
            val outside = intArrayOf(x0 - p.x, p.z - z1, p.x - x1, z0 - p.z)
            val face =
                if (outside.any { it > 0 }) {
                    outside.indices.maxByOrNull { outside[it] }!!
                } else {
                    val inside = intArrayOf(p.x - x0, z1 - p.z, x1 - p.x, p.z - z0)
                    inside.indices.minByOrNull { inside[it] }!!
                }
            val cx = p.x.coerceIn(x0, x1)
            val cz = p.z.coerceIn(z0, z1)
            at =
                when (face) {
                    0 -> Tile(x0 - 1, cz, tile.height)
                    1 -> Tile(cx, z1 + 1, tile.height)
                    2 -> Tile(x1 + 1, cz, tile.height)
                    else -> Tile(cx, z0 - 1, tile.height)
                }
            type = 4
            rot = rotFor((face + 2) and 3)
        } else {
            val edges = if (wall.type == 0) listOf(wall.rot) else listOf(wall.rot, (wall.rot + 1) and 3)
            run {
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
        if (flat && wall == null) fit = LocFit(pitch = LocFit.PITCH_FLAT)
        val session = Session(locId, name(world, locId), at, type, rot, fit, original = null)
        session.diagonal = diagonal
        player.attr[SESSION] = session
        show(player, session)
        player.message("Place mode: ${session.name} - arrows move, PgUp/PgDn wall distance, +/- size, Q/E angle, R turn, F other side, T step size. ENTER SAVES it, Esc cancels.")
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
        // Hung in the middle of its tile at an angle (a diagonal wall): F turns it on the spot.
        session.diagonal = obj.type in 4..8 && fit.dz <= -LocFit.UNITS_PER_TILE * 3 / 8 && fit.yaw % LocFit.YAW_QUARTER != 0
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
        val step = OFFSET_STEPS[s.stepLevel]
        val d = if (dir >= 0) 1 else -1
        var f = s.fit
        // Along / away follow the decoration's own angle (its yaw), so an angled one keeps sliding along its own face
        // (owner 2026-10-09). The client turns x' = x cos + z sin, z' = z cos - x sin: local +x -> (cos, -sin), +z -> (sin, cos).
        val angle = f.yaw * 2 * Math.PI / LocFit.YAW_TURN
        val cos = Math.cos(angle)
        val sin = Math.sin(angle)
        when (op) {
            "along" -> f = f.copy(dx = f.dx + Math.round(d * step * cos).toInt(), dz = f.dz - Math.round(d * step * sin).toInt())
            "up" -> f = f.copy(dy = f.dy - d * step)
            "out" -> f = f.copy(dx = f.dx - Math.round(d * step * sin).toInt(), dz = f.dz - Math.round(d * step * cos).toInt())
            "scale" -> f = f.copy(scale = (f.scale + d * SCALE_STEPS[s.stepLevel]).coerceIn(LocFit.MIN_SCALE, LocFit.MAX_SCALE))
            "yaw" -> f = f.copy(yaw = f.yaw + d * YAW_STEPS[s.stepLevel])
            "pitch" -> f = f.copy(pitch = f.pitch + d * YAW_STEPS[s.stepLevel])
            "flat" -> {
                // L: lay it flat on the floor of its tile (a loc standing on the tile, face up), or stand it up again.
                if (f.pitch % LocFit.YAW_TURN == 0) {
                    s.type = 10
                    s.diagonal = false
                    f = LocFit(scale = f.scale, yaw = f.yaw, pitch = LocFit.PITCH_FLAT)
                } else {
                    f = f.copy(pitch = 0, dy = 0)
                }
            }
            "turn" -> s.rot = (s.rot + d) and 3
            "flip" -> {
                if (!s.diagonal) {
                    val e = edgeOf(s.rot)
                    s.tile = Tile(s.tile.x + EDGE_DX[e], s.tile.z + EDGE_DZ[e], s.tile.height)
                }
                s.rot = (s.rot + 2) and 3
                f = f.copy(dx = -f.dx)
            }
            "step" -> s.stepLevel = (s.stepLevel + 1) % OFFSET_STEPS.size
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
        s.fit = f.copy(dy = f.dy.coerceIn(-LocFit.MAX_OFFSET, LocFit.MAX_OFFSET), dz = f.dz.coerceIn(-LocFit.MAX_OFFSET, LocFit.MAX_OFFSET), yaw = ((f.yaw % LocFit.YAW_TURN) + LocFit.YAW_TURN) % LocFit.YAW_TURN, pitch = ((f.pitch % LocFit.YAW_TURN) + LocFit.YAW_TURN) % LocFit.YAW_TURN)
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
        copy.stepLevel = s.stepLevel
        copy.diagonal = s.diagonal
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
        return "along ${tiles(f.dx)} up ${tiles(-f.dy)} out ${tiles(-f.dz)} tile, size ${f.scale * 100 / LocFit.SCALE_ONE}%, angle ${String.format(java.util.Locale.ROOT, "%+.1f", LocFit.degrees(if (f.yaw > LocFit.YAW_TURN / 2) f.yaw - LocFit.YAW_TURN else f.yaw))} deg" +
            if (f.pitch % LocFit.YAW_TURN == 0) "" else (if (f.pitch == LocFit.PITCH_FLAT) ", flat on the floor" else ", tilt ${String.format(java.util.Locale.ROOT, "%+.1f", LocFit.degrees(if (f.pitch > LocFit.YAW_TURN / 2) f.pitch - LocFit.YAW_TURN else f.pitch))} deg")
    }

    /** The overlay lines: what, where and how (values the client shows next to the keys). */
    fun status(s: Session): List<String> {
        val where = if (s.isWall) "back ${BACK[s.rot]}" else "rotation ${s.rot}"
        val mode = if (s.original == null) "new" else "adjusting"
        return listOf(
            "${s.name}  ${s.tile.x},${s.tile.z},${s.tile.height}  $where  ($mode)",
            describe(s),
            "step: ${STEP_NAMES[s.stepLevel]}   (T: next step size)   ENTER = SAVE",
        )
    }
}
