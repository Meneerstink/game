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
}
