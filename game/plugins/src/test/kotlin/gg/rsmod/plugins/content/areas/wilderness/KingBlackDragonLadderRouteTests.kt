package gg.rsmod.plugins.content.areas.wilderness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Protects both cache-proven KBD ladder pairs from regressing into a guessed or region-only
 * destination. The plugin scripts are registered by the boot-time loader, so this is source-level
 * coverage for the exact object IDs, options and destinations found in the local 667 cache.
 */
class KingBlackDragonLadderRouteTests {
    private val kbd = File("src/main/kotlin/gg/rsmod/plugins/content/areas/wilderness/king_black_dragon_lair.plugin.kts")
    private val sharedLadders = File("src/main/kotlin/gg/rsmod/plugins/content/areas/draynor/wizards_tower_basement.plugin.kts")

    @Test
    fun `surface KBD ladders use the cache proven dungeon destinations`() {
        assertTrue(kbd.exists(), "KBD ladder plugin source is missing")
        val source = kbd.readText()
        assertTrue(source.contains("Objs.LADDER_1765"))
        assertTrue(source.contains("player.handleLadder(x = 3069, z = 10257"))
        assertTrue(source.contains("Objs.LADDER_1767"))
        assertTrue(source.contains("player.handleLadder(x = 3017, z = 10249"))
    }

    @Test
    fun `KBD dungeon ladder distinguishes the two cache placements`() {
        assertTrue(sharedLadders.exists(), "Shared ladder plugin source is missing")
        val source = sharedLadders.readText()
        assertTrue(source.contains("3017 to 10249 -> player.handleLadder(x = 3069, z = 3857)"))
        assertTrue(source.contains("else -> player.handleLadder(x = 3017, z = 3850)"))
    }
}
