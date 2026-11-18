package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import kotlin.test.Test
import kotlin.test.assertEquals

/** N631 18-11: the Keris family swings with OSRS keris_stab/slash/crush (RuneLite gameval SoundEffectID 2507/2506/2505). */
class KerisSoundsTests {
    @Test fun `keris swings use the keris sounds per attack type`() {
        assertEquals(Sfx.KERIS_STAB, OsrsWeaponSounds.melee(Items.KERIS, StyleType.STAB))
        assertEquals(Sfx.KERIS_SLASH, OsrsWeaponSounds.melee(Items.KERIS_P, StyleType.SLASH))
        assertEquals(Sfx.KERIS_CRUSH, OsrsWeaponSounds.melee(Items.KERIS_P_10584, StyleType.CRUSH))
        assertEquals(2505, Sfx.KERIS_CRUSH)
        assertEquals(2507, Sfx.KERIS_STAB)
    }
}
