package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.entity.Player
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

    fun floor(p: Player, floor: Int, olm: Boolean = false) {
        when {
            olm -> p.playSong(FIRE_IN_THE_DEEP, "Fire in the Deep")
            floor <= 0 -> p.playSong(UPPER_DEPTHS, "Upper Depths")
            else -> p.playSong(LOWER_DEPTHS, "Lower Depths")
        }
    }
}
