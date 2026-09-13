package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.collision.ObjectGroup
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.content.mechanics.gates.GateService

val STICK_STATE = AttributeKey<DoorStickState>()

val CHANGES_BEFORE_STICK_TAG = "opens_before_stick"
val RESET_STICK_DELAY_TAG = "reset_stuck_doors_delay"

/**
 * The amount of times a door can be opened or closed before it gets
 * "stuck".
 */
val changesBeforeStick: Int
    get() = getProperty<Int>(CHANGES_BEFORE_STICK_TAG)!!

/**
 * The amount of cycles that must go by before a door becomes
 * "unstuck".
 */
val resetStickDelay: Int
    get() = getProperty<Int>(RESET_STICK_DELAY_TAG)!!

load_metadata {
    propertyFileName = "doors"

    author = "Tomm"
    name = "General Doors"
    description = "Handle the opening and closing of general doors."

    properties(
        CHANGES_BEFORE_STICK_TAG to 5,
        RESET_STICK_DELAY_TAG to 25,
    )
}

load_service(DoorService())

on_world_init {
    world.getService(DoorService::class.java)!!.let { service ->
        service.doors.forEach { door ->
            if (if_obj_has_option(obj = door.opened, option = "close")) {
                on_obj_option(obj = door.opened, option = "close") {
                    val obj = player.getInteractingGameObj()
                    if (!is_stuck(world, obj)) {
                        val newDoor =
                            world.closeDoor(
                                obj,
                                closed = door.closed,
                                invertTransform = obj.type == ObjectType.DIAGONAL_WALL.value,
                            )
                        copy_stick_vars(obj, newDoor)
                        add_stick_var(world, newDoor)
                        player.playSound(Sfx.DOOR_CLOSE)
                    } else {
                        player.message("The door seems to be stuck.")
                        player.playSound(Sfx.DOOR_CREAK)
                    }
                }
            }

            if (if_obj_has_option(obj = door.closed, option = "open")) {
                on_obj_option(obj = door.closed, option = "open") {
                    val obj = player.getInteractingGameObj()
                    val newDoor =
                        world.openDoor(
                            obj,
                            opened = door.opened,
                            invertTransform = obj.type == ObjectType.DIAGONAL_WALL.value,
                        )
                    copy_stick_vars(obj, newDoor)
                    add_stick_var(world, newDoor)
                    player.playSound(Sfx.DOOR_OPEN)
                }
            }
        }

        service.doubleDoors.forEach { doors ->
            if (if_obj_has_option(obj = doors.closed.left, option = "open")) {
                on_obj_option(obj = doors.closed.left, option = "open") {
                    handle_double_doors(player, player.getInteractingGameObj(), doors, open = true)
                }
            }

            if (if_obj_has_option(obj = doors.closed.right, option = "open")) {
                on_obj_option(obj = doors.closed.right, option = "open") {
                    handle_double_doors(player, player.getInteractingGameObj(), doors, open = true)
                }
            }

            if (if_obj_has_option(obj = doors.opened.left, option = "close")) {
                on_obj_option(obj = doors.opened.left, option = "close") {
                    handle_double_doors(player, player.getInteractingGameObj(), doors, open = false)
                }
            }

            if (if_obj_has_option(obj = doors.opened.right, option = "close")) {
                on_obj_option(obj = doors.opened.right, option = "close") {
                    handle_double_doors(player, player.getInteractingGameObj(), doors, open = false)
                }
            }
        }
    }
}

on_world_init_late {
    bind_cache_derived_doors()
    bind_void_sourced_doors()
}

/**
 * Single doors [DoorPairing] declines as ambiguous but Void names explicitly (`<name>_closed` /
 * `<name>_opened` in its `*.objs.toml`, converted to `data/cfg/doors/void-door-pairs.json`, quest/
 * minigame/activity excluded). Void's single-door swing (tile rotated one step anticlockwise,
 * rotation +1 on open; the reverse on close) is exactly [World.openDoor]/[World.closeDoor], so the
 * binding matches [bind_cache_derived_doors]. Each pair is only bound when this cache gives the
 * closed id `Open` and the opened id `Close`, neither id belongs to a configured double door or gate,
 * and no plugin already binds that slot. Void "single" doors (hinge-less, replaced in place) keep
 * their tile and rotation. Gates and double doors need the two-leaf logic and are not bound here.
 */
