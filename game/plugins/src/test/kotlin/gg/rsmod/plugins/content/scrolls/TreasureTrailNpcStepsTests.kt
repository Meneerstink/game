package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

class TreasureTrailNpcStepsTests {
    companion object {
        private val rows = TreasureTrailStepCatalogue.current.rows
        init { rows.filter(TreasureTrailNpcSteps::supports).map(::TreasureTrailSourceStep).forEach(ClueScrollManager::registerStep) }
    }
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    @BeforeTest fun setup() {
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks = false } }
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(7269) } returns 30102
    }
    @AfterTest fun cleanup() { unmockkObject(ClueItems) }
    private fun account(row: TreasureTrailStepCatalogue.Step, length: Int = 2): Pair<Player, TreasureTrailSourceStep> {
        val p = mockk<Player>(relaxed=true)
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { p.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { p.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        p.inventory[0] = Item(row.tier.scrollId)
        ClueScrollManager.prepare(p, p.inventory[0]!!, row.tier)
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, row.id)
        p.attr[AttributeKey<Int>(persistenceKey="clue_length_"+row.tier.name.lowercase())] = length
        return p to (ClueScrollManager.activeStep(p,row.tier) as TreasureTrailSourceStep)
    }
    @Test fun `modern Traiborn cipher uses only the existing native tower actor and leaves ordinary options intact`() {
        val row = rows.single { it.key == "Clue scroll (medium) - XJABSE USBJCPSO" }
        val native = NpcDef(881).apply { name = "Traiborn"; options[0] = "Talk-to" }
        every { definitions.getNullable(NpcDef::class.java, 881) } returns native
        every { definitions.get(NpcDef::class.java, any()) } answers { native }
        every { definitions.getNullable(NpcDef::class.java, neq(881)) } returns null
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { ClueItems.local(2842) } returns 30101
        assertTrue(TreasureTrailBasicSteps.available(world,row))
        assertEquals(setOf(881),TreasureTrailNpcSteps.npcs(row))
        val actor = mockk<Npc>()
        every { actor.id } returns 881; every { actor.world } returns world
        every { actor.spawnTile } returns Tile(3112,3162,1)
        assertTrue(TreasureTrailBasicSteps.matches(actor,row))
        every { actor.id } returns 8180
        assertFalse(TreasureTrailBasicSteps.matches(actor,row))
        every { actor.id } returns 881
        every { actor.spawnTile } returns Tile(3112,3162,0)
        assertFalse(TreasureTrailBasicSteps.matches(actor,row))
        assertFalse(TreasureTrailNpcSteps.matchesName(881,"Traiborn",row.copy(key="other")))
        assertFalse(TreasureTrailNpcSteps.matchesName(8180,"Traiborn",row))
        assertEquals("Traiborn",native.name);assertEquals("Talk-to",native.options[0])
    }

    @Test fun `seven easy NPC branches retain their own source text including three silent handovers`() {
        val sources = JsonParser().parse(File(RspsPaths.data("../docs/CLUE/NPC-COMPLETION-SOURCES.json")).readText()).asJsonArray
        assertEquals(7, sources.size())
        for ((name,text) in TreasureTrailNpcSteps.lines) {
            val pin = sources.single { it.asJsonObject["npc"].asString == name }.asJsonObject
            val file = pin["source"].asJsonObject
            val raw = File(RspsPaths.data("../docs/CLUE/sources/"+file["file"].asString)).readBytes()
            val hash = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(file["sha256"].asString,hash)
            val section = pin["section"].asString
            assertTrue(raw.toString(Charsets.UTF_8).contains(section))
            for (line in text.spoken+listOf(text.next,text.casket)) assertTrue(section.contains(line),"$name: $line")
            val quotes = "'".repeat(3)
            val spoken = Regex(quotes+Regex.escape(name)+":"+quotes).findAll(section).count()
            assertEquals(text.spoken.size,spoken,name)
        }
        assertEquals(setOf("Hans","Ellis","Cassie"), TreasureTrailNpcSteps.lines.filterValues { it.spoken.isEmpty() }.keys)
    }
    @Test fun `every easy handover works with full inventory and replaces the final clue exactly once`() {
        for (row in rows.filter(TreasureTrailNpcSteps::supports)) {
            val (p, step) = account(row)
            (1..27).forEach { p.inventory[it] = Item(995) }
            val contract = assertNotNull(ClueStepContract.capture(p, step))
            assertEquals(TreasureTrailNpcSteps.Receipt(row.tier.scrollId,TreasureTrailNpcSteps.lines.getValue(row.npcName!!).next),
                TreasureTrailNpcSteps.finish(p, contract))
            assertEquals(1,ClueScrollManager.progress(p,row.tier))
            assertEquals(0,p.inventory.freeSlotCount)
            assertEquals(27,p.inventory.getItemCount(995))
            assertNull(TreasureTrailNpcSteps.finish(p, contract))
            val (final, last) = account(row,1)
            (1..27).forEach { final.inventory[it] = Item(995) }
            val ending = assertNotNull(ClueStepContract.capture(final,last))
            assertEquals(TreasureTrailNpcSteps.Receipt(row.tier.casketId,"You've obtained a casket!"),
                TreasureTrailNpcSteps.finish(final,ending))
            assertEquals(1,final.inventory.getItemCount(row.tier.casketId))
            assertEquals(0,final.inventory.freeSlotCount)
            assertEquals(27,final.inventory.getItemCount(995))
            assertNull(TreasureTrailNpcSteps.finish(final,ending))
        }
    }
    @Test fun `banked replaced dead and other-account clues cannot complete a suspended handover`() {
        val row=rows.single { TreasureTrailNpcSteps.supports(it) && it.npcName=="Doric" }
        val (p,step)=account(row); val (other,_)=account(row)
        val contract=assertNotNull(ClueStepContract.capture(p,step))
        assertNull(TreasureTrailNpcSteps.finish(other,contract))
        val paper=p.inventory[0]!!
        p.inventory[0]=null; p.bank[0]=paper
        assertNull(TreasureTrailNpcSteps.finish(p,contract))
        p.inventory[0]=Item(paper); p.bank[0]=null
        assertNull(TreasureTrailNpcSteps.finish(p,contract))
        p.inventory[0]=paper
        every { p.isDead() } returns true
        assertNull(TreasureTrailNpcSteps.finish(p,contract))
        every { p.isDead() } returns false
        assertNotNull(TreasureTrailNpcSteps.finish(p,contract))
    }
    @Test fun `Ned on the ship and alternate Wise Old Man forms cannot satisfy town clues or steal trade options`() {
        val ned=rows.single { TreasureTrailNpcSteps.supports(it) && it.npcName=="Ned" }
        val (p,_)=account(ned)
        val npc=mockk<Npc>(relaxed=true)
        every { npc.world } returns world
        every { npc.id } returns 918
        every { npc.spawnTile } returns Tile(3048,3209,1)
        val definition=NpcDef(918).also { it.name="Ned"; it.options[0]="Talk-to"; it.options[2]="Trade" }
        every { definitions.get(NpcDef::class.java,any()) } returns definition
        assertFalse(TreasureTrailBasicSteps.matches(npc,ned))
        assertFalse(TreasureTrailBasicSteps.talk(p,npc,1))
        every { npc.spawnTile } returns Tile(3098,3257,0)
        assertTrue(TreasureTrailBasicSteps.matches(npc,ned))
        assertFalse(TreasureTrailBasicSteps.talk(p,npc,3))
        val wise=rows.single { it.npcName=="Wise Old Man" }
        every { npc.id } returns 2566
        every { npc.spawnTile } returns Tile(3088,3255,0)
        assertFalse(TreasureTrailNpcSteps.matchesActor(npc,wise))
        every { npc.id } returns 2253
        assertTrue(TreasureTrailNpcSteps.matchesActor(npc,wise))
    }

}
