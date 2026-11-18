package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import gg.rsmod.plugins.content.scrolls.ClueItems
import io.mockk.*
import kotlin.random.Random
import kotlin.test.*

class MimicAttackTests {
    @AfterTest fun cleanup()=unmockkAll()
    private fun at(n:Double)=object:Random() { override fun nextBits(bitCount:Int)=0;override fun nextDouble()=n }
    @Test fun `attack choices use only supplied measured weights and reject impossible distributions`() {
        assertEquals(MimicAttacks.Choice.CANDY,MimicAttacks.choose(0.25,0.25,at(0.0)))
        assertEquals(MimicAttacks.Choice.FORWARD,MimicAttacks.choose(0.25,0.25,at(0.25)))
        assertEquals(MimicAttacks.Choice.MELEE,MimicAttacks.choose(0.25,0.25,at(0.5)))
        assertEquals(MimicAttacks.Choice.MELEE,MimicAttacks.choose(0.0,0.0,at(0.0)))
        for((a,b) in listOf(-0.1 to 0.0,0.7 to 0.4,Double.NaN to 0.0,0.0 to Double.POSITIVE_INFINITY))
            assertFailsWith<IllegalArgumentException> { MimicAttacks.choose(a,b,at(0.0)) }
        assertNotNull(MimicAttacks.plan) // 18-11 night run: spread/ballistics measured from uncut OSRS videos (mimic-encounter.json 'measurement').
    }
    private fun account():MimicEncounter.Session {
        mockkObject(MimicEncounter,ClueItems)
        every { ClueItems.local(23184) } returns 23729
        val defs=mockk<DefinitionSet>();every { defs.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()) }
        val world=mockk<World>(relaxed=true);every { world.currentCycle } returns 0
        val p=mockk<Player>(relaxed=true);every { p.world } returns world;every { p.isOnline } returns true
        every { p.tile } returns Tile(6417,6412,1)
        every { p.inventory } returns ItemContainer(defs,28,ContainerStackType.NORMAL)
        val box=MimicChallenge.create(23729,ClueScrollTier.ELITE);p.inventory[0]=box
        val token=MimicChallenge.begin(box)!!
        val map=mockk<InstancedMap>();every { map.area } returns Area(6400,6400,6463,6463)
        val s=MimicEncounter.Session(p,map,box,token)
        val npc=mockk<Npc>(relaxed=true);every { npc.world } returns world;every { npc.tile } returns Tile(6410,6410,1)
        every { npc.isSpawned() } returns true;every { npc.getSize() } returns 5;every { npc.timers } returns TimerMap()
        s.boss=npc;every { MimicEncounter.session(p) } returns s
        return s
    }
    @Test fun `forward checks the whole five tile body collision freeze and instance bounds`() {
        val s=account();val npc=s.boss!!;val world=s.player.world
        val collision=world.collision;val origin=npc.tile
        every { collision.canBodyStep(origin,5,5,Direction.EAST,false) } returns true
        assertTrue(MimicAttacks.forward(s));verify(exactly=1) { npc.moveTo(Tile(6411,6410,1)) }
        npc.timers[FROZEN_TIMER]=10;assertFalse(MimicAttacks.forward(s));npc.timers.remove(FROZEN_TIMER)
        every { collision.canBodyStep(origin,5,5,Direction.EAST,false) } returns false
        assertFalse(MimicAttacks.forward(s))
        every { npc.tile } returns Tile(6459,6410,1);every { s.player.tile } returns Tile(6463,6412,1)
        assertFalse(MimicAttacks.forward(s))
        verify(exactly=1) { npc.moveTo(any<Tile>()) }
    }
    @Test fun `six actual candy projectiles preserve measured deadline source colours and fixed launch position`() {
        val s=account();val world=s.player.world
        // Synthetic offsets/ballistics test the transport contract; these are NOT OSRS constants.
        val pattern=MimicAttacks.Plan(listOf(0 to 0,1 to 0,2 to 0,0 to 1,1 to 1,2 to 1),MimicAttacks.Flight(80,0,32,0,30))
        val projectiles=mutableListOf<Projectile>()
        every { world.spawn(capture(projectiles)) } just Runs
        assertTrue(MimicAttacks.candy(s,MimicEncounter.Assets(true,1,0.0,4,0.0,true),pattern))
        assertEquals(6,projectiles.size)
        assertEquals(listOf(3012,3012,3012,3014,3015,3013),projectiles.map { it.gfx })
        assertTrue(projectiles.all { it.delay==30 && it.lifespan==120 && it.tile==Tile(6412,6412,1) })
        assertEquals(pattern.tiles(Tile(6417,6412,1)),projectiles.map { it.targetTile })
        every { s.player.tile } returns Tile(6440,6440,1)
        assertEquals(Tile(6417,6412,1),projectiles.first().targetTile)
        MimicCandies.cancel(s)
    }
}
