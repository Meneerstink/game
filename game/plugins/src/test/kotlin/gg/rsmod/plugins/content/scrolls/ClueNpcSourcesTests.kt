package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.drops.*
import io.mockk.*
import java.security.SecureRandom
import kotlin.test.*

class ClueNpcSourcesTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    private val row = ClueNpcSources.rows.first()
    private val box = 20001 // isolated fixture, never a cache allocation

    private fun player(): Player = mockk<Player>(relaxed = true).also { player ->
        every { player.world } returns world
        every { player.tile } returns Tile(3200, 3200)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { player.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { player.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { player.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
    }

    @BeforeTest fun setup() {
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        every { definitions.getNullable(NpcDef::class.java, row.npc) } returns NpcDef(row.npc).also {
            it.name = row.name; it.combatLevel = row.level
        }
        mockkObject(ClueItems)
        every { ClueItems.local(any()) } answers { callOriginal() }
        every { ClueItems.local(ClueContainers.boxes.getValue(row.tier)) } returns box
    }

    @AfterTest fun cleanup() { unmockkObject(ClueItems) }

    @Test fun `NPC without a clue source keeps every ordinary roll and denominator`() {
        val npc = Int.MAX_VALUE - 819
        assertFalse(ClueNpcSources.has(npc))
        DropTableFactory.register({ main { total(8); obj(995, quantity = 7, slots = 2); obj(554, quantity = 3, slots = 3); nothing(slots = 3) } }, npc)
        val player = player()
        val built = assertNotNull(DropTableFactory.tables(player, npc))
        assertSame(built, ClueNpcSources.tables(player, npc, built))
        assertContentEquals(doubleArrayOf(2.0 / 8, 3.0 / 8, 0.0), DropTableFactory.lineChances(built.single(), 1.0))
        val old = DropTableFactory.prng
        try {
            for (pick in 0..7) {
                DropTableFactory.prng = object : SecureRandom() { override fun nextInt(bound: Int): Int {
                    assertEquals(8, bound); return pick
                } }
                val actual = DropTableFactory.getDrop(player, npc)!!.map { it.id to it.amount }
                assertEquals(when (pick) { in 0..1 -> listOf(995 to 7); in 2..4 -> listOf(554 to 3); else -> emptyList() }, actual)
            }
        } finally { DropTableFactory.prng = old }
    }

    @Test fun `clue overlay never prevents registration of ordinary bulk loot`() {
        mockkObject(ClueNpcSources)
        val npc = Int.MAX_VALUE - 820
        try {
            every { ClueNpcSources.has(npc) } returns true
            assertFalse(DropTableFactory.hasTable(npc))
            DropTableFactory.register({ guaranteed { obj(995, quantity = 17) } }, npc)
            assertTrue(DropTableFactory.hasTable(npc))
        } finally { unmockkObject(ClueNpcSources) }
    }

    @Test fun `legacy clue is removed before independent sourced roll and ordinary loot stays`() {
        val player = player()
        val fixture = Int.MAX_VALUE - 821
        mockkObject(ClueNpcSources)
        try {
            every { ClueNpcSources.has(fixture) } returns true
            every { ClueNpcSources.tables(player, fixture, any()) } answers {
                ClueNpcSources.strip(thirdArg(), setOf(row.tier))
            }
            every { ClueNpcSources.roll(player, fixture, any()) } returns listOf(Item(box))
            DropTableFactory.register({ guaranteed { obj(row.tier.scrollId); obj(995, quantity = 11) } }, fixture)
            val loot = assertNotNull(DropTableFactory.getDrop(player, fixture))
            assertEquals(listOf(995 to 11, box to 1), loot.map { it.id to it.amount })
            verify(exactly = 1) { ClueNpcSources.roll(player, fixture, any()) }
        } finally { unmockkObject(ClueNpcSources) }
    }

    @Test fun `source viewer uses exact OSRS chance and separate account capacity`() {
        val full = player(); val second = player()
        full.bank.add(box, ClueContainers.capacity(full, row.tier))
        val fullLine = ClueNpcSources.lines(full, row.npc).single { it.item == box }
        val secondLine = ClueNpcSources.lines(second, row.npc).single { it.item == box }
        assertEquals(0.0, fullLine.your)
        assertEquals(1.0 / row.denominator, fullLine.normal)
        assertEquals(fullLine.normal, secondLine.your)
        assertEquals(fullLine.normal, secondLine.normal)
    }
}
