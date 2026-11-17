package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import kotlin.random.Random
import kotlin.test.*

class TreasureTrailLightBoxTests {
    private val accounts = mutableListOf<Player>()
    @BeforeTest fun setup() {
        mockkObject(ClueScrollManager, ClueItems)
        every { ClueItems.local(19835) } returns 30500
        every { ClueItems.local(20355) } returns 30501
    }
    @AfterTest fun cleanup() { accounts.forEach(TreasureTrailLightBoxes::remove); unmockkAll() }
    private fun account(): Pair<Player, TreasureTrailSourceStep> {
        val step = TreasureTrailSourceStep(TreasureTrailStepCatalogue.current.rows.single { it.key == "master:mandrith" })
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { defs.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.isOnline } returns true
        every { p.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { p.bank } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { p.gravestone } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { p.deathRecovery } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { ClueScrollManager.activeStep(p, any()) } returns null
        every { ClueScrollManager.activeStep(p, ClueScrollTier.MASTER) } returns step
        every { ClueScrollManager.progress(p, ClueScrollTier.MASTER) } returns 1
        p.inventory[0] = Item(30500).putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
        accounts += p
        return p to step
    }
    private fun solution(board: TreasureTrailLightPuzzle): Int = (0..255).first { combination ->
        val candidate = TreasureTrailLightPuzzle.restore(board.effects(), board.bits)
        (0..7).filter { combination and (1 shl it) != 0 }.forEach(candidate::toggle)
        candidate.solved
    }
    @Test fun `source eight-switch boards always have a solution among all 256 positions`() {
        repeat(100) { seed ->
            val board = TreasureTrailLightPuzzle.create(Random(seed))
            assertFalse(board.solved)
            val bits = board.bits
            assertFalse(board.toggle(-1)); assertFalse(board.toggle(8)); assertEquals(bits, board.bits)
            val answer = solution(board)
            (0..7).filter { answer and (1 shl it) != 0 }.forEach { assertTrue(board.toggle(it)) }
            assertTrue(board.solved)
            assertTrue((0..24).all(board::lit))
        }
    }
    @Test fun `input arrays copies and malformed impossible networks cannot corrupt another board`() {
        val effects = IntArray(8) { 1 shl it }
        val board = TreasureTrailLightPuzzle.restore(effects, TreasureTrailLightPuzzle.ALL xor 1)
        effects[0] = 0; board.effects()[0] = 0
        assertTrue(board.toggle(0)); assertTrue(board.solved)
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightPuzzle.restore(IntArray(7), 0) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightPuzzle.restore(IntArray(8), 0) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightPuzzle.restore(IntArray(8) { -1 }, 0) }
        assertFailsWith<IllegalArgumentException> { TreasureTrailLightPuzzle.restore(IntArray(8), 1 shl 25) }
    }
    @Test fun `wrong tier full inventory repeated issue and banked puzzle never spend or duplicate a box`() {
        val (p, step) = account()
        (1..27).forEach { p.inventory[it] = Item(995) }
        assertNull(TreasureTrailLightBoxes.issue(p, step))
        p.inventory[27] = null
        val box = assertNotNull(TreasureTrailLightBoxes.issue(p, step, Random(42)))
        assertSame(box, TreasureTrailLightBoxes.issue(p, step))
        p.inventory[27] = null; p.bank[0] = box
        assertNull(TreasureTrailLightBoxes.issue(p, step))
        val hard = TreasureTrailSourceStep(TreasureTrailStepCatalogue.current.rows.first { it.tier == ClueScrollTier.HARD })
        assertNull(TreasureTrailLightBoxes.issue(p, hard))
        verify(exactly = 0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
    }
    @Test fun `closing banking relog and serialization preserve the actual item network`() {
        val (p, step) = account()
        val box = assertNotNull(TreasureTrailLightBoxes.issue(p, step, Random(3)))
        val original = assertNotNull(TreasureTrailLightBoxes.start(p, step, box))
        assertTrue(TreasureTrailLightBoxes.toggle(p, original, 2))
        val bits = original.board.bits
        val effects = original.board.effects()
        TreasureTrailLightBoxes.remove(p)
        // The same integer attributes cross the existing item persistence/container boundary.
        val restored = Item(box.id).copyAttr(box)
        p.inventory[1] = restored
        val reopened = assertNotNull(TreasureTrailLightBoxes.start(p, step, restored))
        assertEquals(bits, reopened.board.bits); assertContentEquals(effects, reopened.board.effects())
        assertFalse(TreasureTrailLightBoxes.toggle(p, original, 0))
        p.inventory[1] = null; p.bank[0] = restored
        assertFalse(TreasureTrailLightBoxes.toggle(p, reopened, 0))
        p.bank[0] = null; p.inventory[1] = restored
        TreasureTrailLightBoxes.remove(p)
        assertEquals(bits, assertNotNull(TreasureTrailLightBoxes.start(p, step, restored)).board.bits)
    }
    @Test fun `other account banked or replaced scroll cannot toggle or hand in a solved puzzle`() {
        val (p, step) = account(); val (other, _) = account()
        val box = assertNotNull(TreasureTrailLightBoxes.issue(p, step))
        val session = assertNotNull(TreasureTrailLightBoxes.start(p, step, box))
        assertFalse(TreasureTrailLightBoxes.toggle(other, session, 0))
        assertFalse(TreasureTrailLightBoxes.handIn(other, step, box))
        p.inventory[0] = Item(p.inventory[0]!!)
        assertFalse(TreasureTrailLightBoxes.toggle(p, session, 0))
        assertFalse(TreasureTrailLightBoxes.handIn(p, step, box))
        verify(exactly = 0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
    }
    @Test fun `solving requires NPC hand in and a failed replacement transaction retains both items for retry`() {
        val (p, step) = account()
        val box = assertNotNull(TreasureTrailLightBoxes.issue(p, step, Random(98)))
        val session = assertNotNull(TreasureTrailLightBoxes.start(p, step, box))
        assertFalse(TreasureTrailLightBoxes.handIn(p, step, box))
        val answer = solution(session.board)
        (0..7).filter { answer and (1 shl it) != 0 }.forEach { assertTrue(TreasureTrailLightBoxes.toggle(p, session, it)) }
        assertTrue(session.board.solved)
        verify(exactly = 0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
        every { ClueScrollManager.advance(p, ClueScrollTier.MASTER, any(), 1, listOf(box.id)) } returns false
        assertFalse(TreasureTrailLightBoxes.handIn(p, step, box))
        assertSame(box, p.inventory[1]); assertNotNull(p.inventory[0])
        assertTrue(assertNotNull(TreasureTrailLightBoxes.restore(box)).solved)
        every { ClueScrollManager.advance(p, ClueScrollTier.MASTER, any(), 1, listOf(box.id)) } answers {
            p.inventory[1] = null; true
        }
        assertTrue(TreasureTrailLightBoxes.handIn(p, step, box))
        assertFalse(TreasureTrailLightBoxes.handIn(p, step, box))
    }
    @Test fun `missing or corrupt network never rerolls a stale box or trusts a forged complete bit`() {
        val (p, step) = account()
        val box = Item(30501).putAttr(ItemAttribute.CLUE_TASK_COMPLETE, 1)
        p.inventory[1] = box
        assertNull(TreasureTrailLightBoxes.start(p, step, box))
        assertFalse(TreasureTrailLightBoxes.handIn(p, step, box))
        assertSame(box, p.inventory[1])
        verify(exactly = 0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
    }
}
