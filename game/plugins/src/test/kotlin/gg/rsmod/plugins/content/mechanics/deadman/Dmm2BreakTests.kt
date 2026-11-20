package gg.rsmod.plugins.content.mechanics.deadman

import com.google.gson.Gson
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.SAFE_DEPOSIT_BOX_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.model.skill.ExperienceLocks
import gg.rsmod.plugins.api.Skills
import gg.rsmod.game.service.serializer.json.JsonPlayerSerializer.PersistentContainer
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.death.*
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.plugins.content.starter.DeadmanStarterPack
import io.mockk.every
import kotlin.test.*

/** A8/I10: cross-service interruption/retry/reload tests. No saves, cache, servers or other accounts. */
class Dmm2BreakTests {
    private val f = DeadmanPlayerFixture()
    private val ids = Dmm2Ids.ItemId.values().associateWith { 1800 + it.ordinal }
    private val rewards = NigelRewardStore.Reward.values().associateWith { 2000 + it.ordinal }
    private val dp = StoreCatalogue.Currency.DEADMAN.attr
    private val timers = TimerMap()
    private val store: NigelRewardStore
    init {
        Dmm2Ids.install(ids.map { "item\t${it.key.source}\t${it.value}" })
        ids.values.forEach { f.define(it).tradeable = true }
        // Wiki Blood money rev15380671 + native cache2686: stackable, UNTRADEABLE.
        f.define(id(Dmm2Ids.ItemId.BLOOD_MONEY), true).tradeable = false
        f.defs.getValue(id(Dmm2Ids.ItemId.SCYTHE)).tradeable = false
        f.defs.getValue(id(Dmm2Ids.ItemId.SHADOW)).tradeable = false
        rewards.values.forEach { f.define(it).tradeable = true }
        f.define(Items.COINS_995, true).tradeable = true; f.define(Items.TELEPORT_TO_HOUSE, true)
        f.define(Items.BLOOD_RUNE, true); f.define(900).tradeable = true
        every { f.player.timers } returns timers
        store = NigelRewardStore(NigelRewardStore.Contract(rewards, emptyList(), null))
    }
    private fun id(item: Dmm2Ids.ItemId) = ids.getValue(item)
    private fun totalUnits(): Long = f.containers.values.sumOf { c -> c.filterNotNull().sumOf { it.amount.toLong() } }
    private fun encoded(): String = Gson().toJson(f.containers.map { (key, c) -> PersistentContainer(key.name, c.toMap()) })
    /** Use the real container-save DTO and typed persistent attributes, then discard transient session state. */
    private fun reload() {
        val gson = Gson()
        val saved = f.containers.map { (key, c) -> key to gson.fromJson(gson.toJson(PersistentContainer(key.name, c.toMap())), PersistentContainer::class.java) }
        val attrs = f.attr.toPersistentMap()
        val savedTimers = timers.toPersistentTimers()
        f.attr.clear()
        attrs.forEach { (name, value) ->
            // These scenarios deliberately exercise only Int/Boolean keys, like the serializer's primitive branch.
            when (value) {
                is Int -> f.attr[AttributeKey<Int>(name)] = value
                is Boolean -> f.attr[AttributeKey<Boolean>(name)] = value
                else -> error("Add the serializer's typed branch before using $name")
            }
        }
        saved.forEach { (key, dto) ->
            f.containers[key] = ItemContainer(f.definitions, key).also { c -> dto.items.forEach { (slot, item) -> c[slot] = item } }
        }
        timers.clear()
        savedTimers.forEach { t -> timers[TimerKey(t.identifier, t.tickOffline, t.resetOnDeath, t.tickForward, t.removeOnZero)] = t.timeLeft }
        // Fixture accessors are stable container references: restore their saved contents into those same containers.
        listOf(f.inventory, f.bank, f.equipment).forEach { target ->
            val key = f.containers.keys.single { it.name == targetKey(target) }
            val loaded = f.containers.getValue(key)
            target.rawItems.indices.forEach { target[it] = loaded[it] }
            f.containers[key] = target
        }
    }
    private fun targetKey(c: ItemContainer) = when (c) {
        f.inventory -> "inventory"
        f.bank -> "bank"
        else -> "equipment"
    }

