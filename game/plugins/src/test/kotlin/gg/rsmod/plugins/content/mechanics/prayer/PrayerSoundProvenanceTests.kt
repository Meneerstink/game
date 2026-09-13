package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.plugins.api.cfg.Sfx
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins late standard prayer activation sounds to Void's rev-667 prayer.sounds.toml. */
class PrayerSoundProvenanceTests {
    @Test
    fun `late standard prayers use the sourced cache sound groups`() {
        assertEquals(Sfx.RAPID_RENEWAL, Prayer.RAPID_RENEWAL.sound)
        assertEquals(Sfx.RIGOUR, Prayer.RIGOUR.sound)
        assertEquals(Sfx.AUGURY, Prayer.AUGURY.sound)
        assertEquals(4265, Prayer.PROTECT_FROM_SUMMONING.sound)
    }
}