fun bind_void_sourced_doors() {
    val path = java.nio.file.Paths.get("./data/cfg/doors/void-door-pairs.json")
    if (!java.nio.file.Files.exists(path)) {
        logger.info("Void doors: {} missing; skipped.", path)
        return
    }
    val root =
        java.nio.file.Files.newBufferedReader(path).use {
            com.google.gson.JsonParser().parse(it).asJsonObject
        }
    val multiLeaf = HashSet<Int>()
    world.getService(DoorService::class.java)?.doubleDoors?.forEach { set ->
        multiLeaf += listOf(set.opened.left, set.opened.right, set.closed.left, set.closed.right)
    }
    world.getService(GateService::class.java)?.gates?.forEach { set ->
        multiLeaf += listOf(set.opened.hinge, set.opened.extension, set.closed.hinge, set.closed.extension)
    }

    val closedToOpened = HashMap<Int, Int>()
    val openedToClosed = HashMap<Int, Int>()
    val singleNamed = HashSet<Int>()
    for (element in root.getAsJsonArray("pairs")) {
        val pair = element.asJsonObject
        val closed: Int = pair.get("closed").asInt
        val opened: Int = pair.get("opened").asInt
        closedToOpened[closed] = opened
        openedToClosed[opened] = closed
        if (pair.get("name").asString.contains("single")) {
            singleNamed.add(closed)
            singleNamed.add(opened)
        }
    }

    fun swing(
        player: Player,
        obj: GameObject,
        open: Boolean,
    ) {
        if (!is_wall_object(obj)) {
            return
        }
        if (!open && is_stuck(world, obj)) {
            player.message("The door seems to be stuck.")
            player.playSound(Sfx.DOOR_CREAK)
            return
        }
        val plan =
            VoidDoors.plan(
                obj = obj,
                open = open,
                closedToOpened = closedToOpened,
                openedToClosed = openedToClosed,
                objectAt = { tile, type ->
                    ObjectType.values().firstOrNull { it.value == type }?.let { world.getObject(tile, type = it) }
                },
                defOf = { world.definitions.getNullable(ObjectDef::class.java, it) },
                inPlace = { it in singleNamed },
            )
        if (plan == null) {
            val name = world.definitions.getNullable(ObjectDef::class.java, obj.id)?.name?.lowercase() ?: "door"
            player.message("The $name won't budge.")
            return
        }
        plan.forEach { world.remove(it.original) }
        plan.forEach { replacement ->
            val spawned = DynamicObject(id = replacement.id, type = replacement.original.type, rot = replacement.rot, tile = replacement.tile)
            world.spawn(spawned)
            copy_stick_vars(replacement.original, spawned)
            add_stick_var(world, spawned)
        }
        player.playSound(if (open) Sfx.DOOR_OPEN else Sfx.DOOR_CLOSE)
    }

    var bound = 0
    var skipped = 0
    var rejected = 0
    for (element in root.getAsJsonArray("pairs")) {
        val pair = element.asJsonObject
        val closed: Int = pair.get("closed").asInt
        val opened: Int = pair.get("opened").asInt
        if (closed in multiLeaf || opened in multiLeaf) {
            skipped++
            continue
        }
        val closedDef = world.definitions.getNullable(ObjectDef::class.java, closed)
        val openedDef = world.definitions.getNullable(ObjectDef::class.java, opened)
        val openSlot = closedDef?.options?.indexOfFirst { it.equals("open", ignoreCase = true) } ?: -1
        val closeSlot = openedDef?.options?.indexOfFirst { it.equals("close", ignoreCase = true) } ?: -1
        if (openSlot == -1 || closeSlot == -1) {
            rejected++
            continue
        }

        if ((openSlot + 1) in world.plugins.boundObjectOptions(closed)) {
            skipped++
        } else {
            on_obj_option(obj = closed, option = "open") {
                swing(player, player.getInteractingGameObj(), open = true)
            }
            bound++
        }

        if ((closeSlot + 1) in world.plugins.boundObjectOptions(opened)) {
            skipped++
        } else {
            on_obj_option(obj = opened, option = "close") {
                swing(player, player.getInteractingGameObj(), open = false)
            }
            bound++
        }
    }
    logger.info("Void doors: bound {} sourced door options ({} already handled or multi-leaf, {} not in this cache).", bound, skipped, rejected)
}

