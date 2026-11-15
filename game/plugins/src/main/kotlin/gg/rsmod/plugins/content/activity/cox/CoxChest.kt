package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.message.impl.LocAddChangeMessage
import gg.rsmod.game.message.impl.LocDelMessage
import gg.rsmod.game.message.impl.UpdateZonePartialFollowsMessage
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player

/**
 * The raid's end in the Olm's chamber (tsps chambers-of-xeric.md 'The Great Olm' + Rewards.ChambersOfXeric.js, from rsprox OSRS captures):
 * 'Its death shatters the exit crystal and turns the crystal by the steps into the ancient chest' - RAIDS_REWARD_CRYSTAL 30027 becomes
 * RAIDS_REWARD_CHEST 30028 6 ticks after the death ('the chest appears 6 ticks after it dies').
 * The chest's light is per raider (varbit 5456 raids_client_rewardindicator on the map's multiloc RAIDS_REWARD_LOOTBEAM 28848): 2 = purple
 * (RAIDS_REWARD_LOOTBEAM_SPECIAL 30030) for a unique's finder, 1 = white (_BASIC 30029) for common loot, 0 once the chest is empty.
 * The 667 client has no varbit multiloc for it (the import dropped 28848's transform), so each raider's client is sent its own beam loc
 * on 28848's tile, the way DecoPlacer previews a loc for one player.
 * ONZEKER: a raider who walks out of view and back gets the map's (invisible) 28848 again until the next chest action redraws the beam.
 */
object CoxChest {
    const val CRYSTAL = 30027
    const val CHEST = 30028
    const val BEAM_BASIC = 30029
    const val BEAM_SPECIAL = 30030
    const val BEAM_SPOT = 28848
    const val CHEST_DELAY = 6

    private class Spot(val tile: Tile, val type: Int, val rot: Int)

    private val beams = java.util.concurrent.ConcurrentHashMap<Long, Spot>()

    private fun olmObjects(raid: CoxRaid, osrs: Int): List<GameObject> {
        val id = CoxIds.loc(osrs) ?: return emptyList()
        val base = CoxMap.olmTile(raid.map, Tile(CoxMap.OLM_CHUNK_X * 8, CoxMap.OLM_CHUNK_Z * 8, 0))
        val out = ArrayList<GameObject>()
        for (cx in 0 until 5) for (cz in 0 until 7) {
            val chunk = raid.world.chunks.get(Tile(base.x + cx * 8, base.z + cz * 8, 0), createIfNeeded = false) ?: continue
            chunk.getEntities<GameObject>(EntityType.STATIC_OBJECT, EntityType.DYNAMIC_OBJECT).filterTo(out) { it.id == id && it.tile.height == 0 }
        }
        return out
    }

    /** The Olm is dead (or ::coxloot): rewards are rolled now, the crystal turns into the chest after [CHEST_DELAY] ticks. */
    fun complete(raid: CoxRaid) {
        if (raid.progress >= 5) return
        val raiders = raid.world.players.entries.filterNotNull().filter { raid.map.area.contains(it.tile) }
        CoxRewards.generate(raid, raiders) { p, osrs -> CoxLootItems.local(osrs)?.let { p.inventory.contains(it) || p.bank.contains(it) } ?: false }
        raid.progress = 5
        CoxPartyTab.updateAll(raid)
        raid.world.queue {
            wait(CHEST_DELAY)
            if (raid.closed) return@queue
            val chest = CoxIds.loc(CHEST) ?: return@queue
            olmObjects(raid, CRYSTAL).forEach { o ->
                raid.world.remove(o)
                raid.world.spawn(DynamicObject(chest, o.type, o.rot, o.tile))
            }
            olmObjects(raid, BEAM_SPOT).firstOrNull()?.let { beams[raid.id] = Spot(it.tile, it.type, it.rot) }
            raiders.filter { it.isOnline && raid.map.area.contains(it.tile) }.forEach { showBeam(it, raid) }
        }
    }

    /** The raider's own light: purple with a unique, white with commons, none once the chest is empty. */
    fun showBeam(player: Player, raid: CoxRaid) {
        val spot = beams[raid.id] ?: return
        if (player.lastKnownRegionBase == null) return
        val zone = zone(player, spot.tile)
        val settings = (spot.type shl 2) or (spot.rot and 3)
        val hash = (spot.tile.chunkOffsetX shl 4) or (spot.tile.chunkOffsetZ and 0x7)
        val beam = when {
            !CoxRewards.has(player) -> null
            CoxRewards.hadUnique(player) -> CoxIds.loc(BEAM_SPECIAL)
            else -> CoxIds.loc(BEAM_BASIC)
        }
        if (beam == null) player.write(zone, LocDelMessage(settings, hash))
        else { player.write(zone, LocAddChangeMessage(beam, settings, hash)) }
    }

    fun refresh(player: Player) {
        val raid = CoxRaids.raidAt(player.tile) ?: return
        showBeam(player, raid)
    }

    fun forget(raid: CoxRaid) { beams.remove(raid.id) }

    private fun zone(player: Player, tile: Tile): UpdateZonePartialFollowsMessage {
        val local = player.lastKnownRegionBase!!.toLocal(tile)
        return UpdateZonePartialFollowsMessage(local.x shr 3, local.z shr 3, tile.height)
    }
}
