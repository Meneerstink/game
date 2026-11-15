package gg.rsmod.plugins.content.scrolls

import com.google.gson.Gson
import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.areas.watson.WatsonClues
import io.mockk.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

class TreasureTrailNumberChallengeTests {
    companion object {
        private val steps = TreasureTrailStepCatalogue.current.rows.filter {
            it.challenge == TreasureTrailStepCatalogue.Challenge.NUMBER
        }.map(::TreasureTrailSourceStep)
        init { steps.forEach(ClueScrollManager::registerStep) }
    }
    @BeforeTest fun setup() {
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(2842) } returns 30101
        every { ClueItems.local(7269) } returns 30102
        every { ClueItems.local(12139) } returns 30103
    }
    @AfterTest fun cleanup() { unmockkObject(ClueItems) }

    @Test fun `all emitted numeric dialogue lines come from the original pinned conditional transcripts`() {
        val sources=JsonParser().parse(File(RspsPaths.data("../docs/CLUE/NPC-DIALOGUE-SOURCES.json")).readText()).asJsonArray
        for ((name,lines) in TreasureTrailNumberDialogue.lines) {
            val source=sources.single { it.asJsonObject["npc"].asString.equals(name,true) }.asJsonObject
            val pin=source["source"].asJsonObject
            val raw=File(RspsPaths.data("../docs/CLUE/sources/${pin["file"].asString}")).readBytes()
            val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(pin["sha256"].asString,hash,name)
            val section=source["section"].asString
            assertTrue(raw.toString(Charsets.UTF_8).contains(section),name)
            for (line in lines.initial+lines.prompt+listOfNotNull(lines.wrong,lines.correct,lines.handover)) {
                val spoken=if (line==TreasureTrailNumberDialogue.PLAYER_WHAT) "What?" else line
                assertTrue(section.contains(spoken),"$name: $spoken")
            }
        }
    }

    private fun step(npc: String) = steps.single { it.source.npcName == npc }
    private fun account(step: TreasureTrailSourceStep): Player {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks = false } }
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        return mockk<Player>(relaxed = true).also { p ->
            every { p.world } returns world
            every { p.attr } returns AttributeMap()
            every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
            every { p.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
            every { p.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
            every { p.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
            p.inventory[0] = Item(step.tier.scrollId)
            ClueScrollManager.prepare(p, p.inventory[0]!!, step.tier)
            p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
        }
    }

    @Test fun `three physical challenge papers resolve to their correct tiers and counts`() {
        for (name in listOf("Baraek", "Doomsayer", "Mandrith")) {
            val step = step(name); val p = account(step)
            val before = p.inventory[0]!!
            val pending = assertNotNull(ClueStepContract.capture(p, step))
            val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
            assertTrue(assignment.first)
            assertFalse(pending.valid(p))
            assertNotSame(before, p.inventory[0])
            assertEquals(step.tier, ClueOwnership.tier(p.inventory[0]!!.id))
            assertEquals(step.persistenceId, p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
            assertEquals(1, ClueContainers.count(p, step.tier))
            assertTrue(ClueDeathItems.contains(p.inventory[0]!!.id))
            assertFalse(ClueOwnership.mayReceive(p, step.tier.scrollId))
        }
    }

    @Test fun `handover and answer both preserve a completely full inventory`() {
        val step = step("Baraek"); val p = account(step)
        (1..27).forEach { p.inventory[it] = Item(995) }
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
        assertEquals(0, p.inventory.freeSlotCount)
        assertEquals(TreasureTrailNumberChallenge.Answer.CORRECT, TreasureTrailNumberChallenge.answer(p, assignment, 5))
        assertEquals(step.tier.scrollId, p.inventory[0]!!.id)
        assertEquals(27, p.inventory.getItemCount(995))
        assertEquals(1, ClueScrollManager.progress(p, step.tier))
        assertNull(p.inventory[0]!!.getAttr(ItemAttribute.CLUE_TASK_KEY))
        assertEquals(0, p.inventory.freeSlotCount)
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(p, assignment, 5))
    }

    @Test fun `wrong and historically unavailable answers retain exactly the same paper and progress`() {
        val step = step("Father Aereck"); val p = account(step)
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
        val paper = p.inventory[0]!!; val attrs = paper.attr.toMap()
        for (answer in listOf(-1, 0, 20, Int.MAX_VALUE)) {
            assertEquals(TreasureTrailNumberChallenge.Answer.WRONG, TreasureTrailNumberChallenge.answer(p, assignment, answer))
            assertSame(paper, p.inventory[0]); assertEquals(attrs, paper.attr)
            assertEquals(0, ClueScrollManager.progress(p, step.tier))
        }
        assertEquals(TreasureTrailNumberChallenge.Answer.CORRECT, TreasureTrailNumberChallenge.answer(p, assignment, 19))
    }

    @Test fun `repeated talks keep the same question and physical paper`() {
        val step = step("Drezel"); val p = account(step)
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
        val paper = p.inventory[0]
        repeat(5) {
            val next = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
            assertFalse(next.first); assertSame(paper, next.contract.scroll)
            assertEquals(assignment.source.question, next.source.question)
        }
    }

    @Test fun `missing source import retains the original clue and all attributes`() {
        val step = step("Mandrith"); val p = account(step)
        val old = p.inventory[0]!!; val attrs = old.attr.toMap()
        every { ClueItems.local(12139) } returns null
        assertNull(TreasureTrailNumberChallenge.begin(p, step))
        assertSame(old, p.inventory[0]); assertEquals(attrs, old.attr)
        assertEquals(0, ClueScrollManager.progress(p, step.tier))
    }

    @Test fun `bank drop replacement and second account invalidate a suspended answer`() {
        val step = step("Martin Thwait"); val p = account(step); val other = account(step)
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
        val paper = p.inventory[0]!!
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(other, assignment, 2))
        p.inventory[0] = null; p.bank[0] = paper
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(p, assignment, 2))
        assertTrue(ClueOwnership.owns(p, step.tier))
        p.bank[0] = null; p.inventory[0] = Item(paper)
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(p, assignment, 2))
        p.inventory[0] = paper
        assertEquals(TreasureTrailNumberChallenge.Answer.CORRECT, TreasureTrailNumberChallenge.answer(p, assignment, 2))
    }

    @Test fun `serialized paper keeps its question but requires a fresh account contract`() {
        val step = step("Weird Old Man"); val p = account(step)
        val old = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
        p.inventory[0] = Gson().fromJson(Gson().toJson(p.inventory[0]), Item::class.java)
        val restored = assertNotNull(TreasureTrailNumberChallenge.current(p, step))
        assertEquals(old.source.question, restored.source.question)
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(p, old, 150))
        assertEquals(TreasureTrailNumberChallenge.Answer.CORRECT, TreasureTrailNumberChallenge.answer(p, restored, 150))
    }

    @Test fun `dead player or changed step cannot answer and a moved slot can`() {
        val step = step("Baraek"); val p = account(step)
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p, step))
        every { p.isDead() } returns true
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(p, assignment, 5))
        every { p.isDead() } returns false
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, 0)
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE, TreasureTrailNumberChallenge.answer(p, assignment, 5))
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
        p.inventory[4] = p.inventory[0]; p.inventory[0] = null
        assertEquals(TreasureTrailNumberChallenge.Answer.CORRECT, TreasureTrailNumberChallenge.answer(p, assignment, 5))
    }

    @Test fun `last numeric answer atomically replaces the challenge with one casket`() {
        val step = step("Baraek"); val p = account(step)
        p.attr[AttributeKey<Int>(persistenceKey="clue_length_medium")] = 1
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p,step))
        assertEquals(TreasureTrailNumberChallenge.Answer.CORRECT,TreasureTrailNumberChallenge.answer(p,assignment,5))
        assertEquals(step.tier.casketId,p.inventory[0]!!.id)
        assertEquals(0,ClueScrollManager.progress(p,step.tier))
        assertFalse(ClueOwnership.owns(p,step.tier))
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE,TreasureTrailNumberChallenge.answer(p,assignment,5))
        assertEquals(1,p.inventory.filterNotNull().count())
    }

    @Test fun `Watson hand-in recognizes the physical numeric paper and invalidates its suspended answer`() {
        val step = step("Baraek"); val p = account(step)
        val assignment = assertNotNull(TreasureTrailNumberChallenge.begin(p,step))
        assertEquals(WatsonClues.Result.STORED,WatsonClues.offer(p,setOf(step.tier),masterId=30201,boxId=30202))
        assertEquals(setOf(step.tier),WatsonClues.stored(p))
        assertTrue(p.inventory.isEmpty)
        assertEquals(TreasureTrailNumberChallenge.Answer.STALE,TreasureTrailNumberChallenge.answer(p,assignment,5))
    }
}
