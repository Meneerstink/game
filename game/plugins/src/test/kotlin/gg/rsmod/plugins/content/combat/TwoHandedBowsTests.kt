package gg.rsmod.plugins.content.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Combat run 2026-09-29 (equipment conformance "two-handed removes the shield"): every bow is two-handed (OSRS Wiki infobox
 * "Two-handed"), which EquipAction reads as equip_type 5. The 667 Dark bow recolours, the god bows and the Zaryte bow had
 * equip_type -1, so a dark bow or Zaryte bow was worn with a shield.
 */
class TwoHandedBowsTests {
    @Test
    fun `every bow in the item metadata is two-handed`() {
        val notTwoHanded = mutableListOf<String>()
        var id: String? = null
        var name: String? = null
        var equipType: String? = null
        var weaponType: String? = null
        fun flush() {
            if (id != null && weaponType == "16" && equipType != "5") notTwoHanded += "$id $name equip_type=$equipType"
        }
        for (line in File("../../data/cfg/items.yml").readLines()) {
            Regex("""^- id: (\d+)""").find(line)?.let {
                flush()
                id = it.groupValues[1]
                name = null
                equipType = null
                weaponType = null
            }
            Regex("""^\s+name: "(.*)"""").find(line)?.let { name = it.groupValues[1] }
            Regex("""^\s+equip_type: (-?\d+)""").find(line)?.let { equipType = it.groupValues[1] }
            Regex("""^\s+weapon_type: (\d+)""").find(line)?.let { weaponType = it.groupValues[1] }
        }
        flush()
        assertEquals(emptyList(), notTwoHanded)
    }
}
