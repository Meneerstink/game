package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.*
import kotlin.test.*

class EarlyDeathCollectionTests {
    private val value = ItemRiskValueProvider { if (it == 100) 1_000_000L else 50_000L }
    private fun player(officeSlots: Int = 4, coins: Int = 200_000): Player {
        val defs = mockk<DefinitionSet>()
        every { defs.getNullable(ItemDef::class.java, any()) } answers {
            ItemDef(secondArg()).apply { name = "test item"; stacks = id == Items.COINS_995 }
        }
        every { defs.get(ItemDef::class.java, any()) } answers { defs.getNullable(ItemDef::class.java, secondArg())!! }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        return mockk<Player>(relaxed = true).also { p ->
            every { p.world } returns world
            every { p.attr } returns AttributeMap()
            every { p.tile } returns Tile(3200, 3800)
            every { p.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
            every { p.bank } returns ItemContainer(defs, 8, ContainerStackType.STACK)
            every { p.gravestone } returns ItemContainer(defs, 4, ContainerStackType.NORMAL)
            every { p.deathRecovery } returns ItemContainer(defs, officeSlots, ContainerStackType.NORMAL)
            p.bank[0] = Item(Items.COINS_995, coins)
            p.attr[GRAVESTONE_TILE_ATTR] = Tile(3200, 3800).as30BitInteger
            p.attr[GRAVESTONE_TICKS_ATTR] = 1500
            p.gravestone[0] = Item(100).putAttr(ItemAttribute.BROKEN, 2)
        }
    }

    @Test fun `early collection pays fifteen percent exactly once and the subsequent office retrieve is free`() {
        val p = player()
        val quote = EarlyDeathCollection.quote(p, value)!!
        assertEquals(150_000L, quote.fee)
        assertEquals(EarlyDeathCollection.Outcome.COLLECTED, EarlyDeathCollection.collect(p, quote))
        assertEquals(50_000, p.bank.getItemCount(Items.COINS_995))
        assertTrue(p.gravestone.isEmpty)
        assertFalse(Gravestone.exists(p))
        assertEquals(2, p.deathRecovery[0]!!.getAttr(ItemAttribute.BROKEN))
        assertEquals(0L, DeathFees.officeFee(p.deathRecovery[0]!!, 1, value))
        assertEquals(EarlyDeathCollection.Outcome.STALE, EarlyDeathCollection.collect(p, quote))
        val result = DeathsOffice.retrieve(p, 0, 1, value) as OfficeRetrieveOutcome.Retrieved
        assertEquals(0L, result.fee)
        assertEquals(50_000, p.bank.getItemCount(Items.COINS_995))
        assertEquals(2, p.inventory[0]!!.getAttr(ItemAttribute.BROKEN))
    }

    @Test fun `insufficient money full office stale contents and expired grave preserve every item and coin`() {
        for (reason in listOf("money", "room", "changed", "expired", "new death")) {
            val p = player(officeSlots = 1, coins = if (reason == "money") 1 else 200_000)
            val q = EarlyDeathCollection.quote(p, value)!!
            when (reason) {
                "room" -> p.deathRecovery[0] = Item(200)
                "changed" -> p.gravestone[0] = Item(101)
                "expired" -> p.attr[GRAVESTONE_TICKS_ATTR] = 0
                "new death" -> p.attr[Gravestone.GENERATION] = 1L
            }
            val before = p.bank.getItemCount(Items.COINS_995)
            assertNotEquals(EarlyDeathCollection.Outcome.COLLECTED, EarlyDeathCollection.collect(p, q), reason)
            assertEquals(before, p.bank.getItemCount(Items.COINS_995), reason)
            assertFalse(p.gravestone.isEmpty, reason)
        }
    }

    @Test fun `expired office fees stay five percent and early collection uses the same free threshold`() {
        assertEquals(50_000L, DeathFees.officeFee(Item(100), 1, value))
        assertEquals(0L, DeathFees.earlyCollectionFee(listOf(Item(101)), value))
    }
}
