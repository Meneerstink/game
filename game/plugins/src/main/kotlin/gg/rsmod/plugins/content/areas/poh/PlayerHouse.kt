package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.StaticObject
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAttribute
import gg.rsmod.game.model.instance.InstancedMapConfiguration
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs

/**
 * Owner request 2026-09-19: a player-owned house reached with a house teleport tab, holding a rejuvenation pool, a
 * combat dummy, a mounted amulet of glory and a prayer altar; follow-up "make the house a bit nicer, more options".
 * Owner choices: private instanced house; the 667 POH ornamental fountain as the pool (667 has no rejuvenation
 * pool); the 667 melee dummy NPC as the combat dummy.
 *
 * Two real revision-667 POH rooms (basic wood style, region 7503 plane 0, 2009scape `RoomProperties`), verified by
 * decoding the rev-667 loc file l29_79, surrounded by the style's lawn chunk (7503 chunk 1,0):
 *  * chapel (chunk 2,5) at instance chunk (1,1): pool, altar, glory, dummy, exit portal, statues, icon, rugs;
 *  * portal chamber (chunk 1,4) at instance chunk (1,0), turned 180 degrees so its only doorway meets the chapel's
 *    south doorway: marble teleport portals on its three portal spots.
 * Every build-mode spot ("... space", "Door hotspot") is removed and replaced per [SPOT_REPLACEMENTS], with the
 * spot's own type and rotation, as Construction does. A door spot facing another door spot stays an open doorway;
 * every other door spot becomes wall. The Construction options ("Remove", "Lock") these POH locs carry in the cache
 * are stripped by `PohObjectOptionTool`.
 */
object PlayerHouse {
    /** Rimmington house portal exit (2009scape `HouseLocation.RIMMINGTON`: portal loc 15478, exit 2953,3224). */
    val EXIT_TILE = Tile(2953, 3224, 0)
    const val RIMMINGTON_PORTAL = Objs.PORTAL_15478

    private const val REGION_X = 1856
    private const val REGION_Z = 5056
    private val LAWN = Tile(REGION_X + 1 * 8, REGION_Z + 0 * 8, 0)
    private val CHAPEL = Tile(REGION_X + 2 * 8, REGION_Z + 5 * 8, 0)
    private val PORTAL_CHAMBER = Tile(REGION_X + 1 * 8, REGION_Z + 4 * 8, 0)

    /** Instance chunk of the chapel; the portal chamber sits directly south of it. */
    private const val CHAPEL_CHUNK_X = 1
    private const val CHAPEL_CHUNK_Z = 1

    const val WALL = 13098
    const val POOL = Objs.ORNAMENTAL_FOUNTAIN
    const val PORTAL = 13405
    const val GLORY = Objs.AMULET_OF_GLORY_13523
    const val ALTAR = Objs.ALTAR_13199
    const val DUMMY = Npcs.MELEE_DUMMY

    /** Marble teleport portals (2009scape Decoration MARBLE_*_PORTAL). */
    const val VARROCK_PORTAL = 13629
    const val FALADOR_PORTAL = 13631
    const val CAMELOT_PORTAL = 13632

    private const val DOOR_SPOT_1 = 15313
    private const val DOOR_SPOT_2 = 15314

    /**
     * Build-mode spot -> decoration (2009scape BuildHotspot -> Decoration): rug spots 15273/15274 -> opulent rug
     * end/corner, window spots 13730 -> basic-wood stained-glass window, statue spots 15275 -> large statue, icon
     * spot 15269 -> icon of Saradomin, portal spots 15406/15407/15408 -> marble Varrock/Falador/Camelot portals.
     */
    private val SPOT_REPLACEMENTS =
        mapOf(
            15273 to 13595, 15274 to 13594, 13730 to 13255, 15275 to 13282, 15269 to 13175,
            15406 to VARROCK_PORTAL, 15407 to FALADOR_PORTAL, 15408 to CAMELOT_PORTAL,
        )

    val DUMMY_HEAL_TIMER = TimerKey()
    const val DUMMY_HEAL_TICKS = 5