    @Test fun `migration reload retry twice preserves exactly one credit and the complete audit`() {
        f.attr[BreachPoints.LEGACY_BALANCE] = Int.MAX_VALUE; f.attr[BreachPoints.LEGACY_EARNED] = 75_000
        f.attr[dp] = Int.MAX_VALUE
        val before = f.attr.toPersistentMap()
        repeat(5) {
            assertEquals(BreachRetirement.Result.OVERFLOW, BreachRetirement.migrate(f.player, true))
            reload(); assertEquals(before, f.attr.toPersistentMap())
        }
        f.attr[dp] = 0
        assertEquals(BreachRetirement.Result.OK, BreachRetirement.migrate(f.player, true))
        repeat(5) {
            reload(); assertEquals(BreachRetirement.Result.ALREADY_DONE, BreachRetirement.migrate(f.player, true))
            assertEquals(10_737_419, f.attr[dp]); assertEquals(Int.MAX_VALUE, f.attr[BreachRetirement.OLD_BALANCE])
            assertEquals(75_000, f.attr[BreachRetirement.OLD_EARNED]); assertNull(f.attr[BreachPoints.LEGACY_BALANCE])
        }
    }

    @Test fun `Nigel armour refuses zero blood money and never spends DP then survives relog and retries`() {
        f.attr[dp] = Int.MAX_VALUE
        val money = id(Dmm2Ids.ItemId.BLOOD_MONEY)
        BloodMoney.Armour.values().forEach { armour ->
            val before = encoded()
            repeat(5) { assertEquals(BloodMoney.Result.NO_MONEY, BloodMoney.unlock(f.player, armour)) }
            assertEquals(before, encoded()); assertEquals(Int.MAX_VALUE, f.attr[dp])
            f.inventory[0] = Item(money)
            assertEquals(BloodMoney.Result.OK, BloodMoney.unlock(f.player, armour))
            reload()
            assertEquals(BloodMoney.Result.ALREADY_UNLOCKED, BloodMoney.unlock(f.player, armour))
            assertEquals(0L, f.count(money)); assertEquals(1L, f.count(armour.item.local!!))
            f.inventory.rawItems.indices.forEach { f.inventory[it] = null }
        }
    }

