package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner answer 2026-09-14: Magus ring and Venator ring are imported (batch magegear, OSRS 28313 / 28310) and can be worn without the boss
 * kill (OSRS gates them on one Duke Sucellus / Leviathan kill: "The ring slips off your finger. The power within it seems unfamiliar.").
 */
class OsrsAncientRingsTests {
    private val yml = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")

    private fun entry(id: Int): String = yml.substringAfter("\n- id: $id\n").substringBefore("\n- id: ")

    @Test
    fun `both rings carry the OSRS Wiki bonuses and no wear requirement`() {
        val magus = entry(Items.MAGUS_RING)
        assertTrue("name: \"Magus ring\"" in magus && "equip_slot: 12" in magus && "attack_magic: 15" in magus && "magic_damage: 2.0" in magus, magus)
        val venator = entry(Items.VENATOR_RING)
        assertTrue("name: \"Venator ring\"" in venator && "equip_slot: 12" in venator && "attack_ranged: 10" in venator && "ranged_strength: 2" in venator, venator)
        listOf(magus, venator).forEach { assertFalse("skill_req" in it && Regex("skill_reqs:\\s*\\n\\s+- ").containsMatchIn(it), "no wear requirement: $it") }
    }

    /** Owner 2026-09-26: the Magus ring is a Desert Treasure II unlock of the quest choices; the one gate is that quest (OsrsQuestRequirements), never a boss kill. */
    @Test
    fun `no plugin gates wearing either ring on a boss kill`() {
        val offenders =
            File("src/main/kotlin").walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name != "Items.kt" && it.name != "OsrsQuestRequirements.kt" }
                .filter { f -> f.readText().let { "MAGUS_RING" in it || "VENATOR_RING" in it || "slips off your finger" in it } }
                .map { it.name }.toList()
        assertEquals(emptyList(), offenders)
    }
}
