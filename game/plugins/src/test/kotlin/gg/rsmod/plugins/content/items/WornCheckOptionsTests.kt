package gg.rsmod.plugins.content.items

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.RING_OF_RECOIL_CHARGE_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.kandarin.castlewars.CastleWarsHandler
import gg.rsmod.plugins.content.mechanics.combatresponse.RingOfRecoil
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * K12 N11 (census 2026-10-30): worn "Check" options that did nothing now report the item's real charges.
 */
class WornCheckOptionsTests {
    @Test
    fun `ring of recoil check counts down from 40 as it reflects`() {
        val player = mockk<Player>(relaxed = true)
        val attr = AttributeMap()
        every { player.attr } returns attr
        assertEquals("You can inflict 40 more points of damage before a ring will shatter.", RingOfRecoil.checkMessage(player))
        attr[RING_OF_RECOIL_CHARGE_ATTR] = 13
        assertEquals("You can inflict 27 more points of damage before a ring will shatter.", RingOfRecoil.checkMessage(player))
    }

    @Test
    fun `castle wars bracelet check reads its charges from the item`() {
        assertEquals("Your Castle wars bracelet has 3 charges left.", CastleWarsHandler.braceCheckMessage(Items.CASTLE_WARS_BRACE_3))
        assertEquals("Your Castle wars bracelet has 2 charges left.", CastleWarsHandler.braceCheckMessage(Items.CASTLE_WARS_BRACE_2))
        assertEquals("Your Castle wars bracelet has 1 charge left.", CastleWarsHandler.braceCheckMessage(Items.CASTLE_WARS_BRACE_1))
    }
}
