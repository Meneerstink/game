package gg.rsmod.plugins.content.areas.watson

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.*
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import java.io.File

/** Private instance lifecycle. Unsupported timing stays disabled by owner decision 15-Nov 00:2x. */
object MimicEncounter {
    val OUTSIDE = Tile(1646, 3569, 1)
    val SAVED_INSIDE = AttributeKey<Boolean>(persistenceKey = "mimic_inside")
    private val sessions = HashMap<Player, Session>()
    class Session(val player: Player, val map: InstancedMap, val casket: Item, val token: Int) {
        var boss: Npc? = null
        var stompTicks = 0
        var won = false
        var finished = false
        val minions = mutableListOf<Npc>()
        fun tile(x: Int, z: Int) = Tile(map.area.bottomLeftX + x - 2688, map.area.bottomLeftZ + z - 4288, 1)
    }

    data class Assets(val imported: Boolean, val keyhole: Int?, val candyInterval: Int?, val impactDelay: Int?, val stepChance: Double?)
    val assets by lazy {
        val obj = JsonParser().parse(File(RspsPaths.data("cfg/clue/mimic-encounter.json")).readText()).asJsonObject
        fun number(key: String) = obj[key]?.takeUnless { it.isJsonNull }
        Assets(obj["arenaImported"].asBoolean, number("keyholeLocalId")?.asInt,
            number("candyIntervalTicks")?.asInt, number("candyImpactDelayTicks")?.asInt, number("forwardStepChance")?.asDouble)
    }

    fun session(player: Player) = sessions[player]
    fun session(npc: Npc) = sessions.values.firstOrNull { it.boss === npc || it.minions.any { minion -> minion === npc } }
    fun inArena(player: Player) = sessions[player]?.map?.area?.contains(player.tile) == true
    fun contains(tile: Tile) = sessions.values.any { it.map.area.contains(tile) }

    fun enter(player: Player, casket: Item): Boolean {
        if (sessions.containsKey(player) || !player.inventory.any { it === casket } ||
            MimicChallenge.tier(casket) == null || MimicChallenge.defeated(casket) || MimicChallenge.remaining(casket) == 0) return false
        if (!assets.imported || assets.keyhole == null) {
            println("PROBLEM CLUE Mimic arena assets not applied/verified; attempt retained")
            player.message("This challenge is temporarily unavailable.")
            return false
        }
        val chunks = InstancedChunkSet.Builder()
        for (x in 0..7) for (z in 0..7) for (height in 0..3)
            chunks.set(x, z, height, 0, Tile(2688 + x * 8, 4288 + z * 8, height))
        val config = InstancedMapConfiguration.Builder().setOwner(player.uid).setExitTile(OUTSIDE)
            .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
            .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_DEATH).build()
        val map = player.world.instanceAllocator.allocate(player.world, chunks.build(), config) ?: return false
        val token = MimicChallenge.begin(casket) ?: run { player.world.instanceAllocator.release(player.world, map); return false }
        val session = Session(player, map, casket, token)
        sessions[player] = session
        player.attr[SAVED_INSIDE] = true
        player.inventory.dirty = true
        // Plane 1 is established by the actual keyhole/coins placements in cache 2686.
        // These clear interior staging tiles still require Claude's collision/client check.
        player.teleportTo(session.tile(2719, 4314))
        val boss = Npc(Npcs.THE_MIMIC, session.tile(2716, 4317), player.world)
        boss.respawns = false
        session.boss = boss
        player.world.spawn(boss)
        player.persistNow()
        return true
    }

    fun challenge(player: Player, npc: Npc) {
        val session = sessions[player] ?: return
        if (session.boss !== npc || session.finished || session.won || npc.id != Npcs.THE_MIMIC) return
        val fighting = Npc(Npcs.THE_MIMIC_14402, npc.tile, npc.world)
        fighting.respawns = false
        npc.world.remove(npc)
        session.boss = fighting
        npc.world.spawn(fighting)
        fighting.attack(player)
        player.attack(fighting)
    }

    /** Death callback and exit can coincide. A dead boss wins before any failure settlement. */
    fun victory(npc: Npc) {
        val session = session(npc) ?: return
        if (session.boss !== npc || session.won || session.finished) return
        if (!MimicChallenge.settle(session.casket, session.token, won = true)) return
        session.won = true
        session.player.inventory.dirty = true
        session.minions.forEach { if (it.isSpawned()) it.world.remove(it) }
        session.minions.clear()
        session.player.persistNow()
    }

    fun settleDeath(player: Player) {
        val session = sessions[player] ?: return
        if (session.boss?.isDead() == true) victory(session.boss!!)
        if (!session.won) MimicChallenge.settle(session.casket, session.token, won = false)
        player.inventory.dirty = true
    }

    fun leave(player: Player, moveOutside: Boolean = true) {
        val session = sessions[player] ?: return
        if (session.finished) return
        settleDeath(player)
        session.finished = true
        sessions.remove(player)
        player.attr.remove(SAVED_INSIDE)
        session.boss?.let { if (it.isSpawned()) it.world.remove(it) }
        session.minions.forEach { if (it.isSpawned()) it.world.remove(it) }
        if (moveOutside) player.teleportTo(OUTSIDE)
        player.world.instanceAllocator.release(player.world, session.map)
        player.persistNow()
    }

    fun cycle(@Suppress("UNUSED_PARAMETER") world: World) {
        for (session in sessions.values.toList()) {
            val player = session.player
            if (!player.isOnline || !session.map.area.contains(player.tile)) { leave(player, moveOutside = false); continue }
            val boss = session.boss ?: continue
            if (session.won || boss.id != Npcs.THE_MIMIC_14402 || boss.isDead() || player.isDead()) continue
            val under = player.tile.height == boss.tile.height && player.tile.x in boss.tile.x until boss.tile.x + boss.getSize() &&
                player.tile.z in boss.tile.z until boss.tile.z + boss.getSize()
            if (under) player.hit(MimicChallenge.stompDamage(++session.stompTicks)) else session.stompTicks = 0
            // TODO-BRON (owner Q1): candy cycle/projectile impact delay and random forward-step chance.
            // Never substitute donor constants. Claude supplies OSRS tick/video evidence in the handoff.
        }
    }

    fun recover(player: Player) {
        if (player.attr[SAVED_INSIDE] == true) {
            player.inventory.filterNotNull().forEach { MimicChallenge.recover(it) }
            player.attr.remove(SAVED_INSIDE)
            player.moveTo(OUTSIDE)
            player.inventory.dirty = true
        }
    }
}
