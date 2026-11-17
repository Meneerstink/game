package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class Dmm2InteractionTests {
    private val f = DeadmanPlayerFixture()
    private var position = Tile(3200, 3200)
    private var visible = true
    private val wizard = mockk<Npc>(relaxed = true)
    private val nigel = mockk<Npc>(relaxed = true)
    init {
        Dmm2Ids.install(listOf("npc\t6516\t1600", "npc\t6485\t1601", "npc\t8391\t1602"))
        every { f.player.tile } answers { position }
        every { wizard.id } returns 1600; every { wizard.tile } returns Tile(3200, 3201); every { wizard.isSpawned() } returns true
        every { nigel.id } returns 1602; every { nigel.tile } returns Tile(3200, 3201); every { nigel.isSpawned() } returns true
        Dmm2Interactions.safeUi = object : Dmm2Interactions.SafeUi {
            override fun open(player: gg.rsmod.game.model.entity.Player, items: List<Item?>) {}
            override fun visible(player: gg.rsmod.game.model.entity.Player) = visible
            override fun refresh(player: gg.rsmod.game.model.entity.Player, items: List<Item?>) {}
        }
        Dmm2Interactions.rewardUi = object : Dmm2Interactions.RewardUi {
            override fun open(player: gg.rsmod.game.model.entity.Player, store: NigelRewardStore) {}
            override fun visible(player: gg.rsmod.game.model.entity.Player) = visible
            override fun refresh(player: gg.rsmod.game.model.entity.Player, store: NigelRewardStore) {}
        }
    }
    @Test fun `safe packets require correct npc open widget and continued proximity`() {
        assertFalse(Dmm2Interactions.openSafe(f.player, nigel))
        assertEquals(SafeDepositBox.Status.NOT_OPEN, Dmm2Interactions.deposit(f.player, 0, 10, 1).status)
        assertTrue(Dmm2Interactions.openSafe(f.player, wizard))
        position = Tile(3300, 3300)
        assertEquals(SafeDepositBox.Status.NOT_OPEN, Dmm2Interactions.withdraw(f.player, 0, 10).status)
        position = Tile(3200, 3200)
        assertTrue(Dmm2Interactions.openSafe(f.player, wizard)); visible = false
        assertEquals(SafeDepositBox.Status.NOT_OPEN, Dmm2Interactions.deposit(f.player, 0, 10, 1).status)
    }
    @Test fun `reward packets cannot invent shop access or continue after close teleport or npc despawn`() {
        val rewards = NigelRewardStore.Reward.values().associateWith { 1700 + it.ordinal }
        rewards.values.forEach { f.define(it) }
        NigelRewardStore.install(NigelRewardStore.Contract(rewards, emptyList(), null))
        f.attr[gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Currency.DEADMAN.attr] = 10_000
        assertEquals(NigelRewardStore.Result.NOT_READY, Dmm2Interactions.buy(f.player, NigelRewardStore.Reward.RUG, 1))
        assertFalse(Dmm2Interactions.openRewards(f.player, wizard)); assertTrue(Dmm2Interactions.openRewards(f.player, nigel))
        assertEquals(NigelRewardStore.Result.OK, Dmm2Interactions.buy(f.player, NigelRewardStore.Reward.RUG, 1))
        assertEquals(7000, NigelRewardStore.balance(f.player))
        visible = false
        assertEquals(NigelRewardStore.Result.NOT_READY, Dmm2Interactions.buy(f.player, NigelRewardStore.Reward.RUG, 1))
        visible = true; assertTrue(Dmm2Interactions.openRewards(f.player, nigel)); every { nigel.isSpawned() } returns false
        assertEquals(NigelRewardStore.Result.NOT_READY, Dmm2Interactions.buy(f.player, NigelRewardStore.Reward.RUG, 1))
        assertEquals(7000, NigelRewardStore.balance(f.player))
    }
}
