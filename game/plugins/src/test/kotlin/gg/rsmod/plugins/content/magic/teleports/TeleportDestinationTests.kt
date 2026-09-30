package gg.rsmod.plugins.content.magic.teleports

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.plugins.testutil.RealMapWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Owner 2026-09-30 (bug report "I teleport to trollheim i end up bugged in this exact position", foto gg.png): the Trollheim
 * Teleport put the player on a rock where he could not step in any direction. Every teleport lands through [Landing]; checked on
 * the real map collision of the cache (terrain + locs, the same [gg.rsmod.game.fs.DefinitionSet.createRegion] the server runs).
 */
class TeleportDestinationTests {
    @Test
    fun `the spellbook areas really contain tiles inside scenery - a random tile is not a landing`() {
        // The data the owner's Trollheim teleport hit: 2888,3675 is a rock (a pocket of one tile).
        assertEquals(1, Landing.pocket(world.collision, Tile(2888, 3675)).size)
        val blocked = TeleportSpell.values().sumOf { spell -> tiles(spell.endArea).count { Landing.pocket(world.collision, it).size == 1 } }
        assertTrue("expected the measured blocked tiles in the spell areas, found $blocked", blocked >= 20)
    }

    @Test
    fun `every spellbook teleport lands on open ground`() {
        val random = Random(1)
        val bad =
            TeleportSpell.values().flatMap { spell ->
                (1..30).map { Landing.pick(world.collision, spell.endArea, random = random) }.distinct()
                    .filter { Landing.pocket(world.collision, it).size < Landing.OPEN_GROUND }
                    .map { "${spell.name} landed on ${it.x},${it.z} (pocket ${Landing.pocket(world.collision, it).size})" }
            }
        assertTrue("teleports that land where the player cannot walk away:" + bad.joinToString("; "), bad.isEmpty())
    }

    @Test
    fun `a fixed destination inside scenery lands on the nearest open ground`() {
        val landed = Landing.resolve(world.collision, Tile(2888, 3675))
        assertNotEquals(Tile(2888, 3675), landed)
        assertEquals(Landing.OPEN_GROUND, Landing.pocket(world.collision, landed).size)
        assertTrue("landed $landed", landed.isWithinRadius(Tile(2888, 3675), Landing.SEARCH_RADIUS))
        // An open destination stays exactly where it is.
        assertEquals(Tile(2889, 3678), Landing.resolve(world.collision, Tile(2889, 3678)))
    }

    private fun tiles(area: Area) = (area.bottomLeftX..area.topRightX).flatMap { x -> (area.bottomLeftZ..area.topRightZ).map { z -> Tile(x, z, 0) } }

    private val world get() = RealMapWorld.world
}
