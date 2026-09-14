package gg.rsmod.game.tools.importer

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT step 4 casket sub-batch "casket-cosmetics" (tx-20260914-083647, 23422-23611): every wear requirement stated on the OSRS Wiki
 * item pages is present in `data/cfg/items.yml` (667 skill ids Defence 1, Ranged 4, Prayer 5), and no other imported cosmetic carries one.
 */
class OsrsCasketCosmeticsImportTests {
    private val expected =
        mapOf(
            "Gilded boots" to "[{'skill': 1, 'level': 40}]",
            "Gilded chainbody" to "[{'skill': 1, 'level': 40}]",
            "Gilded med helm" to "[{'skill': 1, 'level': 40}]",
            "Gilded sq shield" to "[{'skill': 1, 'level': 40}]",
            "Gilded coif" to "[{'skill': 4, 'level': 40}]",
            "Gilded d'hide chaps" to "[{'skill': 4, 'level': 40}]",
            "Gilded d'hide vambraces" to "[{'skill': 4, 'level': 40}]",
            "Gilded d'hide body" to "[{'skill': 1, 'level': 40}, {'skill': 4, 'level': 40}]",
            "Rangers' tunic" to "[{'skill': 4, 'level': 40}]",
            "Rangers' tights" to "[{'skill': 4, 'level': 40}]",
            "Ranger gloves" to "[{'skill': 4, 'level': 40}]",
            "3rd Age plateskirt" to "[{'skill': 1, 'level': 65}]",
            "3rd Age druidic robe bottoms" to "[{'skill': 5, 'level': 65}]",
            "3rd Age cloak" to "[{'skill': 5, 'level': 65}]",
            "Holy wraps" to "[{'skill': 5, 'level': 31}]",
            "Black d'hide body (g)" to "[{'skill': 1, 'level': 40}, {'skill': 4, 'level': 70}]",
            "Black d'hide body (t)" to "[{'skill': 1, 'level': 40}, {'skill': 4, 'level': 70}]",
            "Black d'hide chaps (g)" to "[{'skill': 4, 'level': 70}]",
            "Black d'hide chaps (t)" to "[{'skill': 4, 'level': 70}]",
        )

    @Test
    fun `casket cosmetics carry exactly the wiki wear requirements`() {
        val path = Paths.get("../data/cfg/items.yml")
        assertTrue(Files.exists(path), "expected items.yml at $path")
        val found = mutableMapOf<String, String>()
        var id = -1
        var name = ""
        var wearables = 0
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trimEnd('\r')
            when {
                line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                id in 23422..23611 && line.startsWith("  name: ") -> name = line.removePrefix("  name: ").trim().removeSurrounding("\"")
                id in 23422..23611 && line == "  equipment:" -> wearables++
                id in 23422..23611 && line.startsWith("    skill_reqs: ") -> found[name] = line.removePrefix("    skill_reqs: ").trim()
            }
        }
        // 95 imported cosmetics; the Ring of 3rd Age is not equipable (wiki infobox "equipable = No", no wear position upstream), while
        // the six blessings are ("equipable = Yes", slot ammo, inventory option "Equip").
        assertEquals(94, wearables, "one equipped definition per equipable imported cosmetic")
        assertEquals(expected, found)
    }
}
