package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.areas.watson.MimicRecovery
import gg.rsmod.plugins.content.areas.watson.WatsonClues
import io.mockk.*
import kotlin.random.Random
import kotlin.test.*

class TreasureTrailSherlockTests {
    private val zero = object : Random() { override fun nextBits(bitCount: Int) = 0 }
    private val levels = mutableMapOf<Int, Int>()
    companion object {
        private val steps = TreasureTrailStepCatalogue.current.rows.filter {
            it.action == TreasureTrailStepCatalogue.Action.SHERLOCK
        }.associate { it.tier to TreasureTrailSourceStep(it) }
        init { steps.values.forEach(ClueScrollManager::registerStep) }
    }

    @BeforeTest fun setup() {
        levels.clear()
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(19835) } returns 30001
        every { ClueItems.local(19836) } returns 30002
        every { ClueItems.local(12128) } returns 30003
        every { ClueItems.local(24366) } returns 30004
        every { ClueItems.local(24365) } returns 30005
    }
    @AfterTest fun cleanup() { unmockkObject(ClueItems) }

    private fun account(tier: ClueScrollTier): Player {
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks = false } }
        every { defs.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        return mockk<Player>(relaxed = true).also { p ->
            every { p.world } returns world
            every { p.attr } returns AttributeMap()
            every { p.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
            every { p.bank } returns ItemContainer(defs, 100, ContainerStackType.STACK)
            every { p.gravestone } returns ItemContainer(defs, 100, ContainerStackType.NORMAL)
            every { p.deathRecovery } returns ItemContainer(defs, 100, ContainerStackType.NORMAL)
            every { p.skills.getCurrentLevel(any()) } answers { levels[firstArg()] ?: 99 }
            every { p.skills.getMaxLevel(any()) } returns 1
            p.inventory[0] = Item(tier.scrollId)
            ClueScrollManager.prepare(p, p.inventory[0]!!, tier)
            p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, steps.getValue(tier).persistenceId)
        }
    }

    @Test fun `elite handover replaces one physical clue in a full inventory and repeated talks do not reroll`() {
        val p = account(ClueScrollTier.ELITE)
        (1..27).forEach { p.inventory[it] = Item(995) }
        val old = p.inventory[0]!!
        val pending = assertNotNull(ClueStepContract.capture(p, steps.getValue(ClueScrollTier.ELITE)))
        val task = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, zero))
        assertTrue(task.first)
        assertEquals("NATURE_RUNE", task.task.key)
        assertEquals(30003, p.inventory[0]!!.id)
        assertEquals(0, p.inventory.freeSlotCount)
        assertFalse(pending.valid(p))
        assertFalse(p.inventory.any { it === old })
        val repeated = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, Random(1234)))
        assertFalse(repeated.first); assertEquals(task.task.key, repeated.task.key)
        assertEquals(1, ClueContainers.count(p, ClueScrollTier.ELITE))
    }

    @Test fun `missing challenge import retains the original clue and all of its state`() {
        val p = account(ClueScrollTier.ELITE)
        val original = p.inventory[0]!!
        val attrs = original.attr.toMap()
        every { ClueItems.local(12128) } returns null
        assertNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, zero))
        assertSame(original, p.inventory[0]); assertEquals(attrs, original.attr)
        assertEquals(0, ClueScrollManager.progress(p, ClueScrollTier.ELITE))
    }

    @Test fun `possessing the finished item or performing a different action does not complete a task`() {
        val p = account(ClueScrollTier.MASTER)
        val assignment = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.MASTER, zero))
        assertEquals("RUNE_MED_HELM", assignment.task.key)
        p.inventory[1] = Item(1147)
        assertFalse(assignment.done)
        assertEquals(0, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.MITHRIL_ORE))
        assertFalse(assignment.done); assertFalse(TreasureTrailSherlock.finish(p, assignment))
        assertEquals(0, ClueScrollManager.progress(p, ClueScrollTier.MASTER))
    }

    @Test fun `banked dropped or different account challenges are not marked by a successful action`() {
        val first = account(ClueScrollTier.ELITE)
        val second = account(ClueScrollTier.ELITE)
        val assigned = assertNotNull(TreasureTrailSherlock.begin(first, ClueScrollTier.ELITE, zero))
        val held = first.inventory[0]!!
        first.inventory[0] = null; first.bank[0] = held
        assertEquals(0, TreasureTrailSherlock.succeeded(first, TreasureTrailSherlock.Action.NATURE_RUNE))
        assertEquals(0, TreasureTrailSherlock.succeeded(second, TreasureTrailSherlock.Action.NATURE_RUNE))
        assertFalse(assigned.done)
        first.bank[0] = null; first.inventory[0] = held
        assertEquals(1, TreasureTrailSherlock.succeeded(first, TreasureTrailSherlock.Action.NATURE_RUNE))
        assertTrue(assigned.done)
        assertEquals(0, TreasureTrailSherlock.succeeded(first, TreasureTrailSherlock.Action.NATURE_RUNE))
    }

    @Test fun `visible temporary boosts count while an insufficient current level does not`() {
        val p = account(ClueScrollTier.MASTER)
        assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.MASTER, zero))
        levels[Skills.SMITHING] = 87
        assertEquals(0, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.RUNE_MED_HELM))
        levels[Skills.SMITHING] = 88
        assertEquals(1, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.RUNE_MED_HELM))
        assertEquals(1, p.skills.getMaxLevel(Skills.SMITHING))
    }

    @Test fun `a completed assignment takes priority over an incomplete other tier`() {
        val p = account(ClueScrollTier.MASTER)
        val master = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.MASTER, zero))
        assertEquals(1, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.RUNE_MED_HELM))
        p.inventory[1] = Item(ClueScrollTier.ELITE.scrollId)
        ClueScrollManager.prepare(p, p.inventory[1]!!, ClueScrollTier.ELITE)
        p.inventory[1]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, steps.getValue(ClueScrollTier.ELITE).persistenceId)
        val elite = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, zero))
        assertFalse(elite.done)
        assertEquals(master.task.key, TreasureTrailSherlock.forDialogue(p)!!.task.key)
        assertTrue(TreasureTrailSherlock.finish(p, master))
        assertEquals(elite.task.key, TreasureTrailSherlock.forDialogue(p)!!.task.key)
    }

    @Test fun `completing the task waits for Sherlock and then atomically restores the canonical clue once`() {
        val p = account(ClueScrollTier.ELITE)
        (1..27).forEach { p.inventory[it] = Item(995) }
        val assignment = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, zero))
        assertEquals(1, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.NATURE_RUNE))
        assertEquals(0, ClueScrollManager.progress(p, ClueScrollTier.ELITE))
        assertEquals(30003, p.inventory[0]!!.id)
        assertTrue(TreasureTrailSherlock.finish(p, assignment))
        assertEquals(ClueScrollTier.ELITE.scrollId, p.inventory[0]!!.id)
        assertEquals(1, ClueScrollManager.progress(p, ClueScrollTier.ELITE))
        assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_COMPLETE))
        assertFalse(TreasureTrailSherlock.finish(p, assignment))
        assertEquals(27, p.inventory.getItemCount(995)); assertEquals(0, p.inventory.freeSlotCount)
    }

    @Test fun `completed task survives actual item serialization with a fresh account interaction contract`() {
        val p = account(ClueScrollTier.MASTER)
        val old = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.MASTER, zero))
        assertEquals(1, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.RUNE_MED_HELM))
        val raw = Gson().toJson(p.inventory[0])
        p.inventory[0] = Gson().fromJson(raw, Item::class.java)
        assertFalse(old.contract.valid(p))
        val restored = assertNotNull(TreasureTrailSherlock.current(p, ClueScrollTier.MASTER))
        assertTrue(restored.done); assertEquals(old.task.key, restored.task.key)
        assertTrue(TreasureTrailSherlock.finish(p, restored))
    }

    @Test fun `Sherlock challenge blocks another elite across inventory bank grave Death and Mimic retrieval`() {
        val p = account(ClueScrollTier.ELITE)
        assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, zero))
        val challenge = p.inventory[0]!!; p.inventory[0] = null
        assertTrue(ClueDeathItems.contains(challenge.id))
        for (container in listOf(p.inventory, p.bank, p.gravestone, p.deathRecovery)) {
            container[0] = challenge
            assertFalse(ClueOwnership.mayReceive(p, ClueScrollTier.ELITE.scrollId))
            assertEquals(1, ClueContainers.count(p, ClueScrollTier.ELITE))
            container[0] = null
        }
        MimicRecovery.store(p, listOf(challenge))
        assertFalse(ClueOwnership.mayReceive(p, ClueScrollTier.ELITE.scrollId))
        assertEquals(1, ClueContainers.count(p, ClueScrollTier.ELITE))
    }

    @Test fun `Watson accepts the actual Sherlock challenge and invalidates its pending response`() {
        val p = account(ClueScrollTier.ELITE)
        val assignment = assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.ELITE, zero))
        assertEquals(WatsonClues.Result.STORED, WatsonClues.offer(p, setOf(ClueScrollTier.ELITE)))
        assertEquals(setOf(ClueScrollTier.ELITE), WatsonClues.stored(p))
        assertFalse(assignment.contract.valid(p))
        assertFalse(ClueOwnership.owns(p, ClueScrollTier.ELITE))
    }

    @Test fun `all thirteen selected actions require the issued tier challenge and mark once`() {
        val tasks = TreasureTrailTaskCatalogue.current.sherlock
        assertEquals(TreasureTrailSherlock.Action.values().map { it.name }.toSet(), tasks.map { it.key }.toSet())
        for (task in tasks) {
            val p = account(task.tier)
            assertNotNull(TreasureTrailSherlock.begin(p, task.tier, zero))
            p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, task.key.hashCode())
            assertEquals(1, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.valueOf(task.key)), task.key)
            assertEquals(0, TreasureTrailSherlock.succeeded(p, TreasureTrailSherlock.Action.valueOf(task.key)), task.key)
        }
    }

    @Test fun `tower task excludes other Nechryael locations and the gem task excludes other gem stalls`() {
        val p = account(ClueScrollTier.MASTER)
        assertNotNull(TreasureTrailSherlock.begin(p, ClueScrollTier.MASTER, zero))
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY, "NECHRYAEL_TOWER".hashCode())
        val def = NpcDef(1613).also { it.name = "Nechryael"; it.combatLevel = 115 }
        every { p.world.definitions.get(NpcDef::class.java, 1613) } returns def
        val npc = mockk<Npc>(relaxed = true)
        every { npc.world } returns p.world; every { npc.id } returns 1613
        every { npc.tile } returns Tile(2695, 8850, 0) // Wiki Charred Dungeon variant.
        TreasureTrailSherlock.killed(p, npc)
        assertFalse(TreasureTrailSherlock.current(p, ClueScrollTier.MASTER)!!.done)
        every { npc.tile } returns Tile(3433, 3570, 2) // Wiki Tower top floor.
        TreasureTrailSherlock.killed(p, npc)
        assertTrue(TreasureTrailSherlock.current(p, ClueScrollTier.MASTER)!!.done)
        assertTrue(TreasureTrailSherlock.tower(Tile(3403, 9968, 0)))
        assertFalse(TreasureTrailSherlock.tower(Tile(3433, 3570, 0)))
        assertTrue(TreasureTrailSherlock.ardougneGem(Tile(2668, 3303, 0)))
        assertFalse(TreasureTrailSherlock.ardougneGem(Tile(2887, 10210, 0)))
        assertFalse(TreasureTrailSherlock.ardougneGem(Tile(2668, 3303, 1)))
    }
}
