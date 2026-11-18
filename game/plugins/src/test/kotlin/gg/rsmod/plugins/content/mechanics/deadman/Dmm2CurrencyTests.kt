package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachPoints
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import io.mockk.verify
import io.mockk.every
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
    @Test fun `migration crash during atomic write or immediately after commit reloads to exactly one credit`() {
        // Synthetic JSON in an isolated temporary directory; never a player save or a running server.
        val directory = kotlin.io.path.createTempDirectory("dmm2-migration-crash").toFile()
        val file = java.io.File(directory, "snapshot.json")
        val client = io.mockk.mockk<gg.rsmod.game.model.entity.Client>(relaxed = true)
        val serializer = io.mockk.mockk<gg.rsmod.game.service.serializer.PlayerSerializerService>()
        every { client.world } returns f.world
        every { client.attr } returns f.attr
        every { f.world.getService(gg.rsmod.game.service.serializer.PlayerSerializerService::class.java, true) } returns serializer
        try {
            for (crashDuringWrite in listOf(true, false)) {
                f.attr.clear()
                f.attr[BreachPoints.BALANCE] = 201; f.attr[BreachPoints.EARNED] = 999; f.attr[dp] = 50
                file.writeText(com.google.gson.Gson().toJson(f.attr.toPersistentMap()))
                var fail = true
                every { serializer.saveClientData(client) } answers {
                    val json = com.google.gson.Gson().toJson(f.attr.toPersistentMap())
                    gg.rsmod.game.service.serializer.json.JsonPlayerSerializer.writeAtomically(file.toPath()) { writer ->
                        if (fail && crashDuringWrite) {
                            writer.write(json.take(json.length / 2))
                            throw java.io.IOException("simulated interrupted migration write")
                        }
                        writer.write(json)
                    }
                    if (fail) throw java.io.IOException("simulated crash after atomic migration commit")
                    true
                }
                assertFailsWith<java.io.IOException> { BreachRetirement.migrate(client, true) }
                fun reconnect() {
                    val saved = com.google.gson.JsonParser().parse(file.readText()).asJsonObject
                    f.attr.clear()
                    saved.entrySet().forEach { (key, value) -> f.attr[gg.rsmod.game.model.attr.AttributeKey<Int>(key)] = value.asInt }
                }
                reconnect()
                fail = false
                assertEquals(if (crashDuringWrite) BreachRetirement.Result.OK else BreachRetirement.Result.ALREADY_DONE,
                    BreachRetirement.migrate(client, true))
                repeat(5) {
                    reconnect()
                    assertEquals(BreachRetirement.Result.ALREADY_DONE, BreachRetirement.migrate(client, true))
                    assertEquals(52, f.attr[dp]); assertNull(f.attr[BreachPoints.BALANCE])
                    assertNull(f.attr[BreachPoints.EARNED]); assertEquals(201, f.attr[BreachRetirement.OLD_BALANCE])
                    assertEquals(999, f.attr[BreachRetirement.OLD_EARNED]); assertEquals(1, f.attr[BreachRetirement.MIGRATED])
                }
                assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
            }
        } finally {
            directory.listFiles()?.forEach { it.delete() }; directory.delete()
        }
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
    @Test fun `valid blood money is one owned PvP drop at the victim with D5 times inside and outside Wilderness`() {
        val victim = DeadmanPlayerFixture().player
        every { f.player.uid } returns gg.rsmod.game.model.PlayerUID(1L)
        every { victim.uid } returns gg.rsmod.game.model.PlayerUID(2L)
        val drops = mutableListOf<gg.rsmod.game.model.entity.GroundItem>()
        every { f.world.spawn(capture(drops)) } returns Unit
        for ((tile, expiry) in listOf(gg.rsmod.game.model.Tile(3200, 3600) to 500,
                gg.rsmod.game.model.Tile(3200, 3200) to 6100)) {
            every { victim.tile } returns tile
            BloodMoney.onJudgedKill(f.player, victim, ValidPkKill.Verdict.VALID, true)
            val drop = drops.last()
            assertEquals(money, drop.item); assertEquals(1, drop.amount); assertEquals(tile, drop.tile)
            assertTrue(drop.isOwnedBy(f.player)); assertFalse(drop.canBeViewedBy(victim))
            assertEquals(100, drop.publicCycles); assertEquals(expiry, drop.despawnCycles)
        }
        assertEquals(2, drops.size); assertEquals(0L, f.count(money))
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
