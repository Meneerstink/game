package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import kotlin.test.*

class TreasureTrailEmoteTests {
    private val accounts = mutableListOf<Player>()
    @BeforeTest fun setup() = mockkObject(ClueScrollManager)
    @AfterTest fun cleanup() {
        accounts.forEach { TreasureTrailEmotes.remove(it) }
        unmockkAll()
    }
    private val catalogue get() = TreasureTrailStepCatalogue.current.rows
    private fun account(row: TreasureTrailStepCatalogue.Step): Pair<Player, Npc> {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks=false } }
        val world = mockk<World>(relaxed=true)
        every { world.definitions } returns definitions
        val p = mockk<Player>(relaxed=true)
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        every { p.tile } returns row.points.single()
        val step = TreasureTrailSourceStep(row)
        val scroll = Item(row.tier.scrollId).putAttr(ItemAttribute.CLUE_STEP_KEY, row.id)
        p.inventory[0] = scroll
        row.equipment.forEachIndexed { index, group -> p.equipment[index]=Item(group.first { it.local()!=null }.local()!!) }
        every { ClueScrollManager.activeStep(p, any()) } returns null
        every { ClueScrollManager.activeStep(p, row.tier) } returns step
        every { ClueScrollManager.progress(p, row.tier) } returns 0
        every { ClueScrollManager.advance(p, row.tier, scroll, 0, emptyList()) } returns true
        accounts += p
        val uri = mockk<Npc>(relaxed=true)
        every { uri.world } returns world
        return p to uri
    }

    @Test fun `all easy and elite URI steps need exact emote tile plane and attire then one talk completion`() {
        val rows = catalogue.filter { it.action == TreasureTrailStepCatalogue.Action.EMOTE &&
            it.challenge == TreasureTrailStepCatalogue.Challenge.URI && it.secondEmote == null }
        assertEquals(8, rows.size)
        for (row in rows) {
            val (p, uri) = account(row)
            var spawned=0
            val spawn = { _: Player, _: TreasureTrailSourceStep -> spawned++; uri }
            val at = row.points.single()
            assertFalse(TreasureTrailEmotes.perform(p, -1, spawn))
            every { p.tile } returns Tile(at.x+1,at.z,at.height)
            assertFalse(TreasureTrailEmotes.perform(p, row.firstEmote!!, spawn))
            every { p.tile } returns Tile(at.x,at.z,(at.height+1)%4)
            assertFalse(TreasureTrailEmotes.perform(p, row.firstEmote!!, spawn))
            every { p.tile } returns at
            val gear = p.equipment[0]!!
            p.equipment[0]=null
            assertFalse(TreasureTrailEmotes.perform(p, row.firstEmote!!, spawn))
            p.equipment[0]=gear
            assertTrue(TreasureTrailEmotes.perform(p, row.firstEmote!!, spawn))
            assertTrue(TreasureTrailEmotes.perform(p, row.firstEmote!!, spawn))
            assertEquals(1,spawned)
            verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
            p.equipment[0]=null
            assertFalse(TreasureTrailEmotes.finish(p,uri))
            p.equipment[0]=gear
            assertTrue(TreasureTrailEmotes.finish(p,uri))
            assertFalse(TreasureTrailEmotes.finish(p,uri))
            verify(exactly=1) { ClueScrollManager.advance(p,row.tier,any(),0,emptyList()) }
        }
    }

    @Test fun `medium requires its own second emote in order and never posts another Uri`() {
        for(row in catalogue.filter { it.tier == ClueScrollTier.MEDIUM && it.action == TreasureTrailStepCatalogue.Action.EMOTE }) {
            val (p,uri)=account(row)
            var spawned=0
            val spawn={ _: Player, _: TreasureTrailSourceStep -> spawned++; uri }
            assertFalse(TreasureTrailEmotes.perform(p,row.secondEmote!!,spawn))
            assertTrue(TreasureTrailEmotes.perform(p,row.firstEmote!!,spawn))
            assertFalse(TreasureTrailEmotes.finish(p,uri))
            assertFalse(TreasureTrailEmotes.perform(p,-1,spawn))
            assertTrue(TreasureTrailEmotes.perform(p,row.secondEmote!!,spawn))
            assertEquals(1,spawned)
            assertTrue(TreasureTrailEmotes.finish(p,uri))
        }
    }

    @Test fun `Uri refuses another account replacement scroll banked scroll dead player and replay`() {
        val row=catalogue.first { it.action == TreasureTrailStepCatalogue.Action.EMOTE && it.challenge == TreasureTrailStepCatalogue.Challenge.URI }
        val (p,uri)=account(row)
        val (other,otherUri)=account(row)
        assertTrue(TreasureTrailEmotes.perform(p,row.firstEmote!!){_,_->uri})
        assertFalse(TreasureTrailEmotes.finish(other,uri))
        assertFalse(TreasureTrailEmotes.finish(p,otherUri))
        val scroll=p.inventory[0]!!
        p.inventory[0]=Item(scroll.id).copyAttr(scroll)
        assertFalse(TreasureTrailEmotes.finish(p,uri))
        p.inventory[0]=null
        assertFalse(TreasureTrailEmotes.finish(p,uri))
        p.inventory[27]=scroll // Moving this same item within inventory is allowed.
        every { p.isDead() } returns true
        assertFalse(TreasureTrailEmotes.finish(p,uri))
        every { p.isDead() } returns false
        assertTrue(TreasureTrailEmotes.finish(p,uri))
        assertFalse(TreasureTrailEmotes.finish(p,uri))
    }

    @Test fun `failed next-clue transaction retains Uri readiness for a safe retry`() {
        val row=catalogue.first { it.action == TreasureTrailStepCatalogue.Action.EMOTE && it.challenge == TreasureTrailStepCatalogue.Challenge.URI }
        val (p,uri)=account(row)
        val scroll=p.inventory[0]!!
        (1..27).forEach { p.inventory[it]=Item(995) }
        assertTrue(TreasureTrailEmotes.perform(p,row.firstEmote!!){_,_->uri})
        every { ClueScrollManager.advance(p,row.tier,scroll,0,emptyList()) } returns false
        assertFalse(TreasureTrailEmotes.finish(p,uri))
        assertSame(scroll,p.inventory[0])
        assertFalse(TreasureTrailEmotes.session(p)!!.settled)
        every { ClueScrollManager.advance(p,row.tier,scroll,0,emptyList()) } returns true
        assertTrue(TreasureTrailEmotes.finish(p,uri))
    }

    @Test fun `failed actor spawn consumes neither the clue nor the completion and can retry`() {
        val row=catalogue.first { it.action == TreasureTrailStepCatalogue.Action.EMOTE && it.challenge == TreasureTrailStepCatalogue.Challenge.URI }
        val (p,uri)=account(row)
        val scroll=p.inventory[0]
        assertTrue(TreasureTrailEmotes.perform(p,row.firstEmote!!){_,_->null})
        assertNull(TreasureTrailEmotes.session(p))
        assertSame(scroll,p.inventory[0])
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
        assertTrue(TreasureTrailEmotes.perform(p,row.firstEmote!!){_,_->uri})
        assertTrue(TreasureTrailEmotes.finish(p,uri))
    }

    @Test fun `hard and master agents cannot be bypassed by the guard-free Uri service`() {
        for(row in catalogue.filter { it.action == TreasureTrailStepCatalogue.Action.EMOTE && it.challenge == TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT }) {
            val (p,uri)=account(row)
            assertFalse(TreasureTrailEmotes.perform(p,row.firstEmote!!){_,_->uri})
            assertNull(TreasureTrailEmotes.session(p))
            assertFalse(TreasureTrailEmotes.finish(p,uri))
        }
    }

    @Test fun `banking or changing progress invalidates the actor and a new emote cannot reuse it`() {
        val row=catalogue.first { it.action == TreasureTrailStepCatalogue.Action.EMOTE && it.challenge == TreasureTrailStepCatalogue.Challenge.URI }
        val (p,uri)=account(row)
        assertTrue(TreasureTrailEmotes.perform(p,row.firstEmote!!){_,_->uri})
        every { ClueScrollManager.progress(p,row.tier) } returns 1
        TreasureTrailEmotes.cycle()
        assertNull(TreasureTrailEmotes.session(p))
        val world = uri.world
        verify(exactly=1) { world.remove(uri) }
        assertFalse(TreasureTrailEmotes.finish(p,uri))
    }
}
