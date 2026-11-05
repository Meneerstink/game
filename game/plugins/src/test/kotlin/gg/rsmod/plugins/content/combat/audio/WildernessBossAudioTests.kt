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
            // Every enabled cue carries its source (a Jagex config name) - never a bare guessed id.
            cues.values.filter { it.sound != null }.forEach { cue -> assertTrue(cue.source!!.contains("List of sound IDs")) }
        }
    }
    @Test fun `owner V1 - old Callisto and Vet'ion use the current OSRS cues, Maledictus and spiderlings stay withheld`() {
        val c = WildernessBossAudio.contracts
        assertEquals(586, c.getValue(15076).getValue("attack.shockwave").sound) // KBD icy breath (Wiki Callisto)
        assertEquals(10575, c.getValue(15076).getValue("death").sound)          // wbr_callisto_death_roar_01
        assertEquals(10597, c.getValue(15078).getValue("death").sound)          // wbr_vetion_death_disintergrate_01 (Reborn)
        assertNull(c.getValue(15077).getValue("death").sound)                   // the first form does not die
        assertEquals(10305, c.getValue(15086).getValue("attack.melee").sound)   // wbr_vention_hellhound_attack_bark_01
        assertTrue(c.getValue(15069).values.all { it.sound == null })          // Maledictus: no source yet
        assertTrue(c.getValue(15071).values.all { it.sound == null })
    }
    @Test fun `a sound ID cannot be enabled without provenance radius and timing`() {
        assertFailsWith<IllegalArgumentException> { WildernessBossAudio.Cue("attack", sound = 1) }
        assertFailsWith<IllegalArgumentException> { WildernessBossAudio.Cue("attack", source = "", sound = 1, radius = 15, delay = 0) }
    }
}
