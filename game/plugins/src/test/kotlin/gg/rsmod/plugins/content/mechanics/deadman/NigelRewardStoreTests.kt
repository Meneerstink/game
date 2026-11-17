package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.mechanics.deadman.NigelRewardStore.*
import kotlin.test.*

class NigelRewardStoreTests {
    private val f = DeadmanPlayerFixture()
    private val rewardIds = Reward.values().associateWith { 800 + it.ordinal }
    private val skins = listOf(Skin(Reward.ARMAGEDDON_WEAPONS, 900, 901, setOf(900, 906)), Skin(Reward.ANNIHILATION_WEAPONS, 902, 903), Skin(Reward.CAPE_FABRIC, 904, 905))
    private val store = NigelRewardStore(Contract(rewardIds, skins, 850))
    private val points = StoreCatalogue.Currency.DEADMAN.attr
    init { (800..850).forEach { f.define(it) }; (900..906).forEach { f.define(it) }; f.attr[points] = 100_000 }

    @Test fun `all seven rewards use exact DP prices and full sellback without touching other wallets`() {
        val breach = AttributeKey<Int>("breach_points")
        f.attr[breach] = 123; f.attr[StoreCatalogue.Currency.LOYALTY.attr] = 456
        Reward.values().forEach { reward ->
            val before = NigelRewardStore.balance(f.player)
            assertEquals(Result.OK, store.buy(f.player, reward))
            assertEquals(before - reward.price, NigelRewardStore.balance(f.player))
            val slot = f.inventory.getItemIndex(rewardIds.getValue(reward), skipAttrItems = false)
            assertEquals(Result.OK, store.sellBack(f.player, slot, rewardIds.getValue(reward)))
            assertEquals(before, NigelRewardStore.balance(f.player))
        }
        assertEquals(123, f.attr[breach]); assertEquals(456, f.attr[StoreCatalogue.Currency.LOYALTY.attr])
    }

