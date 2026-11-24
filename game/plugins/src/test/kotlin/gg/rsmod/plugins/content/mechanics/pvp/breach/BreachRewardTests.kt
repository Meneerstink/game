package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.HitType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
    fun `breach guardian immunity also rejects critical melee and ranged hits`() {
        mockkObject(DeadmanBreach)
        try {
            val source = player()
            for (id in listOf(14446, 14447, 14448)) {
                val npc = mockk<Npc>(relaxed = true)
                every { npc.id } returns id
                every { DeadmanBreach.isBreachNpc(npc) } returns true
                for (type in listOf(HitType.MELEE, HitType.CRIT_MELEE, HitType.RANGE, HitType.CRIT_RANGE)) {
                    assertEquals(0, DeadmanBreach.modifyIncomingDamage(npc, type, 40, source, true), "guardian $id $type")
                }
                assertEquals(40, DeadmanBreach.modifyIncomingDamage(npc, HitType.MAGIC, 40, source, true))
            }
        } finally { unmockkObject(DeadmanBreach) }
    }

    @Test
    fun `Shade summon flat armour adds three per successful player physical hit only`() {
        mockkObject(DeadmanBreach)
        try {
            val shade = mockk<Npc>(relaxed = true)
            every { shade.id } returns 14472
            every { DeadmanBreach.isBreachNpc(shade) } returns true
            val source = player()
            for (type in listOf(HitType.MELEE, HitType.CRIT_MELEE, HitType.RANGE, HitType.CRIT_RANGE)) {
                assertEquals(13, DeadmanBreach.modifyIncomingDamage(shade, type, 10, source, true))
                assertEquals(0, DeadmanBreach.modifyIncomingDamage(shade, type, 0, source, false), "misses stay zero")
                assertEquals(3, DeadmanBreach.modifyIncomingDamage(shade, type, 0, source, true), "accurate zero roll gets the flat bonus")
                assertEquals(10, DeadmanBreach.modifyIncomingDamage(shade, type, 10, mockk<Npc>(relaxed = true), true), "summons ignore flat armour")
            }
            val lizard = mockk<Npc>(relaxed = true)
            every { lizard.id } returns 14456
            every { DeadmanBreach.isBreachNpc(lizard) } returns true
            assertEquals(11, DeadmanBreach.modifyIncomingDamage(lizard, HitType.MELEE, 10, source, true), "Sulphur Lizard has -1 flat armour")
            assertEquals(10, DeadmanBreach.modifyIncomingDamage(lizard, HitType.MAGIC, 10, source, true))
            for (type in listOf(HitType.MAGIC, HitType.CRIT_MAGIC, HitType.REGULAR_HIT)) {
                assertEquals(10, DeadmanBreach.modifyIncomingDamage(shade, type, 10, source, true), "magic and other damage ignore flat armour")
            }
        } finally { unmockkObject(DeadmanBreach) }
    }

    @Test
    fun `direct hits credit breach damage on impact after the HP cap and credit a summon's owner`() {
        mockkObject(DeadmanBreach)
        try {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.attr } returns AttributeMap()
            every { DeadmanBreach.isBreachNpc(npc) } returns true
            val owner = player("owner")
            val hit = npc.hit(80, delay = 5, source = owner)
            assertEquals(0, DeadmanBreach.contribution(npc).size, "queued/cancelled hits must not claim a slot")
            hit.hitmarks.single().damage = 1 // actual engine cap: only one HP remained at impact
            val actions = hit.javaClass.declaredMethods.single { it.name.startsWith("invokeActions") }.apply { isAccessible = true }
            actions.invoke(hit)
            assertEquals(1, DeadmanBreach.contribution(npc).damageOf("owner"), "overkill is never damage dealt")
            npc.hit(500, delay = 5, source = owner) // cancelled: no impact actions run
            assertEquals(1, DeadmanBreach.contribution(npc).damageOf("owner"))
            val summon = mockk<Npc>(relaxed = true)
            every { summon.attr } returns AttributeMap()
            summon.attr[DAMAGE_CREDIT_ATTR] = java.lang.ref.WeakReference<Pawn>(owner)
            val summonedHit = npc.hit(4, source = summon)
            assertEquals(1, DeadmanBreach.contribution(npc).damageOf("owner"))
            actions.invoke(summonedHit)
            assertEquals(5, DeadmanBreach.contribution(npc).damageOf("owner"), "the summoned attack belongs to its player")
        } finally { unmockkObject(DeadmanBreach) }
    }

    @Test
    fun `recoil poison and venom only credit capped impact damage and retain the queued poisoner`() {
        mockkObject(DeadmanBreach)
        try {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.attr } returns AttributeMap()
            every { DeadmanBreach.isBreachNpc(npc) } returns true
            val owner = player("original")
            val other = player("later")
            val recoil = npc.hit(30, type = HitType.REFLECTED)
            DeadmanBreach.recordHit(npc, owner, recoil)
            val actions = recoil.javaClass.declaredMethods.single { it.name.startsWith("invokeActions") }.apply { isAccessible = true }
            assertEquals(0, DeadmanBreach.contribution(npc).size)
            recoil.hitmarks.single().damage = 1
            actions.invoke(recoil)
            assertEquals(1, DeadmanBreach.contribution(npc).damageOf("original"))
            for (type in listOf(HitType.POISON, HitType.VENOM)) {
                npc.attr[LAST_HIT_BY_ATTR] = java.lang.ref.WeakReference<Pawn>(owner)
                DeadmanBreach.notePoisoner(npc)
                val tick = npc.hit(20, type = type)
                DeadmanBreach.recordDotDamage(npc, tick)
                npc.attr[LAST_HIT_BY_ATTR] = java.lang.ref.WeakReference<Pawn>(other)
                DeadmanBreach.notePoisoner(npc) // another player poisons before the pending tick lands
                tick.hitmarks.single().damage = 1
                actions.invoke(tick)
            }
            assertEquals(3, DeadmanBreach.contribution(npc).damageOf("original"))
            assertEquals(0, DeadmanBreach.contribution(npc).damageOf("later"))
        } finally { unmockkObject(DeadmanBreach) }
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
