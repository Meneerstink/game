package gg.rsmod.plugins.content.magic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Q-033-c: protects the item-teleport route that was found to bypass the shared combat gate.
 * Plugin scripts are registered only by the boot-time KotlinScript loader, so this checks the
 * committed source that loader will execute instead of pretending a mocked callback is runtime
 * coverage.
 */
class TeleportRouteCoverageTests {
    private val ringOfKinship = File("src/main/kotlin/gg/rsmod/plugins/content/items/jewellery/ring_of_kinship.plugin.kts")

    @Test
    fun `ring of kinship teleport checks the shared combat lockout before queuing`() {
        assertTrue(ringOfKinship.exists(), "Ring of Kinship plugin source is missing")
        val source = ringOfKinship.readText()
        assertTrue(source.contains("player.canTeleport(TeleportType.RING_OF_KINSHIP)"))
        assertTrue(source.contains("return@on_item_option"))
    }
}
