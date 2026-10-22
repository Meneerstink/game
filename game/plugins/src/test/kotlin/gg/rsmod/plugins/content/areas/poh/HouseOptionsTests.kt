package gg.rsmod.plugins.content.areas.poh

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A29 (owner 10-10: "Leave house direct, geen timer") and A40 (owner 10-11: "House options alleen in het eigen house").
 */
class HouseOptionsTests {
    private val poh = File("src/main/kotlin/gg/rsmod/plugins/content/areas/poh")

    private fun handler(source: String, start: String, end: String?): String =
        source.substringAfter(start).let { if (end != null) it.substringBefore(end) else it }

    @Test
    fun `leaving the house - button and exit portal - is instant, entering keeps the Deadman gate`() {
        val options = File(poh, "house_options.plugin.kts").readText()
        val leave = handler(options, "on_button(interfaceId = 398, component = 29)", null)
        assertTrue("player.moveTo(PlayerHouse.locationOf(player).exit)" in leave)
        assertFalse("requestRoute" in leave, "Leave house has no countdown")

        val house = File(poh, "player_house.plugin.kts").readText()
        val exit = handler(house, "on_obj_option(obj = PlayerHouse.PORTAL, option = \"enter\")", "\n}")
        assertTrue("player.moveTo(PlayerHouse.locationOf(player).exit)" in exit)
        assertFalse("requestRoute" in exit, "the exit portal has no countdown")
        assertTrue("SevenSecondAction.Kind.PORTAL" in house, "going INTO the house keeps the gate")
    }

    @Test
    fun `the house buttons answer outside the house like Darkan`() {
        val options = File(poh, "house_options.plugin.kts").readText()
        val expel = handler(options, "on_button(interfaceId = 398, component = 27)", "on_button(")
        assertTrue("PlayerHouse.isSafeTile(player.tile)" in expel && "HouseOptions.EXPEL_OUTSIDE" in expel)
        val leave = handler(options, "on_button(interfaceId = 398, component = 29)", null)
        assertTrue("HouseOptions.LEAVE_OUTSIDE" in leave)
        assertEquals("You can only expel guests when you are in your own house.", HouseOptions.EXPEL_OUTSIDE)
        assertEquals("You're not in a house.", HouseOptions.LEAVE_OUTSIDE)
    }
}
