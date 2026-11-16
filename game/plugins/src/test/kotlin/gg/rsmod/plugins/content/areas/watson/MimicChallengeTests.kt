package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import kotlin.test.*
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import gg.rsmod.plugins.content.scrolls.ClueItems

class MimicChallengeTests {
    @Test fun `casket conversion preserves the actual Mimic source tier and full inventory never spends a stacked casket`() {
        mockkObject(ClueItems)
        try {
            every { ClueItems.local(any()) } answers { callOriginal() }
            every { ClueItems.local(19836) } returns 20002 // synthetic DefinitionSet fixture
            for (tier in listOf(ClueScrollTier.ELITE, ClueScrollTier.MASTER)) {
                val player = recoveryPlayer()
                val stacked = Item(tier.casketId, 2)
                player.inventory[0] = stacked
                assertFalse(MimicCaskets.convert(player, 0, stacked, tier, encounterReady=true))
                assertSame(stacked, player.inventory[0]); assertEquals(2, stacked.amount)
                assertFalse(player.attr[MimicCaskets.RECEIVED] == true)
                val single = Item(tier.casketId)
                player.inventory[0] = single
                assertTrue(MimicCaskets.convert(player, 0, single, tier, encounterReady=true))
                val mimic = assertNotNull(player.inventory[0])
                assertEquals(tier, MimicChallenge.tier(mimic))
                assertNotNull(MimicChallenge.begin(mimic))
                assertFalse(MimicCaskets.convert(player, 0, single, tier, encounterReady=true))
            }
        } finally { unmockkObject(ClueItems) }
    }

    @Test fun `unavailable encounter retains the ordinary casket and its guaranteed first Mimic eligibility`() {
        val player=recoveryPlayer()
        val source=Item(ClueScrollTier.ELITE.casketId)
        player.inventory[0]=source
        assertFalse(MimicCaskets.convert(player,0,source,ClueScrollTier.ELITE))
        assertSame(source,player.inventory[0])
        assertNull(player.attr[MimicCaskets.RECEIVED])
        assertNull(source.getAttr(ItemAttribute.MIMIC_SOURCE_TIER))
    }

