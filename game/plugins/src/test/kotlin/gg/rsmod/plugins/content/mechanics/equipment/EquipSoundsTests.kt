package gg.rsmod.plugins.content.mechanics.equipment

import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Sfx
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N33 (C2 10-16): imported OSRS items played the cloth sound 2238 on equip - the Void material lists only know 667 ids. An
 * unlisted weapon now sounds like its combat type, unlisted armour takes its material from the armour name.
 */
class EquipSoundsTests {
    private fun weapon(id: Int, name: String, type: Int) = EquipSounds.soundFor(id, name, EquipmentType.WEAPON, type)

    @Test
    fun `imported OSRS weapons sound like their kind, not like cloth`() {
        assertEquals(2233, weapon(23891, "Colossal blade", 7), "OSRS Wiki: equip_blunt")
        assertEquals(2244, weapon(22623, "Toxic blowpipe", 18))
        assertEquals(2247, weapon(22929, "Kodai wand", 1))
        assertEquals(2248, weapon(22689, "Abyssal dagger", 5))
        assertEquals(2248, weapon(22685, "Ancient godsword", 7))
        assertEquals(2243, weapon(23770, "Tzhaar-ket-om (t)", 10))
    }

    @Test
    fun `imported OSRS armour takes its material from the name`() {
        assertEquals(2239, EquipSounds.soundFor(23001, "Dragon platebody (g)", EquipmentType.CHEST))
        assertEquals(2245, EquipSounds.soundFor(23002, "Dragon kiteshield (g)", EquipmentType.SHIELD))
        assertEquals(2237, EquipSounds.soundFor(23003, "Gilded boots", EquipmentType.BOOTS))
        assertEquals(2241, EquipSounds.soundFor(23004, "Black d'hide body (g)", EquipmentType.CHEST))
        assertEquals(Sfx.EQUIP_FUN, EquipSounds.soundFor(23005, "Ancestral robe top", EquipmentType.CHEST))
    }

    @Test
    fun `667 items keep the Void rules`() {
        assertEquals(2249, weapon(4151, "Abyssal whip", 11))
        assertEquals(2241, EquipSounds.soundFor(1129, "Leather body", EquipmentType.CHEST))
        assertEquals(Sfx.EQUIP_FUN, EquipSounds.soundFor(579, "Blue wizard robe", EquipmentType.CHEST))
        assertEquals(2242, EquipSounds.soundFor(1067, "Iron platelegs", EquipmentType.LEGS))
    }

    @Test
    fun `an untagged 667 sword also sounds like a sword instead of cloth`() {
        assertEquals(2248, weapon(1277, "Bronze sword", 6))
    }
}