/**
 * Fills in the single doors the cache describes *unambiguously* and nobody has written behaviour
 * for.
 *
 * `data/cfg/doors/single-doors.json` lists 35 pairs and `double-doors.json` 15 sets, against 211
 * unambiguous Open/Close pairs in the production cache
 * (`./gradlew :game:runObjectDefProbeTool --args="<cache> doorpairs"`). That gap is the "many doors
 * and gates are non-functional" report: the mechanic was never missing, only the data was, and
 * hand-maintaining a JSON list of every door in Gielinor was never going to converge.
 *
 * The pairing rule, and the ambiguity it refuses to resolve, live in [DoorPairing]. This function
 * only decides which of the resulting pairs may be bound, and is deliberately conservative:
 *  * runs from `on_world_init_late`, i.e. strictly after every ordinary world-init block, and skips
 *    any option slot already bound. Hand-written area doors, quest doors, `gates.plugin.kts` and
 *    the two JSON lists therefore always win, and this only reaches doors nobody has written
 *    behaviour for. The late phase is not cosmetic: `bindObject` throws on a duplicate slot, so a
 *    plain `on_world_init` block here would stop the server booting whenever plugin discovery
 *    happened to order this file before `gates.plugin.kts`;
 *  * every id belonging to a configured double door or gate set is excluded outright, even the
 *    halves those configs happen not to bind. Swinging one leaf of a double door as though it were
 *    a single door leaves the other leaf shut and the tile half-blocked;
 *  * the 1480 definitions that advertise `Open` with no qualifying partner, and the 22 whose
 *    opened half is contested, are never produced by [DoorPairing] in the first place. What they
 *    should do is not derivable from the definitions, so they report through the `Unhandled object
 *    action` diagnostic instead of being guessed at;
 *  * the swing itself is refused at runtime for anything that is not a wall-group object, since
 *    [World.openDoor]'s tile transform is only meaningful for walls.
 */
fun bind_cache_derived_doors() {
    val multiLeaf = HashSet<Int>()
    world.getService(DoorService::class.java)?.doubleDoors?.forEach { set ->
        multiLeaf += listOf(set.opened.left, set.opened.right, set.closed.left, set.closed.right)
    }
    world.getService(GateService::class.java)?.gates?.forEach { set ->
        multiLeaf += listOf(set.opened.hinge, set.opened.extension, set.closed.hinge, set.closed.extension)
    }

    val pairs =
        DoorPairing.derive(
            ids = world.definitions.getAllKeys(ObjectDef::class.java),
            lookup = { world.definitions.getNullable(ObjectDef::class.java, it) },
            excluded = multiLeaf,
        )

    var derived = 0
    var skipped = 0

    pairs.forEach { (closed, opened, slot) ->
        if ((slot + 1) in world.plugins.boundObjectOptions(closed)) {
            skipped++
        } else {
            on_obj_option(obj = closed, option = "open") {
                val obj = player.getInteractingGameObj()
                if (!is_wall_object(obj)) {
                    return@on_obj_option
                }
                val newDoor =
                    world.openDoor(
                        obj,
                        opened = opened,
                        invertTransform = obj.type == ObjectType.DIAGONAL_WALL.value,
                    )
                copy_stick_vars(obj, newDoor)
                add_stick_var(world, newDoor)
                player.playSound(Sfx.DOOR_OPEN)
            }
            derived++
        }

        if ((slot + 1) in world.plugins.boundObjectOptions(opened)) {
            skipped++
        } else {
            on_obj_option(obj = opened, option = "close") {
                val obj = player.getInteractingGameObj()
                if (!is_wall_object(obj)) {
                    return@on_obj_option
                }
                if (is_stuck(world, obj)) {
                    player.message("The door seems to be stuck.")
                    player.playSound(Sfx.DOOR_CREAK)
                    return@on_obj_option
                }
                val newDoor =
                    world.closeDoor(
                        obj,
                        closed = closed,
                        invertTransform = obj.type == ObjectType.DIAGONAL_WALL.value,
                    )
                copy_stick_vars(obj, newDoor)
                add_stick_var(world, newDoor)
                player.playSound(Sfx.DOOR_CLOSE)
            }
            derived++
        }
    }

    logger.info(
        "General Doors: bound $derived cache-derived door options from ${pairs.size} unambiguous " +
            "pairs ($skipped already handled elsewhere).",
    )
}