    @Test fun `buy failures negative MAX amounts and full inventory never charge or partially grant`() {
        assertEquals(Result.INVALID_AMOUNT, store.buy(f.player, Reward.RUG, -1))
        assertEquals(Result.NOT_ENOUGH_POINTS, store.buy(f.player, Reward.RUG, Int.MAX_VALUE))
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(906) }
        assertEquals(Result.NO_SPACE, store.buy(f.player, Reward.RUG))
        assertEquals(100_000, NigelRewardStore.balance(f.player))
        f.inventory[0] = null
        assertEquals(Result.NO_SPACE, store.buy(f.player, Reward.RUG, 2))
        assertNull(f.inventory[0]); assertEquals(100_000, NigelRewardStore.balance(f.player))
    }

    @Test fun `redeem consumes once persists unlock and cannot refund an unlock or consume second scroll`() {
        val id = rewardIds.getValue(Reward.ARMAGEDDON_WEAPONS)
        f.inventory[0] = Item(id); f.inventory[1] = Item(id)
        assertEquals(Result.OK, store.unlock(f.player, Reward.ARMAGEDDON_WEAPONS, 0, id))
        assertTrue(NigelRewardStore.unlocked(f.player, Reward.ARMAGEDDON_WEAPONS))
        assertEquals(Result.ALREADY_UNLOCKED, store.unlock(f.player, Reward.ARMAGEDDON_WEAPONS, 1, id))
        assertEquals(id, f.inventory[1]?.id)
        assertEquals(Result.ITEM_CHANGED, store.sellBack(f.player, 0, id))
        val persisted = f.attr.toPersistentMap()
        val loaded = AttributeMap()
        persisted.forEach { (k, v) -> loaded[AttributeKey<Int>(k)] = (v as Number).toInt() }
        assertEquals(1, loaded[Reward.ARMAGEDDON_WEAPONS.unlock!!])
    }

    @Test fun `skin conversion requires unlock and preserves every charge state in both directions without alias`() {
        skins.forEach { skin ->
            val original = Item(skin.base).putAttr(ItemAttribute.CHARGES, 731).putAttr(ItemAttribute.ATTACHED_ITEM_COUNT, 89)
            f.inventory[0] = original
            assertEquals(Result.LOCKED, store.convert(f.player, 0, skin.base))
            f.attr[skin.unlock.unlock!!] = 1
            assertEquals(Result.OK, store.convert(f.player, 0, skin.base))
            assertEquals(skin.cosmetic, f.inventory[0]?.id)
            assertEquals(original.attr, f.inventory[0]!!.attr)
            f.inventory[0]!!.putAttr(ItemAttribute.CHARGES, 700)
            assertEquals(731, original.getAttr(ItemAttribute.CHARGES))
            assertEquals(700, store.baseItem(f.inventory[0]!!)!!.getAttr(ItemAttribute.CHARGES))
            assertEquals(Result.OK, store.convert(f.player, 0, skin.cosmetic))
            assertEquals(skin.base, f.inventory[0]?.id)
            assertEquals(700, f.inventory[0]!!.getAttr(ItemAttribute.CHARGES))
        }
        // Another source colour can be applied, but removing the Deadman skin always restores the canonical colour.
        f.inventory[0] = Item(906).putAttr(ItemAttribute.CHARGES, 17)
        assertEquals(Result.OK, store.convert(f.player, 0, 906))
        assertEquals(901, f.inventory[0]?.id)
        assertEquals(Result.OK, store.convert(f.player, 0, 901))
        assertEquals(900, f.inventory[0]?.id)
        assertEquals(17, f.inventory[0]!!.getAttr(ItemAttribute.CHARGES))
    }

    @Test fun `sellback overflow stale slots modified and noted redemption preserve item and points`() {
        val id = rewardIds.getValue(Reward.RUG)
        f.inventory[0] = Item(id)
        f.attr[points] = Int.MAX_VALUE
        assertEquals(Result.POINT_OVERFLOW, store.sellBack(f.player, 0, id))
        assertEquals(id, f.inventory[0]?.id)
        assertEquals(Result.INVALID_SLOT, store.sellBack(f.player, -1, id))
        assertEquals(Result.ITEM_CHANGED, store.sellBack(f.player, 0, 999))
        f.inventory[0]!!.putAttr(ItemAttribute.CHARGES, 1)
        assertEquals(Result.NOTED_OR_MODIFIED, store.sellBack(f.player, 0, id))
        val scroll = rewardIds.getValue(Reward.CAPE_FABRIC)
        f.inventory[1] = Item(scroll)
        f.defs.getValue(scroll).noteTemplateId = 799
        assertEquals(Result.NOTED_OR_MODIFIED, store.unlock(f.player, Reward.CAPE_FABRIC, 1, scroll))
        assertFalse(NigelRewardStore.unlocked(f.player, Reward.CAPE_FABRIC))
    }

    @Test fun `house blueprint conversion replacement reclaim and selection require permanent unlocks`() {
        val id = rewardIds.getValue(Reward.BLUEPRINTS)
        assertEquals(Result.LOCKED, store.reclaimExitBlueprints(f.player))
        f.inventory[0] = Item(id)
        assertEquals(Result.UNSUPPORTED, store.unlock(f.player, Reward.BLUEPRINTS, 0, id))
        assertEquals(Result.OK, store.unlockWildernessStyle(f.player, 0, id))
        assertEquals(850, f.inventory[0]?.id)
        assertEquals(Result.OK, store.reclaimExitBlueprints(f.player))
        assertEquals(2L, f.count(850)); assertEquals(100_000, NigelRewardStore.balance(f.player))
        assertEquals(Result.LOCKED, store.selectTeleport(f.player, Teleport.ANNIHILATION))
        assertEquals(Teleport.DEFAULT, store.selectedTeleport(f.player))
        f.attr[Reward.ANNIHILATION_TELEPORT.unlock!!] = 1
        assertEquals(Result.OK, store.selectTeleport(f.player, Teleport.ANNIHILATION))
        assertEquals(Teleport.ANNIHILATION, store.selectedTeleport(f.player))
        assertEquals(Result.OK, store.selectTeleport(f.player, Teleport.DEFAULT))
        assertEquals(Teleport.DEFAULT, store.selectedTeleport(f.player))
    }

    @Test fun `missing local ids fail closed without phantom item or point changes`() {
        val missing = NigelRewardStore(Contract(emptyMap(), emptyList(), null))
        assertEquals(Result.NOT_READY, missing.buy(f.player, Reward.RUG))
        assertEquals(Result.NOT_READY, missing.unlockWildernessStyle(f.player, 0, 1))
        assertEquals(100_000, NigelRewardStore.balance(f.player))
        assertTrue(f.inventory.filterNotNull().isEmpty())
    }

    @Test fun `sale X handles multiple copies and notes while refusing partial shortfall`() {
        val id = rewardIds.getValue(Reward.RUG)
        f.inventory[0] = Item(id); f.inventory[1] = Item(id)
        assertEquals(Result.ITEM_CHANGED, store.sellBack(f.player, 0, id, 3))
        assertEquals(2L, f.count(id)); assertEquals(100_000, NigelRewardStore.balance(f.player))
        assertEquals(Result.OK, store.sellBack(f.player, 0, id, 2))
        assertEquals(106_000, NigelRewardStore.balance(f.player))
        f.define(950, true).also { it.noteTemplateId = 799; it.noteLinkId = id }
        f.inventory[0] = Item(950, 10)
        assertEquals(Result.OK, store.sellBack(f.player, 0, 950, 10))
        assertEquals(136_000, NigelRewardStore.balance(f.player)); assertEquals(0L, f.count(950))
    }
}
