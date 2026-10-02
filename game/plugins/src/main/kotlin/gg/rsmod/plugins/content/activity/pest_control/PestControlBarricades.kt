package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message

/**
 * Barricades, gates and the camp's tower ladders (2009scape PCObjectHandler + PestControlSession barricade list).
 *
 * Every game tracks its own barricade objects (they live in its instance). Damage moves an id one step along its chain
 * (barricades +3, gates +4) until it reaches a destroyed id ([PC_DESTROYED_IDS]); a repair (hammer + logs, 5 zeal) moves it
 * one step back. Gates open and close in pairs and cannot be moved once damaged.
 */
object PestControlBarricades {
    /** 2009scape PestControlSession.OBJECT_OFFSETS: every barricade and gate tile of the island, local coordinates. */
    private val OFFSETS =
        listOf(
            46 to 33, 46 to 32, 32 to 25, 33 to 25, 19 to 32, 19 to 33, 49 to 33, 49 to 32, 49 to 31, 49 to 30, 52 to 27, 52 to 26,
            52 to 25, 52 to 24, 52 to 14, 52 to 13, 52 to 12, 52 to 11, 42 to 18, 43 to 18, 44 to 18, 45 to 18, 32 to 15, 33 to 15,
            34 to 15, 35 to 15, 23 to 18, 24 to 18, 25 to 18, 26 to 18, 13 to 11, 13 to 12, 13 to 13, 13 to 14, 12 to 28, 12 to 29,
            12 to 30, 12 to 31,
        )

    /** 2009scape: "STRANGE_PHANTOM_OBJECT_THAT_SHARES_A_TILE_WITH_A_BARRICADE" (667 cache: "Barricade", no options). */
    private const val PHANTOM = 25636

    fun isBarricadeOrGate(id: Int): Boolean = id in PC_BARRICADE_IDS || id in PC_GATE_IDS

    fun objectsAt(
        world: World,
        tile: Tile,
    ): List<GameObject> =
        world.chunks.get(tile, createIfNeeded = true)?.getEntities<GameObject>(tile, EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT)
            ?: emptyList()

    fun collect(game: PestControlGame) {
        for ((x, z) in OFFSETS) {
            for (obj in objectsAt(game.world, game.local(x, z))) {
                if (obj.id != PHANTOM && isBarricadeOrGate(obj.id)) game.barricades.add(obj)
            }
        }
        if (game.barricades.isEmpty()) PestControlTrace.problem("no barricades found in the ${game.tier.displayName} instance at ${game.base}")
    }

    private fun replace(
        game: PestControlGame,
        old: GameObject,
        newId: Int,
        tile: Tile = old.tile,
        rot: Int = old.rot,
        type: Int = old.type,
    ): GameObject {
        val replacement = DynamicObject(newId, type, rot, tile)
        game.barricades.remove(old)
        game.world.remove(old)
        game.world.spawn(replacement)
        game.barricades.add(replacement)
        return replacement
    }

    /** One step of damage (ravager hit, splatter explosion). Returns the new object, or null when it was already destroyed. */
    fun damage(
        game: PestControlGame,
        obj: GameObject,
    ): GameObject? {
        if (obj !in game.barricades || obj.id in PC_DESTROYED_IDS) return null
        val newId = obj.id + if (obj.id < 14233) 3 else 4
        // 2009scape: a destroyed piece becomes ground decoration (type 22) so it no longer blocks the path.
        val type = if (newId in PC_DESTROYED_IDS) 22 else obj.type
        return replace(game, obj, newId, type = type)
    }

    fun isDamageable(obj: GameObject): Boolean = obj.id !in PC_DESTROYED_IDS

