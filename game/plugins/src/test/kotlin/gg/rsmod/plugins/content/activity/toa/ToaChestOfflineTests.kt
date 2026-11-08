package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.toa.core.*
import io.mockk.*
import kotlin.test.*

class ToaChestOfflineTests {
    @Test fun `offline completion and loot are in the saved escrow before the last online member can release the raid`() {
        ToaChest.install()
        val raid = mockk<ToaRaid>(relaxed = true)
        val online = mockk<Player>(relaxed = true)
        val offline = mockk<Player>(relaxed = true)
        val members = listOf(online, offline)
        members.forEachIndexed { i, p ->
            every { p.uid } returns PlayerUID(i)
            every { p.attr } returns AttributeMap()
            every { raid.contains(p) } returns (p === online) // allocator.logout already moved the offline member out
        }
        every { raid.players() } returns listOf(online)
        every { raid.memberObjects() } returns members
        every { raid.roster } returns members.map { it.uid }
        every { raid.remaining } returns members.map { it.uid }.toMutableSet()
        every { raid.rewardToken } returns "offline-regression"
        every { raid.settings } returns ToaRaidSettings(raidLevel = 150)
        every { raid.progress.finished } returns true
        every { raid.points.deathless } returns true
        mockkObject(ToaLoot, ToaLootItems)
        val claimed = ToaRewards.claimed
        try {
            every { ToaLootItems.local(any()) } returns 995
            every { ToaLoot.roll(any(), any(), any(), any(), any()) } returns ToaLoot.Result(
                null, members.associate { it.uid to listOf(ToaLootDrop(995, 123)) })
            ToaChest.fill(raid)
            val saved = assertNotNull(offline.attr[ToaRewards.ESCROW])
            assertEquals(listOf(ToaRewardItem(995, 123)), ToaPersonalReward.decode(saved).items)
            assertEquals(1, offline.attr[ToaChest.COMPLETIONS])
            assertEquals(1, offline.attr[ToaChest.COMPLETIONS_NORMAL_EXPERT])
            assertEquals(1, offline.attr[ToaChest.COMPLETIONS_BY_MODE.getValue(ToaMode.NORMAL)])
            // Rejoin after the raid was released reads only persistent attributes; no raid object or pending map is needed.
            val replacement = mockk<Player>(relaxed = true)
            every { replacement.attr } returns AttributeMap()
            every { replacement.uid } returns offline.uid
            replacement.attr[ToaChest.COMPLETIONS_BY_MODE.getValue(ToaMode.ENTRY)] = 5
            // Login loaded an older save concurrently with the kill; reconcile without needing the released raid.
            ToaChest.reconcileOffline(replacement)
            ToaChest.reconcileOffline(replacement)
            assertEquals(ToaRewards.stored(offline), ToaRewards.stored(replacement))
            assertEquals(1, replacement.attr[ToaChest.COMPLETIONS])
            assertEquals(1, replacement.attr[ToaChest.COMPLETIONS_NORMAL_EXPERT])
            assertEquals(1, replacement.attr[ToaChest.COMPLETIONS_BY_MODE.getValue(ToaMode.NORMAL)])
            assertEquals(5, replacement.attr[ToaChest.COMPLETIONS_BY_MODE.getValue(ToaMode.ENTRY)], "newer mode counts must not roll back")
            assertEquals(ToaRewards.stored(online)?.items, ToaRewards.stored(offline)?.items)
        } finally { unmockkObject(ToaLoot, ToaLootItems); ToaRewards.claimed = claimed }
    }
}
