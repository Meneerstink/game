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
    private val entering = HashSet<Player>()
    class Session(val player: Player, val map: InstancedMap, val casket: Item, val token: Int) {
        var boss: Npc? = null
        var stompTicks = 0
        var won = false
        var finished = false
        val minions = mutableListOf<Npc>()
        val addedMultiRegions = mutableSetOf<Int>()
        fun tile(x: Int, z: Int) = arenaTile(map, x, z)
    }

    data class Assets(val imported: Boolean, val keyhole: Int?, val candyChance: Double?, val impactDelay: Int?, val stepChance: Double?,
                      val combatImplemented: Boolean = false, val arenaRegion: Int? = null) {
        val timingsReady: Boolean get() = candyChance != null && candyChance.isFinite() && candyChance in 0.0..1.0 &&
            impactDelay != null && impactDelay > 0 &&
            stepChance != null && stepChance.isFinite() && stepChance in 0.0..1.0 && candyChance+stepChance<=1.0
        val ready: Boolean get() = imported && keyhole != null && arenaRegion != null && arenaRegion in 0..65535 &&
            arenaRegion != 10819 && combatImplemented && timingsReady
    }
    val assets by lazy {
        val obj = JsonParser().parse(File(RspsPaths.data("cfg/clue/mimic-encounter.json")).readText()).asJsonObject
        fun number(key: String) = obj[key]?.takeUnless { it.isJsonNull }
        val scene=MimicArenaScenery.mapping
        Assets(obj["arenaImported"].asBoolean && scene!=null, scene?.locals?.get(34727),
            number("candyAttackChance")?.asDouble, number("candyImpactDelayTicks")?.asInt, number("forwardStepChance")?.asDouble,
            MimicCombat.ENCOUNTER_IMPLEMENTED, scene?.region)
    }

    fun session(player: Player) = sessions[player]
    fun session(npc: Npc) = sessions.values.firstOrNull { it.boss === npc || it.minions.any { minion -> minion === npc } }
    fun inArena(player: Player) = player.tile.height==1 && sessions[player]?.map?.area?.contains(player.tile) == true
    fun contains(tile: Tile) = tile.height==1 && sessions.values.any { it.map.area.contains(tile) }
    fun ready(world: World) = assets.ready && MimicAttacks.available(world)

    fun enter(player: Player, casket: Item, assets: Assets = MimicEncounter.assets,
              spawn: (Player, InstancedMap) -> Npc? = ::spawnIdle): Boolean {
        if (!entering.add(player)) return false
        return try { enterOnce(player, casket, assets, spawn) } finally { entering.remove(player) }
    }

    private fun enterOnce(player: Player, casket: Item, assets: Assets,
                          spawn: (Player, InstancedMap) -> Npc?): Boolean {
        if (player.isDead() || sessions.containsKey(player) || !player.inventory.any { it === casket } ||
            MimicChallenge.tier(casket) == null || MimicChallenge.defeated(casket) || MimicChallenge.remaining(casket) == 0 || MimicChallenge.active(casket)) return false
        if (!assets.ready || (assets === MimicEncounter.assets && !ready(player.world))) {
            println("PROBLEM CLUE Mimic arena/combat not implemented or verified; attempt retained")
            player.message("This challenge is temporarily unavailable.")
            return false
        }
        // Source10819 is occupied native667 content. Copy only the verified relocated room.
        val chunks = sourceChunks(assets.arenaRegion!!)
        val config = InstancedMapConfiguration.Builder().setOwner(player.uid).setExitTile(OUTSIDE)
            .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
            .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_DEATH).build()
        val map = player.world.instanceAllocator.allocate(player.world, chunks, config) ?: return false
        // A refused NPC spawn is not an attempt. Publish the actor before issuing a token.
        val boss = spawn(player, map) ?: run {
            player.world.instanceAllocator.release(player.world, map)
            return false
        }
        if (!player.inventory.any { it === casket } || player.isDead()) {
            player.world.remove(boss)
            player.world.instanceAllocator.release(player.world, map)
            return false
        }
        val token = MimicChallenge.begin(casket) ?: run {
            player.world.remove(boss)
            player.world.instanceAllocator.release(player.world, map)
            return false
        }
        val session = Session(player, map, casket, token)
        session.boss = boss
        sessions[player] = session
        val area=map.area
        for(x in (area.bottomLeftX shr 6)..(area.topRightX shr 6))for(z in (area.bottomLeftZ shr 6)..(area.topRightZ shr 6)) {
            val region=(x shl 8) or z
            if(region !in player.world.getMultiCombatRegions()) {
                player.world.plugins.addMultiCombatRegion(region)
                session.addedMultiRegions+=region
            }
        }
        player.attr[SAVED_INSIDE] = true
        player.inventory.dirty = true
        // Plane 1 is established by the actual keyhole/coins placements in cache 2686.
        // These clear interior staging tiles still require Claude's collision/client check.
        player.teleportTo(session.tile(2719, 4314))
        player.persistNow()
        return true
    }

    internal fun sourceChunks(region: Int): InstancedChunkSet {
        require(region in 0..65535 && region != 10819) { "Mimic source must be a verified relocated square, never native10819" }
        val baseX=(region ushr 8)*64; val baseZ=(region and 255)*64
        val chunks=InstancedChunkSet.Builder()
        for(x in 0..7) for(z in 0..7) for(plane in 0..3)
            chunks.set(x,z,plane,0,Tile(baseX+x*8,baseZ+z*8,plane))
        return chunks.build()
    }

    private fun arenaTile(map: InstancedMap, x: Int, z: Int) =
        Tile(map.area.bottomLeftX + x - 2688, map.area.bottomLeftZ + z - 4288, 1)

    private fun spawnIdle(player: Player, map: InstancedMap): Npc? {
        val npc = Npc(Npcs.THE_MIMIC, arenaTile(map, 2716, 4317), player.world)
        npc.respawns = false
        if (player.world.spawn(npc)) return npc
        println("PROBLEM CLUE Mimic passive spawn refused; no attempt spent")
        return null
    }

    private fun spawnFighting(idle: Npc): Npc? {
        val npc = Npc(Npcs.THE_MIMIC_14402, idle.tile, idle.world)
        npc.respawns = false
        if (idle.world.spawn(npc)) return npc
        println("PROBLEM CLUE Mimic fighting spawn refused; passive actor retained")
        return null
    }

    fun challenge(player: Player, npc: Npc, spawn: (Npc) -> Npc? = ::spawnFighting) {
        val session = sessions[player] ?: return
        if (session.boss !== npc || session.finished || session.won || npc.id != Npcs.THE_MIMIC) return
        val fighting = spawn(npc) ?: return
        npc.world.remove(npc)
        session.boss = fighting
        fighting.attack(player)
        player.attack(fighting)
    }

    /** Death callback and exit can coincide. A dead boss wins before any failure settlement. */
    fun victory(npc: Npc) {
        val session = session(npc) ?: return
        if (session.boss !== npc || session.won || session.finished) return
        if (!MimicChallenge.settle(session.casket, session.token, won = true)) return
        session.won = true
        MimicCandies.cancel(session)
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
        MimicCandies.cancel(session)
        sessions.remove(player)
        player.attr.remove(SAVED_INSIDE)
        session.boss?.let { if (it.isSpawned()) it.world.remove(it) }
        session.minions.forEach { if (it.isSpawned()) it.world.remove(it) }
        if (moveOutside) player.teleportTo(OUTSIDE)
        session.addedMultiRegions.forEach { player.world.plugins.removeMultiCombatRegion(it) }
        player.world.instanceAllocator.release(player.world, session.map)
        player.persistNow()
    }

    fun cycle(world: World) {
        MimicCandies.cycle(world)
        for (session in sessions.values.toList()) {
            val player = session.player
            if (player.world !== world) continue
            if (!player.isOnline || player.tile.height!=1 || !session.map.area.contains(player.tile)) { leave(player, moveOutside = false); continue }
            val boss = session.boss ?: continue
            if (session.won || boss.id != Npcs.THE_MIMIC_14402 || boss.isDead() || player.isDead()) continue
            val under = player.tile.height == boss.tile.height && player.tile.x in boss.tile.x until boss.tile.x + boss.getSize() &&
                player.tile.z in boss.tile.z until boss.tile.z + boss.getSize()
            if (under) player.hit(MimicChallenge.stompDamage(++session.stompTicks), source = boss) else session.stompTicks = 0
            // The custom combat loop owns candy/forward choices; this independent tick owns standing-under damage.
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
