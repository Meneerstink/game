package gg.rsmod.plugins.content.magic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Q-033-c: protected the Ring of kinship item teleport, which once bypassed the shared combat gate.
 * DGN 2026-10-21 (owner 2026-10-11: Dungeoneering removed completely): the ring, its teleport and its TeleportType are gone
 * (every held ring is purged at login, DungeoneeringRemovedTests) - the route may not come back without the gate.
 */
class TeleportRouteCoverageTests {
    private val ringOfKinship = File("src/main/kotlin/gg/rsmod/plugins/content/items/jewellery/ring_of_kinship.plugin.kts")

    @Test
    fun `the ring of kinship teleport route is removed with Dungeoneering`() {
        assertFalse(ringOfKinship.exists(), "Ring of Kinship teleport came back")
        assertTrue(TeleportType.values().none { it.name == "RING_OF_KINSHIP" || it.name == "DUNGEONEERING_GATESTONE" })
    }

    @Test
    fun `skillcape, max cape and Construction cape teleports stop above level 20 Wilderness (OSRS)`() {
        assertTrue(TeleportType.SKILLCAPE.wildLvlRestriction == 20)
        listOf(
            "src/main/kotlin/gg/rsmod/plugins/content/areas/poh/poh_build.plugin.kts",
            "src/main/kotlin/gg/rsmod/plugins/content/items/osrs/max_cape_options.plugin.kts",
            "src/main/kotlin/gg/rsmod/plugins/content/items/osrs/skillcape_teleports.plugin.kts",
        ).forEach { path ->
            val text = File(path).readText()
            assertTrue("TeleportType.SKILLCAPE" in text, path)
            assertFalse("TeleportType.JEWELRY" in text, "$path still uses the level-30 jewellery limit")
        }
    }
}
