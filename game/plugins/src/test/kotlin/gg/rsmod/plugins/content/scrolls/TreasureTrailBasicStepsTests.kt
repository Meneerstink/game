package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import io.mockk.*
import kotlin.test.*

class TreasureTrailBasicStepsTests {
    private val catalogue = TreasureTrailStepCatalogue.current.rows
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    @BeforeTest fun setup() {
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()).also { it.stacks = false } }
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        mockkObject(ClueScrollManager)
    }
    @AfterTest fun cleanup() { unmockkObject(ClueScrollManager) }
    private fun account(row: TreasureTrailStepCatalogue.Step): Pair<Player, TreasureTrailSourceStep> {
        val step = TreasureTrailSourceStep(row)
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { p.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { p.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { ClueScrollManager.activeStep(p, any()) } returns null
        every { ClueScrollManager.activeStep(p, row.tier) } returns step
        every { ClueScrollManager.progress(p, row.tier) } returns 0
        p.inventory[0] = Item(row.tier.scrollId).putAttr(ItemAttribute.CLUE_STEP_KEY, row.id)
        every { ClueScrollManager.advance(p, row.tier, any(), 0, emptyList()) } returns true
        return p to step
    }

    @Test fun `source cryptic and guard-free coordinates dig only on their exact tile and plane`() {
        for (row in catalogue.filter { it.action == TreasureTrailStepCatalogue.Action.DIG && it.kind != "MAP" &&
            !it.tornPart && it.challenge == TreasureTrailStepCatalogue.Challenge.NONE }) {
            val (p, _) = account(row); val tile = row.points.single()
            every { p.tile } returns Tile(tile.x+1, tile.z, tile.height)
            assertFalse(TreasureTrailBasicSteps.dig(p))
            every { p.tile } returns Tile(tile.x, tile.z, (tile.height+1)%4)
            assertFalse(TreasureTrailBasicSteps.dig(p))
            every { p.tile } returns tile
            assertTrue(TreasureTrailBasicSteps.dig(p))
            verify(exactly=1) { ClueScrollManager.advance(p, row.tier, any(), 0, emptyList()) }
        }
    }

    @Test fun `digging a guardian step cannot advance without its fight`() {
        val row = catalogue.first { it.challenge == TreasureTrailStepCatalogue.Challenge.GUARD }
        val (p, _) = account(row)
        every { p.tile } returns row.points.single()
        assertFalse(TreasureTrailBasicSteps.dig(p))
        verify(exactly=0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
    }

    @Test fun `wrong corpse identity cannot satisfy the skeleton kill clue`() {
        val row = catalogue.single { it.action == TreasureTrailStepCatalogue.Action.KILL }
        val (p, _) = account(row)
        val npc = mockk<Npc>(relaxed=true)
        every { npc.world } returns world
        every { npc.id } returns row.existingNpcs.first()
        val def = NpcDef(npc.id).also { it.name = "Wrong NPC" }
        every { definitions.get(NpcDef::class.java, any()) } returns def
        TreasureTrailBasicSteps.killed(p, npc)
        verify(exactly=0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
        def.name = row.npcName!!
        TreasureTrailBasicSteps.killed(p, npc)
        verify(exactly=1) { ClueScrollManager.advance(p, row.tier, any(), 0, emptyList()) }
    }

    @Test fun `search requires the exact source-mapped object tile and menu`() {
        val row = catalogue.single { it.key.endsWith("Search for a crate in Varrock Castle") }
        val (p, _) = account(row)
        val target = TreasureTrailSearchTargets.current.single { it.key == row.key }
        val def = ObjectDef(target.localId).also { it.name = target.name; it.options[0] = "Search" }
        every { definitions.get(ObjectDef::class.java, any()) } returns def
        val obj = mockk<GameObject>()
        every { obj.id } returns target.localId
        every { obj.tile } returns Tile(target.tile.x+1,target.tile.z,target.tile.height)
        assertFalse(TreasureTrailSearchTargets.attempt(p,obj,1))
        every { obj.tile } returns target.tile
        every { obj.id } returns target.localId+1
        assertFalse(TreasureTrailSearchTargets.attempt(p,obj,1))
        every { obj.id } returns target.localId
        assertFalse(TreasureTrailSearchTargets.attempt(p,obj,2))
        def.options[0] = "Open"
        assertFalse(TreasureTrailSearchTargets.attempt(p,obj,1))
        def.options[0] = "Search"
        assertTrue(TreasureTrailSearchTargets.attempt(p,obj,1))
        verify(exactly=1) { ClueScrollManager.advance(p,row.tier,any(),0,emptyList()) }
    }

    @Test fun `ordinary Talk-to and trades fall through without a matching held clue`() {
        val row = catalogue.single { it.npcName == "Captain Tobias" }
        val (p, _) = account(row)
        val npc = mockk<Npc>(relaxed=true)
        every { npc.world } returns world
        every { npc.id } returns row.existingNpcs.first()
        val def = NpcDef(npc.id).also {
            it.name = row.npcName!!; it.options[0] = "Talk-to"; it.options[2] = "Pay-fare"
        }
        every { definitions.get(NpcDef::class.java,any()) } returns def
        assertFalse(TreasureTrailBasicSteps.talk(p,npc,3))
        every { ClueScrollManager.activeStep(p,row.tier) } returns null
        assertFalse(TreasureTrailBasicSteps.talk(p,npc,1))
        verify(exactly=0) { ClueScrollManager.advance(p,any(),any(),any(),any()) }
    }
}
