package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
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

class TreasureTrailFaloTests {
    private val zero = object : Random() { override fun nextBits(bitCount: Int) = 0 }
    private val step get() = TreasureTrailSourceStep(TreasureTrailStepCatalogue.current.rows.single {
        it.action == TreasureTrailStepCatalogue.Action.FALO
    })

    @BeforeTest fun setup() {
        mockkObject(ClueScrollManager, ClueItems)
        every { ClueItems.local(19835) } returns 30001
    }
    @AfterTest fun cleanup() { unmockkObject(ClueScrollManager, ClueItems) }

    private fun player(): Player {
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also {
            if (it.id == 4587) it.noteTemplateId = 0
            if (it.id == 4588) it.noteTemplateId = 799
        } }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        val active = step
        return mockk<Player>(relaxed = true).also { p ->
            every { p.attr } returns AttributeMap()
            every { p.world } returns world
            every { p.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
            p.inventory[0] = Item(30001).putAttr(ItemAttribute.CLUE_STEP_KEY, active.persistenceId)
            every { ClueScrollManager.activeStep(p, ClueScrollTier.MASTER) } returns active
            every { ClueScrollManager.progress(p, ClueScrollTier.MASTER) } returns 2
        }
    }

    @Test fun `seven pinned lyrics keep canonical and optional variants without accepting missing assets`() {
        val tasks = TreasureTrailTaskCatalogue.current.falo
        assertEquals(7, tasks.size)
        assertEquals(7, tasks.map { it.id }.toSet().size)
        assertTrue(tasks.first().accepts(4587))
        assertTrue(tasks.first().accepts(23670))
        assertFalse(tasks.first().accepts(-1))
        assertFalse(tasks.first().accepts(28031)) // Unmapped source identity is never a local item ID.
        assertEquals(13, TreasureTrailTaskCatalogue.current.sherlock.size)
    }

    @Test fun `assigned lyric survives repeated dialogue bank copies and actual item serialization`() {
        val p = player()
        val first = assertNotNull(TreasureTrailFalo.offer(p, zero))
        assertTrue(first.first)
        assertEquals(first.task, TreasureTrailFalo.offer(p, Random(1234))!!.task)
        assertFalse(TreasureTrailFalo.offer(p, zero)!!.first)
        val serialized = Gson().toJson(p.inventory[0])
        p.inventory[0] = Gson().fromJson(serialized, Item::class.java)
        val restored = assertNotNull(TreasureTrailFalo.offer(p, Random(4321)))
        assertEquals(first.task.id, restored.task.id)
        assertFalse(first.contract.valid(p))
        assertTrue(restored.contract.valid(p))
        val second = player()
        assertTrue(TreasureTrailFalo.offer(second, zero)!!.first)
    }

    @Test fun `wrong noted or stale shown items cannot advance or consume a requested object`() {
        val p = player()
        val offer = assertNotNull(TreasureTrailFalo.offer(p, zero))
        for (item in listOf(Item(995), Item(4588), Item(28031))) {
            p.inventory[1] = item
            assertEquals(TreasureTrailFalo.Match.WRONG, TreasureTrailFalo.match(p, offer, item))
            assertFalse(TreasureTrailFalo.finish(p, offer, item))
            assertSame(item, p.inventory[1])
        }
        val sword = Item(4587); p.inventory[1] = sword
        assertEquals(TreasureTrailFalo.Match.CORRECT, TreasureTrailFalo.match(p, offer, sword))
        p.inventory[1] = Item(sword)
        assertEquals(TreasureTrailFalo.Match.STALE, TreasureTrailFalo.match(p, offer, sword))
        assertFalse(TreasureTrailFalo.finish(p, offer, sword))
        verify(exactly = 0) { ClueScrollManager.advance(any(), any(), any(), any(), any()) }
    }

    @Test fun `correct object is only shown and advances once through the exact clue transaction`() {
        val p = player()
        val offer = assertNotNull(TreasureTrailFalo.offer(p, zero))
        val sword = Item(4587).putAttr(ItemAttribute.CHARGES, 77); p.inventory[1] = sword
        every { ClueScrollManager.advance(p, ClueScrollTier.MASTER, offer.contract.scroll, 2, emptyList()) } answers {
            p.inventory[0] = null; true
        }
        assertTrue(TreasureTrailFalo.finish(p, offer, sword))
        assertFalse(TreasureTrailFalo.finish(p, offer, sword))
        assertSame(sword, p.inventory[1]); assertEquals(77, sword.getAttr(ItemAttribute.CHARGES))
        verify(exactly = 1) { ClueScrollManager.advance(p, ClueScrollTier.MASTER, offer.contract.scroll, 2, emptyList()) }
    }

    @Test fun `unknown saved task is preserved rather than rerolled or freely completed`() {
        val p = player()
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, 0x12345678)
        assertNull(TreasureTrailFalo.offer(p, zero))
        assertEquals(0x12345678, p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        assertEquals(1, p.inventory.filterNotNull().size)
    }

    @Test fun `full inventory can finish a real Falo action without consuming shown gear or preserving old task state`() {
        val p = player()
        val active = step
        ClueScrollManager.registerStep(active)
        p.inventory[0] = Item(30001)
        every { ClueScrollManager.progress(p, ClueScrollTier.MASTER) } answers { callOriginal() }
        ClueScrollManager.prepare(p, p.inventory[0]!!, ClueScrollTier.MASTER)
        // Other real lifecycle tests may have registered other master fixtures in this JVM.
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, active.persistenceId)
        every { ClueScrollManager.activeStep(p, ClueScrollTier.MASTER) } returns active
        val sword = Item(4587).putAttr(ItemAttribute.CHARGES, 77)
        p.inventory[1] = sword
        (2..27).forEach { p.inventory[it] = Item(995, 1) }
        val offer = assertNotNull(TreasureTrailFalo.offer(p, zero))
        assertTrue(TreasureTrailFalo.finish(p, offer, sword))
        assertEquals(1, ClueScrollManager.progress(p, ClueScrollTier.MASTER))
        assertSame(sword, p.inventory[1]); assertEquals(77, sword.getAttr(ItemAttribute.CHARGES))
        assertEquals(26, p.inventory.getItemCount(995))
        assertEquals(0, p.inventory.freeSlotCount)
        assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        assertFalse(TreasureTrailFalo.finish(p, offer, sword))
    }
}
