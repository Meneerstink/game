package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.DropTable
import gg.rsmod.plugins.content.drops.DropEntry
import gg.rsmod.plugins.content.drops.TableBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.*
import kotlin.random.Random

class ClueDropOwnershipTests {
    @Test fun `replacing legacy clue slots preserves every non-clue slot and nested table denominator`() {
        val tier = ClueScrollTier.HARD
        val nested = DropTable("nested", arrayOf(
            TableBuilder.Entry(2, DropEntry.ItemDrop(gg.rsmod.game.model.item.Item(tier.scrollId))),
            TableBuilder.Entry(5, DropEntry.ItemDrop(gg.rsmod.game.model.item.Item(995, 10)))
        ), 128)
        val table = DropTable("main", arrayOf(TableBuilder.Entry(8, DropEntry.TableDrop(nested, "existing label"))), 256)
        val replaced = ClueNpcSources.strip(listOf(table), setOf(tier)).single()
        val child = (replaced.entries.single().drop as DropEntry.TableDrop)
        assertEquals("existing label", child.label)
        assertEquals(256, replaced.totalSlots)
        assertEquals(128, child.table.totalSlots)
        assertEquals(listOf(2, 5), child.table.entries.map { it.index })
        assertSame(DropEntry.NothingDrop, child.table.entries.first().drop)
        assertEquals(10, (child.table.entries.last().drop as DropEntry.ItemDrop).item.amount)
        assertEquals(3.0 / 128, DropTableFactory.lineChances(child.table, 1.0)[1])
        assertTrue(nested.entries.first().drop is DropEntry.ItemDrop) // source is not mutated
    }

    @Test fun `skilling formulas use visible level independent tier rolls and no beginner or master`() {
        assertEquals(6335, ClueSkilling.denominator(741600, 99, ClueScrollTier.EASY))
        assertEquals(7453, ClueSkilling.denominator(741600, 99, ClueScrollTier.MEDIUM))
        assertEquals(6149, ClueSkilling.denominator(741600, 105, ClueScrollTier.EASY))
        val always = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextDouble() = 0.0
        }
        assertEquals(listOf(ClueScrollTier.EASY, ClueScrollTier.MEDIUM, ClueScrollTier.HARD, ClueScrollTier.ELITE),
            ClueSkilling.rolls(741600, 99, { true }, always))
        assertEquals(listOf(ClueScrollTier.ELITE), ClueSkilling.rolls(741600, 99, { it == ClueScrollTier.ELITE }, always))
        assertNull(ClueSkilling.base("mining", "ESSENCE"))
        assertNull(ClueSkilling.base("woodcutting", "IVY"))
        assertEquals(72321, ClueSkilling.base("woodcutting", "MAGIC"))
        assertEquals(82243, ClueSkilling.base("fishing", "SHARK"))
        assertFailsWith<IllegalArgumentException> { ClueSkilling.denominator(741600, 99, ClueScrollTier.MASTER) }
    }

    @Test fun `impling sources match the pinned Wiki and no RS2 impling gets an invented clue rate`() {
        val sources = ClueImplings.sources
        val npcs = gg.rsmod.plugins.api.cfg.Npcs
        assertEquals(10, sources.size)
        assertEquals(ClueImplings.Source(ClueScrollTier.ELITE, 50), sources[npcs.DRAGON_IMPLING])
        assertEquals(ClueImplings.Source(ClueScrollTier.MEDIUM, 25), sources[npcs.ECLECTIC_IMPLING])
        assertNull(sources[npcs.KINGLY_IMPLING])
        assertNull(sources[npcs.SPIRIT_IMPLING])
        assertNotNull(gg.rsmod.plugins.content.skills.hunter.ImplingData.byNpc(npcs.DRAGON_IMPLING))
    }

    @Test fun `actual drop path gives boxes up to capacity without changing other loot or casket eligibility`() {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { player.bank } returns ItemContainer(definitions, 100, ContainerStackType.STACK)
        every { player.gravestone } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        every { player.deathRecovery } returns ItemContainer(definitions, 100, ContainerStackType.NORMAL)
        val tier = ClueScrollTier.EASY
        val fixture = Int.MAX_VALUE - 817 // isolated test registry key, never an NPC/cache allocation
        DropTableFactory.register({ guaranteed { obj(tier.scrollId); obj(995, quantity = 7) } }, fixture)
        fun loot() = DropTableFactory.getDrop(player, fixture)!!.associate { it.id to it.amount }
        mockkObject(ClueItems)
        try {
            val box = 20001 // isolated item definition fixture, not an allocated cache ID
            every { ClueItems.local(24362) } returns box
            assertEquals(mapOf(box to 1, 995 to 7), loot())
            player.deathRecovery.add(tier.scrollId)
            assertEquals(mapOf(box to 1, 995 to 7), loot()) // one active clue leaves one box slot
            player.bank.add(box)
            assertEquals(mapOf(995 to 7), loot())
            player.deathRecovery[0] = null
            player.inventory.add(tier.casketId)
            player.bank.add(ClueScrollTier.HARD.scrollId)
            assertEquals(mapOf(box to 1, 995 to 7), loot())
        } finally { unmockkObject(ClueItems) }
    }
}
