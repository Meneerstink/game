package gg.rsmod.plugins.content.mechanics.worldedit

import gg.rsmod.game.model.World
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

    fun send(player: Player) = ClientHud.send(player, payload(player.world))

    fun broadcast(world: World) {
        val payload = payload(world)
        world.players.forEach { ClientHud.send(it, payload) }
    }
}
