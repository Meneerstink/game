package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Audit C-07: burn stacks do not survive a death and a stopped burn leaves no stack behind. */
class BurnsDeathTests {
    @Test
    fun `five burns, death, respawn - a new burn starts and nothing old is paid out`() {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        repeat(Burns.MAX_STACKS) { assertTrue(Burns.apply(player)) }
        assertFalse(Burns.apply(player))

        Burns.clear(player)

        assertEquals(0, Burns.consumeRemaining(player))
        assertTrue(Burns.apply(player), "a burn starts again after the death")
        assertEquals(Burns.DAMAGE, Burns.consumeRemaining(player))
    }

    @Test
    fun `a stopped burn is zeroed and removed, and the death hook clears the stacks`() {
        val burns = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/Burns.kt").readText()
        // Owner 2026-09-30: one cycle driver per target and ONE combined hit per cycle - never a queue or a hit per stack.
        assertEquals(1, Regex("""world\.queue""").findAll(burns).count(), "one burn driver")
        assertEquals(1, Regex("""target\.hit\(""").findAll(burns).count(), "one burn hit call")
        val stop = burns.indexOf("return@queue")
        assertTrue(burns.indexOf("burns.forEach { it.remaining = 0 }", stop - 200) > 0, "a stopped driver zeroes its burns")
        assertTrue(burns.indexOf("target.attr.remove(ACTIVE)", burns.indexOf("finally")) > 0, "no stack left behind")
        val hook = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/burns.plugin.kts").readText()
        assertTrue("on_player_pre_death" in hook && "Burns.clear(player)" in hook)
    }
}
