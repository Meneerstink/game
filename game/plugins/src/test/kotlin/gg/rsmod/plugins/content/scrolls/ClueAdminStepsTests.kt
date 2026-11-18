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

class ClueAdminStepsTests {
    @BeforeTest fun setup() { mockkObject(ClueScrollManager,ClueOwnership,TreasureTrailGuardians,TreasureTrailEmotes) }
    @AfterTest fun cleanup()=unmockkAll()
    private val tier=ClueScrollTier.HARD
    private val step=object:ClueStep {
        override val tier=ClueScrollTier.HARD
        override val hint="ADMIN fixture: source puzzle"
        override val persistenceId=987654
    }
    private fun pair(): Pair<Player,Item> {
        val defs=mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()).also { it.stacks=false } }
        val player=mockk<Player>(relaxed=true)
        every { player.inventory } returns ItemContainer(defs,28,ContainerStackType.NORMAL)
        val scroll=Item(tier.scrollId).putAttr(ItemAttribute.CLUE_STEP_KEY,123)
        player.inventory[0]=scroll
        every { ClueScrollManager.registeredSteps(tier) } returns listOf(step)
        every { ClueScrollManager.prepare(player,scroll,tier) } just Runs
        every { ClueOwnership.carried(player,tier) } returns scroll
        every { ClueOwnership.stored(player) } returns listOf(scroll)
        every { TreasureTrailGuardians.session(player) } returns null
        every { TreasureTrailEmotes.remove(player) } just Runs
        return player to scroll
    }
    @Test fun `unavailable id never enters the installed pool or changes a real clue`() {
        val(p,scroll)=pair()
        assertFalse(ClueAdminSteps.assign(p,tier,111))
        assertEquals(123,scroll.getAttr(ItemAttribute.CLUE_STEP_KEY))
        verify(exactly=0) { ClueScrollManager.prepare(any(),any(),any()) }
    }
    @Test fun `selection keeps the exact clue and amount while discarding old guardian credit`() {
        val(p,scroll)=pair()
        scroll.putAttr(ItemAttribute.CLUE_GUARD_KEY,123).putAttr(ItemAttribute.CLUE_GUARD_PROGRESS,1)
        assertTrue(ClueAdminSteps.assign(p,tier,step.persistenceId))
        assertSame(scroll,p.inventory[0]);assertEquals(1,scroll.amount)
        assertEquals(step.persistenceId,scroll.getAttr(ItemAttribute.CLUE_STEP_KEY))
        assertNull(scroll.getAttr(ItemAttribute.CLUE_GUARD_KEY));assertNull(scroll.getAttr(ItemAttribute.CLUE_GUARD_PROGRESS))
        assertEquals(1,p.inventory.filterNotNull().size)
        verify(exactly=1) { ClueScrollManager.prepare(p,scroll,tier) }
    }
    @Test fun `pending challenge and duplicate carried clues cannot be overwritten`() {
        val(p,scroll)=pair()
        scroll.putAttr(ItemAttribute.CLUE_TASK_KEY,123)
        assertFalse(ClueAdminSteps.assign(p,tier,step.persistenceId))
        scroll.removeAttr(ItemAttribute.CLUE_TASK_KEY);p.inventory[1]=Item(tier.scrollId)
        assertFalse(ClueAdminSteps.assign(p,tier,step.persistenceId))
        assertEquals(123,scroll.getAttr(ItemAttribute.CLUE_STEP_KEY))
        verify(exactly=0) { ClueScrollManager.prepare(any(),any(),any()) }
    }
    @Test fun `78 alias and unique search preserve exclusions and ambiguity`() {
        assertEquals(ClueScrollTier.MASTER,ClueAdminSteps.tier("78"))
        assertNull(ClueAdminSteps.tier("beginner"))
        assertEquals(listOf(step),ClueAdminSteps.matching(listOf(step),step.persistenceId.toString()))
        assertEquals(2,ClueAdminSteps.matching(listOf(step,step),"puzzle").size)
        assertTrue(ClueAdminSteps.matching(listOf(step),"unavailable").isEmpty())
    }
}