fun is_wall_object(obj: GameObject): Boolean =
    ObjectType.values().firstOrNull { it.value == obj.type }?.group == ObjectGroup.WALL

fun handle_double_doors(
    p: Player,
    obj: GameObject,
    doors: DoubleDoorSet,
    open: Boolean,
) {
    val left = obj.id == doors.opened.left || obj.id == doors.closed.left
    val right = obj.id == doors.opened.right || obj.id == doors.closed.right

    check(left || right)

    val otherDoorId =
        if (open) {
            if (left) doors.closed.right else doors.closed.left
        } else {
            if (left) doors.opened.right else doors.opened.left
        }
    val otherDoor = get_neighbour_door(world, obj, otherDoorId) ?: return

    if (!open && (is_stuck(world, obj) || is_stuck(world, otherDoor))) {
        p.message("The door seems to be stuck.")
        p.playSound(Sfx.DOOR_CREAK)
        return
    }

    if (open) {
        val door1 = world.openDoor(obj, opened = if (left) doors.opened.left else doors.opened.right, invertRot = left)
        val door2 =
            world.openDoor(otherDoor, opened = if (left) doors.opened.right else doors.opened.left, invertRot = right)
        copy_stick_vars(obj, door1)
        add_stick_var(world, door1)
        copy_stick_vars(obj, door2)
        add_stick_var(world, door2)
        p.playSound(Sfx.DOOR_OPEN)
    } else {
        val door1 =
            world.closeDoor(
                obj,
                closed = if (left) doors.closed.left else doors.closed.right,
                invertRot = left,
                invertTransform = left,
            )
        val door2 =
            world.closeDoor(
                otherDoor,
                closed = if (left) doors.closed.right else doors.closed.left,
                invertRot = right,
                invertTransform = right,
            )
        copy_stick_vars(obj, door1)
        add_stick_var(world, door1)
        copy_stick_vars(obj, door2)
        add_stick_var(world, door2)
        p.playSound(Sfx.DOOR_CLOSE)
    }
}

fun get_neighbour_door(
    world: World,
    obj: GameObject,
    otherDoor: Int,
): GameObject? {
    val tile = obj.tile

    for (x in -1..1) {
        for (z in -1..1) {
            if (x == 0 && z == 0) {
                continue
            }
            val transform = tile.transform(x, z)
            val type = ObjectType.values().firstOrNull { it.value == obj.type } ?: continue
            val tileObj = world.getObject(transform, type = type)
            if (tileObj?.id == otherDoor) {
                return tileObj
            }
        }
    }
    return null
}

fun copy_stick_vars(
    from: GameObject,
    to: GameObject,
) {
    if (from.attr.has(STICK_STATE)) {
        to.attr[STICK_STATE] = from.attr[STICK_STATE]!!
    }
}

fun add_stick_var(
    world: World,
    obj: GameObject,
) {
    var currentChanges = get_stick_changes(obj)
    if (obj.attr.has(STICK_STATE) &&
        Math.abs(world.currentCycle - obj.attr[STICK_STATE]!!.lastChangeCycle) >= resetStickDelay
    ) {
        currentChanges = 0
    }
    obj.attr[STICK_STATE] = DoorStickState(currentChanges + 1, world.currentCycle)
}

fun get_stick_changes(obj: GameObject): Int = obj.attr[STICK_STATE]?.changeCount ?: 0

fun is_stuck(
    world: World,
    obj: GameObject,
): Boolean {
    val stuck = get_stick_changes(obj) >= changesBeforeStick
    if (stuck && Math.abs(world.currentCycle - obj.attr[STICK_STATE]!!.lastChangeCycle) >= resetStickDelay) {
        obj.attr.remove(STICK_STATE)
        return false
    }
    return stuck
}
