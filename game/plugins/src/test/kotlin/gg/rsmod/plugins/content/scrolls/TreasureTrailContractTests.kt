package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import kotlin.test.*

class TreasureTrailContractTests {
    private val tier = ClueScrollTier.EASY
    private val step = object : ClueStep {
        override val tier = ClueScrollTier.EASY
        override val hint = "contract fixture"
        override fun onAttempt(player: Player) = false
    }
    private fun player(): Player {
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        return mockk<Player>(relaxed = true).also {
            every { it.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
            every { ClueScrollManager.activeStep(it, tier) } returns step
            every { ClueScrollManager.progress(it, tier) } returns 0
            it.inventory[0] = Item(tier.scrollId).also { item -> item.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId) }
        }
    }
    @BeforeTest fun setup() { mockkObject(ClueScrollManager) }
    @AfterTest fun cleanup() { unmockkObject(ClueScrollManager) }

    @Test fun `a pending answer belongs to one account and one exact item even for identical steps`() {
        val first = player(); val second = player()
        val held = first.inventory[0]!!
        val ticket = assertNotNull(ClueStepContract.capture(first, step))
        assertTrue(ticket.valid(first))
        assertFalse(ticket.valid(second))
        first.inventory[0] = Item(held)
        assertFalse(ticket.valid(first)) // Same ID, attributes and step do not make it the same held item.
        first.inventory[0] = held
        assertTrue(ticket.valid(first))
        first.inventory[1] = held; first.inventory[0] = null
        assertTrue(ticket.valid(first)) // Ordinary inventory rearrangement is allowed.
        first.inventory[1] = null
        assertFalse(ticket.valid(first))
    }

    @Test fun `changed step progress or registry cannot complete a suspended answer`() {
        val p = player()
        val ticket = assertNotNull(ClueStepContract.capture(p, step))
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId + 1)
        assertFalse(ticket.valid(p))
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
        every { ClueScrollManager.progress(p, tier) } returns 1
        assertFalse(ticket.valid(p))
        every { ClueScrollManager.progress(p, tier) } returns 0
        every { ClueScrollManager.activeStep(p, tier) } returns null
        assertFalse(ticket.valid(p))
        assertFalse(ticket.advance(p))
        verify(exactly = 0) { ClueScrollManager.advance(any(), any(), any(), any(), any()) }
    }

    @Test fun `a successful delayed completion uses the atomic lifecycle and cannot be replayed`() {
        val p = player()
        val held = p.inventory[0]!!
        val ticket = assertNotNull(ClueStepContract.capture(p, step))
        every { ClueScrollManager.advance(p, tier, held, 0, listOf(100)) } answers {
            p.inventory[0] = null
            true
        }
        assertTrue(ticket.advance(p, listOf(100)))
        assertFalse(ticket.advance(p, listOf(100)))
        verify(exactly = 1) { ClueScrollManager.advance(p, tier, held, 0, listOf(100)) }
    }

    @Test fun `unprepared or different active clues cannot manufacture a pending contract`() {
        val p = player()
        p.inventory[0] = Item(tier.scrollId)
        assertNull(ClueStepContract.capture(p, step))
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId + 1)
        assertNull(ClueStepContract.capture(p, step))
        every { ClueScrollManager.activeStep(p, tier) } returns null
        assertNull(ClueStepContract.capture(p, step))
    }

    @Test fun `death invalidates a suspended clue answer before inventory recovery runs`() {
        val p = player()
        val ticket = assertNotNull(ClueStepContract.capture(p, step))
        every { p.isDead() } returns true
        assertFalse(ticket.valid(p))
        assertFalse(ticket.advance(p))
        verify(exactly = 0) { ClueScrollManager.advance(any(), any(), any(), any(), any()) }
    }
}
