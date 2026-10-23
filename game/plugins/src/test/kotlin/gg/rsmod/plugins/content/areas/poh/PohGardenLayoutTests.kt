package gg.rsmod.plugins.content.areas.poh

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A25 (owner 2026-10-11: "the poh exit portal needs to be in the middle of the poh now the spiritual fairy tree is there"): the
 * garden's centrepiece spot (3,3) holds the exit portal with its purple minimap marker; the spiritual fairy tree stands in the
 * north-west corner (0,6) with the transport arrow; both still have their option routes.
 */
class PohGardenLayoutTests {
    private val source = File("src/main/kotlin/gg/rsmod/plugins/content/areas/poh/PlayerHouse.kt").readText()

    @Test
    fun `exit portal in the middle, fairy tree in the north-west corner`() {
        assertTrue("15361 to PORTAL," in source, "the centrepiece spot is the exit portal")
        assertTrue("15361 to SPIRITUAL_FAIRY_TREE" !in source)
        val garden = source.substringAfter("private fun furnishGarden(").substringBefore("fun spawnTheme(")
        assertTrue("room.spawn(SPIRITUAL_FAIRY_TREE, 10, 0, 0, 6)" in garden)
        assertTrue("room.spawn(PORTAL_MARKER, 22, 0, 3, 3)" in garden && "room.spawn(TRANSPORT_MARKER, 22, 0, 0, 6)" in garden)
        assertTrue("room.spawn(PORTAL, 10" !in garden, "only one exit portal")
        assertTrue(PlayerHouse.SPIRITUAL_FAIRY_TREE in PlayerHouse.PLACED_OBJECTS && PlayerHouse.PORTAL in PlayerHouse.PLACED_OBJECTS)
    }

    /** A23: each edging space gets the piece with its own shape (OSRS cache geometry, see [PlayerHouse.OTHERWORLDLY_EDGES]). */
    @Test
    fun `Otherworldly pieces match their spaces by shape`() {
        val expected = mapOf(62932 to 62943, 62933 to 62942, 62934 to 62941, 62935 to 62939, 62936 to 62939, 62937 to 62939, 62938 to 62940)
        kotlin.test.assertEquals(expected, PlayerHouse.OTHERWORLDLY_EDGES)
    }

    /** A23 (owner 2026-10-11: everyone gets the theme, existing and new accounts): built unless the owner removed it. */
    @Test
    fun `every house has the Otherworldly theme until it is removed`() {
        val player = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
        val attr = gg.rsmod.game.model.attr.AttributeMap()
        io.mockk.every { player.attr } returns attr
        assertTrue(PlayerHouse.hasGardenTheme(player), "a fresh account has the theme")
        attr[PlayerHouse.GARDEN_THEME_ATTR] = false
        assertTrue(!PlayerHouse.hasGardenTheme(player), "removed in building mode")
        attr[PlayerHouse.GARDEN_THEME_ATTR] = true
        assertTrue(PlayerHouse.hasGardenTheme(player), "built again")
        val build = File("src/main/kotlin/gg/rsmod/plugins/content/areas/poh/PohBuild.kt").readText()
        assertTrue("player.attr[PlayerHouse.GARDEN_THEME_ATTR] = false" in build.substringAfter("fun removeTheme("), "Remove remembers false")
    }
}
