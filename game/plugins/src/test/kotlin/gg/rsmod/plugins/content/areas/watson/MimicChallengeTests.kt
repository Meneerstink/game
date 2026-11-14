package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.scrolls.ClueScrollTier
import kotlin.test.*

class MimicChallengeTests {
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