    private fun recoveryPlayer(): Player {
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(ItemDef::class.java, any()) } answers {
            ItemDef(secondArg()).also { it.stacks = it.id == 995 }
        }
        return mockk<Player>(relaxed = true).also { player ->
            every { player.attr } returns AttributeMap()
            every { player.inventory } returns ItemContainer(definitions, 1, ContainerStackType.NORMAL)
            every { player.bank } returns ItemContainer(definitions, 10, ContainerStackType.STACK)
        }
    }

    @Test fun `partial Mimic IRS retrieval pays once preserves item state and never shares another account storage`() {
        val player = recoveryPlayer()
        player.bank.add(995, 100000)
        val mimic = MimicChallenge.create(23729, ClueScrollTier.ELITE)
        val token = assertNotNull(MimicChallenge.begin(mimic))
        assertTrue(MimicChallenge.settle(mimic, token, won = true))
        MimicRecovery.store(player, listOf(Item(995, 50000), Item(2000).putAttr(ItemAttribute.CHARGES, 17), mimic))
        assertTrue(MimicRecovery.items(recoveryPlayer()).isEmpty())
        assertEquals(MimicRecovery.Result.RETRIEVED, MimicRecovery.retrieve(player))
        assertEquals(10000, player.bank.getItemCount(995))
        assertEquals(50000, player.inventory.getItemCount(995))
        assertEquals(2, MimicRecovery.items(player).size)
        player.inventory[0] = null
        assertEquals(MimicRecovery.Result.RETRIEVED, MimicRecovery.retrieve(player))
        assertEquals(2000, player.inventory[0]?.id)
        assertEquals(17, player.inventory[0]?.getAttr(ItemAttribute.CHARGES))
        player.inventory[0] = null
        assertEquals(MimicRecovery.Result.RETRIEVED, MimicRecovery.retrieve(player))
        assertTrue(MimicChallenge.defeated(player.inventory[0]!!))
        assertEquals(5, MimicChallenge.remaining(player.inventory[0]!!))
        assertEquals(10000, player.bank.getItemCount(995))
        assertTrue(MimicRecovery.items(player).isEmpty())
        assertEquals(MimicRecovery.Result.EMPTY, MimicRecovery.retrieve(player))
    }

    @Test fun `full inventory and insufficient funds leave Mimic IRS items and wallet unchanged`() {
        val player = recoveryPlayer()
        MimicRecovery.store(player, listOf(Item(2000)))
        assertEquals(MimicRecovery.Result.NO_COINS, MimicRecovery.retrieve(player))
        player.bank.add(995, 100000)
        player.inventory[0] = Item(2001)
        assertEquals(MimicRecovery.Result.NO_SPACE, MimicRecovery.retrieve(player))
        assertEquals(100000, player.bank.getItemCount(995))
        assertEquals(2001, player.inventory[0]?.id)
        assertEquals(2000, MimicRecovery.items(player).single().id)
        assertFalse(player.attr[MimicRecovery.PAID] == true)
    }

    @Test fun `each failed attempt costs exactly one roll across repeated exit death and logout callbacks`() {
        for ((tier, attempts) in listOf(ClueScrollTier.ELITE to 5,ClueScrollTier.MASTER to 6)) {
            val item = MimicChallenge.create(23729,tier)
            for (failed in 1..attempts) {
                val token = assertNotNull(MimicChallenge.begin(item))
                assertNull(MimicChallenge.begin(item))
                assertTrue(MimicChallenge.settle(item,token,won=false))
                assertFalse(MimicChallenge.settle(item,token,won=false))
                assertFalse(MimicChallenge.settle(item,token,won=true))
                assertEquals(attempts-failed,MimicChallenge.remaining(item))
            }
            assertNull(MimicChallenge.begin(item))
            assertEquals(0,MimicChallenge.remaining(item))
        }
    }

    @Test fun `a settled victory cannot be charged by a later death callback and copies preserve the source tier`() {
        val item = MimicChallenge.create(23729,ClueScrollTier.MASTER)
        val token = assertNotNull(MimicChallenge.begin(item))
        assertTrue(MimicChallenge.settle(item,token,won=true))
        assertFalse(MimicChallenge.settle(item,token,won=false))
        val copy = Item(item)
        assertTrue(MimicChallenge.defeated(copy))
        assertEquals(6,MimicChallenge.remaining(copy))
        assertEquals(ClueScrollTier.MASTER,MimicChallenge.tier(copy))
        assertEquals(19836,copy.getAttr(ItemAttribute.MIMIC_SOURCE_TIER))
        assertNull(MimicChallenge.begin(copy))
        assertFalse(MimicChallenge.recover(copy))
    }

    @Test fun `a saved outstanding attempt is charged once after reconnect while untagged items get no free rewards`() {
        val item = MimicChallenge.create(23729,ClueScrollTier.ELITE)
        assertNotNull(MimicChallenge.begin(item))
        val restored = Item(item)
        assertTrue(MimicChallenge.recover(restored))
        assertFalse(MimicChallenge.recover(restored))
        assertEquals(4,MimicChallenge.remaining(restored))
        assertNull(MimicChallenge.begin(Item(23729)))
        assertNull(MimicChallenge.tier(Item(995).copyAttr(item)))
        assertFailsWith<IllegalArgumentException> { MimicChallenge.create(23729,ClueScrollTier.EASY) }
    }

    @Test fun `stomp follows the sourced consecutive-tick sequence and caps at six`() {
        assertEquals(listOf(1,1,1,2,2,2,3,3,3,4,4,4,5,5,5,6,6,6,6,6),(1..20).map(MimicChallenge::stompDamage))
        assertFailsWith<IllegalArgumentException> { MimicChallenge.stompDamage(0) }
    }
}
