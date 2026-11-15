package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.quests.tab.CollectionLog
import gg.rsmod.plugins.content.areas.watson.MimicCaskets
import gg.rsmod.plugins.content.areas.watson.MimicChallenge
import gg.rsmod.plugins.content.areas.watson.MimicRecovery
import io.mockk.*
import kotlin.random.Random
import kotlin.test.*

class ClueCasketTransactionTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    private val player = mockk<Player>(relaxed = true)
    private val tier = ClueScrollTier.EASY

    @BeforeTest fun setup() {
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { world.definitions } returns definitions
        every { player.world } returns world
        every { player.tile } returns Tile(3200, 3200)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { player.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { player.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { player.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        mockkObject(CollectionLog)
        every { CollectionLog.record(any(), any(), any()) } returns emptyList()
    }

    @AfterTest fun cleanup() { unmockkObject(CollectionLog) }

    @Test fun `real award consumes exactly one stacked casket and stale double click cannot spend again`() {
        val source = Item(tier.casketId, 2)
        player.inventory[0] = source
        val plan = ClueCasketRewards.Plan(tier, 2, listOf(Item(995, 3), Item(995, 4)))
        val award = assertNotNull(ClueCasketRewards.award(player, 0, source, plan))
        assertEquals(1, award.count)
        assertEquals(1, player.inventory.getItemCount(tier.casketId))
        assertEquals(7, player.inventory.getItemCount(995))
        assertNull(ClueCasketRewards.award(player, 0, source, plan))
        assertEquals(1, ClueCompletions.get(player, tier))
        verify(exactly = 1) { CollectionLog.record(player, tier.key, listOf(995 to 3, 995 to 4)) }
    }

    @Test fun `full inventory keeps every reward either inside or on private ground`() {
        val source = Item(tier.casketId)
        player.inventory[0] = source
        (1..27).forEach { player.inventory[it] = Item(1000 + it) }
        val drops = mutableListOf<GroundItem>()
        every { world.spawn(capture(drops)) } just Runs
        val result = assertNotNull(ClueCasketRewards.award(player, 0, source,
            ClueCasketRewards.Plan(tier, 2, listOf(Item(2000), Item(2001)))))
        assertEquals(0, player.inventory.getItemCount(tier.casketId))
        assertEquals(1, player.inventory.getItemCount(2000))
        assertEquals(listOf(2001), result.overflow.map { it.id })
        assertEquals(1, drops.size)
        assertEquals(1, ClueCompletions.get(player, tier))
    }

    @Test fun `wrong tier missing definition and stale source all retain the casket and count`() {
        val source = Item(tier.casketId)
        player.inventory[0] = source
        val plan = ClueCasketRewards.Plan(tier, 2, listOf(Item(995)))
        assertNull(ClueCasketRewards.award(player, 0, Item(source), plan))
        assertNull(ClueCasketRewards.award(player, 0, source, plan.copy(tier = ClueScrollTier.HARD)))
        every { definitions.getNullable(ItemDef::class.java, 995) } returns null
        assertNull(ClueCasketRewards.award(player, 0, source, plan))
        assertSame(source, player.inventory[0])
        assertEquals(0, ClueCompletions.get(player, tier))
    }

    @Test fun `unmapped reward elsewhere in a table prevents spending even when it was not selected`() {
        var lookups = 0
        val plan = ClueCasketRewards.plan(tier, Random(7), local = { lookups++; null })
        assertNull(plan)
        assertTrue(lookups > 1)
        assertEquals(0, ClueCompletions.get(player, tier))
    }

    @Test fun `Mimic IRS blocks duplicate clues and counts towards box capacity after serialization`() {
        val original = Item(tier.scrollId).putAttr(gg.rsmod.game.model.item.ItemAttribute.CLUE_STEP_KEY, 123)
        MimicRecovery.store(player, listOf(original))
        assertTrue(ClueOwnership.owns(player, tier))
        assertFalse(ClueOwnership.mayReceive(player, tier.scrollId))
        assertEquals(1, ClueContainers.count(player, tier))
        assertEquals(123, MimicRecovery.items(player).single().getAttr(gg.rsmod.game.model.item.ItemAttribute.CLUE_STEP_KEY))
        MimicRecovery.clear(player)
        assertFalse(ClueOwnership.owns(player, tier))
    }

    @Test fun `Mimic warning follows inventory possession so juggling remains possible`() {
        val mimic = MimicChallenge.create(ClueItems.local(23184)!!, ClueScrollTier.ELITE)
        MimicRecovery.store(player, listOf(mimic))
        assertFalse(MimicCaskets.held(player))
        MimicRecovery.clear(player)
        player.gravestone[0] = mimic
        assertFalse(MimicCaskets.held(player))
        player.gravestone[0] = null
        player.inventory[0] = mimic
        assertTrue(MimicCaskets.held(player))
        player.inventory[0] = null
        assertFalse(MimicCaskets.held(player))
    }

    @Test fun `opening a case unlocks once without recording a second collection item`() {
        val case = ClueContainers.cases.first()
        mockkObject(ClueItems)
        try {
            every { ClueItems.local(case.osrs) } returns 20000
            val item = Item(20000)
            player.inventory[0] = item
            assertTrue(ClueContainers.openCase(player, 0, item, case))
            assertEquals(3, ClueContainers.capacity(player, tier))
            assertNull(player.inventory[0])
            assertFalse(ClueContainers.openCase(player, 0, item, case))
            verify(exactly = 0) { CollectionLog.record(any(), any(), any()) }
        } finally { unmockkObject(ClueItems) }
    }

    @Test fun `one drop batch cannot exceed scroll box capacity`() {
        mockkObject(ClueItems)
        try {
            every { ClueItems.local(24362) } returns 20001
            player.inventory[0] = Item(tier.scrollId)
            val items = ClueAcquisition.transform(player, listOf(Item(tier.scrollId), Item(tier.scrollId), Item(995, 10)))
            assertEquals(listOf(20001, 995), items.map { it.id })
            assertEquals(10, items.last().amount)
        } finally { unmockkObject(ClueItems) }
    }

    @Test fun `guaranteed Mimic conversion suppresses ordinary and tertiary loot in the viewer`() {
        val master = ClueScrollTier.MASTER
        mockkObject(ClueItems, ClueCasketLocation, gg.rsmod.plugins.content.skills.summoning.Pets)
        try {
            every { ClueItems.local(any()) } answers { firstArg() }
            every { ClueCasketLocation.permitted(player, master) } returns true
            every { ClueCasketLocation.catalogue(player) } returns ClueRewardCatalogue.current
            every { gg.rsmod.plugins.content.skills.summoning.Pets.owns(player, "Bloodhound") } returns false
            player.attr[gg.rsmod.plugins.content.areas.watson.StrangeCasket.MIMIC_CHALLENGES] = true
            repeat(9) { ClueCompletions.opened(player, master) }
            val lines = ClueRewardView.view(player, master).lines
            assertEquals(1.0, lines.single { it.key == "clue:mimic" }.your)
            assertTrue(lines.filter { it.key != "clue:mimic" }.all { it.your == 0.0 })
            // The separate post-fight source includes the once-per-casket Bloodhound roll.
            val postFight = gg.rsmod.plugins.content.areas.watson.MimicRewardView.view(player, master)
            assertEquals(1.0 / 1000, postFight.lines.single { it.item == 19730 }.your)
            assertTrue(postFight.lines.none { it.key == "clue:mimic" })
        } finally { unmockkObject(ClueItems, ClueCasketLocation, gg.rsmod.plugins.content.skills.summoning.Pets) }
    }

    @Test fun `player dropped clue retains its identity and lasts an hour while stale confirmation cannot drop another scroll`() {
        val handler = gg.rsmod.game.message.handler.OpHeld5Handler
        val previous = handler.configureFloor
        val spawned = mutableListOf<GroundItem>()
        every { world.spawn(capture(spawned)) } just Runs
        try {
            handler.configureFloor = { ClueGroundItems.configure(it, playerDrop = true) }
            val scroll = Item(tier.scrollId).putAttr(gg.rsmod.game.model.item.ItemAttribute.CLUE_STEP_KEY, 321)
            val replacement = Item(scroll)
            player.inventory[0] = replacement
            handler.drop(player, world, scroll, 0)
            assertSame(replacement, player.inventory[0])
            assertTrue(spawned.isEmpty())
            handler.drop(player, world, replacement, 0)
            assertNull(player.inventory[0])
            assertEquals(6000, spawned.single().despawnCycles)
            assertEquals(321, spawned.single().asItem().getAttr(gg.rsmod.game.model.item.ItemAttribute.CLUE_STEP_KEY))
            val npcDrop = GroundItem(Item(tier.scrollId), player.tile, player)
            assertNull(ClueGroundItems.configure(npcDrop).despawnCycles)
            val explicitDeath = GroundItem(Item(tier.scrollId), player.tile, player).also { it.despawnCycles = 200 }
            assertEquals(200, ClueGroundItems.configure(explicitDeath, playerDrop = true).despawnCycles)
        } finally { handler.configureFloor = previous }
    }

    @Test fun `NPC viewer does not cache one account clue capacity for a second account`() {
        val viewer = gg.rsmod.plugins.content.quests.tab.DropSources
        val npc = gg.rsmod.game.fs.def.NpcDef(1).also { it.name = "Man"; it.combatLevel = 2 }
        every { definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, 1) } returns npc
        val second = mockk<Player>(relaxed = true)
        every { second.world } returns world
        every { second.tile } returns Tile(3200, 3200)
        every { second.attr } returns AttributeMap()
        every { second.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { second.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { second.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { second.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        mockkObject(ClueItems)
        try {
            every { ClueItems.local(24362) } returns 20001
            player.bank.add(20001, 2)
            viewer.reset()
            val source = gg.rsmod.plugins.content.quests.tab.DropSources.Source("npc:1", "Man", 1, intArrayOf(1), 2,
                gg.rsmod.plugins.content.quests.tab.DropSources.Group.MONSTERS,
                gg.rsmod.plugins.content.quests.tab.DropSources.Kind.NPC, 1)
            assertEquals(0.0, viewer.view(player, source, 1.0).lines.single { it.item == 20001 }.your)
            val line = viewer.view(second, source, 1.0).lines.single { it.item == 20001 }
            assertEquals(1.0 / 128, line.your)
            assertEquals(1.0 / 128, line.normal)
        } finally { viewer.reset(); unmockkObject(ClueItems) }
    }

    @Test fun `impling roll and jar viewer agree on tier chance and capacity`() {
        mockkObject(ClueItems)
        try {
            every { ClueItems.local(24362) } returns 20001
            val npc = gg.rsmod.plugins.api.cfg.Npcs.GOURMET_IMPLING
            val impling = gg.rsmod.plugins.content.skills.hunter.ImplingData.byNpc(npc)!!
            val hit = object : Random() { override fun nextBits(bitCount: Int) = 0 }
            assertEquals(20001, ClueImplings.roll(player, npc, hit)?.id)
            var line = ClueImplingView.view(player, impling).lines.single { it.item == 20001 }
            assertEquals(1.0 / 25, line.your)
            assertEquals(1.0 / 25, line.normal)
            player.bank.add(20001, 2)
            assertNull(ClueImplings.roll(player, npc, hit))
            line = ClueImplingView.view(player, impling).lines.single { it.item == 20001 }
            assertEquals(0.0, line.your)
            assertEquals(1.0 / 25, line.normal)
        } finally { unmockkObject(ClueItems) }
    }

    @Test fun `missing unselected cache definition blocks planning before any RNG or casket mutation`() {
        mockkObject(ClueItems, ClueCasketLocation)
        try {
            val medium = ClueScrollTier.MEDIUM
            every { ClueItems.local(any()) } answers { firstArg<Int>() }
            every { ClueCasketLocation.permitted(player, medium) } returns true
            every { ClueCasketLocation.catalogue(player) } returns ClueRewardCatalogue.current
            every { definitions.getNullable(ItemDef::class.java, 2577) } returns null
            val source = Item(medium.casketId)
            player.inventory[0] = source
            val forbiddenRandom = object : Random() {
                override fun nextBits(bitCount: Int): Int = error("RNG reached before complete asset validation")
            }
            assertNull(ClueCasketRewards.planForPlayer(player, medium, forbiddenRandom))
            assertSame(source, player.inventory[0])
            assertEquals(0, ClueCompletions.get(player, medium))
        } finally { unmockkObject(ClueItems, ClueCasketLocation) }
    }
}
