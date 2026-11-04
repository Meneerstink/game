package gg.rsmod.game.action

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N451 (owner 2026-10-30, Warriors' Guild: Animated armour "als ze doodgaan verschijnen ze nog 1 seconde levend"): a short death
 * animation is waited for the whole ticks it really plays, and the npc is hidden for its death delay - it never stands again.
 */
class NpcDeathTimingTests {
    @Test
    fun `the animated armour collapse is waited 3 whole ticks, not 4`() {
        val cache = CacheLibrary(File("../data/cache").path)
        try {
            val defs = DefinitionSet().also { it.load(cache, AnimDef::class.java) }
            val collapse = defs.get(AnimDef::class.java, 4167)
            assertEquals(95, collapse.frameLength, "4167: 12 frames, 95 client cycles = 1.9 s")
            assertEquals(4, collapse.cycleLength, "rounded up it was 2.4 s - the armour stood again for half a second")
            assertEquals(3, NpcDeathAction.deathAnimationTicks(collapse))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `a death sequence that holds its last pose is waited for its played frames only`() {
        val cache = CacheLibrary(System.getenv("RSPS_TEST_CACHE") ?: File("../data/cache").path)
        try {
            val defs = DefinitionSet().also { it.load(cache, AnimDef::class.java) }
            // OSRS CHAOSELEMENTAL_DEATH 3147 imported as 16234: 19 frames of 62 client cycles, then a 20000-cycle hold frame.
            val death = defs.get(AnimDef::class.java, 16234)
            assertEquals(62, death.playedFrameLength)
            assertEquals(2, NpcDeathAction.deathAnimationTicks(death))
            assertEquals(95, defs.get(AnimDef::class.java, 4167).playedFrameLength, "no hold frame: unchanged")
        } finally {
            cache.close()
        }
    }

    @Test
    fun `the npc is hidden for its death delay after a death animation`() {
        val source = File("src/main/kotlin/gg/rsmod/game/action/NpcDeathAction.kt").readText()
        val delay = source.substringAfter("if (deathDelay > 0) {").substringBefore("wait(deathDelay)")
        assertTrue("npc.invisible = true" in delay)
        assertTrue("else deathAnimationTicks(def)" in source)
    }
}
