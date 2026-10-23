package gg.rsmod.plugins.content.mechanics.worldedit

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud

/**
 * World-map icons that move with their npc (owner 2026-10-09 202308/213724/214334: "fix alle minimap iconen ... zorg ervoor
 * dat de iconen meebewegen", "ik ga nog npcs verplaatsen via devmode, mogelijk etage naar andere etage").
 *
 * The minimap needs nothing from here: the client draws an npc type's map element (NPCType opcode 142) at the npc's live
 * position on its own level (Jagex `Minimap.draw`). The world map only draws its prebuilt data, so the client's
 * NpcWorldMapIcons gets the post of every npc with a map element in the Grand Exchange square from this payload -
 * `npcicons|id,x,z,level;...` - on login and after every world edit (a devmode move is an npc_remove + npc_spawn).
 */
object NpcMapIcons {
    /** Region 12598, the Grand Exchange and the Royal Hall (owner's scope: the mansion / GE npcs). */
    private const val MIN_X = 3136
    private const val MAX_X = 3199
    private const val MIN_Z = 3456
    private const val MAX_Z = 3519

    fun payload(world: World): String =
        "npcicons|" +
            world.npcs.entries
                .filterNotNull()
                .filter { it.def.mapElement != -1 && it.spawnTile.x in MIN_X..MAX_X && it.spawnTile.z in MIN_Z..MAX_Z }
                .sortedWith(compareBy({ it.id }, { it.spawnTile.x }, { it.spawnTile.z }, { it.spawnTile.height }))
                .joinToString(";") { "${it.id},${it.spawnTile.x},${it.spawnTile.z},${it.spawnTile.height}" }

    /**
     * N231 (owner 2026-10-23: the GE summoning obelisk needs its minimap / world-map icon, "meebewegen als ik hem verplaats"):
     * the same for spawned locs whose LocType carries a map element (opcode 107) - `locicons|element,x,z,level;...`. The
     * minimap draws a scene loc's element by itself; the world map, again, only knows its prebuilt data, which never holds a
     * loc spawned or moved by a world edit.
     */
    fun locPayload(world: World): String {
        val posts = ArrayList<GameObject>()
        for (x in MIN_X..MAX_X step 8) {
            for (z in MIN_Z..MAX_Z step 8) {
                world.chunks.get(Tile(x, z), createIfNeeded = false)?.getEntities<GameObject>(EntityType.DYNAMIC_OBJECT)?.let { posts.addAll(it) }
            }
        }
        return "locicons|" +
            posts
                .mapNotNull { obj -> world.definitions.getNullable(ObjectDef::class.java, obj.id)?.mapElement?.takeIf { it != -1 }?.let { it to obj.tile } }
                .sortedWith(compareBy({ it.first }, { it.second.x }, { it.second.z }, { it.second.height }))
                .joinToString(";") { (element, tile) -> "$element,${tile.x},${tile.z},${tile.height}" }
    }

    fun send(player: Player) {
        ClientHud.send(player, payload(player.world))
        ClientHud.send(player, locPayload(player.world))
    }

    fun broadcast(world: World) {
        val payload = payload(world)
        val locs = locPayload(world)
        world.players.forEach {
            ClientHud.send(it, payload)
            ClientHud.send(it, locs)
        }
    }
}
