package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.mechanics.exchange.GePrice
import gg.rsmod.plugins.content.items.osrs.AncientCape
import io.mockk.*
import kotlin.test.*

/** Explicit local choices where published OSRS sources stop short; never claims to measure OSRS timing/value. */
class Dmm2SourcePolicyTests {
    @Test fun `avarice uses the shared GE stack value and clamps overflow without wrapping the pickup threshold`() {
        val f = DeadmanPlayerFixture()
        val drop = Item(900, 100)
        mockkObject(GePrice)
        try {
            DeadmanTrinkets.lootValue = null
            every { GePrice.stack(f.world, drop) } returns 3_000_000_000L
            assertEquals(Int.MAX_VALUE, DeadmanTrinkets.valueOfDrop(f.player, drop))
            every { GePrice.stack(f.world, drop) } returns 100_000L
            assertEquals(100_000, DeadmanTrinkets.valueOfDrop(f.player, drop))
            every { GePrice.stack(f.world, drop) } returns -1L
            assertEquals(0, DeadmanTrinkets.valueOfDrop(f.player, drop))
            DeadmanTrinkets.lootValue = { _, _ -> 777 }
            assertEquals(777, DeadmanTrinkets.valueOfDrop(f.player, drop))
        } finally { DeadmanTrinkets.lootValue = null; unmockkObject(GePrice) }
    }

    @Test fun `khopesh default uses ordinary melee launch offset with source pinned four bolt schedule`() {
        val look = object : ThunderKhopesh.Appearance {
            override val passiveDelay = 1
            override val pvmHitDelays = listOf(1, 2)
            override fun swing(player: Player, special: Boolean) {}
            override fun bolt(player: Player, tile: Tile, special: Boolean) {}
        }
        assertEquals(1, look.pvpInitialDelay)
        assertEquals(listOf(7, 9, 11, 13), ThunderKhopesh.PVP_BOLT_TICKS)
        assertEquals(60, ThunderKhopesh.SPECIAL_ENERGY)
        assertTrue(ThunderKhopesh.withinBolt(Tile(3200, 3200), Tile(3201, 3201)))
        assertFalse(ThunderKhopesh.withinBolt(Tile(3200, 3200), Tile(3202, 3200)))
    }

    @Test fun `ancient Deadman cape requires fabric and a Deadman god cape with no duplicate conversion or Nigel bypass`() {
        val f = DeadmanPlayerFixture()
        Dmm2Ids.install(listOf("item\t34607\t9003", "item\t29613\t9004", "item\t29615\t9005", "item\t29617\t9006"))
        listOf(9000, 9001, 9002, 9003, 9004, 9005, 9006).forEach { f.define(it) }
        val store = NigelRewardStore(NigelRewardStore.Contract(emptyMap(),
            listOf(NigelRewardStore.Skin(NigelRewardStore.Reward.CAPE_FABRIC, 9000, 9003)), null))
        f.attr[NigelRewardStore.Reward.CAPE_FABRIC.unlock!!] = 1
        f.inventory[0] = Item(9000)
        assertEquals(NigelRewardStore.Result.UNSUPPORTED, store.convert(f.player, 0, 9000))
        mockkObject(AncientCape)
        try {
            every { AncientCape.assets } returns AncientCape.Assets({ if (it == 34641) 9001 else if (it == 34633) 9000 else null })
            assertEquals(NigelRewardStore.Result.UNSUPPORTED, store.craftAncientCape(f.player, 0, 9000))
            f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(9002) }
            f.inventory[0] = Item(9004)
            assertEquals(NigelRewardStore.Result.ITEM_CHANGED, store.craftAncientCape(f.player, 0, 9004))
            assertEquals(1L, f.count(9004)); assertEquals(0L, f.count(9003))
            f.inventory[1] = Item(9001)
            assertEquals(NigelRewardStore.Result.OK, store.craftAncientCape(f.player, 0, 9004))
            repeat(5) { assertEquals(NigelRewardStore.Result.ITEM_CHANGED, store.craftAncientCape(f.player, 0, 9004)) }
            assertEquals(0L, f.count(9001)); assertEquals(0L, f.count(9004)); assertEquals(1L, f.count(9003))
            assertEquals(NigelRewardStore.Result.UNSUPPORTED, store.convert(f.player, 0, 9003))
            assertEquals(9000, store.baseItem(f.inventory[0]!!)!!.id)
        } finally { unmockkObject(AncientCape) }
    }
}
