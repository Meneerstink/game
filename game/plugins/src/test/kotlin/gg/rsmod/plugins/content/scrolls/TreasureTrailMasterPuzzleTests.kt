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
import gg.rsmod.plugins.content.inter.bank.Bank
import gg.rsmod.plugins.content.inter.bank.BankTabs
import gg.rsmod.plugins.content.mechanics.pvp.BankSecurity
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import gg.rsmod.plugins.api.ext.filterableMessage
import io.mockk.*
import kotlin.random.Random
import kotlin.test.*

class TreasureTrailMasterPuzzleTests {
    private val accounts = mutableListOf<Player>()
    @BeforeTest fun setup() {
        mockkObject(ClueScrollManager, ClueItems)
        every { ClueItems.local(19835) } returns 30500
        every { ClueItems.local(23184) } returns 30600
        TreasureTrailMasterPuzzles.sourceItems.forEachIndexed { index, id -> every { ClueItems.local(id) } returns 30501 + index }
    }
    @AfterTest fun cleanup() { accounts.forEach { TreasureTrailLightBoxes.remove(it); TreasureTrailPuzzleBoxes.remove(it) }; unmockkAll() }
    private fun account(key: String = "master:mandrith"): Pair<Player, TreasureTrailSourceStep> {
        val step = TreasureTrailSourceStep(TreasureTrailStepCatalogue.current.rows.single { it.key == key })
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { defs.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns world; every { p.attr } returns AttributeMap(); every { p.isOnline } returns true
        every { p.varcs } returns MutableList(4000) { 0 }
        every { p.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { p.bank } returns ItemContainer(defs, 28, ContainerStackType.STACK)
        every { p.gravestone } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { p.deathRecovery } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(defs, 14, ContainerStackType.NORMAL)
        every { ClueScrollManager.activeStep(p, any()) } returns null
        every { ClueScrollManager.activeStep(p, ClueScrollTier.MASTER) } returns step
        every { ClueScrollManager.progress(p, ClueScrollTier.MASTER) } returns 1
        p.inventory[0] = Item(30500).putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
        accounts += p
        return p to step
    }
    @Test fun `both NPC routes can issue every actual master picture and lightbox without advancing`() {
        for (key in listOf("master:mandrith", "master:radimus")) for (source in TreasureTrailMasterPuzzles.sourceItems) {
            val (p, step) = account(key)
            p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, source)
            val box = assertNotNull(TreasureTrailMasterPuzzles.issue(p, step, Random(source)))
            assertEquals(ClueItems.local(source), box.id)
            assertEquals(ClueScrollTier.MASTER, TreasureTrailPuzzleBoxes.tier(box.id))
            assertSame(box, TreasureTrailMasterPuzzles.issue(p, step))
            if (source == 20355) assertNotNull(TreasureTrailLightBoxes.restore(box)) else {
                val session = assertNotNull(TreasureTrailPuzzleBoxes.start(p, step, box))
                assertEquals(source, session.source.item)
                assertFalse(session.board.solved)
            }
            verify(exactly = 0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
        }
    }
    @Test fun `full inventory stored and duplicate pictures cannot bypass the return-to-NPC handover`() {
        val (p, step) = account()
        (1..27).forEach { p.inventory[it] = Item(995) }
        assertNull(TreasureTrailMasterPuzzles.issue(p, step))
        assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        p.inventory[1] = null
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, 20280)
        val box = assertNotNull(TreasureTrailMasterPuzzles.issue(p, step))
        box.putAttr(ItemAttribute.CLUE_TASK_COMPLETE, 1)
        p.bank[0] = Item(ClueItems.local(20281)!!)
        assertFalse(TreasureTrailMasterPuzzles.handIn(p, step, box))
        p.bank[0] = null
        every { ClueScrollManager.advance(p, ClueScrollTier.MASTER, any(), 1, listOf(box.id)) } returns true
        assertTrue(TreasureTrailMasterPuzzles.handIn(p, step, box))
    }
    @Test fun `box kind survives bank and discard and invalid kind retains the held clue`() {
        val (p, step) = account()
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, 20281)
        val box = assertNotNull(TreasureTrailMasterPuzzles.issue(p, step))
        p.inventory[1] = null; p.bank[0] = box
        assertNull(TreasureTrailMasterPuzzles.issue(p, step))
        p.bank[0] = null
        val replacement = assertNotNull(TreasureTrailMasterPuzzles.issue(p, step))
        assertNotSame(box, replacement); assertEquals(box.id, replacement.id)
        p.inventory[1] = null
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, -999)
        assertNull(TreasureTrailMasterPuzzles.issue(p, step))
        assertNotNull(p.inventory[0])
    }
    @Test fun `public bank deposit rejects gnome child and Mimic from every source container`() {
        val (p, _) = account()
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        val messages = mutableListOf<String>()
        every { p.filterableMessage(capture(messages)) } just Runs
        for (from in listOf(p.inventory, p.equipment)) for (source in listOf(20282, 23184)) {
            messages.clear()
            val item = Item(ClueItems.local(source)!!)
            from[2] = item
            assertFalse(Bank.deposit(p, from, 2, 1))
            assertSame(item, from[2]); assertEquals(0, p.bank.occupiedSlotCount)
            assertEquals(listOf("A magical force prevents you from banking this item!"), messages)
        }
        messages.clear()
        p.inventory[2] = Item(ClueItems.local(20282)!!)
        Bank.depositAll(p, p.inventory.also { it[0] = null }, "")
        assertNotNull(p.inventory[2]); assertEquals(0, p.bank.occupiedSlotCount)
        assertTrue("A magical force prevents you from banking this item!" in messages)
    }
    @Test fun `ordinary lightbox bank deposit preserves all network attributes`() {
        val (p, step) = account()
        val box = assertNotNull(TreasureTrailLightBoxes.issue(p, step, Random(14)))
        val before = assertNotNull(TreasureTrailLightBoxes.restore(box))
        mockkObject(LootKeys, BankSecurity, BankTabs)
        every { LootKeys.isKey(any()) } returns false
        every { LootKeys.value(any<World>(), any()) } returns 0L
        every { BankSecurity.blocksDeposit(any(), any(), any()) } returns false
        every { BankTabs.selectedTab(p) } returns 0
        assertTrue(Bank.deposit(p, p.inventory, 1, 1))
        val banked = p.bank.filterNotNull().single()
        assertNull(p.inventory[1])
        val after = assertNotNull(TreasureTrailLightBoxes.restore(banked))
        assertEquals(before.bits, after.bits); assertContentEquals(before.effects(), after.effects())
    }
}
