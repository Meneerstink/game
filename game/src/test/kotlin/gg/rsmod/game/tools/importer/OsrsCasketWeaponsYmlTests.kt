package gg.rsmod.game.tools.importer

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT casket sub-batch "casket-weapons-tools" (23612-23641): items.yml carries the OSRS Wiki wield requirement (Attack / Ranged /
 * Magic only - the Woodcutting / Mining level to use a tool is enforced by AxeType / PickaxeType) and the 667 weapon type of the analogue
 * combat style.
 */
class OsrsCasketWeaponsYmlTests {
    private data class Expected(val weaponType: Int, val reqs: String?)

    private val expected =
        mapOf(
            "3rd Age bow" to Expected(16, "[{'skill': 4, 'level': 65}]"),
            "3rd Age longsword" to Expected(6, "[{'skill': 0, 'level': 65}]"),
            "3rd Age wand" to Expected(1, "[{'skill': 6, 'level': 65}]"),
            "3rd Age axe" to Expected(2, "[{'skill': 0, 'level': 65}]"),
            "3rd Age pickaxe" to Expected(4, "[{'skill': 0, 'level': 65}]"),
            "Gilded scimitar" to Expected(6, "[{'skill': 0, 'level': 40}]"),
            "Gilded 2h sword" to Expected(7, "[{'skill': 0, 'level': 40}]"),
            "Gilded spear" to Expected(14, "[{'skill': 0, 'level': 40}]"),
            "Gilded hasta" to Expected(14, "[{'skill': 0, 'level': 40}]"),
            "Gilded axe" to Expected(2, "[{'skill': 0, 'level': 40}]"),
            "Gilded pickaxe" to Expected(4, "[{'skill': 0, 'level': 40}]"),
            "Gilded spade" to Expected(10, null),
            "Katana" to Expected(6, "[{'skill': 0, 'level': 40}]"),
            "Dragon cane" to Expected(10, "[{'skill': 0, 'level': 60}]"),
            "Briefcase" to Expected(-1, null),
        )

    @Test
    fun `reward weapons carry the wiki wield requirement and analogue weapon type`() {
        val path = Paths.get("../data/cfg/items.yml")
        assertTrue(Files.exists(path), "expected items.yml at $path")
        val weaponTypes = mutableMapOf<String, Int>()
        val reqs = mutableMapOf<String, String>()
        var id = -1
        var name = ""
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trimEnd('\r')
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                id !in 23612..23641 -> Unit
                line.startsWith("  name: ") -> name = line.removePrefix("  name: ").trim().removeSurrounding("\"")
                line.startsWith("    weapon_type: ") -> weaponTypes[name] = line.removePrefix("    weapon_type: ").trim().toInt()
                line.startsWith("    skill_reqs: ") -> reqs[name] = line.removePrefix("    skill_reqs: ").trim()
            }
        }
        assertEquals(expected.mapValues { it.value.weaponType }, weaponTypes)
        assertEquals(expected.filterValues { it.reqs != null }.mapValues { it.value.reqs!! }, reqs)
    }
}
