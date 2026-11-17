package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import io.mockk.verify
import kotlin.test.*

class Dmm2CurrencyTests {
    private val f = DeadmanPlayerFixture()
    private val dp = StoreCatalogue.Currency.DEADMAN.attr
    private val money = 1500
    init {
        Dmm2Ids.install(listOf("item\t13307\t1500", "item\t13317\t1501", "item\t13318\t1502", "item\t13319\t1503"))
        f.define(money, true); (1501..1503).forEach { f.define(it) }
    }
    @Test fun `migration is disabled by default and one successful snapshot is idempotent`() {
        f.attr[BreachPoints.BALANCE] = 1_500_001; f.attr[BreachPoints.EARNED] = 75_000; f.attr[dp] = 50
        assertEquals(BreachRetirement.Result.DISABLED, BreachRetirement.migrate(f.player, false))
        assertEquals(1_500_001, f.attr[BreachPoints.BALANCE]); assertNull(f.attr[BreachRetirement.MIGRATED])
        assertEquals(BreachRetirement.Result.OK, BreachRetirement.migrate(f.player, true))
        assertEquals(7551, f.attr[dp]); assertNull(f.attr[BreachPoints.BALANCE]); assertNull(f.attr[BreachPoints.EARNED])
        assertEquals(1_500_001, f.attr[BreachRetirement.OLD_BALANCE]); assertEquals(75_000, f.attr[BreachRetirement.OLD_EARNED])
        assertEquals(BreachRetirement.Result.ALREADY_DONE, BreachRetirement.migrate(f.player, true)); assertEquals(7551, f.attr[dp])
        assertEquals(1, f.attr.toPersistentMap()["dmm2_breach_migrated"])
    }
    @Test fun `overflow never debits marks or loses lifetime state and succeeds when room is made`() {
        f.attr[BreachPoints.BALANCE] = 201; f.attr[BreachPoints.EARNED] = 99; f.attr[dp] = Int.MAX_VALUE - 1
        val before = f.attr.toPersistentMap()
        assertEquals(BreachRetirement.Result.OVERFLOW, BreachRetirement.migrate(f.player, true)); assertEquals(before, f.attr.toPersistentMap())
        f.attr[dp] = Int.MAX_VALUE - 2
        assertEquals(BreachRetirement.Result.OK, BreachRetirement.migrate(f.player, true)); assertEquals(Int.MAX_VALUE, f.attr[dp])
    }
    @Test fun `zero small MAX negative historical balances have defined nonwrapping conversion`() {
        assertEquals(0L, BreachRetirement.converted(0)); assertEquals(1L, BreachRetirement.converted(1))
        assertEquals(1L, BreachRetirement.converted(200)); assertEquals(2L, BreachRetirement.converted(201))
        assertEquals(375L, BreachRetirement.converted(75_000)); assertEquals(7500L, BreachRetirement.converted(1_500_000))
        assertEquals(10_737_419L, BreachRetirement.converted(Int.MAX_VALUE)); assertEquals(10_737_419L, BreachRetirement.converted(-1))
        f.attr[dp] = -1; f.attr[BreachPoints.BALANCE] = 1
        val before = f.attr.toPersistentMap()
        assertEquals(BreachRetirement.Result.OVERFLOW, BreachRetirement.migrate(f.player, true)); assertEquals(before, f.attr.toPersistentMap())
    }
    @Test fun `armour purchase unlocks each piece at exactly one blood money and reclaim is free`() {
        f.inventory[0] = Item(money, 3); f.attr[dp] = 900
        BloodMoney.Armour.values().forEach { armour ->
            assertEquals(BloodMoney.Result.LOCKED, BloodMoney.reclaim(f.player, armour))
            assertEquals(BloodMoney.Result.OK, BloodMoney.unlock(f.player, armour))
            assertEquals(BloodMoney.Result.ALREADY_UNLOCKED, BloodMoney.unlock(f.player, armour))
            assertEquals(BloodMoney.Result.OK, BloodMoney.reclaim(f.player, armour))
            assertEquals(2L, f.count(armour.item.local!!))
        }
        assertEquals(0L, f.count(money)); assertEquals(900, f.attr[dp])
    }
    @Test fun `full inventory cannot consume money or grant an unlock and missing bindings cannot invent an item`() {
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(1501) }
        f.inventory[0] = Item(money, 2)
        assertEquals(BloodMoney.Result.NO_SPACE, BloodMoney.unlock(f.player, BloodMoney.Armour.CHEST))
        assertEquals(2L, f.count(money)); assertNull(f.attr[BloodMoney.Armour.CHEST.unlocked])
        Dmm2Ids.install(emptyList())
        assertEquals(BloodMoney.Result.NOT_READY, BloodMoney.unlock(f.player, BloodMoney.Armour.CHEST))
        assertEquals(2L, f.count(money))
    }
    @Test fun `blood money award reuses every existing antifarm verdict and pays nothing before release`() {
        val victim = DeadmanPlayerFixture().player
        ValidPkKill.Verdict.values().filter { !it.valid }.forEach { BloodMoney.onJudgedKill(f.player, victim, it, true) }
        BloodMoney.onJudgedKill(f.player, victim, ValidPkKill.Verdict.VALID, false)
        BloodMoney.onJudgedKill(f.player, f.player, ValidPkKill.Verdict.VALID, true)
        verify(exactly = 0) { f.world.spawn(any<gg.rsmod.game.model.entity.GroundItem>()) }
        BloodMoney.onJudgedKill(f.player, victim, ValidPkKill.Verdict.VALID, true)
        verify(exactly = 1) { f.world.spawn(any<gg.rsmod.game.model.entity.GroundItem>()) }
    }
    @Test fun `missing blood money mapping queues a durable claim paid once after import`() {
        Dmm2Ids.install(emptyList())
        BloodMoney.onJudgedKill(f.player, DeadmanPlayerFixture().player, ValidPkKill.Verdict.VALID, true)
        assertEquals(1, f.attr.toPersistentMap()["dmm2_blood_money_pending"])
        assertEquals(BloodMoney.Result.NOT_READY, BloodMoney.deliverPending(f.player))
        Dmm2Ids.install(listOf("item\t13307\t1500"))
        assertEquals(BloodMoney.Result.OK, BloodMoney.deliverPending(f.player)); assertEquals(1L, f.count(money))
        assertEquals(BloodMoney.Result.OK, BloodMoney.deliverPending(f.player)); assertEquals(1L, f.count(money))
    }
    @Test fun `released server cannot mint or spend legacy breach balances through public award and admin helpers`() {
        val previous = System.getProperty("rsps.dmm2.release")
        try {
            System.setProperty("rsps.dmm2.release", "true")
            f.attr[BreachPoints.BALANCE] = 201; f.attr[BreachPoints.EARNED] = 123
            val before = f.attr.toPersistentMap()
            assertEquals(0, BreachPoints.award(f.player, 200))
            BreachPoints.addBalance(f.player, Int.MAX_VALUE)
            BreachPoints.addBalance(f.player, -201)
            assertEquals(before, f.attr.toPersistentMap())
        } finally {
            if (previous == null) System.clearProperty("rsps.dmm2.release") else System.setProperty("rsps.dmm2.release", previous)
        }
    }
}
