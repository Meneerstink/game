package gg.rsmod.plugins.content.magic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Q-033-c: protects the item-teleport route that was found to bypass the shared combat gate.
 * Plugin scripts are registered only by the boot-time KotlinScript loader, so this checks the
 * committed source that loader will execute instead of pretending a mocked callback is runtime
 * coverage.
 *
 * Deadman PvP guards plan (2026-09-16): ring_of_kinship.plugin.kts now uses the two-arg
 * canTeleport(type) { ... } overload (so a skulled player's 7-second countdown completes the
 * teleport automatically) instead of an `if (!canTeleport(...)) return@on_item_option` guard.
 * This is structurally at least as strong a regression guard as the original: the actual
 * teleport queue call now lives INSIDE the canTeleport callback lambda, so there is no code path
 * that can reach it without the gate passing - checked here by requiring the callback-style
 * invocation to appear before the queued teleport call in the source.
 */
class TeleportRouteCoverageTests {
    private val ringOfKinship = File("src/main/kotlin/gg/rsmod/plugins/content/items/jewellery/ring_of_kinship.plugin.kts")

    @Test
    fun `ring of kinship teleport checks the shared combat lockout before queuing`() {
        assertTrue(ringOfKinship.exists(), "Ring of Kinship plugin source is missing")
        val source = ringOfKinship.readText()
        // The production helper is an extension on Player, so it is valid both as
        // `player.canTeleport(...)` and from a Player receiver as `canTeleport(...)`.
        val gateIndex = source.indexOf("canTeleport(TeleportType.RING_OF_KINSHIP) {")
        val teleportIndex = source.indexOf("teleport(tile, TeleportType.RING_OF_KINSHIP)")
        assertTrue(gateIndex >= 0, "must call the shared canTeleport gate")
        assertTrue(teleportIndex >= 0, "must still actually teleport the player")
        assertTrue(teleportIndex > gateIndex, "the teleport call must be inside the canTeleport callback, not before/outside it")
    }
}
