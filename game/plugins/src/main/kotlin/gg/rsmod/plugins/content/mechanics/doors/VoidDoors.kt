package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.GameObject

/**
 * Door, double door and gate swings ported from Void (`content/entity/obj/door/{Door,DoubleDoor,Gate}.kt`,
 * `entity/obj/Replace.kt`).
 *
 * Void does not decide at data time whether a door is single or double: on every click it looks for a
 * neighbouring door leaf of the same shape (for gates also the two perpendicular neighbours) and only
 * swings a lone door when none exists. Ids pair through Void's `<name>_closed` / `<name>_opened` naming
 * (`data/cfg/doors/void-door-pairs.json`). Rotation math uses Void's `Direction.cardinal` order
 * (NORTH, EAST, SOUTH, WEST); `mirrored` is cache opcode 62 ([ObjectDef.rotated]).
 *
 * This object only computes the replacement; the plugin applies it.
 */
object VoidDoors {
    data class Replacement(
        val original: GameObject,
        val id: Int,
        val tile: Tile,
        val rot: Int,
    )

    private val CARDINAL = arrayOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)

    fun rotate(
        rotation: Int,
        clockwise: Int,
    ): Int = (rotation + clockwise) and 0x3

    /** Void `Door.tile(tile, rotation, anticlockwise)`. */
    fun tile(
        tile: Tile,
        rotation: Int,
        anticlockwise: Int,
    ): Tile {
        val (dx, dz) = CARDINAL[rotate(rotation, -anticlockwise)]
        return Tile(tile.x + dx, tile.z + dz, tile.height)
    }

    fun isDoor(def: ObjectDef?): Boolean {
        val name = def?.name ?: return false
        return (name.contains("door", true) && !name.contains("trap", true)) || name.contains("gate", true)
    }

    fun isGate(def: ObjectDef?): Boolean = def?.name?.contains("gate", true) == true

    /**
     * Void `DoubleDoor.get`: the neighbouring leaf of [obj], looking along its rotation turned
     * [clockwise] (0 when opening, 1 when closing), then the opposite side, then for gates the two
     * perpendicular sides.
     */
    fun neighbour(
        obj: GameObject,
        def: ObjectDef,
        clockwise: Int,
        objectAt: (Tile, Int) -> GameObject?,
        defOf: (Int) -> ObjectDef?,
    ): GameObject? {
        var index = rotate(obj.rot, clockwise)
        val order = mutableListOf(index, (index + 2) and 0x3)
        if (isGate(def)) {
            // Void: orientation.inverse().rotate(2) is the inverse turned 90 degrees clockwise, then its inverse.
            index = ((index + 2) and 0x3)
            val perpendicular = (index + 1) and 0x3
            order += perpendicular
            order += (perpendicular + 2) and 0x3
        }
        order.forEachIndexed { i, direction ->
            val (dx, dz) = CARDINAL[direction]
            val candidate = objectAt(Tile(obj.tile.x + dx, obj.tile.z + dz, obj.tile.height), obj.type) ?: return@forEachIndexed
            val candidateDef = defOf(candidate.id)
            val qualifies = if (i < 2) isDoor(candidateDef) else isGate(candidateDef)
            if (qualifies) return candidate
        }
        return null
    }

    /**
     * The replacement for toggling [obj] ([open] = true swings a closed door open). Null when Void
     * would answer "won't budge": the other leaf of a double is not in the same state.
     */
    fun plan(
        obj: GameObject,
        open: Boolean,
        closedToOpened: Map<Int, Int>,
        openedToClosed: Map<Int, Int>,
        objectAt: (Tile, Int) -> GameObject?,
        defOf: (Int) -> ObjectDef?,
        inPlace: (Int) -> Boolean,
    ): List<Replacement>? {
        val def = defOf(obj.id) ?: return null
        val next = (if (open) closedToOpened else openedToClosed)[obj.id] ?: return null
        val double = neighbour(obj, def, if (open) 0 else 1, objectAt, defOf)
        if (double == null) {
            if (inPlace(obj.id)) {
                return listOf(Replacement(obj, next, obj.tile, obj.rot))
            }
            return if (open) {
                listOf(Replacement(obj, next, tile(obj.tile, obj.rot, 1), rotate(obj.rot, 1)))
            } else {
                listOf(Replacement(obj, next, tile(obj.tile, obj.rot, 0), rotate(obj.rot, 3)))
            }
        }
        val doubleNext = (if (open) closedToOpened else openedToClosed)[double.id] ?: return null

        val (dirX, dirZ) = CARDINAL[obj.rot and 0x3]
        val deltaX = (obj.tile.x - double.tile.x).coerceIn(-1, 1)
        val deltaZ = (obj.tile.z - double.tile.z).coerceIn(-1, 1)
        val flip = dirX == deltaX && dirZ == deltaZ

        if (isGate(def)) {
            val first = if (flip) double else obj
            val second = if (flip) obj else double
            val firstNext = if (flip) doubleNext else next
            val secondNext = if (flip) next else doubleNext
            val objRotation = if (open) 3 else 1
            val hingeTileRotation = if (open) 1 else 2
            val tileRotation = if (open) 1 else 3
            val hinge = tile(first.tile, first.rot, hingeTileRotation)
            return listOf(
                Replacement(first, firstNext, hinge, rotate(first.rot, objRotation)),
                Replacement(second, secondNext, tile(hinge, second.rot, tileRotation), rotate(second.rot, objRotation)),
            )
        }
        if (open) {
            return listOf(
                Replacement(obj, next, tile(obj.tile, obj.rot, 1), rotate(obj.rot, if (flip) 1 else 3)),
                Replacement(double, doubleNext, tile(double.tile, double.rot, 1), rotate(double.rot, if (flip) 3 else 1)),
            )
        }
        val mirror = def.rotated
        return listOf(
            Replacement(obj, next, tile(obj.tile, obj.rot, if (mirror) 2 else 0), rotate(obj.rot, if (flip || mirror) 1 else 3)),
            Replacement(double, doubleNext, tile(double.tile, double.rot, if (mirror) 0 else 2), rotate(double.rot, if (flip || mirror) 3 else 1)),
        )
    }
}
