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
}
