package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DEATH_RECOVERY_EXPIRY_ATTR
import gg.rsmod.game.model.attr.LAST_LOGOUT_DATE
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.mechanics.pvp.LootKeys
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cache-free regression tests for the death-flow fixes from the 2026-09-25 audit (X-06, X-13, D-09).
 */
class DeathAuditFixTests {
    @Test
    fun `reclaim fee follows the value tiers`() {
        assertEquals(0, DeathRecoveryConfig.feeFor(0L))
        assertEquals(0, DeathRecoveryConfig.feeFor(100_000L))
        assertEquals(5_000, DeathRecoveryConfig.feeFor(100_001L))
        assertEquals(50_000, DeathRecoveryConfig.feeFor(10_000_000L))
        assertEquals(500_000, DeathRecoveryConfig.feeFor(100_000_000L))
        assertEquals(2_000_000, DeathRecoveryConfig.feeFor(200_000_000L))
    }

    @Test
    fun `loot key encoding keeps item attributes and still reads the old format`() {
        val charged = Item(4151, 1).also { it.attr[ItemAttribute.CHARGES] = 1234 }
        val decoded = LootKeys.decode(LootKeys.encode(listOf(charged, Item(995, 10))))
        assertEquals(2, decoded.size)
        assertEquals(1234, decoded[0].attr[ItemAttribute.CHARGES])
        assertEquals(10, decoded[1].amount)
        assertEquals(0, decoded[1].attr.size)

        val legacy = LootKeys.decode("4151:1,995:25")
        assertEquals(25, legacy[1].amount)
    }

    @Test
    fun `recovery deadline is pushed back by the time spent offline`() {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { player.attr } returns attrs
        attrs[DEATH_RECOVERY_EXPIRY_ATTR] = 10_000L
        attrs[LAST_LOGOUT_DATE] = 4_000L

        DeathRecoveryService.shiftForOfflineTime(player, nowMs = 9_000L)

        assertEquals(15_000L, attrs[DEATH_RECOVERY_EXPIRY_ATTR])
    }

    @Test
    fun `a batch that expired before the logout is not revived`() {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        every { player.attr } returns attrs
        attrs[DEATH_RECOVERY_EXPIRY_ATTR] = 3_000L
        attrs[LAST_LOGOUT_DATE] = 4_000L

        DeathRecoveryService.shiftForOfflineTime(player, nowMs = 9_000L)

        assertEquals(3_000L, attrs[DEATH_RECOVERY_EXPIRY_ATTR])
    }
}
