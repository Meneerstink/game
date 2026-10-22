package gg.rsmod.plugins.content.newplayer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * N180 (owner 2026-10-22: "Skilling dungeon: geen geluid bij het trainen van elke skill"): every basement station makes a sound -
 * its animation's own audible frame sound, or the station's sound played by the training loop.
 */
class AfkStationSoundProbeTests {
    @Test
    fun `every station is audible`() {
        val silent =
            AfkArea.STATIONS.filter { station ->
                val anim = DEFINITIONS.getNullable(AnimDef::class.java, station.animation)
                val animAudible = anim?.hasAudibleFrameSounds == true
                !animAudible && station.sound <= 0
            }.map { "${it.option}/${it.skill} anim=${it.animation}" }
        println("AFK_STATION_SOUNDS " + AfkArea.STATIONS.joinToString { "${it.option}:${it.animation}:anim=${DEFINITIONS.getNullable(AnimDef::class.java, it.animation)?.hasAudibleFrameSounds}:sfx=${it.sound}" })
        assertTrue(silent.isEmpty(), "silent stations: $silent")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