    @Test fun `pending blood money with full inventory bank and max stack remains payable once after reload`() {
        val pending = AttributeKey<Int>("dmm2_blood_money_pending")
        val money = id(Dmm2Ids.ItemId.BLOOD_MONEY)
        f.attr[pending] = 2
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(900) }
        f.bank.rawItems.indices.forEach { f.bank[it] = Item(900) }
        f.inventory[0] = Item(money, Int.MAX_VALUE - 1)
        val before = encoded()
        repeat(5) {
            assertEquals(BloodMoney.Result.NO_SPACE, BloodMoney.deliverPending(f.player)); reload()
            assertEquals(before, encoded()); assertEquals(2, f.attr[pending])
        }
        f.inventory[0]!!.amount--
        assertEquals(BloodMoney.Result.OK, BloodMoney.deliverPending(f.player)); reload()
        assertEquals(BloodMoney.Result.OK, BloodMoney.deliverPending(f.player))
        assertEquals(Int.MAX_VALUE.toLong(), f.count(money)); assertNull(f.attr[pending])
    }

    @Test fun `all seven reward purchase refund loops conserve DP value through relog and stale sell packets`() {
        f.attr[dp] = 50_000
        NigelRewardStore.Reward.values().forEach { reward -> repeat(5) {
            assertEquals(NigelRewardStore.Result.OK, store.buy(f.player, reward))
            assertEquals(50_000L, NigelRewardStore.balance(f.player).toLong() + reward.price * f.count(rewards.getValue(reward)))
            reload()
            val slot = f.inventory.rawItems.indices.single { f.inventory[it]?.id == rewards[reward] }
            assertEquals(NigelRewardStore.Result.OK, store.sellBack(f.player, slot, rewards.getValue(reward)))
            assertEquals(NigelRewardStore.Result.ITEM_CHANGED, store.sellBack(f.player, slot, rewards.getValue(reward)))
            reload(); assertEquals(50_000, f.attr[dp]); assertEquals(0L, totalUnits())
        } }
    }

    @Test fun `unlocked reward cannot be sold or consumed again after logout`() {
        val reward = NigelRewardStore.Reward.ARMAGEDDON_WEAPONS
        f.attr[dp] = reward.price
        assertEquals(NigelRewardStore.Result.OK, store.buy(f.player, reward))
        assertEquals(NigelRewardStore.Result.OK, store.unlock(f.player, reward, 0, rewards.getValue(reward)))
        reload()
        assertEquals(NigelRewardStore.Result.ALREADY_UNLOCKED, store.unlock(f.player, reward, 0, rewards.getValue(reward)))
        assertEquals(NigelRewardStore.Result.ITEM_CHANGED, store.sellBack(f.player, 0, rewards.getValue(reward)))
        assertEquals(0, f.attr[dp]); assertEquals(0L, totalUnits())
    }

    @Test fun `safebox fortuity cannot grant an effect or refund while skull relog and death preserve it`() {
        val active = id(Dmm2Ids.ItemId.FORTUITY)
        f.inventory[0] = Item(active)
        assertEquals(SafeDepositBox.Status.OK, SafeDepositBox.beginAccess(f.player))
        assertEquals(1, SafeDepositBox.deposit(f.player, 0, active).moved)
        assertEquals(0.0, DeadmanTrinkets.bonus(f.player))
        assertEquals(DeadmanTrinkets.Result.ITEM_CHANGED, DeadmanTrinkets.charge(f.player, 0, active, false))
        val before = encoded()
        for (context in DeathContext.values()) for (skulled in listOf(false, true)) {
            if (skulled) timers[SKULL_ICON_DURATION_TIMER] = 100 else timers.remove(SKULL_ICON_DURATION_TIMER)
            val result = DeathResolver.resolve(f.player, null, skulled, ItemRiskValueProvider { 10_000_000 }, contextOverride = context)
            assertTrue(result.itemRisk.lost.isEmpty()); assertTrue(result.itemRisk.protected.isEmpty())
            reload()
            assertEquals(before, encoded()); assertEquals(0L, f.count(Items.COINS_995)); assertEquals(0.0, DeadmanTrinkets.bonus(f.player))
            assertEquals(if (skulled) SafeDepositBox.Status.SKULLED else SafeDepositBox.Status.NOT_OPEN,
                SafeDepositBox.withdraw(f.player, 0, active).status)
        }
        timers.remove(SKULL_ICON_DURATION_TIMER)
        assertEquals(SafeDepositBox.Status.OK, SafeDepositBox.beginAccess(f.player))
        assertEquals(1, SafeDepositBox.withdraw(f.player, 0, active).moved)
        assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.charge(f.player, 0, active, false))
        assertEquals(10_000_000L, f.count(Items.COINS_995)); assertEquals(1L, f.count(id(Dmm2Ids.ItemId.FORTUITY_EMPTY)))
        assertEquals(0L, f.count(active))
    }

    @Test fun `charged scythe refuses safe storage without charge loss then empty form is storable`() {
        val empty = id(Dmm2Ids.ItemId.SCYTHE_EMPTY); val active = id(Dmm2Ids.ItemId.SCYTHE)
        f.inventory[0] = Item(empty); f.inventory[1] = Item(Items.BLOOD_RUNE, 600)
        assertEquals(CorruptedWeapons.Result.OK, CorruptedWeapons.chargeScythe(f.player, 0, empty, 2))
        assertEquals(SafeDepositBox.Status.OK, SafeDepositBox.beginAccess(f.player))
        assertEquals(SafeDepositBox.Status.UNTRADEABLE, SafeDepositBox.deposit(f.player, 0, active).status)
        reload(); assertEquals(200, f.inventory[0]!!.getAttr(ItemAttribute.CHARGES))
        assertEquals(0, f.containers[SAFE_DEPOSIT_BOX_KEY]?.occupiedSlotCount ?: 0)
        val slot = f.inventory.rawItems.indices.single { f.inventory[it]?.id == active }
        assertEquals(CorruptedWeapons.Result.OK, CorruptedWeapons.unchargeScythe(f.player, slot, active, atWell = true))
        reload()
        assertEquals(CorruptedWeapons.Result.ITEM_CHANGED, CorruptedWeapons.unchargeScythe(f.player, slot, active, atWell = true))
        assertEquals(600L, f.count(Items.BLOOD_RUNE)); assertEquals(1L, f.count(empty)); assertEquals(0L, f.count(active))
        SafeDepositBox.beginAccess(f.player)
        assertEquals(1, SafeDepositBox.deposit(f.player, slot, empty).moved)
        reload(); assertEquals(SafeDepositBox.Status.NOT_OPEN, SafeDepositBox.withdraw(f.player, 0, empty).status)
        SafeDepositBox.beginAccess(f.player)
        assertEquals(1, SafeDepositBox.withdraw(f.player, 0, empty).moved)
        assertEquals(600L, f.count(Items.BLOOD_RUNE)); assertEquals(1L, f.count(empty))
    }

    @Test fun `starter reclaim reload duplicates neither unopened equipment pack nor introduction cash`() {
        val pack = NewPlayerConfig.current.starterChestItem
        f.define(pack)
        f.attr[NEW_ACCOUNT_ATTR] = true
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.reclaim(f.player))
        repeat(5) {
            reload(); assertEquals(DeadmanStarterPack.Result.ALREADY_OWNED, DeadmanStarterPack.reclaim(f.player))
            assertEquals(1L, f.count(pack)); assertEquals(0L, f.count(Items.COINS_995))
        }
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.grantIntroductionReward(f.player))
        repeat(5) {
            reload(); assertEquals(DeadmanStarterPack.Result.ALREADY_GIVEN, DeadmanStarterPack.grantIntroductionReward(f.player))
            assertEquals(1_000_000L, f.count(Items.COINS_995)); assertEquals(100L, f.count(Items.TELEPORT_TO_HOUSE))
        }
    }

    @Test fun `all six carried trinkets are counted once and every safe copy is excluded from each death context`() {
        val family = listOf(Dmm2Ids.ItemId.ADVANCED_WEAPONRY, Dmm2Ids.ItemId.UNDEAD,
            Dmm2Ids.ItemId.FORTUITY, Dmm2Ids.ItemId.FAIRIES, Dmm2Ids.ItemId.AVARICE, Dmm2Ids.ItemId.VENGEANCE)
        SafeDepositBox.beginAccess(f.player)
        family.forEach { item ->
            f.inventory[0] = Item(id(item))
            assertEquals(1, SafeDepositBox.deposit(f.player, 0, id(item)).moved)
        }
        family.forEachIndexed { slot, item -> f.inventory[slot] = Item(id(item)) }
        val total = totalUnits()
        repeat(5) {
            for (context in DeathContext.values()) for (skulled in listOf(false, true)) {
                val risk = DeathResolver.resolve(f.player, null, skulled, ItemRiskValueProvider { 1L }, contextOverride = context).itemRisk
                val carried = risk.protected + risk.lost
                assertEquals(6L, carried.sumOf { it.item.amount.toLong() })
                assertEquals(family.map(::id).toSet(), carried.map { it.item.id }.toSet())
                assertEquals(6, f.containers.getValue(SAFE_DEPOSIT_BOX_KEY).occupiedSlotCount)
                assertEquals(total, totalUnits())
                reload()
            }
        }
    }

    @Test fun `ten physical safe slots full inventory and stale withdrawals conserve every unit after reload`() {
        val money = Items.COINS_995
        f.inventory[0] = Item(money, 11)
        SafeDepositBox.beginAccess(f.player)
        assertEquals(10, SafeDepositBox.deposit(f.player, 0, money, Int.MAX_VALUE).moved)
        assertEquals(SafeDepositBox.Status.BOX_FULL, SafeDepositBox.deposit(f.player, 0, money).status)
        assertEquals(11L, f.count(money)); reload(); SafeDepositBox.beginAccess(f.player)
        f.bank[0] = f.inventory[0]
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(900) }
        val before = encoded()
        repeat(5) {
            assertEquals(SafeDepositBox.Status.INVENTORY_FULL, SafeDepositBox.withdraw(f.player, 0, money).status)
            assertEquals(before, encoded())
        }
        f.inventory[0] = null
        assertEquals(1, SafeDepositBox.withdraw(f.player, 0, money).moved)
        assertEquals(SafeDepositBox.Status.ITEM_CHANGED, SafeDepositBox.withdraw(f.player, 0, money).status)
        reload(); assertEquals(11L, f.count(money))
    }

    @Test fun `OSRS blood money cannot enter safe deposit box before or after relog`() {
        val money = id(Dmm2Ids.ItemId.BLOOD_MONEY)
        f.inventory[0] = Item(money, 11)
        repeat(5) {
            SafeDepositBox.beginAccess(f.player)
            val before = encoded()
            assertEquals(SafeDepositBox.Status.UNTRADEABLE, SafeDepositBox.deposit(f.player, 0, money, 11).status)
            assertEquals(before, encoded())
            assertEquals(11L, f.count(money))
            reload()
        }
    }

    @Test fun `fire recipe in a full inventory consumes its exact inputs once across relog and repeated completion`() {
        every { f.player.skills.getCurrentLevel(Skills.RUNECRAFTING) } returns 21
        f.define(Items.AMULET_OF_MAGIC); f.define(Items.FIRE_RUNE, true)
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(900) }
        f.inventory[0] = Item(id(Dmm2Ids.ItemId.FIRE_RUBY))
        f.inventory[1] = Item(Items.AMULET_OF_MAGIC)
        f.inventory[2] = Item(Items.FIRE_RUNE, 100)
        assertEquals(FireAmulet.Result.OK, FireAmulet.craft(f.player))
        val after = encoded()
        repeat(5) {
            reload(); assertEquals(FireAmulet.Result.INGREDIENTS, FireAmulet.craft(f.player))
            assertEquals(after, encoded())
        }
        assertEquals(1L, f.count(id(Dmm2Ids.ItemId.FIRE_AMULET)))
        assertEquals(0L, f.count(id(Dmm2Ids.ItemId.FIRE_RUBY)))
        assertEquals(0L, f.count(Items.AMULET_OF_MAGIC)); assertEquals(0L, f.count(Items.FIRE_RUNE))
        io.mockk.verify(exactly = 1) { f.player.addXp(Skills.RUNECRAFTING, 125.0, true, false) }
    }

    @Test fun `advanced trinket interrupted reload and retry with full inventory yields exactly one of eight weapons`() {
        val advanced = id(Dmm2Ids.ItemId.ADVANCED_WEAPONRY)
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(900) }
        f.inventory[0] = Item(advanced)
        val before = encoded()
        repeat(5) {
            assertEquals(DeadmanTrinkets.Result.OBSTRUCTED, DeadmanTrinkets.openAdvanced(f.player, 0, advanced, false) { error("no roll before completion") })
            reload(); assertEquals(before, encoded())
        }
        assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.openAdvanced(f.player, 0, advanced, true) { 0 })
        reload()
        repeat(5) { assertEquals(DeadmanTrinkets.Result.ITEM_CHANGED, DeadmanTrinkets.openAdvanced(f.player, 0, advanced, true) { error("duplicate roll") }) }
        assertEquals(28L, totalUnits()); assertEquals(1L, f.count(id(Dmm2Ids.ItemId.VOIDWAKER)))
        assertEquals(0L, f.count(advanced))
    }

    @Test fun `relog preserves shared undead cooldown without persisting a stale summon actor`() {
        val actor = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val look = io.mockk.mockk<UndeadThralls.Appearance>(relaxed = true)
        every { look.spawn(f.player, any()) } returns actor
        f.inventory[0] = Item(id(Dmm2Ids.ItemId.UNDEAD))
        UndeadThralls.appearance = look
        try {
            assertEquals(UndeadThralls.Result.OK, UndeadThralls.summon(f.player, UndeadThralls.Choice.ZUK))
            repeat(5) {
                reload()
                assertEquals(UndeadThralls.Result.COOLDOWN, UndeadThralls.summon(f.player, UndeadThralls.Choice.WISP))
                assertEquals(17, timers[UndeadThralls.SUMMON_COOLDOWN])
            }
            io.mockk.verify(exactly = 1) { look.spawn(f.player, any()) }
            assertEquals(1L, totalUnits())
        } finally { UndeadThralls.appearance = null }
    }

    @Test fun `independent combat and hitpoints locks survive repeated reconnects without changing DP`() {
        f.attr[dp] = 1234
        ExperienceLocks.setCombatDisabled(f.player, true)
        repeat(5) {
            reload()
            (0..6).forEach { assertTrue(ExperienceLocks.blocks(f.player, it)) }
            assertFalse(ExperienceLocks.blocks(f.player, Skills.SUMMONING))
            assertEquals(1234, f.attr[dp])
        }
        ExperienceLocks.setHitpointsDisabled(f.player, true)
        ExperienceLocks.setCombatDisabled(f.player, false)
        repeat(5) {
            reload()
            (0..6).forEach { assertEquals(it == Skills.CONSTITUTION, ExperienceLocks.blocks(f.player, it)) }
            assertEquals(1234, f.attr[dp])
        }
    }

    @Test fun `POH construction logout before completion consumes nothing and a completed rug cannot pay twice`() {
        val houses = gg.rsmod.plugins.content.areas.poh.PlayerHouse
        io.mockk.mockkObject(houses)
        try {
            every { houses.ownsHouseAt(f.player) } returns true
            every { houses.isBuildMode(f.player) } returns true
            every { f.player.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
            every { f.player.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 28
            var cycle = 10
            every { f.world.currentCycle } answers { cycle }
            val rug = NigelHouseRewards.Slot("break_rug", NigelHouseRewards.Kind.RUG)
            val service = NigelHouseRewards(store, setOf(rug), { _, _ -> true }, { 1 })
            listOf(Items.SAW, Items.HAMMER, Items.BOLT_OF_CLOTH).forEach { f.define(it) }
            f.inventory[0] = Item(Items.SAW); f.inventory[1] = Item(Items.HAMMER)
            f.inventory[2] = Item(rewards.getValue(NigelRewardStore.Reward.RUG))
            repeat(4) { f.inventory[3 + it] = Item(Items.BOLT_OF_CLOTH) }
            val before = encoded()
            assertEquals(NigelHouseRewards.Result.OK, service.beginRug(f.player, rug))
            reload(); cycle = 15
            assertEquals(NigelHouseRewards.Result.NOT_STARTED, service.finishRug(f.player))
            assertEquals(before, encoded())
            assertEquals(NigelHouseRewards.Result.OK, service.beginRug(f.player, rug))
            cycle = 20
            assertEquals(NigelHouseRewards.Result.OK, service.finishRug(f.player))
            repeat(5) {
                reload(); assertTrue(service.installed(f.player, rug))
                assertEquals(NigelHouseRewards.Result.NOT_STARTED, service.finishRug(f.player))
                assertEquals(NigelHouseRewards.Result.OCCUPIED, service.beginRug(f.player, rug))
            }
            assertEquals(NigelHouseRewards.Result.OK, service.remove(f.player, rug))
            reload()
            assertEquals(NigelHouseRewards.Result.EMPTY, service.remove(f.player, rug))
            assertEquals(1L, f.count(rewards.getValue(NigelRewardStore.Reward.RUG)))
            assertEquals(0L, f.count(Items.BOLT_OF_CLOTH))
            io.mockk.verify(exactly = 1) { f.player.addXp(Skills.CONSTRUCTION, 283.0, true, false) }
        } finally { io.mockk.unmockkObject(houses) }
    }

    @Test fun `real PvM death execution transfers carried trinkets to the grave once and never touches safe copies`() {
        val grave = ItemContainer(f.definitions, GRAVESTONE_KEY)
        val office = ItemContainer(f.definitions, DEATH_RECOVERY_KEY)
        f.containers[GRAVESTONE_KEY] = grave; f.containers[DEATH_RECOVERY_KEY] = office
        every { f.player.gravestone } returns grave
        every { f.player.deathRecovery } returns office
        every { f.player.tile } returns gg.rsmod.game.model.Tile(3200, 3200)
        every { f.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, any()) } answers { f.defs[secondArg()] }
        val family = listOf(Dmm2Ids.ItemId.ADVANCED_WEAPONRY, Dmm2Ids.ItemId.UNDEAD,
            Dmm2Ids.ItemId.FORTUITY, Dmm2Ids.ItemId.FAIRIES, Dmm2Ids.ItemId.AVARICE, Dmm2Ids.ItemId.VENGEANCE)
        SafeDepositBox.beginAccess(f.player)
        family.forEach { item ->
            f.inventory[0] = Item(id(item))
            assertEquals(1, SafeDepositBox.deposit(f.player, 0, id(item)).moved)
        }
        family.forEachIndexed { slot, item -> f.inventory[slot] = Item(id(item)) }
        val result = DeathResolver.resolve(f.player, null, true, ItemRiskValueProvider { 1L }, contextOverride = DeathContext.PVM_SAFE)
        assertTrue(DeathExecutor.execute(f.world, result))
        repeat(5) { assertFalse(DeathExecutor.execute(f.world, result)) }
        assertTrue(f.inventory.isEmpty)
        assertEquals(6L, grave.filterNotNull().sumOf { it.amount.toLong() })
        assertEquals(6, f.containers.getValue(SAFE_DEPOSIT_BOX_KEY).occupiedSlotCount)
        assertEquals(12L, totalUnits())
        family.forEach { assertEquals(2L, f.count(id(it))) }
        assertTrue(office.isEmpty)
        io.mockk.verify(exactly = 0) { f.world.spawn(any<gg.rsmod.game.model.entity.GroundItem>()) }
    }
}
