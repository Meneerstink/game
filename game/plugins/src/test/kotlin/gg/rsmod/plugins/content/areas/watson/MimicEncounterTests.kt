package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAllocator
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import io.mockk.*
import kotlin.test.*

class MimicEncounterTests {
    private val accounts=mutableListOf<Player>()
    // Synthetic complete encounter fixture for lifecycle tests; probabilities are not OSRS assertions.
    private val cacheReady=MimicEncounter.Assets(true,1,0.0,4,0.0,true,arenaRegion=4160)
    data class Account(val player:Player,val world:World,val allocator:InstancedMapAllocator,
                       val map:InstancedMap,val idle:Npc,val casket:Item)
    @AfterTest fun cleanup() { accounts.forEach { MimicEncounter.leave(it,moveOutside=false) };unmockkAll() }
    private fun account(): Account {
        val defs=mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()).also { it.stacks=false } }
        val allocator=mockk<InstancedMapAllocator>(relaxed=true)
        val map=mockk<InstancedMap>(relaxed=true)
        every { map.area } returns Area(6400,6400,6463,6463)
        val world=mockk<World>(relaxed=true)
        every { world.instanceAllocator } returns allocator
        every { world.definitions } returns defs
        every { allocator.allocate(world,any(),any()) } returns map
        val p=mockk<Player>(relaxed=true)
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(defs,28,ContainerStackType.NORMAL)
        every { p.tile } returns MimicEncounter.OUTSIDE
        val casket=MimicChallenge.create(23729,ClueScrollTier.ELITE)
        p.inventory[0]=casket
        val idle=mockk<Npc>(relaxed=true)
        every { idle.id } returns Npcs.THE_MIMIC
        every { idle.world } returns world
        every { idle.isSpawned() } returns true
        accounts+=p
        return Account(p,world,allocator,map,idle,casket)
    }

    @Test fun `missing mapping or occupied native source cannot allocate an encounter`() {
        val a=account()
        for(region in listOf(null,-1,65536,10819)) {
            assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady.copy(arenaRegion=region)) { _,_->error("Invalid source cannot spawn") })
            assertFalse(MimicChallenge.active(a.casket))
        }
        verify(exactly=0) { a.allocator.allocate(any(),any(),any()) }
    }
    @Test fun `all256copied chunks keep source relative coordinates rotation and planes in the relocated square`() {
        val chunks=MimicEncounter.sourceChunks(4160)
        assertEquals(1,chunks.regionSize);assertEquals(256,chunks.values.size)
        for(x in 0..7) for(z in 0..7) for(plane in 0..3) {
            val key=gg.rsmod.game.model.instance.InstancedChunkSet.getCoordinates(x,z,plane)
            val chunk=assertNotNull(chunks.values[key])
            assertEquals(Tile(1024+x*8,4096+z*8,plane),Tile.fromRotatedHash(chunk.packed))
            assertEquals(0,chunk.rot)
        }
        assertFailsWith<IllegalArgumentException> { MimicEncounter.sourceChunks(10819) }
    }

    @Test fun `refused passive spawn releases the room without a token failure or teleport`() {
        val a=account()
        assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->null})
        assertFalse(MimicChallenge.active(a.casket))
        assertEquals(0,MimicChallenge.failures(a.casket))
        assertNull(MimicEncounter.session(a.player))
        assertNull(a.player.attr[MimicEncounter.SAVED_INSIDE])
        verify(exactly=1) { a.allocator.release(a.world,a.map) }
        verify(exactly=0) { a.player.teleportTo(any()) }
    }

    @Test fun `an arena import alone cannot activate incomplete combat or spend an attempt`() {
        val a=account()
        assertFalse(MimicCombat.ENCOUNTER_IMPLEMENTED)
        for (assets in listOf(cacheReady.copy(combatImplemented=false), cacheReady.copy(candyChance=null),
                cacheReady.copy(impactDelay=null), cacheReady.copy(stepChance=null), cacheReady.copy(candyChance=Double.NaN))) {
            assertFalse(MimicEncounter.enter(a.player,a.casket,assets){_,_->error("incomplete encounter cannot spawn")})
            assertFalse(MimicChallenge.active(a.casket))
            assertEquals(0,MimicChallenge.failures(a.casket))
        }
        verify(exactly=0) { a.allocator.allocate(any(),any(),any()) }
    }

    @Test fun `missing arena banked casket and an outstanding attempt do not allocate or spend another attempt`() {
        val a=account()
        assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady.copy(imported=false)){_,_->error("unapplied")})
        a.player.inventory[0]=null
        assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->error("banked")})
        a.player.inventory[0]=a.casket
        val token=assertNotNull(MimicChallenge.begin(a.casket))
        assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->error("already active")})
        assertEquals(0,MimicChallenge.failures(a.casket))
        verify(exactly=0) { a.allocator.allocate(any(),any(),any()) }
        assertTrue(MimicChallenge.settle(a.casket,token,false))
    }

    @Test fun `spawn callback cannot replace the casket or kill the player before attempt publication`() {
        for(dead in listOf(false,true)) {
            val a=account()
            assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->
                if(dead) every { a.player.isDead() } returns true else a.player.inventory[0]=Item(a.casket)
                a.idle
            })
            assertFalse(MimicChallenge.active(a.casket))
            assertEquals(0,MimicChallenge.failures(a.casket))
            verify(exactly=1) { a.world.remove(a.idle) }
            verify(exactly=1) { a.allocator.release(a.world,a.map) }
            verify(exactly=0) { a.player.teleportTo(any()) }
        }
    }

    @Test fun `successful entry publishes exactly one attempt after spawn and duplicate entry cannot allocate again`() {
        val a=account();var spawned=0
        val spawn={_:Player,_:InstancedMap->
            assertFalse(MimicChallenge.active(a.casket))
            assertNull(MimicEncounter.session(a.player))
            spawned++;a.idle
        }
        assertTrue(MimicEncounter.enter(a.player,a.casket,cacheReady,spawn))
        assertEquals(1,spawned)
        assertTrue(MimicChallenge.active(a.casket))
        assertSame(a.idle,MimicEncounter.session(a.player)?.boss)
        assertEquals(true,a.player.attr[MimicEncounter.SAVED_INSIDE])
        assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->error("duplicate")})
        verify(exactly=1) { a.allocator.allocate(any(),any(),any()) }
        verify(exactly=1) { a.player.teleportTo(Tile(6431,6426,1)) }
        MimicEncounter.leave(a.player,moveOutside=false)
        MimicEncounter.leave(a.player,moveOutside=false)
        assertEquals(1,MimicChallenge.failures(a.casket))
        verify(exactly=1) { a.allocator.release(a.world,a.map) }
    }

    @Test fun `refused fighting spawn retains the passive actor and attempt for retry`() {
        val a=account()
        assertTrue(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->a.idle})
        repeat(5) { MimicEncounter.challenge(a.player,a.idle){null} }
        assertSame(a.idle,MimicEncounter.session(a.player)?.boss)
        assertTrue(MimicChallenge.active(a.casket))
        assertEquals(0,MimicChallenge.failures(a.casket))
        verify(exactly=0) { a.world.remove(a.idle) }
    }

    @Test fun `spawn hooks cannot recursively enter before the first token is published`() {
        val a=account()
        assertTrue(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->
            assertFalse(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->error("recursive entry")})
            a.idle
        })
        verify(exactly=1) { a.allocator.allocate(any(),any(),any()) }
        assertTrue(MimicChallenge.active(a.casket))
        assertEquals(0,MimicChallenge.failures(a.casket))
    }

    @Test fun `another account cannot challenge the encounter or settle its attempt`() {
        val first=account();val other=account()
        assertTrue(MimicEncounter.enter(first.player,first.casket,cacheReady){_,_->first.idle})
        MimicEncounter.challenge(other.player,first.idle){error("other account")}
        MimicEncounter.leave(other.player)
        assertTrue(MimicChallenge.active(first.casket))
        assertEquals(0,MimicChallenge.failures(first.casket))
        assertNull(MimicEncounter.session(other.player))
        verify(exactly=0) { other.allocator.allocate(any(),any(),any()) }
    }
    @Test fun `instance multi registration is private repeat safe and removed on a plane escape`() {
        val a=account()
        assertTrue(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->a.idle})
        val plugins=a.world.plugins
        verify(exactly=1) { plugins.addMultiCombatRegion((100 shl 8) or 100) }
        every { a.player.tile } returns Tile(6410,6410,1)
        assertTrue(MimicEncounter.inArena(a.player));assertTrue(MimicEncounter.contains(a.player.tile))
        every { a.player.tile } returns Tile(6410,6410,0)
        assertFalse(MimicEncounter.inArena(a.player));assertFalse(MimicEncounter.contains(a.player.tile))
        MimicEncounter.cycle(a.world);MimicEncounter.cycle(a.world)
        assertNull(MimicEncounter.session(a.player));assertEquals(1,MimicChallenge.failures(a.casket))
        verify(exactly=1) { plugins.removeMultiCombatRegion((100 shl 8) or 100) }
        verify(exactly=1) { a.allocator.release(a.world,a.map) }
    }
    @Test fun `leaving does not remove a preexisting multi mark`() {
        val a=account();val region=(100 shl 8) or 100
        every { a.world.getMultiCombatRegions() } returns setOf(region)
        assertTrue(MimicEncounter.enter(a.player,a.casket,cacheReady){_,_->a.idle})
        MimicEncounter.leave(a.player,moveOutside=false)
        val plugins=a.world.plugins
        verify(exactly=0) { plugins.addMultiCombatRegion(region) }
        verify(exactly=0) { plugins.removeMultiCombatRegion(region) }
    }
}
