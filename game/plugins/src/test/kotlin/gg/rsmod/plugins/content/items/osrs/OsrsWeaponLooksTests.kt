package gg.rsmod.plugins.content.items.osrs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-24: "FIX alle OSRS GEPORTE WEAPONS die we al in game hebben met de correcte OSRS Attack animaties" / "alle osrs
 * geporte weapons moeten precies als osrs behaven". Root cause of the report: an imported weapon without its own row in
 * [OsrsWeaponLooks] fell back to the 667 weapon class, whose slash-sword and bladed-staff classes animate with RS HD sequences.
 * Guard over the whole set: every imported OSRS weapon (OSRS_IMPORT_MASTER.yml identities with a weapon slot in items.yml) has its
 * OSRS look, or is listed below with the reason it needs none.
 */
class OsrsWeaponLooksTests {
    /** Imported weapons without a row, and why. */
    private val noRowNeeded =
        mapOf(
            23690 to "Ale of the gods - cosmetic, attacks unarmed (punch/kick) like OSRS",
            23770 to "Tzhaar-ket-om (t) - no source names its OSRS attack sequence (SOURCE_GAP); keeps the ket-om class",
        )

    private fun importedWeapons(): Map<Int, String> {
        val master = File("../../../../OSRS_IMPORT_MASTER.yml").readText().replace("\r\n", "\n")
        val imported =
            Regex("""local_item_id: (\d+)\s*\n\s*name: (.*)\n\s*upstream_item_id: (\d+)""")
                .findAll(master).associate { it.groupValues[1].toInt() to it.groupValues[2].trim() }
        val items = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")
        val weapons =
            items.split(Regex("\n(?=- id: )")).mapNotNull { block ->
                val id = Regex("""^-? ?id: (\d+)""").find(block.trim())?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
                if ("equip_slot: 3\n" !in block) null else id
            }.toSet()
        return imported.filterKeys { it in weapons }
    }

    @Test
    fun `every imported OSRS weapon plays its OSRS attack sequences`() {
        val weapons = importedWeapons()
        assertTrue(weapons.size >= 130, "the imported weapon set was read (${weapons.size})")
        val missing = weapons.filterKeys { it !in OsrsWeaponLooks.COVERED && it !in noRowNeeded }
        assertEquals(emptyMap(), missing, "imported OSRS weapons without an OSRS look")
    }

    @Test
    fun `no imported weapon swings an RS HD sequence`() {
        val hd = setOf(15071, 15072, 15074, 12806, 11968, 11969)
        importedWeapons().keys.filter { it in OsrsWeaponLooks.COVERED }.forEach { id ->
            (0..3).forEach { style ->
                val seq = OsrsWeaponLooks.attackAnimation(id, style, againstNpc = false, stabStyle = false)
                assertTrue(seq == null || seq !in hd, "item $id style $style plays HD sequence $seq")
            }
        }
    }

    @Test
    fun `bladed staves and slash swords use the OSRS classic sequences`() {
        assertEquals(OsrsSeq.HUMAN_SCYTHE_SWEEP, OsrsWeaponLooks.attackAnimation(gg.rsmod.plugins.api.cfg.Items.TOXIC_STAFF_OF_THE_DEAD, 2, false, false))
        assertEquals(OsrsSeq.HUMAN_SWORD_STAB, OsrsWeaponLooks.attackAnimation(gg.rsmod.plugins.api.cfg.Items.VOIDWAKER, 2, false, true))
        assertEquals(OsrsSeq.HUMAN_SWORD_SLASH, OsrsWeaponLooks.attackAnimation(gg.rsmod.plugins.api.cfg.Items.VOIDWAKER, 0, false, false))
        assertEquals(OsrsSeq.II_HUMAN_DART_THROW_PVN, OsrsWeaponLooks.attackAnimation(gg.rsmod.plugins.api.cfg.Items.AMETHYST_DART, 0, true, false))
    }

    /** Every dart throws the OSRS seq copies (replay mode 1): the 667 seq 6600 did not restart on a 2-tick rapid throw. */
    @Test
    fun `every dart uses the restartable OSRS dart throw`() {
        for (dart in gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Darts.DARTS) {
            assertEquals(OsrsSeq.II_HUMAN_DART_THROW, OsrsWeaponLooks.attackAnimation(dart, 1, false, false))
            assertEquals(OsrsSeq.II_HUMAN_DART_THROW_PVN, OsrsWeaponLooks.attackAnimation(dart, 1, true, false))
        }
    }
}
