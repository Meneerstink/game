package gg.rsmod.plugins.content.mechanics.pvp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the shared Skully placement rule against bank-interior regressions. */
class SkullyRosterPlacementTests {
    @Test
    fun `exact owner tiles and chest neighbours stay in the customer reachable component`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/SkullyRoster.kt").readText()
        assertTrue("site.wanted in reachable" in source, "exact Skully tiles must be reachable from the bank customer side")
        assertTrue("it in reachable && !world.collision.isClipped(it)" in source, "the chest must share that reachable component")
        assertTrue("isAgainstWall(world, site.wanted)" in source, "exact tiles must remain pinned against a wall")
    }
}
