package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.quests.tab.CollectionLog
import gg.rsmod.plugins.content.quests.tab.CollectionLogPages
import kotlin.test.*

class WildernessCollectionLogTests {
    @Test fun `a boss drop cannot fill an unrelated clue or chest page but can fill its own unique page`() {
        val f = WildernessBossTestSupport.Fixture()
        val page = CollectionLogPages.Category.BOSSES
        CollectionLogPages.install(listOf(
            CollectionLogPages.Page("callisto", page, "Callisto and Artio", listOf("npc:15076", "npc:15083"), listOf(Items.VOIDWAKER_HILT)),
            CollectionLogPages.Page("scorpia", page, "Scorpia", listOf("npc:15055"), listOf(Items.ODIUM_SHARD_3)),
            CollectionLogPages.Page("elite", CollectionLogPages.Category.CLUES, "Clues", emptyList(), listOf(Items.SUPER_RESTORE_4, Items.RUNE_KITESHIELD)),
        ))
        try {
            assertEquals(emptyList(), CollectionLog.record(f.player, "npc:15076", listOf(Items.SUPER_RESTORE_4 to 4)))
            assertEquals(emptyList(), CollectionLog.record(f.player, "npc:15055", listOf(Items.RUNE_KITESHIELD to 1)))
            assertTrue(CollectionLog.entries(f.player).isEmpty())
            assertEquals(listOf(Items.VOIDWAKER_HILT), CollectionLog.record(f.player, "npc:15083", listOf(Items.VOIDWAKER_HILT to 1)))
            assertEquals(listOf(Items.ODIUM_SHARD_3), CollectionLog.record(f.player, "npc:15055", listOf(Items.ODIUM_SHARD_3 to 1)))
            assertEquals(listOf(Items.RUNE_KITESHIELD), CollectionLog.record(f.player, "elite", listOf(Items.RUNE_KITESHIELD to 1)))
        } finally { CollectionLogPages.reset() }
    }

    @Test fun `the chest guardian still logs its emblem on the Breaches page without being a breach kill-count source`() {
        val f = WildernessBossTestSupport.Fixture()
        val breach = CollectionLogPages.Page("breach", CollectionLogPages.Category.DEADMAN, "Breaches", listOf("npc:1"), listOf(Items.ODIUM_SHARD_1))
        CollectionLogPages.install(listOf(breach))
        try {
            assertEquals(listOf(Items.ODIUM_SHARD_1), CollectionLog.record(f.player, "guardian", listOf(Items.ODIUM_SHARD_1 to 1)))
            assertFalse("guardian" in breach.sources)
            assertEquals(emptyList(), CollectionLog.record(f.player, "npc:999", listOf(Items.ODIUM_SHARD_1 to 1)))
        } finally { CollectionLogPages.reset() }
    }
}
