package gg.rsmod.game.tools.importer

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSRS-IMPORT casket sub-batch "casket-misc": Ale of the gods is an unarmed weapon-slot cosmetic ("combatstyle = Unarmed", speed 4 - 667
 * WeaponType.NONE 0) and the Ring of coins / Ring of nature are ring-slot cosmetics; none carries a requirement (OSRS Wiki item pages).
 */
class OsrsCasketMiscYmlTests {
    private data class Worn(val slot: Int, val weaponType: Int, val speed: Int, val reqs: String?)

    @Test
    fun `casket cosmetics carry their wiki slot and no requirements`() {
        val path = Paths.get("../data/cfg/items.yml")
        assertTrue(Files.exists(path), "expected items.yml at $path")
        val wanted = setOf("Ale of the gods", "Ring of coins", "Ring of nature")
        val found = mutableMapOf<String, Worn>()
        var name = ""
        var slot = -2
        var weaponType = -2
        var speed = -2
        var reqs: String? = null
        fun flush() {
            if (name in wanted && slot != -2) found[name] = Worn(slot, weaponType, speed, reqs)
            name = ""; slot = -2; weaponType = -2; speed = -2; reqs = null
        }
        Files.readAllLines(path).forEach { raw ->
            val line = raw.trimEnd('\r')
            when {
                line.startsWith("- id: ") -> flush()
                line.startsWith("  name: ") -> name = line.removePrefix("  name: ").trim().removeSurrounding("\"")
                line.startsWith("    equip_slot: ") -> slot = line.removePrefix("    equip_slot: ").trim().toInt()
                line.startsWith("    weapon_type: ") -> weaponType = line.removePrefix("    weapon_type: ").trim().toInt()
                line.startsWith("    attack_speed: ") -> speed = line.removePrefix("    attack_speed: ").trim().toInt()
                line.startsWith("    skill_reqs: ") -> reqs = line.removePrefix("    skill_reqs: ").trim()
            }
        }
        flush()
        assertEquals(
            mapOf(
                "Ale of the gods" to Worn(slot = 3, weaponType = 0, speed = 4, reqs = null),
                "Ring of coins" to Worn(slot = 12, weaponType = -1, speed = 4, reqs = null),
                "Ring of nature" to Worn(slot = 12, weaponType = -1, speed = 4, reqs = null),
            ),
            found,
        )
    }
}
