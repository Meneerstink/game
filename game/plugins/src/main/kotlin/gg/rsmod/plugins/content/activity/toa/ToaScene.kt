package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player

/** Instance-owned scenery. All coordinates below are OSRS sources, translated only by ToaMap.instance. */
class ToaScene(private val raid: ToaRaid) {
    private val placed = hashMapOf<Tile, Int>()
    private var spirit: Npc? = null
    private var osmumten: Npc? = null
    private val pathOsmumten = hashMapOf<ToaPath, Npc>()

    init {
        ToaMultilocs.placeRaid(raid)
        vault(purple = false)
    }

    /**
     * The burial chamber: sarcophagus 46220, barrier 46221 and tile glow 46222 (cache2686 square 14672) are multilocs on varbit 14373 ->
     * *_closed / *_closed_rare (loc.sym 44825/44826, 44938/44939, 44996/44997). The 667 import drops multiloc tables, so the variants
     * are placed here: white flames, or purple for the whole party when a unique was rolled (Wiki ToA Rewards: "the flames around
     * Osmumten's sarcophagus will glow purple rather than white"). ONZEKER: the opening animation has no sequence id in the sources.
     */
    fun vault(purple: Boolean) {
        for ((normal, rare, at) in VAULT) {
            val id = ToaIds.loc(if (purple) rare else normal) ?: continue
            at.forEach { (tile, shape, rotation) -> raid.world.spawn(DynamicObject(id, shape, rotation, ToaMap.instance(raid.map, tile))) }
        }
    }

    private fun place(id: Int?, source: Tile, rotation: Int) {
        if (id == null || placed[source] == id) return
        raid.world.spawn(DynamicObject(id, 10, rotation, ToaMap.instance(raid.map, source)))
        placed[source] = id
    }

    fun refresh() {
        for (door in DOORS) {
            val source = when {
                door.path in raid.progress.completedPaths -> door.sourceId + 2
                raid.progress.activePath == door.path -> door.sourceId
                else -> door.sourceId + 1
            }
            place(ToaIds.loc(source), door.tile, door.rotation)
        }
        place(if (raid.progress.wardensUnlocked) ToaIds.loc(46168) else ToaLocs.WARDENS, WARDENS_DOOR, 2)
        if (raid.progress.completedPaths.size == 2 || raid.progress.completedPaths.size == 4) {
            val spiritId = ToaNpcs.TOA_MIDRAIDLOOT_TRADER.localId
            if (spirit == null && spiritId != null) spirit = Npc(spiritId, ToaMap.instance(raid.map, SPIRIT), raid.world)
                .also { if (!raid.world.spawn(it)) return@also; it.respawns = false; it.walkRadius = 0 }
            place(ToaIds.loc(49583), DEPOSIT_POT, 0)
        } else {
            spirit?.let { raid.world.remove(it) }; spirit = null
            placed.remove(DEPOSIT_POT)?.let { raid.world.remove(DynamicObject(it, 10, 0, ToaMap.instance(raid.map, DEPOSIT_POT))) }
        }
        // cache2686 2/9/11830-11834 toa_osmumten_<boss>: varbit13836 20-24 shows Osmumten 11689 (Talk-to/Proceed) in the boss room
        // once its demi-boss is dead (LlemonDuck toa plugin QuickProceedSwaps: TOA_OSMUMTEN_VIS 'post-demi-boss').
        val bossOsmumten = ToaNpcs.TOA_OSMUMTEN_VIS.localId
        for (path in raid.progress.completedPaths) {
            if (bossOsmumten == null || path in pathOsmumten) continue
            val tile = BOSS_OSMUMTEN[path] ?: continue
            pathOsmumten[path] = Npc(bossOsmumten, ToaMap.instance(raid.map, tile), raid.world)
                .also { raid.world.spawn(it); it.respawns = false; it.walkRadius = 0 }
        }
        if (raid.progress.finished) {
            CHESTS.take(raid.roster.size).forEach { place(ToaIds.loc(44786), it.tile, it.rotation) }
            val id = ToaNpcs.TOA_OSMUMTEN_VAULT_VIS_2.localId
            if (osmumten == null && id != null) osmumten = Npc(id, ToaMap.instance(raid.map, OSMUMTEN), raid.world)
                .also { raid.world.spawn(it); it.respawns = false; it.walkRadius = 0 }
        }
    }

