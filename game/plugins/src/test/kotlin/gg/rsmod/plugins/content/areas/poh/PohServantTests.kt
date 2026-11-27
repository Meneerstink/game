package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * CONSTRUCTION-99 C11 27-11 (DUPE, regel 6c-3): a butler errand that never comes back to the house (owner logged out or left)
 * settles exactly once - un-noted planks and sawmill planks go to the bank once, a fetch or a deposit adds nothing, and a second
 * settle (logout after leaving the house) never pays out again.
 */
class PohServantTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>()
    private val attr = AttributeMap()
    private val timers = TimerMap()
    private val bank = ItemContainer(definitions, BANK_KEY)
    private val player = mockk<Player>().also { p ->
        every { p.world } returns world
        every { p.attr } returns attr
        every { p.timers } returns timers
        every { p.bank } returns bank
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
    }

    private fun trip(kind: PohServant.Kind, item: Int, amount: Int) {
        attr[PohServant.TRIP_ATTR] = PohServant.Trip(kind, item, amount)
        timers[PohServant.TRIP_TIMER] = PohServant.TRIP_TICKS
    }

    @Test
    fun `un-noted planks of an errand cut short reach the bank exactly once`() {
        trip(PohServant.Kind.UNNOTE, Items.OAK_PLANK, 20)
        PohServant.settleAway(player)
        PohServant.settleAway(player)
        assertEquals(20, bank.getItemCount(Items.OAK_PLANK))
        assertNull(attr[PohServant.TRIP_ATTR])
        assertFalse(timers.has(PohServant.TRIP_TIMER))
    }

    @Test
    fun `sawmill planks of an errand cut short reach the bank exactly once`() {
        trip(PohServant.Kind.SAWMILL, Items.MAHOGANY_PLANK, 5)
        PohServant.settleAway(player)
        PohServant.settleAway(player)
        assertEquals(5, bank.getItemCount(Items.MAHOGANY_PLANK))
    }

    @Test
    fun `a fetch or a deposit cut short adds nothing to the bank`() {
        trip(PohServant.Kind.FETCH, Items.TEAK_PLANK, 26)
        PohServant.settleAway(player)
        trip(PohServant.Kind.DEPOSIT, Items.TEAK_PLANK, 26)
        PohServant.settleAway(player)
        assertEquals(0, bank.getItemCount(Items.TEAK_PLANK))
        assertNull(attr[PohServant.TRIP_ATTR])
    }
}
