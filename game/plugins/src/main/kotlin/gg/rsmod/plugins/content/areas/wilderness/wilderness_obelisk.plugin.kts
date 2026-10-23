package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.magic.teleportRefusal
import gg.rsmod.plugins.content.mechanics.travel.ScrollMenu

// A list of obelisk object IDs that can be activated.
val ObeliskIDs =
    listOf(
        Objs.OBELISK_14826,
        Objs.OBELISK_14827,
        Objs.OBELISK_14828,
        Objs.OBELISK_14829,
        Objs.OBELISK_14830,
        Objs.OBELISK_14831,
    )

// Set up object interaction listeners for each obelisk.
ObeliskIDs.forEach { obeliskID ->
    // Check if the obelisk has the 'activate' option.
    if (if_obj_has_option(obeliskID, option = "activate")) {
        // Define the behavior when a player activates an obelisk.
        on_obj_option(obeliskID, option = "activate") {
            val obj = player.getInteractingGameObj()
            activateObelisk(player, obj)
        }
    }
    // A22: OSRS "Teleport to Destination" / "Set Destination" (ObeliskOptionTool, loc options 2 and 3).
    if (if_obj_has_option(obeliskID, option = "teleport to destination")) {
        on_obj_option(obeliskID, option = "teleport to destination") {
            val obj = player.getInteractingGameObj()
            val destination = player.attr[OBELISK_DESTINATION]?.let { Obelisk.values().getOrNull(it) }
            if (destination == null) {
                player.message("You need to set a destination first.")
                return@on_obj_option
            }
            activateObelisk(player, obj, destination)
        }
    }
    if (if_obj_has_option(obeliskID, option = "set destination")) {
        on_obj_option(obeliskID, option = "set destination") {
            openObeliskDestinations(player)
        }
    }
}

/**
 * A22: the obelisk destination the player picked with "Set Destination" (index into [Obelisk]); kept with the account like OSRS.
 * OSRS asks for the hard Wilderness Diary first - this server has no diaries, so everyone may set one (ADAPTED).
 */
val OBELISK_DESTINATION = gg.rsmod.game.model.attr.AttributeKey<Int>("obelisk_destination")

/** OSRS "Select Obelisk destination" (OSRS Wiki "Wilderness Obelisk interface.png"): "1: Level 13 Wilderness" .. "6: Level 50 Wilderness". */
fun obeliskDestinationLines(): List<String> = ScrollMenu.keyed(Obelisk.values().map { "Level ${it.name.removePrefix("LEVEL_")} Wilderness" })

fun openObeliskDestinations(player: Player) {
    ScrollMenu.open(player, "Select Obelisk destination", obeliskDestinationLines()) { p, row ->
        val choice = Obelisk.values().getOrNull(row) ?: return@open
        ScrollMenu.close(p)
        p.attr[OBELISK_DESTINATION] = row
        p.message("Your obelisk destination is now the Level ${choice.name.removePrefix("LEVEL_")} Wilderness obelisk.")
    }
}

/**
 * N6: any of the six obelisks, the current one included - Darkan WildernessObelisk.java:59
 * `OBELISK_CENTER_TILES[Utils.random(OBELISK_CENTER_TILES.length)]`; OSRS wiki "Wilderness Obelisk": "Players may even be
 * teleported to the same location." (The 2009scape port excluded the current one.)
 */
fun randomObelisk(): Obelisk {
    val all = Obelisk.values()
    return all[world.random(all.indices)]
}

/** OSRS wiki "Wilderness Obelisk" arrival line (N6; the 2009scape port had "...in the wilderness."). */
val ARRIVAL_MESSAGE = "Ancient magic teleports you somewhere in the Wilderness!"

/** N28: obelisks between activation and firing - Darkan WildernessObelisk.java:34 IS_ACTIVE. One script instance per world. */
val activeObelisks = mutableSetOf<Obelisk>()