    fun openChest(player: Player, obj: GameObject): Boolean {
        if (!raid.progress.finished || !raid.contains(player)) return false
        val index = raid.roster.indexOf(player.uid)
        val chest = CHESTS.getOrNull(index) ?: return false
        if (ToaMap.source(raid.map, obj.tile) != chest.tile) return false
        ToaRewards.open(player, raid) ?: return false
        place(ToaIds.loc(44787), chest.tile, chest.rotation)
        ToaUi.showRewards(player)
        return true
    }

    fun close() {
        spirit?.let { raid.world.remove(it) }; spirit = null
        osmumten?.let { raid.world.remove(it) }; osmumten = null
        pathOsmumten.values.forEach { raid.world.remove(it) }; pathOsmumten.clear()
        placed.clear() // The allocator releases all owned scenery/collision with this instance.
    }

    companion object {
        private data class Door(val path: ToaPath, val sourceId: Int, val tile: Tile, val rotation: Int)
        private data class Chest(val tile: Tile, val rotation: Int)
        // 40-loc-placements.tsv, nexus; 50-loc-defs selected/unselected/closed IDs are consecutive triples.
        private val DOORS = listOf(Door(ToaPath.SCABARAS, 46155, Tile(3559, 5155), 0),
            Door(ToaPath.APMEKEN, 46158, Tile(3562, 5146), 1), Door(ToaPath.CRONDIS, 46161, Tile(3541, 5155), 3),
            Door(ToaPath.HET, 46164, Tile(3539, 5146), 3))
        private val WARDENS_DOOR = Tile(3548, 5134)
        /** cache2686 14672 placements (tile, shape, rotation) of the vault sarcophagus / barrier / tile glow, with normal + rare variant. */
        private val VAULT: List<Triple<Int, Int, List<Triple<Tile, Int, Int>>>> = listOf(
            Triple(44825, 44826, listOf(Triple(Tile(3679, 5140), 10, 2))),
            Triple(44938, 44939, listOf(
                Triple(Tile(3678, 5140), 0, 2), Triple(Tile(3678, 5141), 0, 2), Triple(Tile(3679, 5140), 0, 3), Triple(Tile(3679, 5141), 0, 1),
                Triple(Tile(3680, 5140), 0, 3), Triple(Tile(3680, 5141), 0, 1), Triple(Tile(3681, 5140), 0, 3), Triple(Tile(3681, 5141), 0, 1),
                Triple(Tile(3682, 5140), 0, 0), Triple(Tile(3682, 5141), 0, 0))),
            Triple(44996, 44997, (3679..3681).flatMap { x -> (5140..5141).map { z -> Triple(Tile(x, z), 22, 3) } }),
        )
        // Helpful_Spirit and Deposit_pot wiki map pins (source snapshots under N462-notities/toa/sources/wiki).
        private val SPIRIT = Tile(3547, 5157)
        private val DEPOSIT_POT = Tile(3546, 5154)
        // ONZEKER T30: adjacent to sarcophagus46220 at3679,5140 from placements; exact NPC spawn needs OSRS capture.
        private val OSMUMTEN = Tile(3681, 5143)
        // ONZEKER T26: the boss-room Osmumten is a server spawn (not in the map); tile = inside each arena, near the boss's crystal
        // landing (ToaRaid.ARENA_ENTRY). Zebak: on the east platform, the dry side of his pool (ToaZebakReachTests x3926+).
        val BOSS_OSMUMTEN = mapOf(ToaPath.HET to Tile(3687, 5407, 1), ToaPath.APMEKEN to Tile(3802, 5408),
            ToaPath.SCABARAS to Tile(3546, 5408), ToaPath.CRONDIS to Tile(3930, 5408))
        // loc.sym toa_vault_chest_loc0..7 and 40-loc-placements, each mapped to the corresponding roster slot.
        private val CHESTS = listOf(Chest(Tile(3686, 5145), 1), Chest(Tile(3673, 5145), 3),
            Chest(Tile(3686, 5142), 1), Chest(Tile(3673, 5142), 3), Chest(Tile(3686, 5148), 1),
            Chest(Tile(3673, 5148), 3), Chest(Tile(3686, 5139), 1), Chest(Tile(3673, 5139), 3))
    }
}
