package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.*
import io.mockk.*
import kotlin.test.*

class TreasureTrailWizardTests {
    private val accounts=mutableListOf<Player>()
    @AfterTest fun cleanup() { accounts.forEach { TreasureTrailGuardians.remove(it) };unmockkAll() }
    private fun account():Pair<Player,TreasureTrailSourceStep> {
        mockkObject(ClueScrollManager)
        val row=TreasureTrailStepCatalogue.current.rows.first { it.challenge==TreasureTrailStepCatalogue.Challenge.GUARD && it.guards==listOf(2955) }
        val step=TreasureTrailSourceStep(row)
        val defs=mockk<DefinitionSet>();every { defs.get(ItemDef::class.java,any()) } answers { ItemDef(secondArg()) }
        val world=mockk<World>(relaxed=true);every { world.definitions } returns defs
        val p=mockk<Player>(relaxed=true);every { p.world } returns world;every { p.attr } returns AttributeMap()
        every { p.isOnline } returns true;every { p.tile } returns row.points.first()
        every { p.inventory } returns ItemContainer(defs,28,ContainerStackType.NORMAL)
        p.inventory[0]=Item(ClueScrollTier.HARD.scrollId).putAttr(ItemAttribute.CLUE_STEP_KEY,step.persistenceId)
        every { ClueScrollManager.activeStep(p,any()) } returns null
        every { ClueScrollManager.activeStep(p,ClueScrollTier.HARD) } returns step
        every { ClueScrollManager.progress(p,any()) } returns 1
        every { ClueScrollManager.advance(p,any(),any(),any(),any()) } returns true
        accounts+=p;return p to step
    }
    @Test fun `both native actors use Wiki HP stats speed XP and separate melee and magic maxima`() {
        assertEquals(setOf(2954,2955),TreasureTrailWizards.profiles.map { it.source }.toSet())
        val sara=TreasureTrailWizards.profiles.single { it.source==2955 }
        val zam=TreasureTrailWizards.profiles.single { it.source==2954 }
        val s=TreasureTrailWizards.definition(sara);val z=TreasureTrailWizards.definition(zam)
        assertEquals(120,s.lifepoints);assertEquals(76,z.lifepoints)
        assertEquals(listOf(100,80,80,80,1),s.stats)
        assertEquals(listOf(24,24,24,80,1),z.stats)
        assertEquals(1.025,s.xpMultiplier);assertEquals(1.0,z.xpMultiplier)
        assertEquals(4,s.attackSpeed);assertEquals(4,z.attackSpeed)
        val sr=TreasureTrailWizards.attackRow(sara);val zr=TreasureTrailWizards.attackRow(zam)
        assertEquals(listOf("melee","magic"),sr.attacks.map { it.id })
        assertEquals(listOf(140,200),sr.attacks.map { it.hits.single().max })
        assertEquals(60,sr.attacks.first().poison)
        assertEquals(listOf("magic"),zr.attacks.map { it.id })
        assertEquals(0,zr.attacks.single().poison)
        assertEquals(200,zr.attacks.single().hits.single().max)
        assertEquals(3159,sr.attacks.last().impactGfx.single().id)
        assertEquals(3161,zr.attacks.single().impactGfx.single().id)
        assertTrue(sr.attacks.all { it.projectiles.isEmpty() }) // God spells have no projectile.
    }
    @Test fun `Saradomin switches against melee prayer only inside melee reach and Zamorak never melees`() {
        for(reach in listOf(false,true))for(prayer in listOf(false,true)) {
            assertEquals(reach && !prayer,TreasureTrailWizards.melee(2955,reach,prayer))
            assertFalse(TreasureTrailWizards.melee(2954,reach,prayer))
        }
    }
    @Test fun `wrong NPC variant or missing god-spell graphic cannot activate a coordinate fight`() {
        val defs=mockk<DefinitionSet>()
        every { defs.getNullable(AnimDef::class.java,any()) } answers { AnimDef(secondArg()) }
        every { defs.getNullable(SpotAnimDef::class.java,any()) } answers { SpotAnimDef(secondArg()) }
        every { defs.getNullable(NpcDef::class.java,1264) } returns NpcDef(1264).also {
            it.name="Saradomin wizard";it.combatLevel=108;it.size=1;it.options[1]="Attack"
        }
        val world=mockk<World>();every { world.definitions } returns defs
        assertEquals(1264,TreasureTrailWizards.actor(world,2955))
        assertNull(TreasureTrailWizards.actor(world,7310))
        every { defs.getNullable(SpotAnimDef::class.java,3159) } returns null
        assertNull(TreasureTrailWizards.actor(world,2955))
        every { defs.getNullable(SpotAnimDef::class.java,3159) } returns SpotAnimDef(3159)
        defs.getNullable(NpcDef::class.java,1264)!!.combatLevel=109
        assertNull(TreasureTrailWizards.actor(world,2955))
    }
    @Test fun `first correct dig fights once exact wizard death only unlocks a second correct dig`() {
        val (p,step)=account()
        val npc=mockk<Npc>(relaxed=true);every { npc.world } returns p.world;every { npc.attr } returns AttributeMap()
        every { npc.isSpawned() } returns true
        var count=0;val spawn={ _:Player, _:List<Int> ->count++;listOf(npc) }
        repeat(3) { assertTrue(TreasureTrailWizards.dig(p,spawn)) };assertEquals(1,count)
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
        every { npc.isDead() } returns true
        assertTrue(TreasureTrailGuardians.killed(npc));assertFalse(TreasureTrailGuardians.killed(npc))
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
        every { p.tile } returns Tile(1,1)
        assertFalse(TreasureTrailWizards.dig(p,spawn))
        every { p.tile } returns step.source.points.first()
        assertTrue(TreasureTrailWizards.dig(p,spawn));assertEquals(1,count)
        val scroll=p.inventory[0]
        verify(exactly=1) { ClueScrollManager.advance(p,ClueScrollTier.HARD,scroll,1,emptyList()) }
    }
    @Test fun `refused spawns and banked scroll cannot earn a clue or leave a live fight`() {
        val (p,_)=account();assertTrue(TreasureTrailWizards.dig(p) { _,_->null })
        assertNull(TreasureTrailGuardians.session(p))
        val npc=mockk<Npc>(relaxed=true);every { npc.world } returns p.world;every { npc.attr } returns AttributeMap()
        every { npc.isSpawned() } returns true
        assertTrue(TreasureTrailWizards.dig(p) { _,_->listOf(npc) })
        p.inventory[0]=null;TreasureTrailGuardians.cycle()
        assertNull(TreasureTrailGuardians.session(p))
        every { npc.isDead() } returns true
        assertFalse(TreasureTrailGuardians.killed(npc))
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
    }
}