// Activates an obelisk and teleports the players on its centre 3x3 to a random obelisk (the same one included), or to [fixed]
// ("Teleport to Destination": OSRS - everyone on the platform follows the rules of the player who activated it).
fun activateObelisk(
    player: Player,
    obj: GameObject,
    fixed: Obelisk? = null,
) {
    // The rest of this function handles the teleportation logic.
    val obeliskTile = obj.tile
    val stationObelisk = Obelisk.forLocation(obeliskTile)
    if (stationObelisk == null) {
        // A house obelisk. The Superior Garden's obelisk is one of these same locs (PlayerHouse.OBELISK), so it
        // arrives here with no Wilderness station within 20 tiles and used to fall out silently. In OSRS the house
        // obelisk is a chooser for the Wilderness obelisk network, which is what the owner asked for on 2026-09-21
        // ("we are missing ... a wilderness obelisk"); a house is a safe zone, so the trip out is behind the same
        // 7-second transport interface as every other non-teleport transport, plus a confirmation.
        if (!gg.rsmod.plugins.content.areas.poh.PlayerHouse.isSafeTile(obeliskTile)) {
            return
        }
        houseObelisk(player, fixed)
        return
    }
    val world: World = player.world

    // Darkan WildernessObelisk.java:39 `player.soundEffect(204, true)`: sound 204 to the activator and as an area sound
    // (radius 10) for everyone around, played on every touch - also when the obelisk is already active.
    player.playSound(Sfx.WILDERNESS_TELEPORT)
    world.spawn(AreaSound(player.tile, Sfx.WILDERNESS_TELEPORT, radius = 10, volume = 1).also { it.excluded = player })
    // Darkan :40-43. Our pillars are 14825 (no Activate) for 9 ticks and the obelisk fires at tick 10; a touch in between
    // used to start a second run.
    if (!activeObelisks.add(stationObelisk)) {
        player.message("The obelisk is already active.")
        return
    }
    try {
        startObelisk(player, obj, stationObelisk, world, fixed)
    } catch (t: Throwable) {
        activeObelisks.remove(stationObelisk) // a failed start must not leave the obelisk "already active" for good
        throw t
    }
}

