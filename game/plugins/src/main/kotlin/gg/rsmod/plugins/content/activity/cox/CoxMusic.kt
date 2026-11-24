package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.ext.playSong

/**
 * CoX music (OSRS Wiki Chambers_of_Xeric 'Music' + the track pages, 16-11): Upper Depths (#532, cache 492) 'upon entering the Chambers',
 * Lower Depths (#529, cache 487) 'upon descending to the middle floor', Fire in the Deep (#528, cache 493) 'upon descending to the lowest
 * floor', before the Great Olm. Normal mode has two room floors + the Olm: floor 0 = Upper Depths, floor 1 = Lower Depths, the Olm = Fire in
 * the Deep. The raid floors are copied from the same template squares on other planes, so region music (music_by_region.yaml) cannot tell
 * them apart: the raid plays them itself. Mount Quidamortem (4919) plays Ascent (#527, cache 494) through music_by_region.yaml.
 * Imported with OsrsMusicImportTool (song groups below, music indices 1024-1027 - above 1023 = no unlock varp, they still play).
 */
object CoxMusic {
    const val ASCENT = 1025
    const val UPPER_DEPTHS = 1026
    const val LOWER_DEPTHS = 1027
    const val FIRE_IN_THE_DEEP = 1028

    data class Track(val id: Int, val name: String)

    fun track(floor: Int, olm: Boolean = false): Track = when {
        olm -> Track(FIRE_IN_THE_DEEP, "Fire in the Deep")
        floor <= 0 -> Track(UPPER_DEPTHS, "Upper Depths")
        else -> Track(LOWER_DEPTHS, "Lower Depths")
    }

    /** Use this player's floor, not the party's furthest progress (a death returns to the previous floor). */
    fun at(raid: CoxRaid, tile: Tile): Track? {
        if (!raid.map.area.contains(tile)) return null
        val x = tile.x - raid.map.area.bottomLeftX
        val z = tile.z - raid.map.area.bottomLeftZ
        if (x in CoxMap.OLM_AT_CHUNK_X * 8 until (CoxMap.OLM_AT_CHUNK_X + CoxMap.OLM_WIDTH) * 8 &&
            z in CoxMap.OLM_AT_CHUNK_Z * 8 until (CoxMap.OLM_AT_CHUNK_Z + CoxMap.OLM_HEIGHT) * 8) return track(0, olm = true)
        val floor = CoxLayouts.FLOOR_PLANES.indexOf(tile.height)
        return if (z in 0 until CoxMap.OLM_AT_CHUNK_Z * 8 && floor in raid.layout.floors.indices) track(floor) else null
    }

    /** The generic region playlist has no entries for these composed floors; replay explicitly when a song ends or a raider returns. */
    fun resume(p: Player): Boolean {
        val raid = CoxRaids.raidAt(p.tile) ?: return false
        val song = at(raid, p.tile) ?: return false
        p.playSong(song.id, song.name)
        return true
    }

    fun floor(p: Player, floor: Int, olm: Boolean = false) {
        val song = track(floor, olm)
        p.playSong(song.id, song.name)
    }
}
