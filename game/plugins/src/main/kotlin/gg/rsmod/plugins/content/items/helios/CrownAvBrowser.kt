package gg.rsmod.plugins.content.items.helios

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.model.World
import java.util.NavigableSet
import java.util.TreeSet

/**
 * RCV-010 D1 (owner: AV testers need search/browse/preview pickers instead of raw ids).
 *
 * Browsing only ever lands on ids that exist in THIS revision-667 cache: animations from the AnimDef table,
 * GFX/projectiles from the SpotAnimDef table, sounds from the synth sound index (4). Name search is not offered:
 * the 667 cache carries no names for animations, spot animations or sounds, and the local `Sfx` names come from a
 * different game's list, so a name picker would label ids it cannot vouch for (SOURCE_BLOCKED, see HANDOFF).
 */
object CrownAvBrowser {
    const val SYNTH_SOUND_INDEX = 4

    enum class AvSource { ANIMATION, SPOT_ANIM, SYNTH_SOUND }

    fun ids(world: World, source: AvSource): NavigableSet<Int> = ids(world.definitions, world.filestore, source)

    fun ids(definitions: DefinitionSet, filestore: CacheLibrary, source: AvSource): NavigableSet<Int> =
        when (source) {
            AvSource.ANIMATION -> TreeSet(definitions.getAllKeys(AnimDef::class.java))
            AvSource.SPOT_ANIM -> TreeSet(definitions.getAllKeys(SpotAnimDef::class.java))
            AvSource.SYNTH_SOUND -> TreeSet(filestore.index(SYNTH_SOUND_INDEX).archiveIds().toList())
        }

    /**
     * The existing id [delta] away from [current]: a positive delta lands on the first id at or after
     * `current + delta`, a negative one on the last id at or before `current + delta`, zero on the first id at or
     * after [current]. Null when the cache has no id in that direction.
     */
    fun step(ids: NavigableSet<Int>, current: Int, delta: Int): Int? =
        when {
            delta >= 0 -> ids.ceiling(current + delta)
            else -> ids.floor(current + delta)
        }
}
