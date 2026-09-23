package gg.rsmod.plugins.content.mechanics.doors

import gg.rsmod.game.Server.Companion.logger
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.collision.ObjectGroup
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.plugins.content.mechanics.gates.GateService

val STICK_STATE = AttributeKey<DoorStickState>()
val OPENED_FROM = AttributeKey<Int>()

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
    bind_ambiguous_single_doors()
    bind_container_doors()
    bind_trapdoor_teleport_doors()
    bind_walkthrough_doors()
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

/**
 * Binds the [MultiCloseDoor] groups [bind_cache_derived_doors] has to skip: an `opened` id whose
 * `Close` half is structurally claimed by two (or more) equally-valid `closed` ids of the same name.
 * Every closed id was confirmed a real, distinct in-world door with `ObjectPlacementProbeTool` (not
 * two adjacent gate leaves - those are excluded the same way as [bind_cache_derived_doors], via the
 * gate/double-door id sets) before this was written; 2026-09-18's largest single instance was id
 * 14749 (44 separate doorways across a dozen regions) sharing its open state with the much rarer
 * 14751, all of them entirely unbound before this ran.
 *
 * Which closed id a given `opened` instance should revert to cannot be told from the object
 * definitions, so it is not guessed: opening any of the group's closed ids tags the resulting
 * `opened` object with the id it actually came from ([OPENED_FROM]), and closing reads that tag back.
 * A door that somehow reaches `close` without the tag (e.g. spawned by other means) falls back to
 * the group's first closed id - the same door/rotation-invariant reasoning [World.openDoor] and
 * [World.closeDoor] already rely on elsewhere in this file, since the tile/rotation math depends
 * only on the opened object's own rotation, not on which specific closed id is used to redraw it.
 */