    /**
     * The dummy is spawned a few ticks after the owner arrives (as the Fight Cave spawns its waves), so the client
     * has finished building the instance before the npc is added to its view.
     */
    val DUMMY_SPAWN_TIMER = TimerKey()
    const val DUMMY_SPAWN_DELAY = 3
    val PENDING_DUMMY_ATTR = AttributeKey<Pair<InstancedMap, Tile>>()

    /** Allocates a fresh house for [player] and returns the arrival tile, or null when no space is free. */
    fun build(player: Player): Tile? {
        val world = player.world
        val chunks = InstancedChunkSet.Builder()
        for (x in 0 until 8) {
            for (z in 0 until 8) {
                when {
                    x == CHAPEL_CHUNK_X && z == CHAPEL_CHUNK_Z -> chunks.set(x, z, 0, 0, CHAPEL)
                    x == CHAPEL_CHUNK_X && z == CHAPEL_CHUNK_Z - 1 -> chunks.set(x, z, 0, 2, PORTAL_CHAMBER)
                    else -> chunks.set(x, z, 0, 0, LAWN)
                }
            }
        }
        val config =
            InstancedMapConfiguration.Builder()
                .setOwner(player.uid)
                .setExitTile(EXIT_TILE)
                .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
                .setBypassObjectChunkBounds(true)
                .build()
        val map = world.instanceAllocator.allocate(world, chunks.build(), config) ?: return null
        val baseX = map.area.bottomLeftX + CHAPEL_CHUNK_X * 8
        val baseZ = map.area.bottomLeftZ + CHAPEL_CHUNK_Z * 8
        fun local(x: Int, z: Int) = Tile(baseX + x, baseZ + z, 0)

        val spots =
            listOf(local(0, 0), local(0, -8)).flatMap { corner ->
                world.chunks.get(corner.chunkCoords, createIfNeeded = true)!!.getEntities<StaticObject>(EntityType.STATIC_OBJECT)
            }.filter { obj ->
                val name = world.definitions.get(ObjectDef::class.java, obj.id).name.lowercase()
                name.endsWith(" space") || name == "door hotspot"
            }
        val doorTiles = spots.filter { it.id == DOOR_SPOT_1 || it.id == DOOR_SPOT_2 }.map { it.tile }.toSet()
        spots.forEach { obj ->
            world.remove(obj)
            if (obj.id == DOOR_SPOT_1 || obj.id == DOOR_SPOT_2) {
                // Wall-type rotation: 0 west, 1 north, 2 east, 3 south.
                val across =
                    when (obj.rot) {
                        0 -> obj.tile.transform(-1, 0)
                        1 -> obj.tile.transform(0, 1)
                        2 -> obj.tile.transform(1, 0)
                        else -> obj.tile.transform(0, -1)
                    }
                if (across !in doorTiles) {
                    world.spawn(DynamicObject(WALL, 0, obj.rot, obj.tile))
                }
            } else {
                SPOT_REPLACEMENTS[obj.id]?.let { world.spawn(DynamicObject(it, obj.type, obj.rot, obj.tile)) }
            }
        }

        world.spawn(DynamicObject(ALTAR, 10, 0, local(3, 5)))
        world.spawn(DynamicObject(POOL, 10, 0, local(1, 2)))
        world.spawn(DynamicObject(PORTAL, 10, 0, local(5, 1)))
        world.spawn(DynamicObject(GLORY, 5, 0, local(0, 4)))

        player.attr[PENDING_DUMMY_ATTR] = map to local(5, 4)
        player.timers[DUMMY_SPAWN_TIMER] = DUMMY_SPAWN_DELAY
        return local(3, 3)
    }

    /** Spawns the house combat dummy once the owner is inside the house it was built for. */
    fun spawnDummy(player: Player) {
        val (map, tile) = player.attr[PENDING_DUMMY_ATTR] ?: return
        player.attr.remove(PENDING_DUMMY_ATTR)
        if (!map.area.contains(player.tile) || player.world.instanceAllocator.getMap(tile) !== map) {
            return
        }
        val world = player.world
        val dummy = Npc(DUMMY, tile, world)
        dummy.walkRadius = 0
        world.spawn(dummy)
        // A combat dummy never fights back (the generic retaliation needs lock.canAttack()) and never dies.
        dummy.lock = LockState.FULL
        dummy.timers[DUMMY_HEAL_TIMER] = DUMMY_HEAL_TICKS
    }
}
