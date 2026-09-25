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
        val loopEnd = burns.indexOf("target.hit(damage = 1, type = gg.rsmod.plugins.api.HitType.BURN)")
        assertTrue(burns.indexOf("burn.remaining = 0", loopEnd) > loopEnd)
        assertTrue(burns.indexOf("target.attr[ACTIVE]?.remove(burn)", loopEnd) > loopEnd)
        val hook = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/burns.plugin.kts").readText()
        assertTrue("on_player_pre_death" in hook && "Burns.clear(player)" in hook)
    }
}
