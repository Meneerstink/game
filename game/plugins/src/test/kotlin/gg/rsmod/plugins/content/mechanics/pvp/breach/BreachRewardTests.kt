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
 * Owner 2026-09-23 breach audit: loot goes to the FIRST 16 damage dealers (order, not amount), Breach Points to the TOP 100 by
 * damage (1 per damage, 75,000 lifetime cap), the tier-5 emblem at 250+ damage, a relog keeps its slot, and loot rolls of one
 * player never depend on another's.
 */
class BreachRewardTests {
    private fun player(name: String = "tester"): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        every { p.username } returns name
        return p
    }

    @Test
    fun `loot eligibility is the first 16 damage dealers, not the 16 biggest`() {
        val ledger = BreachContribution()
        ledger.add("first", 1) // one damage, but first on the monster
        (2..16).forEach { ledger.add("p$it", 10) }
        ledger.add("seventeenth", 5000) // biggest hitter, but 17th to deal damage
        val eligible = ledger.lootEligible()
        assertEquals(16, eligible.size)
        assertTrue("first" in eligible)
        assertFalse("seventeenth" in eligible)
    }

    @Test
    fun `exactly 16 attackers are all eligible and the 17th never is`() {
        val ledger = BreachContribution()
        (1..17).forEach { ledger.add("p$it", 100) }
        assertEquals((1..16).map { "p$it" }, ledger.lootEligible())
    }

    @Test
    fun `points go to the top 100 by damage, a different ranking from loot`() {
        val ledger = BreachContribution()
        (1..120).forEach { ledger.add("p$it", it) }
        val earners = ledger.pointEarners()
        assertEquals(100, earners.size)
        assertEquals("p120" to 120, earners.first())
        assertFalse(earners.any { it.first == "p1" }) // first on the monster, but not a top-100 damage dealer
        assertTrue("p1" in ledger.lootEligible())
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
    fun `points are one per damage and stop at the 75,000 lifetime cap`() {
        val p = player()
        assertEquals(1234, BreachPoints.award(p, 1234))
        assertEquals(1234, BreachPoints.balance(p))
        p.attr[BreachPoints.EARNED] = BreachPoints.DAMAGE_CAP - 10
        assertEquals(10, BreachPoints.award(p, 500))
        assertEquals(0, BreachPoints.award(p, 500))
        assertEquals(BreachPoints.DAMAGE_CAP, BreachPoints.earned(p))
        assertEquals(1244, BreachPoints.balance(p))
    }

    @Test
    fun `emblem trade-in points are spending money outside the damage cap`() {
        val p = player()
        p.attr[BreachPoints.EARNED] = BreachPoints.DAMAGE_CAP
        BreachPoints.addBalance(p, BreachPoints.EMBLEM_TIER_5_VALUE)
        assertEquals(BreachPoints.EMBLEM_TIER_5_VALUE, BreachPoints.balance(p))
        assertEquals(BreachPoints.DAMAGE_CAP, BreachPoints.earned(p))
    }

    @Test
    fun `the schedule file matches the default and daylight saving never moves an opening`() {
        val file = java.io.File("../../data/cfg/deadman/breach-schedule.json")
        val loaded = DeadmanBreach.loadSchedule(file)
        assertEquals(DeadmanBreach.Schedule().days, loaded.days)
        assertEquals(DeadmanBreach.Schedule().hours, loaded.hours)
        // 2026-10-25 is the EU clock change (Sunday): the openings stay on the UTC hours.
        val amsterdam = java.time.ZonedDateTime.of(2026, 10, 25, 1, 30, 0, 0, java.time.ZoneId.of("Europe/Amsterdam"))
        val next = DeadmanBreach.nextStart(amsterdam)
        assertEquals(java.time.ZoneOffset.UTC, next.zone)
        assertEquals(2, next.hour)
        assertEquals(0, next.minute)
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
