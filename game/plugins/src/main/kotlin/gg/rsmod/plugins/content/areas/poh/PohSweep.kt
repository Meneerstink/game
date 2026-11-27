package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.StaticObject

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C11 'elke optie build/rotate/remove'): an admin self-check (`condev sweep [style|all]`) that builds
 * every room type in every rotation, fills every hotspot with every tier of its pieces, rebuilds the house and checks what a player
 * would see: the piece stands on its spot with the room's rotation and inside the room's own 8x8 cell, the empty hotspot is gone
 * outside building mode, and after Remove (furniture cleared) the hotspot is back in building mode. The player's own rooms and
 * furniture are saved first and put back after. Results: the console and %TEMP%/poh_sweep_<style>.txt.
 */
object PohSweep {
    private fun corner(
        player: Player,
        room: PohRooms.Built,
    ): Tile? {
        val area = PlayerHouse.houseAreaOf(player) ?: return null
        val (ix, iz, h) = PohRooms.instanceCell(room.x, room.z, room.plane)
        return Tile(area.bottomLeftX + ix * 8, area.bottomLeftZ + iz * 8, h)
    }

    private fun placeFor(
        player: Player,
        type: PohRooms.RoomType,
        rot: Int,
    ): List<PohRooms.Built> =
        when (type.where) {
            PohRooms.Where.UPSTAIRS -> listOf(PohRooms.Built(PohRooms.RoomType.PARLOUR, 0, 0, PohRooms.GROUND_FLOOR, 0), PohRooms.Built(type, 0, 0, PohRooms.UPSTAIRS, rot))
            PohRooms.Where.DUNGEON, PohRooms.Where.OUBLIETTE -> listOf(PohRooms.Built(type, 0, 0, PohRooms.DUNGEON, rot))
            else -> listOf(PohRooms.Built(type, 0, 0, PohRooms.GROUND_FLOOR, rot))
        }

    fun run(
        player: Player,
        styles: List<PlayerHouse.Style>,
    ): List<String> {
        val world = player.world
        val savedRooms = player.attr[PohRooms.ROOMS_ATTR]?.toMutableList()
        val savedFurn = player.attr[PohRooms.FURNITURE_ATTR]?.toMutableList()
        val savedStyle = PlayerHouse.styleOf(player)
        val savedBuild = PlayerHouse.isBuildMode(player)
        val fails = ArrayList<String>()
        var checks = 0
        try {
            for (style in styles) {
                PlayerHouse.setStyle(player, style)
                for (type in PohRooms.RoomType.values()) {
                    for (rot in 0..3) {
                        val rooms = placeFor(player, type, rot)
                        val room = rooms.last()
                        // Empty room in building mode: every hotspot shows.
                        player.attr[PohRooms.ROOMS_ATTR] = rooms.map { it.encode() }.toMutableList()
                        player.attr[PohRooms.FURNITURE_ATTR] = mutableListOf()
                        player.attr[PlayerHouse.BUILD_MODE_ATTR] = true
                        if (!PlayerHouse.rebuild(player)) {
                            fails += "$style $type r$rot: rebuild failed"
                            continue
                        }
                        val c = corner(player, room) ?: continue
                        val spots = PohRooms.spots(world, style, room, c)
                        if (spots.isEmpty() && type != PohRooms.RoomType.DUNGEON_CORRIDOR) fails += "$style $type r$rot: no hotspots"
                        spots.forEach { s ->
                            checks++
                            if (staticAt(player, s.tile).none { it.id == s.loc }) fails += "$style $type r$rot: hotspot ${s.loc} (${s.hotspot.key}) missing at ${s.tile} in build mode"
                        }
                        val tiers = spots.maxOfOrNull { it.hotspot.decos.count { d -> d != null } } ?: 0
                        for (t in 0 until tiers) {
                            for (buildMode in listOf(true, false)) {
                                if (!buildMode && t != tiers - 1 && t != 0) continue
                                player.attr[PohRooms.FURNITURE_ATTR] = mutableListOf()
                                val expect = ArrayList<Triple<PohRooms.Spot, Int, String>>()
                                spots.forEach { s ->
                                    val decos = s.hotspot.decos.filterNotNull()
                                    if (decos.isEmpty()) return@forEach
                                    val d = decos[t.coerceAtMost(decos.size - 1)]
                                    val value = if (s.hotspot.type == "HABITAT") "${d.key}#${s.hotspot.ids.indexOf(s.loc)}" else d.key
                                    PohRooms.setFurniture(player, s.key(room), value)
                                    val id = PohRooms.placedObj(value, style, PohHeraldry.crestIndex(player)) ?: return@forEach
                                    if (id > 0) expect += Triple(s, id, d.key)
                                }
                                player.attr[PlayerHouse.BUILD_MODE_ATTR] = buildMode
                                if (!PlayerHouse.rebuild(player)) {
                                    fails += "$style $type r$rot t$t: rebuild failed"
                                    continue
                                }
                                val c2 = corner(player, room) ?: continue
                                val spots2 = PohRooms.spots(world, style, room, c2)
                                expect.forEach { (s0, id, key) ->
                                    val s = spots2.firstOrNull { it.loc == s0.loc && it.templateX == s0.templateX && it.templateZ == s0.templateZ } ?: return@forEach
                                    checks++
                                    val tile = s.pieceTile(world, id)
                                    val obj = PohRooms.objectsAt(world, tile).firstOrNull { it.id == id }
                                    when {
                                        obj == null && PohRooms.objectsAt(world, tile).any { o -> o.id != id && expect.any { it.second == o.id } && world.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, o.id).let { it.width * it.length } > 1 } -> Unit
                                        obj == null -> fails += "$style $type r$rot ${if (buildMode) "build" else "play"}: $key ($id) not at $tile"
                                        obj != null && obj.rot != s.houseRot -> fails += "$style $type r$rot: $key ($id) rot ${obj.rot} != ${s.houseRot}"
                                    }
                                    if (tile.x < c2.x || tile.z < c2.z || tile.x > c2.x + 7 || tile.z > c2.z + 7) fails += "$style $type r$rot: $key ($id) outside its room at $tile"
                                    if (!buildMode && staticAt(player, s.tile).any { it.id == s.loc }) fails += "$style $type r$rot: hotspot ${s.loc} still shows under $key"
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            if (savedRooms != null) player.attr[PohRooms.ROOMS_ATTR] = savedRooms else player.attr.remove(PohRooms.ROOMS_ATTR)
            if (savedFurn != null) player.attr[PohRooms.FURNITURE_ATTR] = savedFurn else player.attr.remove(PohRooms.FURNITURE_ATTR)
            PlayerHouse.setStyle(player, savedStyle)
            player.attr[PlayerHouse.BUILD_MODE_ATTR] = savedBuild
            PlayerHouse.rebuild(player)
        }
        val report = listOf("[sweep] styles=${styles.joinToString(",")} checks=$checks fails=${fails.size}") + fails
        runCatching {
            java.io.File(System.getProperty("java.io.tmpdir"), "poh_sweep_${styles.joinToString("_")}.txt").writeText(report.joinToString("\n") + "\n")
        }
        return report
    }

    private fun staticAt(
        player: Player,
        tile: Tile,
    ): List<StaticObject> =
        player.world.chunks.get(tile, createIfNeeded = true)!!.getEntities<StaticObject>(tile, EntityType.STATIC_OBJECT)
}
