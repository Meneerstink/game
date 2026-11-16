package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.message.impl.IfSetEventsMessage
import gg.rsmod.game.message.impl.IfSetPositionMessage
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import io.mockk.*
import kotlin.random.Random
import kotlin.test.*

class TreasureTrailPuzzleBoxTests {
    companion object {
        private val steps = TreasureTrailStepCatalogue.current.rows.filter {
            it.key in TreasureTrailPuzzleBoxes.sources.map { row -> row.key }
        }.map(::TreasureTrailSourceStep)
        init { steps.forEach(ClueScrollManager::registerStep) }
    }
    private val accounts = mutableListOf<Player>()
    @BeforeTest fun setup() {
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(19887) } returns 31001
        every { ClueItems.local(23173) } returns 31002
    }
    @AfterTest fun cleanup() { accounts.forEach(TreasureTrailPuzzleBoxes::remove); unmockkAll() }
    private fun account(step: TreasureTrailSourceStep = steps.first()): Player {
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
            p.inventory[0] = Item(step.tier.scrollId)
            ClueScrollManager.prepare(p, p.inventory[0]!!, step.tier)
            p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
            accounts += p
        }
    }
    private fun solvedSession(p: Player, step: TreasureTrailSourceStep, box: Item): TreasureTrailPuzzleBoxes.Session {
        TreasureTrailPuzzleBoxes.remove(p)
        box.putAttr(ItemAttribute.CLUE_TASK_COMPLETE, 1)
        val session = assertNotNull(TreasureTrailPuzzleBoxes.start(p, step, box))
        // Two real legal slides return a known solved board. Solve is still uncommitted until Close.
        assertTrue(TreasureTrailPuzzleBoxes.move(p, session, 23))
        assertNull(box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE))
        assertTrue(TreasureTrailPuzzleBoxes.move(p, session, 24))
        assertTrue(session.board.solved)
        return session
    }

    @Test fun `full inventory cannot exchange the clue for a box or lose an item`() {
        val p = account(); val clue = p.inventory[0]
        (1..27).forEach { p.inventory[it] = Item(995) }
        assertNull(TreasureTrailPuzzleBoxes.issue(p, steps.first(), Random(1)))
        assertSame(clue, p.inventory[0]); assertEquals(27, p.inventory.getItemCount(995))
        assertEquals(0, ClueScrollManager.progress(p, ClueScrollTier.HARD))
    }

    @Test fun `both runtime routes retain their pinned tree picture item bytes and explicit companion symbols`() {
        val folder = java.io.File(gg.rsmod.game.RspsPaths.data("../docs/CLUE/cache"))
        val parser = com.google.gson.JsonParser()
        val routes = parser.parse(java.io.File(folder, "puzzle-routes.json").readText()).asJsonObject
        val rawItems = parser.parse(java.io.File(folder, "puzzle-source-items.json").readText()).asJsonObject
        val expected = routes["routes"].asJsonArray.map { row -> row.asJsonObject.let {
            assertEquals(it["clueSymbol"].asString + "_PUZZLEBOX", it["boxSymbol"].asString)
            TreasureTrailPuzzleBoxes.Source(it["key"].asString, it["box"].asInt, it["picture"].asInt)
        } }
        assertEquals(expected, TreasureTrailPuzzleBoxes.sources)
        for ((_, value) in routes["sources"].asJsonObject.entrySet()) {
            val pin = value.asJsonObject
            val bytes = java.io.File(folder.parentFile, "sources/${pin["file"].asString}").readBytes()
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(pin["sha256"].asString, sha)
        }
        val wiki = java.io.File(folder.parentFile, "sources/Puzzle_box.wiki").readText()
        val tree = Regex("(?m)^\\|id2\\s*=([^\\r\\n]+)").find(wiki)!!.groupValues[1].split(',').map { it.trim().toInt() }
        for (route in expected) {
            assertEquals(2, route.picture); assertTrue(route.item in tree)
            val pin = rawItems[route.item.toString()].asJsonObject
            val bytes = java.util.Base64.getDecoder().decode(pin["rawBase64"].asString)
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(pin["sha256"].asString, sha)
            val item = gg.rsmod.game.tools.importer.ModernItemDefDecoder.decode(route.item, bytes)
            assertEquals("Puzzle box (hard)", item.name); assertEquals(100, item.cost)
            assertFalse(item.tradeable); assertFalse(item.geTradeable)
            assertEquals("Open", item.inventoryOptions[0])
        }
    }

    @Test fun `missing or misidentified NPC box or UI never enables a puzzle clue`() {
        mockkObject(ClueInterfaces)
        every { ClueInterfaces.of(306) } returns 701
        val step = steps.single { it.source.npcName == "Gunnjorn" }
        val defs = mockk<DefinitionSet>()
        val world = mockk<World>()
        every { world.definitions } returns defs
        every { defs.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, any()) } returns null
        val box = ItemDef(31002).also { it.name = "Puzzle box (hard)"; it.inventoryMenu[0] = "Open" }
        every { defs.getNullable(ItemDef::class.java, 31002) } returns box
        assertFalse(TreasureTrailPuzzleBoxes.available(world, step.source))
        val npc = gg.rsmod.game.fs.def.NpcDef(607).also { it.name = "Gunnjorn"; it.options[0] = "Talk-to" }
        every { defs.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, 607) } returns npc
        assertTrue(TreasureTrailPuzzleBoxes.available(world, step.source))
        npc.options[0] = "Trade"
        assertFalse(TreasureTrailPuzzleBoxes.available(world, step.source))
        npc.options[0] = "Talk-to"; box.name = "Another box"
        assertFalse(TreasureTrailPuzzleBoxes.available(world, step.source))
        box.name = "Puzzle box (hard)"
        every { ClueInterfaces.of(306) } returns null
        assertFalse(TreasureTrailPuzzleBoxes.available(world, step.source))
    }

    @Test fun `Open mounts the correct atlas enables actual source Close and rejects invisible interface clicks`() {
        val step = steps.first(); val p = account(step)
        val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step, Random(10)))
        mockkObject(ClueInterfaces)
        every { ClueInterfaces.of(306) } returns 701
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        every { p.openInterface(any<Int>(), any<InterfaceDestination>(), any<Boolean>()) } just Runs
        assertTrue(TreasureTrailPuzzleBoxes.open(p, box))
        val session = assertNotNull(TreasureTrailPuzzleBoxes.session(p))
        val source = com.google.gson.JsonParser().parse(java.io.File(gg.rsmod.game.RspsPaths.data("../docs/CLUE/cache/interfaces.json")).readText())
            .asJsonObject["306"].asJsonObject
        val close = source.entrySet().single { (_, value) ->
            value.asJsonObject["actions"]?.takeUnless { it.isJsonNull }?.asJsonArray?.any { it.asString == "Close" } == true
        }.key.toInt()
        verify { p.write(IfSetEventsMessage((701 shl 16) or close, -1, -1, 0x2)) }
        verify(exactly = 0) { p.write(IfSetEventsMessage((701 shl 16) or 3, -1, -1, 0x2)) }
        session.board.tiles().forEachIndexed { cell, piece ->
            if (piece != 0) {
                val component = 31 + 24 + piece - 1 // source picture2: tree, source688 dimensions/stride.
                verify { p.write(IfSetPositionMessage((701 shl 16) or component, cell % 5 * 50, cell / 5 * 50)) }
            }
        }
        every { p.interfaces.isVisible(701) } returns false
        val before = session.board.encode()
        assertFalse(TreasureTrailPuzzleBoxes.click(p, 0))
        assertEquals(before, session.board.encode())
    }

    @Test fun `each issuer keeps the clue and gives its own box once with independent accounts`() {
        for (step in steps) {
            val p = account(step); val other = account(step); val clue = p.inventory[0]
            val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step, Random(2)))
            assertSame(clue, p.inventory[0])
            assertEquals(ClueItems.local(TreasureTrailPuzzleBoxes.source(step)!!.item), box.id)
            assertSame(box, TreasureTrailPuzzleBoxes.issue(p, step))
            assertEquals(1, p.inventory.getItemCount(box.id))
            p.inventory[1] = null; p.bank[0] = box
            assertNull(TreasureTrailPuzzleBoxes.issue(p, step))
            assertNotNull(TreasureTrailPuzzleBoxes.issue(other, step))
        }
    }

    @Test fun `a banked or orphaned puzzle blocks another hard clue across all recovery stores`() {
        val p = account(); val other = account(); val step = steps.first()
        val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step))
        p.inventory[0] = null; p.inventory[1] = null
        for (store in listOf(p.inventory, p.bank, p.gravestone, p.deathRecovery)) {
            store[0] = box
            assertTrue(ClueOwnership.owns(p, ClueScrollTier.HARD))
            assertFalse(ClueOwnership.mayReceive(p, ClueScrollTier.HARD.scrollId))
            assertFalse(ClueOwnership.owns(p, ClueScrollTier.EASY))
            store[0] = null
        }
        other.inventory[0] = null
        assertFalse(ClueOwnership.owns(other, ClueScrollTier.HARD))
        p.inventory[0] = Item(ClueScrollTier.HARD.casketId)
        assertFalse(ClueOwnership.owns(p, ClueScrollTier.HARD))
    }

    @Test fun `moves bind the account exact clue exact box progress and live player`() {
        val step = steps.first(); val p = account(step); val other = account(step)
        val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step, Random(3)))
        val session = assertNotNull(TreasureTrailPuzzleBoxes.session(p))
        val blank = session.board.tiles().indexOf(0)
        val valid = (0..24).first { kotlin.math.abs(it / 5 - blank / 5) + kotlin.math.abs(it % 5 - blank % 5) == 1 }
        val original = session.board.encode()
        assertFalse(TreasureTrailPuzzleBoxes.move(other, session, valid))
        assertFalse(TreasureTrailPuzzleBoxes.move(p, session, -1))
        assertFalse(TreasureTrailPuzzleBoxes.move(p, session, blank))
        val clue = p.inventory[0]!!
        p.inventory[0] = Item(clue).copyAttr(clue)
        assertFalse(TreasureTrailPuzzleBoxes.move(p, session, valid))
        p.inventory[0] = null
        assertFalse(TreasureTrailPuzzleBoxes.move(p, session, valid))
        p.inventory[27] = clue
        p.inventory[1] = Item(box).copyAttr(box)
        assertFalse(TreasureTrailPuzzleBoxes.move(p, session, valid))
        p.inventory[1] = box
        every { p.isDead() } returns true
        assertFalse(TreasureTrailPuzzleBoxes.move(p, session, valid))
        every { p.isDead() } returns false
        assertEquals(original, session.board.encode())
        assertTrue(TreasureTrailPuzzleBoxes.move(p, session, valid))
    }

    @Test fun `solve requires close and NPC handin which consumes once even with full inventory`() {
        for (step in steps) {
            val p = account(step)
            val clue = p.inventory[0]!!
            val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step))
            (2..27).forEach { p.inventory[it] = Item(995) }
            solvedSession(p, step, box)
            assertFalse(TreasureTrailPuzzleBoxes.handIn(p, step, box))
            assertSame(clue, p.inventory[0])
            assertEquals(0, ClueScrollManager.progress(p, step.tier))
            TreasureTrailPuzzleBoxes.close(p)
            assertEquals(1, box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE))
            assertTrue(TreasureTrailPuzzleBoxes.handIn(p, step, box))
            assertEquals(1, ClueScrollManager.progress(p, step.tier))
            assertFalse(p.inventory.contains(box.id))
            assertEquals(26, p.inventory.getItemCount(995))
            assertFalse(TreasureTrailPuzzleBoxes.handIn(p, step, box))
        }
    }

    @Test fun `missing final casket retains solved box and scroll for retry then gives exactly one casket`() {
        val step = steps.first(); val p = account(step)
        val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step))
        solvedSession(p, step, box); TreasureTrailPuzzleBoxes.close(p)
        p.attr[AttributeKey<Int>(persistenceKey = "clue_length_hard")] = 1
        every { ClueItems.local(20544) } returns null
        assertFalse(TreasureTrailPuzzleBoxes.handIn(p, step, box))
        assertSame(box, p.inventory[1]); assertEquals(1, box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE))
        every { ClueItems.local(20544) } answers { callOriginal() }
        assertTrue(TreasureTrailPuzzleBoxes.handIn(p, step, box))
        assertEquals(1, p.inventory.getItemCount(step.tier.casketId))
        assertFalse(p.inventory.contains(step.tier.scrollId)); assertFalse(p.inventory.contains(box.id))
        assertFalse(TreasureTrailPuzzleBoxes.handIn(p, step, box))
    }

    @Test fun `unsolved close and logout discard the board and a fresh opening is still solvable`() {
        val step = steps.first(); val p = account(step)
        val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step, Random(4)))
        val first = assertNotNull(TreasureTrailPuzzleBoxes.session(p))
        TreasureTrailPuzzleBoxes.close(p)
        assertNull(TreasureTrailPuzzleBoxes.session(p)); assertNull(box.getAttr(ItemAttribute.CLUE_TASK_COMPLETE))
        val next = assertNotNull(TreasureTrailPuzzleBoxes.start(p, step, box, Random(5)))
        assertNotSame(first, next); assertNotEquals(first.board.encode(), next.board.encode())
        TreasureTrailSlidingPuzzle.restore(next.board.tiles())
        TreasureTrailPuzzleBoxes.remove(p)
        assertNull(TreasureTrailPuzzleBoxes.session(p))
        assertFalse(TreasureTrailPuzzleBoxes.move(p, next, 0))
    }

    @Test fun `solved box attributes survive container serialization but cannot handin against another step`() {
        val step = steps.first(); val p = account(step)
        val box = assertNotNull(TreasureTrailPuzzleBoxes.issue(p, step))
        solvedSession(p, step, box); TreasureTrailPuzzleBoxes.close(p)
        p.inventory[1] = Item(box)
        val restored = p.inventory[1]!!
        assertNotSame(box, restored)
        assertEquals(1, restored.getAttr(ItemAttribute.CLUE_TASK_COMPLETE))
        assertFalse(TreasureTrailPuzzleBoxes.handIn(p, steps.last(), restored))
        assertFalse(TreasureTrailPuzzleBoxes.handIn(p, step, box))
        assertTrue(TreasureTrailPuzzleBoxes.handIn(p, step, restored))
    }
}
