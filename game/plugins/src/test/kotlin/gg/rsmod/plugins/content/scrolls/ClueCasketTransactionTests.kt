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
}