fun bind_ambiguous_single_doors() {
    val multiLeaf = HashSet<Int>()
    world.getService(DoorService::class.java)?.doubleDoors?.forEach { set ->
        multiLeaf += listOf(set.opened.left, set.opened.right, set.closed.left, set.closed.right)
    }
    world.getService(GateService::class.java)?.gates?.forEach { set ->
        multiLeaf += listOf(set.opened.hinge, set.opened.extension, set.closed.hinge, set.closed.extension)
    }

    val groups =
        DoorPairing.deriveMultiClose(
            ids = world.definitions.getAllKeys(ObjectDef::class.java),
            lookup = { world.definitions.getNullable(ObjectDef::class.java, it) },
            excluded = multiLeaf,
        )

    var derived = 0
    var skipped = 0

    groups.forEach { (closedIds, opened, slot) ->
        closedIds.forEach { closed ->
            if ((slot + 1) in world.plugins.boundObjectOptions(closed)) {
                skipped++
                return@forEach
            }
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
                newDoor.attr[OPENED_FROM] = closed
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
                val closed = obj.attr[OPENED_FROM] ?: closedIds.first()
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
        "General Doors: bound $derived ambiguous-single-door options from ${groups.size} multi-closed " +
            "groups ($skipped already handled elsewhere).",
    )
}

/**
 * Names that structurally look like a door/gate/fence/wall/entrance/exit but are deliberately
 * excluded from the blanket walk-through fallback below, because the name itself claims a state a
 * bare "walk through" would trivialise (a lock, a vault) rather than an ordinary passage.
 */
val WALKTHROUGH_EXCLUDED_NAME_FRAGMENTS =
    listOf("locked", "mithril", "vault", "safe", "drawer", "wardrobe", "cupboard", "cabinet", "chest", "coffin", "crate", "cage", "box")

/**
 * Ids 2009scape's own `DoorActionHandler` carves out by hand rather than trusting the generic path:
 * 25341 is a quest-locked mithril door; 3626-3632 are the Maze random event's walls (special-cased
 * there as "ignore second door for Maze Random"); 4545/4546 are a quest puzzle ("HftD Strange Wall").
 * None of that content is built in this server, so a blind walk-through here would not currently be
 * reachable either way, but the ids are excluded to match the donor precedent instead of assuming.
 */
val WALKTHROUGH_EXCLUDED_IDS = setOf(25341, 3626, 3627, 3628, 3629, 3630, 3631, 3632, 4545, 4546)

/**
 * The generic fallback for the door-shaped remainder [bind_cache_derived_doors],
 * [bind_void_sourced_doors] and [bind_ambiguous_single_doors] cannot bind at all: an object whose
 * *only* option is `Open`, with no cache-derivable second state to swap to (no `Close`-bearing
 * neighbour exists, contested or otherwise - if one did, one of the three binders above would
 * already have claimed it). 2009scape's `DoorActionHandler.handleAutowalkDoor` is the sourced
 * precedent for exactly this shape (`DoorConfigLoader.forId(id) == null` - i.e. no known open/close
 * pair - always falls through to a bounded walk-through with no permanent object mutation, only a
 * temporary collision bypass for the single step across the door's own tile) and
 * `DoorManagingPlugin` is the sourced precedent for the name filter (`door`/`gate`/`fence`/`wall`/
 * `exit`/`entrance`, excluding container-style furniture).
 *
 * 2026-09-18: 449 ids in this cache match name+type+exactly-one-option this conservatively (see
 * `data/cfg/doors/` audit notes) out of the 1483 the general mechanism leaves unbound; the
 * remainder (multi-option doors carrying `Pick-lock`/`Knock-at`/`Quick-pay`/etc., and non-wall
 * "door"-named objects) is left for dedicated, individually-sourced content rather than guessed at
 * here, same as [bind_cache_derived_doors] leaves its own unresolved remainder.
 *
 * Binding happens per definition, not per id list, so it stays correct as the cache is amended.
 */
/**
 * Furniture, not doors: drawers/wardrobes/cupboards/cabinets, sourced from 2009scape's
 * `DoorManagingPlugin` (the same file the walk-through fallback below is sourced from). That plugin
 * dispatches these by name to a self-transform rather than `DoorActionHandler`: `Open`/`go-through`
 * replaces the object with `id + 1` (auto-reverting there after a delay in the donor; simplified
 * here to a manual revert only, matching how every other binder in this file behaves, rather than
 * adding untested timed-revert bookkeeping for furniture no gameplay depends on), and `Close`/`Shut`
 * on the resulting `id + 1` replaces it with `id - 1`. Unlike doors this never moves the object's
 * tile or rotation - the id is the only thing that changes - so `World.openDoor`/`closeDoor` (built
 * for wall rotation math) do not apply; this does a plain in-place `DynamicObject` swap instead.
 *
 * Confirmed with `ObjectDefProbeTool` that this cache's furniture id layout is `closed(Open) ->
 * closed+1(Search, Close|Shut)`, e.g. 348 `Drawers` `Open` -> 349 `Drawers` `Search`+`Shut`; the verb
 * varies (`Close` or `Shut`) so both are accepted, matching the donor's own `case "close": case
 * "shut":` fallthrough.
 */
fun bind_container_doors() {
    val containerNames = listOf("drawer", "wardrobe", "cupboard", "cabinet")

    world.definitions.getAllKeys(ObjectDef::class.java).forEach { id ->
        val def = world.definitions.getNullable(ObjectDef::class.java, id) ?: return@forEach
        val name = def.name
        if (name.isBlank() || containerNames.none { name.lowercase().contains(it) }) {
            return@forEach
        }
        val nonBlank = def.options.filter { !it.isNullOrBlank() && it != "null" }
        if (nonBlank.size != 1 || !nonBlank.single().equals("Open", ignoreCase = true)) {
            return@forEach
        }
        val openedDef = world.definitions.getNullable(ObjectDef::class.java, id + 1) ?: return@forEach
        val closeSlot = openedDef.options.indexOfFirst { it.equals("Close", ignoreCase = true) || it.equals("Shut", ignoreCase = true) }
        if (closeSlot == -1) {
            return@forEach
        }
        val closeOption = openedDef.options[closeSlot]!!
        val openSlot = def.options.indexOfFirst { it.equals("Open", ignoreCase = true) }

        if ((openSlot + 1) !in world.plugins.boundObjectOptions(id)) {
            on_obj_option(obj = id, option = "open") {
                val obj = player.getInteractingGameObj()
                world.remove(obj)
                world.spawn(DynamicObject(id = obj.id + 1, type = obj.type, rot = obj.rot, tile = obj.tile))
                player.playSound(Sfx.DOOR_OPEN)
            }
        }
        if ((closeSlot + 1) !in world.plugins.boundObjectOptions(id + 1)) {
            on_obj_option(obj = id + 1, option = closeOption) {
                val obj = player.getInteractingGameObj()
                world.remove(obj)
                world.spawn(DynamicObject(id = obj.id - 1, type = obj.type, rot = obj.rot, tile = obj.tile))
                player.playSound(Sfx.DOOR_CLOSE)
            }
        }
    }
}

/**
 * Trapdoors/manholes with only `Open` and no cache-derivable partner: 2009scape's
 * `DoorManagingPlugin` handles any `trapdoor`/`trap door`-named object with a blanket
 * `location.transform(0, 6400, 0)` teleport - the same "the dungeon copy of an overworld region
 * sits 6400 higher on the z axis" convention this cache's own dungeon regions already use (e.g. the
 * Taverley Dungeon pipe at z~9799 is 3399+6400; the agility-shortcuts batch earlier today used the
 * same regions). Spot-verified before writing this: obj 881 `Manhole` at (3237,3458,0) has a real
 * `Ladder` (`Climb-up`) waiting at exactly (3237,9858,0), its +6400 destination.
 *
 * Unlike the donor (which has a `RegionManager.isTeleportPermitted` guard this codebase has no
 * equivalent for), this calls `DefinitionSet.createRegion` directly before teleporting - the same
 * function `ChunkSet.get(createIfNeeded = true)` calls internally, except its boolean result (false
 * when the cache genuinely has no map data for that region) is actually read here, so a trapdoor
 * whose "dungeon copy" doesn't exist declines with a message instead of dropping the player into an
 * empty region.
 */
fun bind_trapdoor_teleport_doors() {
    world.definitions.getAllKeys(ObjectDef::class.java).forEach { id ->
        val def = world.definitions.getNullable(ObjectDef::class.java, id) ?: return@forEach
        val name = def.name
        if (name.isBlank()) {
            return@forEach
        }
        val lower = name.lowercase()
        if (!lower.contains("trapdoor") && !lower.contains("trap door") && !lower.contains("manhole")) {
            return@forEach
        }
        val nonBlank = def.options.filter { !it.isNullOrBlank() && it != "null" }
        if (nonBlank.size != 1 || !nonBlank.single().equals("Open", ignoreCase = true)) {
            return@forEach
        }
        val slot = def.options.indexOfFirst { it.equals("Open", ignoreCase = true) }
        if ((slot + 1) in world.plugins.boundObjectOptions(id)) {
            return@forEach
        }
        on_obj_option(obj = id, option = "open") {
            val obj = player.getInteractingGameObj()
            val destination = Tile(obj.tile.x, obj.tile.z + 6400, obj.tile.height)
            if (!world.definitions.createRegion(world, destination.regionId)) {
                player.message("This doesn't seem to go anywhere.")
                return@on_obj_option
            }
            player.playSound(Sfx.DOOR_OPEN)
            player.moveTo(destination)
        }
    }
}

fun bind_walkthrough_doors() {
    val keywords = listOf("door", "gate", "fence", "wall", "exit", "entrance")

    world.definitions.getAllKeys(ObjectDef::class.java).forEach { id ->
        if (id in WALKTHROUGH_EXCLUDED_IDS) {
            return@forEach
        }
        val def = world.definitions.getNullable(ObjectDef::class.java, id) ?: return@forEach
        val name = def.name
        if (name.isBlank()) {
            return@forEach
        }
        val lower = name.lowercase()
        if (WALKTHROUGH_EXCLUDED_NAME_FRAGMENTS.any { lower.contains(it) }) {
            return@forEach
        }
        if (keywords.none { lower.contains(it) }) {
            return@forEach
        }
        val nonBlank = def.options.filter { !it.isNullOrBlank() && it != "null" }
        if (nonBlank.size != 1 || !nonBlank.single().equals("Open", ignoreCase = true)) {
            return@forEach
        }
        val slot = def.options.indexOfFirst { it.equals("Open", ignoreCase = true) }
        if ((slot + 1) in world.plugins.boundObjectOptions(id)) {
            return@forEach
        }
        on_obj_option(obj = id, option = "open") {
            val obj = player.getInteractingGameObj()
            if (is_wall_object(obj)) {
                swing_single_id_door(player, obj)
                return@on_obj_option
            }
            player.queue {
                val destination = walkthrough_destination(player.tile, obj.tile, obj.rot)
                player.lock = LockState.FULL
                player.playSound(Sfx.DOOR_OPEN)
                val route = player.walkTo(this, destination, detectCollision = false)
                wait(1)
                player.lock = LockState.NONE
                if (!route.success || player.tile != destination) {
                    player.message("The door is blocked.")
                }
            }
        }
    }
}

/**
 * Where every door this fallback has swung open now stands, mapped back to where it stood shut: `(open tile, open
 * rot) -> (shut tile, shut rot)`. A door with only one id looks identical open and shut, so this is the only way to
 * tell which of the two states the thing in front of the player is in.
 */
val swungDoors = HashMap<Pair<Tile, Int>, Pair<Tile, Int>>()

/**
 * Opens - really opens - a wall door whose definition gives it no separate "opened" id.
 *
 * Owner 2026-09-21, on Varrock's door 45849: "does open but has no animation the door physically stays shut but i can
 * walk through". That was this fallback: it used to walk the player past the door and never touch the door itself, so
 * every door in the class looked shut forever. The class is large - [bind_walkthrough_doors] catches every loc named
 * door/gate/... that advertises only "Open" and that nothing else binds - and 45849 lands in it because
 * `data/cfg/doors/void-door-pairs.json` pairs it with opened id 55443, which in THIS cache is a nameless,
 * option-less loc, so `bind_void_sourced_doors` rightly refuses the pair and the door falls through to here.
 *
 * A door needs no second id to open. [World.openDoor] is the real RuneScape door swing - the loc leaves its tile,
 * turns ninety degrees and is re-spawned on the tile it swings into, which is what makes the doorway passable - and
 * its `opened` parameter defaults to `id + 1` only as a convenience. Passing the door's own id back swings the same
 * door, so it visibly opens, the collision opens with it, and the player walks through the gap themselves instead of
 * being teleported past a shut door. Clicking it again swings it shut, which is why [swungDoors] exists.
 */
fun swing_single_id_door(
    p: Player,
    obj: GameObject,
) {
    val open = obj.tile to obj.rot
    val shut = swungDoors.remove(open)
    if (shut != null) {
        if (is_stuck(world, obj)) {
            p.message("The door seems to be stuck.")
            p.playSound(Sfx.DOOR_CREAK)
            swungDoors[open] = shut
            return
        }
        world.remove(obj)
        val closed = DynamicObject(id = obj.id, type = obj.type, rot = shut.second, tile = shut.first)
        world.spawn(closed)
        copy_stick_vars(obj, closed)
        add_stick_var(world, closed)
        p.playSound(Sfx.DOOR_CLOSE)
        return
    }
    val newDoor = world.openDoor(obj, opened = obj.id, invertTransform = obj.type == ObjectType.DIAGONAL_WALL.value)
    swungDoors[newDoor.tile to newDoor.rot] = open
    copy_stick_vars(obj, newDoor)
    add_stick_var(world, newDoor)
    p.playSound(Sfx.DOOR_OPEN)
}

/**
 * The far side of a wall-type object's own tile, one step beyond it in the direction the player is
 * already approaching from - the same rotation-only math `barrows.plugin.kts`'s `walkThroughDoor`
 * uses, since it only needs the door's own tile and rotation, not a config-derived pair.
 */
fun walkthrough_destination(
    playerTile: Tile,
    doorTile: Tile,
    rotation: Int,
): Tile {
    val horizontal = rotation == 1 || rotation == 3
    return when {
        horizontal && playerTile.x - doorTile.x >= 0 -> doorTile.transform(-1, 0)
        horizontal -> doorTile.transform(1, 0)
        playerTile.z - doorTile.z >= 0 -> doorTile.transform(0, -1)
        else -> doorTile.transform(0, 1)
    }
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
