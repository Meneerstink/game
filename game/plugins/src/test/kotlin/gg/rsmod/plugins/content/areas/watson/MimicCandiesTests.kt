package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.scrolls.ClueItems
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import io.mockk.*
import kotlin.test.*

class MimicCandiesTests {
    private val accounts=mutableListOf<MimicEncounter.Session>()
    @BeforeTest fun setup() {
        mockkObject(ClueItems,MimicEncounter)
        every { ClueItems.local(23184) } returns 23729
    }
    @AfterTest fun cleanup() { accounts.forEach(MimicCandies::cancel);unmockkAll() }
    private fun account():MimicEncounter.Session {
        val defs=mockk<DefinitionSet>();every { defs.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()) }
        val world=mockk<World>(relaxed=true);every { world.currentCycle } returns 0
        val p=mockk<Player>(relaxed=true);every { p.world } returns world;every { p.isOnline } returns true
        every { p.inventory } returns ItemContainer(defs,28,ContainerStackType.NORMAL)
        every { p.tile } returns Tile(6411,6410,1)
        val box=MimicChallenge.create(23729,ClueScrollTier.ELITE);p.inventory[0]=box
        val token=MimicChallenge.begin(box)!!
        val map=mockk<InstancedMap>();every { map.area } returns Area(6400,6400,6463,6463)
        val session=MimicEncounter.Session(p,map,box,token)
        val boss=mockk<Npc>(relaxed=true);every { boss.world } returns world;every { boss.tile } returns Tile(6410,6410,1)
        every { boss.isSpawned() } returns true;session.boss=boss
        every { MimicEncounter.session(p) } returns session
        accounts+=session;return session
    }
    private fun tiles()=(1..6).map { Tile(6410+it,6410,1) }
    private fun minion(s:MimicEncounter.Session, colour:MimicCandies.Colour, tile:Tile):Npc=mockk<Npc>(relaxed=true).also {
        every { it.id } returns colour.minion!!;every { it.world } returns s.player.world
        every { it.tile } returns tile;every { it.isSpawned() } returns true
    }
    @Test fun `six candies preserve source colour and minion identity and land only after supplied four ticks`() {
        val s=account();val tickWorld=s.player.world;var releases=0;var impacts=0;var spawns=0
        assertTrue(MimicCandies.launch(s,tiles(),4,10,{ candies ->
            releases++;assertEquals(3,candies.count { it.colour==MimicCandies.Colour.PURPLE })
            assertEquals(6,candies.map { it.tile }.distinct().size)
            assertEquals(setOf(1670,1671,1672,1673),candies.map { it.colour.sourceSpot }.toSet())
        },{impacts++},{ colour,tile ->spawns++;minion(s,colour,tile) }))
        every { tickWorld.currentCycle } returns 3;MimicCandies.cycle(s.player.world)
        assertEquals(1,releases);assertEquals(0,impacts);assertEquals(0,spawns)
        verify(exactly=0) { s.player.addHit(any()) }
        every { tickWorld.currentCycle } returns 4;repeat(3) { MimicCandies.cycle(s.player.world) }
        assertEquals(6,impacts);assertEquals(3,spawns)
        assertEquals(setOf(14403,14404,14405),s.minions.map { it.id }.toSet())
        verify(exactly=1) { s.player.addHit(match { it.hitmarks.single().damage==10 }) }
    }
    @Test fun `moving away from the actual landing tile avoids damage but all three colours still summon`() {
        val s=account();val tickWorld=s.player.world;var spawns=0
        assertTrue(MimicCandies.launch(s,tiles(),4,10,{},{},{ colour,tile ->spawns++;minion(s,colour,tile) }))
        every { s.player.tile } returns Tile(6440,6440,1)
        every { tickWorld.currentCycle } returns 4;MimicCandies.cycle(s.player.world)
        assertEquals(3,spawns);verify(exactly=0) { s.player.addHit(any()) }
    }
    @Test fun `living minions are not duplicated and a dead colour is replaced only by another candy impact`() {
        val s=account();val tickWorld=s.player.world;var spawns=0
        val spawn={ colour:MimicCandies.Colour,tile:Tile ->spawns++;minion(s,colour,tile) }
        MimicCandies.launch(s,tiles(),4,10,{},{},spawn)
        every { tickWorld.currentCycle } returns 4;MimicCandies.cycle(s.player.world);assertEquals(3,spawns)
        MimicCandies.launch(s,tiles(),4,10,{},{},spawn)
        every { tickWorld.currentCycle } returns 8;MimicCandies.cycle(s.player.world);assertEquals(3,spawns)
        val warrior=s.minions.single { it.id==14403 };every { warrior.isDead() } returns true
        MimicCandies.cycle(s.player.world);assertEquals(3,spawns)
        MimicCandies.launch(s,tiles(),4,10,{},{},spawn)
        every { tickWorld.currentCycle } returns 12;MimicCandies.cycle(s.player.world)
        assertEquals(4,spawns);assertEquals(3,s.minions.size);assertFalse(s.minions.any { it===warrior })
    }
    @Test fun `leaving winning dying dropping casket changing token boss or account invalidates pending damage`() {
        for(invalidate in listOf<(MimicEncounter.Session)->Unit>(
            {it.finished=true},{it.won=true},{every { it.player.isDead() } returns true},
            {every { it.player.isOnline } returns false},{it.player.inventory[0]=null},
            {it.player.inventory[0]=Item(it.casket)},
            {it.casket.putAttr(ItemAttribute.MIMIC_ATTEMPT_SERIAL,it.token+1)},
            {it.boss=mockk(relaxed=true)},
            {every { MimicEncounter.session(it.player) } returns null},
            {every { it.player.tile } returns Tile(6411,6410,0)})) {
            val s=account();val tickWorld=s.player.world;var impacts=0;var spawns=0
            assertTrue(MimicCandies.launch(s,tiles(),4,10,{},{impacts++},{ _,_->spawns++;null }))
            invalidate(s);every { tickWorld.currentCycle } returns 4;MimicCandies.cycle(s.player.world)
            assertEquals(0,impacts);assertEquals(0,spawns);verify(exactly=0) { s.player.addHit(any()) }
        }
    }
    @Test fun `malformed or foreign landing plans cannot publish partial delayed damage`() {
        val s=account();val tickWorld=s.player.world;var releases=0
        val release={ _:List<MimicCandies.Candy> ->releases++;Unit }
        for(points in listOf(tiles().take(5),List(6) { tiles()[0] },tiles().dropLast(1)+Tile(1,1,1),tiles().dropLast(1)+Tile(6411,6411,0)))
            assertFalse(MimicCandies.launch(s,points,4,10,release,{},{ _,_->null }))
        assertFalse(MimicCandies.launch(s,tiles(),0,10,release,{},{ _,_->null }))
        assertEquals(0,releases)
        assertFailsWith<IllegalStateException> { MimicCandies.launch(s,tiles(),4,10,{error("visual asset failure")},{},{ _,_->null }) }
        every { tickWorld.currentCycle } returns 4;MimicCandies.cycle(s.player.world)
        verify(exactly=0) { s.player.addHit(any()) }
    }
    @Test fun `two separate instances and overlapping volleys keep their exact account and world`() {
        val a=account();val b=account();val worldA=a.player.world;val worldB=b.player.world;var first=0;var second=0
        repeat(2) { assertTrue(MimicCandies.launch(a,tiles(),4,10,{},{first++},{ _,_->null })) }
        assertTrue(MimicCandies.launch(b,tiles(),4,10,{},{second++},{ _,_->null }))
        every { worldA.currentCycle } returns 4;MimicCandies.cycle(a.player.world)
        assertEquals(12,first);assertEquals(0,second)
        verify(exactly=2) { a.player.addHit(any()) };verify(exactly=0) { b.player.addHit(any()) }
        MimicCandies.cancel(b);every { worldB.currentCycle } returns 4;MimicCandies.cycle(b.player.world)
        assertEquals(0,second);verify(exactly=0) { b.player.addHit(any()) }
    }
    @Test fun `exit during a visual callback cancels all remaining impacts hits and minions`() {
        val s=account();val world=s.player.world;var impacts=0;var spawns=0
        assertTrue(MimicCandies.launch(s,tiles(),4,10,{},{ impacts++;s.finished=true },{ _,_->spawns++;null }))
        every { world.currentCycle } returns 4
        repeat(2) { MimicCandies.cycle(world) }
        assertEquals(1,impacts);assertEquals(0,spawns)
        verify(exactly=0) { s.player.addHit(any()) }
    }
    @Test fun `exit during spawn removes the unpublished actor and never leaks a session minion`() {
        val s=account();val world=s.player.world;var spawns=0;var candidate:Npc?=null
        assertTrue(MimicCandies.launch(s,tiles(),4,10,{},{},{ colour,tile ->
            spawns++;minion(s,colour,tile).also { candidate=it;s.finished=true }
        }))
        every { world.currentCycle } returns 4
        MimicCandies.cycle(world)
        assertEquals(1,spawns);assertTrue(s.minions.isEmpty())
        val npc=candidate!!;verify(exactly=1) { world.remove(npc) }
    }
}
