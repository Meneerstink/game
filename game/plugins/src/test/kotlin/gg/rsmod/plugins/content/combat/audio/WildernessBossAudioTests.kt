package gg.rsmod.plugins.content.combat.audio

import kotlin.test.*

class WildernessBossAudioTests {
    @Test fun `every silent boss form and minion has an explicit attack impact defend death contract without guessed sound IDs`() {
        val ids = setOf(15076,15077,15078,15079,15080,15069,15071,15075,15072,15073,15086,15087)
        assertEquals(ids, WildernessBossAudio.contracts.keys)
        WildernessBossAudio.contracts.values.forEach { cues ->
            assertTrue("defend" in cues && "death" in cues)
            assertTrue(cues.keys.any { it.startsWith("attack.") })
            assertTrue(cues.keys.any { it.startsWith("impact.") })
            cues.values.forEach { cue -> assertNull(cue.sound, "V1: no cue assignment has an OSRS source yet") }
        }
    }
    @Test fun `a sound ID cannot be enabled without provenance radius and timing`() {
        assertFailsWith<IllegalArgumentException> { WildernessBossAudio.Cue("attack", sound = 1) }
        assertFailsWith<IllegalArgumentException> { WildernessBossAudio.Cue("attack", source = "", sound = 1, radius = 15, delay = 0) }
    }
}