    /** Repair option (barricades 14227-14232, damaged gates 14237-14248). */
    fun repair(
        player: Player,
        obj: GameObject,
    ) {
        val game = PestControlHandler.gameOf(player) ?: return
        if (obj !in game.barricades) return
        val newId = obj.id - if (obj.id < 14233) 3 else 4
        if (newId < PC_BARRICADE_IDS.first || (obj.id in PC_GATE_IDS && newId < PC_GATE_IDS.first)) {
            player.message("That doesn't need repairing.")
            return
        }
        if (!player.inventory.contains(PC_HAMMER)) {
            player.message("You'll need a hammer to make any repairs!")
            return
        }
        if (player.inventory.remove(PC_LOGS).hasFailed()) {
            player.message("You'll need some logs to make any repairs!")
            return
        }
        PestControlHandler.addZeal(player, PC_REPAIR_ZEAL)
        player.animate(PC_REPAIR_ANIM)
        // 2009scape getObjectType: the diagonal barricade pieces are type 9, the rest type 0; the repaired piece blocks again.
        val type = if (newId == 14225 || newId == 14226 || newId == 14228 || newId == 14229) 9 else if (obj.type == 22) 0 else obj.type
        replace(game, obj, newId, type = type)
    }

    /** Open / Close on a gate (2009scape handleGates). */
    fun toggleGate(
        player: Player,
        obj: GameObject,
    ) {
        val game = PestControlHandler.gameOf(player) ?: return
        if (obj !in game.barricades) return
        val open = obj.id % 2 != 0
        val second = secondDoor(game, obj) ?: return
        if (obj.id > 14240 || second.id > 14240) {
            player.message("It's too damaged to be moved!")
            return
        }
        val rotation = gateRotation(obj)
        val dir = if (open) obj.rot else rotation
        val direction = directionOf(if (!open) dir else (3 + dir) % 4)
        moveGate(game, obj, open, rotation, direction)
        moveGate(game, second, open, gateRotation(second), direction)
    }

    private fun moveGate(
        game: PestControlGame,
        obj: GameObject,
        open: Boolean,
        rotation: Int,
        direction: Direction,
    ) {
        val tile = obj.tile.transform(direction.getDeltaX(), direction.getDeltaZ(), 0)
        val rot = if (open) rotation else (rotationOf(direction) + 3) % 4
        replace(game, obj, obj.id + if (open) 1 else -1, tile = tile, rot = rot)
    }

    private fun gateRotation(obj: GameObject): Int {
        var id = obj.id
        if (id > 14236) id -= 4
        return when (id) {
            14233 -> (obj.rot + 1) % 4
            14234 -> obj.rot % 4
            14235 -> (obj.rot + 3) % 4
            14236 -> (obj.rot + 2) % 4
            else -> 0
        }
    }

    /** 2009scape Direction.get(rotation) (Direction.java: NORTH value 0, EAST 1, SOUTH 2, WEST 3). */
    private fun directionOf(rotation: Int): Direction =
        when (rotation and 3) {
            0 -> Direction.NORTH
            1 -> Direction.EAST
            2 -> Direction.SOUTH
            else -> Direction.WEST
        }

    private fun rotationOf(direction: Direction): Int =
        when (direction) {
            Direction.NORTH -> 0
            Direction.EAST -> 1
            Direction.SOUTH -> 2
            else -> 3
        }

    private fun secondDoor(
        game: PestControlGame,
        obj: GameObject,
    ): GameObject? {
        val name = obj.getDef(game.world.definitions).name
        for ((dx, dz) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val found = objectsAt(game.world, obj.tile.transform(dx, dz, 0)).firstOrNull { it.getDef(game.world.definitions).name == name && it in game.barricades }
            if (found != null) return found
        }
        return null
    }

    /** Tower ladders (2009scape handleTurretTower): step over the tower wall. */
    fun climbTower(
        player: Player,
        obj: GameObject,
    ) {
        val game = PestControlHandler.gameOf(player) ?: return
        val x = obj.tile.x - game.base.x
        val z = obj.tile.z - game.base.z
        val px = player.tile.x - game.base.x
        val pz = player.tile.z - game.base.z
        val dest =
            when {
                x == 45 && z == 41 -> if (px < x + 1) game.local(x + 1, 41) else game.local(x - 1, 41)
                (x == 42 || x == 23) && z == 26 -> if (pz > 25) game.local(x, 25) else game.local(x, 27)
                x == 20 && z == 41 -> if (px > 19) game.local(x - 1, 41) else game.local(x + 1, 41)
                else -> null
            }
        if (dest == null) {
            PestControlTrace.problem("tower ladder at local ($x, $z) has no 2009scape destination")
            return
        }
        player.moveTo(dest)
    }
}
