package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DMM-ARMAGEDDON D3 (owner 20-11-2026, OSRS Wiki Deadman: Armageddon "Breach boss rewards"): the MvP (most damage) + the next 15
 * BY DAMAGE get loot and Deadman Points (1 per damage, 42,000 lifetime cap); only the MvP rolls the unique table; everybody
 * below the top 16 gets nothing. A relog keeps its slot, and loot rolls of one player never depend on another's.
 */
class BreachRewardTests {
    private fun player(name: String = "tester"): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        every { p.username } returns name
        return p
    }

    @Test
    fun `the rewarded group is the 16 biggest damage dealers, the MvP first`() {
        val ledger = BreachContribution()
        ledger.add("first", 1) // one damage, first on the monster: no longer enough
        (2..16).forEach { ledger.add("p$it", 10) }
        ledger.add("seventeenth", 5000) // biggest hitter, 17th to deal damage: the MvP
        val ranking = ledger.ranking()
        assertEquals(16, ranking.size)
        assertEquals("seventeenth" to 5000, ranking.first())
        assertEquals("seventeenth", ledger.mvp())
        assertFalse(ranking.any { it.first == "first" }, "below the top 16 by damage: no loot, no points")
    }

    @Test
    fun `ties keep first-damage order and the 17th never gets anything`() {
        val ledger = BreachContribution()
        (1..17).forEach { ledger.add("p$it", 100) }
        assertEquals((1..16).map { "p$it" }, ledger.ranking().map { it.first })
        assertEquals("p1", ledger.mvp())
    }

    @Test
    fun `nobody hit - no MvP`() {
        assertEquals(null, BreachContribution().mvp())
    }

    @Test
    fun `only the MvP rolls the unique table - a non-MvP never gets a rare drop`() {
        val rare = BreachLoot.RARE.map { it.item }.toSet() + BreachLoot.MEGA_RARE
        // Every random draw 0: the rare trigger (nextInt(9576) == 0 < 133) always fires for the MvP. The Deadman's chest extra
        // (owner 20-11: the whole top 16) is kept out, its list shares items with the unique table.
        val zero = object : Random() {
            override fun nextInt(bound: Int): Int =
                if (bound == gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestConfig.BREACH_EXTRA_CHANCE) bound - 1 else 0
            override fun nextDouble(): Double = 0.0
        }
        assertTrue(BreachLoot.roll(zero, mvp = true).any { it.item in rare }, "the MvP's always-firing trigger gives a unique")
        assertFalse(BreachLoot.roll(zero, mvp = false).any { it.item in rare }, "a non-MvP never rolls the unique table")
    }

    @Test
    fun `a relog keeps the same slot and adds up, zero hits claim nothing`() {
        val ledger = BreachContribution()
        ledger.add("Anudd", 100)
        ledger.add("other", 0)
        ledger.add("anudd", 160) // same account after relog, different case
        assertEquals(1, ledger.size)
        assertEquals(260, ledger.damageOf("ANUDD"))
    }

    @Test
    fun `emblem needs 250 damage - 249 does not qualify`() {
        val ledger = BreachContribution()
        ledger.add("a", 249)
        ledger.add("b", 250)
        assertFalse(ledger.damageOf("a") >= BreachLoot.EMBLEM_DAMAGE)
        assertTrue(ledger.damageOf("b") >= BreachLoot.EMBLEM_DAMAGE)
        assertEquals(250, BreachLoot.EMBLEM_DAMAGE)
    }

    @Test
    fun `Audit D-14 - one Archaic emblem per account per breach event, however many monsters`() {
        val event = 9_001
        assertTrue(DeadmanBreach.claimArchaicEmblem(event, "Anudd"), "the first monster of the event gives the emblem")
        assertFalse(DeadmanBreach.claimArchaicEmblem(event, "anudd"), "a second monster (or a relog) in the same event gives none")
        assertTrue(DeadmanBreach.claimArchaicEmblem(event, "other"), "every account has its own claim")
        assertTrue(DeadmanBreach.claimArchaicEmblem(event + 1, "Anudd"), "the next breach event gives a new one")
    }

    @Test
    fun `breach damage pays Deadman Points one per damage up to the 42,000 lifetime cap`() {
        val p = player()
        val dp = gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Currency.DEADMAN.attr
        assertEquals(42_000, BreachPoints.DP_CAP)
        assertEquals(1234, BreachPoints.awardDp(p, 1234))
        assertEquals(1234, p.attr[dp])
        p.attr[BreachPoints.DP_EARNED] = BreachPoints.DP_CAP - 10
        assertEquals(10, BreachPoints.awardDp(p, 500))
        assertEquals(0, BreachPoints.awardDp(p, 500), "the cap reached: loot still, no more points")
        assertEquals(BreachPoints.DP_CAP, BreachPoints.dpEarned(p))
        assertEquals(1244, p.attr[dp])
        assertEquals(0, BreachPoints.awardDp(player(), 0))
    }

    @Test
    fun `the schedule file matches the default and daylight saving never moves an opening`() {
        val file = java.io.File("../../data/cfg/deadman/breach-schedule.json")
        val loaded = DeadmanBreach.loadSchedule(file)
        assertEquals(DeadmanBreach.Schedule().days, loaded.days)
        assertEquals(DeadmanBreach.Schedule().hours, loaded.hours)
        assertEquals(DeadmanBreach.Schedule().randomPerDay, loaded.randomPerDay)
        // 2026-10-25 is the EU clock change (Sunday): the fixed openings stay on the UTC hours (random one switched off here).
        val saved = DeadmanBreach.schedule
        try {
            DeadmanBreach.schedule = DeadmanBreach.Schedule(randomPerDay = false)
            val amsterdam = java.time.ZonedDateTime.of(2026, 10, 25, 1, 30, 0, 0, java.time.ZoneId.of("Europe/Amsterdam"))
            val next = DeadmanBreach.nextStart(amsterdam)
            assertEquals(java.time.ZoneOffset.UTC, next.zone)
            assertEquals(2, next.hour)
            assertEquals(0, next.minute)
        } finally {
            DeadmanBreach.schedule = saved
        }
    }

    @Test
    fun `poison and venom immunities follow the wiki infoboxes`() {
        val roster = BreachMonsters.ROSTER.map { it.id }.toSet()
        assertTrue(roster.containsAll(BreachMonsters.VENOM_IMMUNE), "every immune id is a breach monster")
        assertTrue(BreachMonsters.VENOM_IMMUNE.containsAll(BreachMonsters.POISON_IMMUNE), "poison-immune implies venom-immune here")
        assertTrue(14458 in BreachMonsters.POISON_IMMUNE) // Cerberus: 100% / 100%
        assertTrue(14438 in BreachMonsters.VENOM_IMMUNE && 14438 !in BreachMonsters.POISON_IMMUNE) // Dagannoth Rex: 0% / 100%
        assertFalse(14439 in BreachMonsters.VENOM_IMMUNE) // King Black Dragon: 0% / 0%
        assertFalse(14468 in BreachMonsters.VENOM_IMMUNE) // Splatter: "?" on the wiki
    }

    @Test
    fun `each eligible player's roll is independent - the same seed gives the same loot whatever others roll`() {
        val a = BreachLoot.roll(Random(42)).map { it.item to it.amount }
        val b = BreachLoot.roll(Random(42)).map { it.item to it.amount }
        assertEquals(a, b)
        // Two regular rolls, plus at most one tertiary roll.
        assertTrue(a.size in 2..3)
    }
}