fun startObelisk(
    player: Player,
    obj: GameObject,
    stationObelisk: Obelisk,
    world: World,
    fixed: Obelisk? = null,
) {
    // Define the corners relative to the obelisk location for animation purposes.
    val corners = arrayOf(Tile(2, 2, 0), Tile(-2, 2, 0), Tile(-2, -2, 0), Tile(2, -2, 0))

    // Animate the obelisk by spawning temporary animated objects on the obelisk platform corners.
    corners.forEach { corner ->
        val newTile = stationObelisk.location.transform(corner.x, corner.z, corner.height)
        val newObj = DynamicObject(Objs.OBELISK_14825, obj.type, obj.rot, newTile)
        val oldObj = DynamicObject(obj.id, obj.type, obj.rot, newTile)
        world.spawnTemporaryObject(newObj, 9, oldObj)
    }

    // Handle the actual teleportation after a short delay.
    val activator = player.username
    world.queue {
        val newObelisk = fixed ?: randomObelisk()
        val destination = newObelisk.location
        val filteredPlayers = mutableListOf<Player>()
        val tile = stationObelisk.location

        try {
            wait(10)
        } finally {
            activeObelisks.remove(stationObelisk)
        }

        // Collect all players within a 1-tile radius of the obelisk.
        for (p in world.players.entries) {
            if (p != null && p.tile.isWithinRadius(tile, 1)) {
                filteredPlayers.add(p)
            }
        }

        // Nobody on the centre 3x3: OSRS moves nobody. Say so - LC12 (2026-10-16) judged the obelisk dead because the
        // activator was still next to the pillar he touched (outer ring of the 5x5) and nothing was traced (N28).
        if (filteredPlayers.isEmpty()) {
            val where = world.players.entries.firstOrNull { it?.username == activator }?.tile?.let { "${it.x},${it.z},${it.height}" } ?: "offline"
            Trace.problem(Trace.Cat.INTERACT) {
                "obelisk ${stationObelisk.name} fired with nobody on its centre 3x3 (${tile.x - 1}..${tile.x + 1},${tile.z - 1}..${tile.z + 1}): " +
                    "only players standing there are teleported; activator $activator stood at $where"
            }
            return@queue
        }

        // Teleport each nearby player.
        for (filteredPlayer in filteredPlayers) {
            // Calculate the offset based on player's current position and the obelisk's center tile
            val offsetX = filteredPlayer.tile.x - tile.x
            val offsetZ = filteredPlayer.tile.z - tile.z

            // Apply the offset to the destination
            val destinationWithOffset = destination.transform(offsetX, offsetZ, 0)

            // Activation is allowed while fighting or frozen; only the actual teleport is gated.
            // This preserves chase counterplay while making Tele Block and the Deadman timer
            // authoritative for every player caught on the platform.
            val refusal = filteredPlayer.teleportRefusal(TeleportType.WILDERNESS_OBELISK)
            if (refusal != null) {
                Trace.problem(Trace.Cat.INTERACT) {
                    "obelisk ${stationObelisk.name}: ${filteredPlayer.username} on the platform was not teleported: " +
                        refusal.ifEmpty { listOf("dead or locked (silent)") }.joinToString(" / ")
                }
            } else {
                Trace.log(Trace.Cat.INTERACT) {
                    "obelisk ${stationObelisk.name} -> ${newObelisk.name}: ${filteredPlayer.username} " +
                        "${filteredPlayer.tile.x},${filteredPlayer.tile.z} -> ${destinationWithOffset.x},${destinationWithOffset.z}"
                }
            }
            filteredPlayer.canTeleport(TeleportType.WILDERNESS_OBELISK) {
                filteredPlayer.teleport(destinationWithOffset, TeleportType.WILDERNESS_OBELISK)
                filteredPlayer.message(ARRIVAL_MESSAGE)
            }
        }

        // Creates 3x3 graphic effect on the obelisk platform to signify multiple player teleportation function.
        for (x in -1..1) {
            for (z in -1..1) {
                val tileGraphicLocation = stationObelisk.location.transform(x, z, 0)
                world.spawn(TileGraphic(tile = tileGraphicLocation, id = Gfx.OBELISK_TELEPORT_TARGET_EFFECT, height = 0))
            }
        }
    }
}

/**
 * Represents an obelisk type.
 */
enum class Obelisk(
    val location: Tile,
) {
    LEVEL_13(Tile(3156, 3620, 0)),
    LEVEL_19(Tile(3219, 3656, 0)),
    LEVEL_27(Tile(3035, 3732, 0)),
    LEVEL_35(Tile(3106, 3794, 0)),
    LEVEL_44(Tile(2980, 3866, 0)),
    LEVEL_50(Tile(3307, 3916, 0)),
    ;

    companion object {
        fun forLocation(location: Tile): Obelisk? {
            return values().firstOrNull { it.location.getDistance(location) <= 20 }
        }
    }
}

/**
 * The house obelisk (OSRS Wiki "Obelisk (Construction)"): like the Wilderness ones it sends you to a random Wilderness obelisk, or to
 * the destination you set ("Teleport to Destination"); unlike them it teleports at once and only you. OSRS asks first with a warning
 * window; here the same question as a choice (ADAPTED).
 */
fun houseObelisk(
    p: Player,
    fixed: Obelisk? = null,
) {
    p.queue {
        if (options("Teleport into the Wilderness.", "Stay here.", title = "Warning: this teleports you into the Wilderness.") != 1) {
            return@queue
        }
        val destination = fixed ?: randomObelisk()
        p.canTeleport(TeleportType.WILDERNESS_OBELISK) {
            p.teleport(destination.location, TeleportType.WILDERNESS_OBELISK)
            p.message(ARRIVAL_MESSAGE)
        }
    }
}
