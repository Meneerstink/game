package gg.rsmod.plugins.content.activity.toa

import kotlin.test.*

class ToaRewardTests {
    @Test fun `chest niche reconnect and an emptied chest cannot roll twice`() {
        var rolls = 0
        var reward = ToaPersonalReward.open(null, "raid-a") { rolls++; listOf(ToaRewardItem(995, 50)) }
        reward = ToaPersonalReward.decode(reward.encode())
        assertSame(reward, ToaPersonalReward.open(reward, "raid-a") { error("reroll on niche") })
        reward = reward.withdraw { _, amount -> amount }
        reward = ToaPersonalReward.decode(reward.encode())
        assertFalse(reward.unclaimed)
        assertSame(reward, ToaPersonalReward.open(reward, "raid-a") { error("reroll after take") })
        assertEquals(1, rolls)
    }

    @Test fun `partial and full container conserve every item across relog and another claim`() {
        var reward = ToaPersonalReward("raid-a", listOf(ToaRewardItem(995, 50), ToaRewardItem(100, 2)))
        var delivered = 0
        reward = reward.withdraw { id, _ -> if (id == 995) { delivered += 30; 30 } else 0 }
        assertEquals(listOf(ToaRewardItem(995, 20), ToaRewardItem(100, 2)), reward.items)
        reward = ToaPersonalReward.decode(reward.encode())
        reward = reward.withdraw { _, amount -> delivered += amount; amount }
        assertEquals(52, delivered)
        reward.withdraw { _, _ -> error("duplicate delivery") }
        assertFalse(reward.unclaimed)
    }

    @Test fun `new raid requires forfeiture and malformed escrow never silently rerolls`() {
        val old = ToaPersonalReward("old", listOf(ToaRewardItem(995, 1)))
        assertFailsWith<IllegalStateException> { ToaPersonalReward.open(old, "new") { error("unconfirmed roll") } }
        assertFailsWith<IllegalArgumentException> { ToaPersonalReward.decode("broken") }
        assertFailsWith<IllegalArgumentException> { ToaPersonalReward.decode("id|995:-1") }
        val empty = ToaPersonalReward.open(null, "new") { emptyList() }
        assertSame(empty, ToaPersonalReward.open(empty, "new") { error("empty reroll") })
    }
}
