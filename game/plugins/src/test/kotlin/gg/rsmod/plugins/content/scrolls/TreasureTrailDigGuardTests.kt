package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import kotlin.test.*

class TreasureTrailDigGuardTests {
    private val accounts=mutableListOf<Player>()
    @BeforeTest fun setup() {
        mockkObject(ClueScrollManager,ClueItems)
        every { ClueItems.local(19835) } returns 30001
    }
    @AfterTest fun cleanup() { accounts.forEach(TreasureTrailGuardians::remove);unmockkAll() }
    private fun account(hotcold: Boolean=false):Pair<Player,TreasureTrailSourceStep> {
        val row=TreasureTrailStepCatalogue.current.rows.first {
            if(hotcold)it.key=="master:jorral" else it.tier==ClueScrollTier.MASTER && it.challenge==TreasureTrailStepCatalogue.Challenge.GUARD
        }
        val step=TreasureTrailSourceStep(row)
        val defs=mockk<DefinitionSet>();every { defs.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()) }
        val world=mockk<World>(relaxed=true);every { world.definitions } returns defs
        val p=mockk<Player>(relaxed=true);every { p.world } returns world;every { p.isOnline } returns true
        every { p.inventory } returns ItemContainer(defs,28,ContainerStackType.NORMAL)
        every { p.tile } returns if(hotcold)Tile(3031,3304) else row.points.single()
        p.inventory[0]=Item(30001).putAttr(ItemAttribute.CLUE_STEP_KEY,row.id)
        every { ClueScrollManager.activeStep(p,any()) } returns null
        every { ClueScrollManager.activeStep(p,ClueScrollTier.MASTER) } returns step
        every { ClueScrollManager.progress(p,ClueScrollTier.MASTER) } returns 1
        every { ClueScrollManager.advance(p,any(),any(),any(),any()) } returns true
        accounts+=p;return p to step
    }
    private fun actor(p:Player):Npc {
        val world=p.world
        return mockk<Npc>(relaxed=true).also { npc ->
            every { npc.world } returns world;every { npc.isSpawned() } returns true
        }
    }
    @Test fun `all six remaining variants preserve Wiki stats max hits bonuses styles and real HP`() {
        val expected=mapOf(6587 to (132 to 10),6588 to (130 to 13),7307 to (80 to 18),7308 to (80 to 23),
            7309 to (80 to 36),7310 to (150 to 16))
        for((id,hits) in expected) {
            val p=ClueGuardianProfiles.all.single { it.source==id }
            val def=ClueGuardianProfiles.definition(p,1,2,3)
            assertEquals(hits.first,def.lifepoints);assertEquals(hits.second,p.max)
            assertEquals(p.stats,def.stats);assertEquals(p.speed,def.attackSpeed)
            assertEquals(1+p.xp/100,def.xpMultiplier)
        }
        assertEquals("Typeless Magic",ClueGuardianProfiles.all.single { it.source==7310 }.style)
        assertEquals(145,ClueGuardianProfiles.all.single { it.source==8637 }.bonuses[2])
    }
    @Test fun `master coordinate kills only its own mage and needs a second correct dig`() {
        val(p,step)=account();val a=actor(p);var spawns=0
        val spawn={ _:Player,ids:List<Int> ->assertEquals(listOf(7310),ids);spawns++;listOf(a) }
        repeat(3) { assertTrue(TreasureTrailDigGuards.dig(p,spawn=spawn)) };assertEquals(1,spawns)
        every { a.isDead() } returns true;assertTrue(TreasureTrailGuardians.killed(a))
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
        every { p.tile } returns Tile(1,1);assertFalse(TreasureTrailDigGuards.dig(p,spawn=spawn))
        every { p.tile } returns step.source.points.single();assertTrue(TreasureTrailDigGuards.dig(p,spawn=spawn))
        assertEquals(1,spawns)
        val scroll=p.inventory[0];verify(exactly=1) { ClueScrollManager.advance(p,ClueScrollTier.MASTER,scroll,1,emptyList()) }
    }
    @Test fun `hotcold target survives the whole ancient trio and never accepts a partial victory`() {
        val(p,step)=account(true)
        val scroll=p.inventory[0]!!;scroll.putAttr(ItemAttribute.CLUE_TASK_KEY,218)
        val actors=List(3) { actor(p) };var spawns=0
        val spawn={ _:Player,ids:List<Int> ->assertEquals(listOf(7307,7308,7309),ids);spawns++;actors }
        assertTrue(TreasureTrailDigGuards.dig(p,spawn=spawn))
        actors.take(2).forEach { every { it.isDead() } returns true;assertTrue(TreasureTrailGuardians.killed(it)) }
        assertFalse(TreasureTrailGuardians.ready(p,step));assertTrue(TreasureTrailDigGuards.dig(p,spawn=spawn));assertEquals(1,spawns)
        every { actors.last().isDead() } returns true;assertTrue(TreasureTrailGuardians.killed(actors.last()))
        assertTrue(TreasureTrailGuardians.ready(p,step));assertEquals(218,scroll.getAttr(ItemAttribute.CLUE_TASK_KEY))
        every { p.tile } returns Tile(3036,3304);assertFalse(TreasureTrailDigGuards.dig(p,spawn=spawn))
        every { p.tile } returns Tile(3035,3308);assertTrue(TreasureTrailDigGuards.dig(p,spawn=spawn))
        verify(exactly=1) { ClueScrollManager.advance(p,ClueScrollTier.MASTER,scroll,1,emptyList()) }
    }
    @Test fun `hotcold sources cannot replace single mage with trio or choose a new saved target`() {
        val(p,step)=account(true);val a=actor(p)
        p.inventory[0]!!.putAttr(ItemAttribute.CLUE_TASK_KEY,216)
        every { p.tile } returns Tile(3033,3438)
        assertEquals(TreasureTrailGuardians.Result.UNAVAILABLE,
            TreasureTrailGuardians.attempt(p,step,listOf(7307,7308,7309)) { _,_->error("unlisted group") })
        assertEquals(TreasureTrailGuardians.Result.FIGHTING,
            TreasureTrailGuardians.attempt(p,step,listOf(7310)) { _,_->listOf(a) })
        p.inventory[0]=Item(30001).putAttr(ItemAttribute.CLUE_STEP_KEY,step.persistenceId).putAttr(ItemAttribute.CLUE_TASK_KEY,218)
        TreasureTrailGuardians.cycle();assertNull(TreasureTrailGuardians.session(p))
        every { a.isDead() } returns true;assertFalse(TreasureTrailGuardians.killed(a))
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
    }
}
